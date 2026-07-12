package com.glaikun.noimpulse.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.glaikun.noimpulse.MainActivity
import com.glaikun.noimpulse.data.FrictionSessionLedger
import com.glaikun.noimpulse.data.LauncherAppsSource
import com.glaikun.noimpulse.data.SettingsRepository
import com.glaikun.noimpulse.data.UsageStatsSource
import com.glaikun.noimpulse.model.FrictionKind
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import com.glaikun.noimpulse.model.TimeWindow
import com.glaikun.noimpulse.ui.exceededDailyLimit
import com.glaikun.noimpulse.ui.isRestrictedNow
import com.glaikun.noimpulse.ui.minuteOfDay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * Watches foreground app changes and re-launches `MainActivity` with an extra
 * naming the package whenever a non-allowlisted launchable app returns to the
 * foreground without an active friction session.
 *
 * Privacy posture (see `res/xml/accessibility_service_config.xml`):
 *  - Only `TYPE_WINDOW_STATE_CHANGED` events.
 *  - `canRetrieveWindowContent="false"` — the OS withholds window content.
 *  - No network access (the app declares no INTERNET permission).
 *
 * Filter chain (in `onAccessibilityEvent`):
 *  - skip our own package, the allowlist, anything that isn't a launchable app
 *    (system overlays, IME), and packages already in the current session.
 *  - what's left triggers a relaunch of [MainActivity] with
 *    [MainActivity.EXTRA_REFRICTION_PACKAGE].
 *
 * Daily limits (LIMIT-kind [FrictionRule]s):
 *  - Packages carrying a limit rule bypass the session short-circuit; their fate is
 *    decided asynchronously in [evaluateLimits] because the minutes query is too heavy
 *    for the event thread. An exceeded limit relaunches [MainActivity] with the verdict
 *    attached, so the block notice shows instead of a passable gate.
 *  - While a minutes-limited app stays in the foreground, a watchdog coroutine re-checks
 *    at the projected limit-hit time — hitting the limit interrupts the app mid-use
 *    rather than waiting for the next launch attempt.
 *  - Bypassing the session short-circuit means [evaluateLimits] re-runs on *every*
 *    subsequent event for a limit-carrying package — including the very event Android
 *    fires for the app's own transition into foreground right after a pass, and any
 *    internal navigation after that. [exceededDailyLimit]'s `inSession` parameter (fed
 *    [FrictionSessionLedger.isInSession]) stops DAILY_LAUNCHES — a per-open count — from
 *    being re-verdicted against a session that already paid for its own open: without
 *    it, the open that pushes the count to the cap gets immediately blocked by its own
 *    increment. DAILY_MINUTES is unaffected — it's meant to keep tripping mid-use, so it
 *    is always checked regardless of session.
 *
 * Session lifetime:
 *  - The [FrictionSessionLedger] records "this app passed friction" entries.
 *  - A [BroadcastReceiver] for [Intent.ACTION_SCREEN_OFF] clears the ledger so
 *    re-friction fires after the next unlock.
 *  - Process death also clears the (in-memory) ledger.
 *
 * Runtime config: [onServiceConnected] mirrors the XML config via
 * [setServiceInfo] — at least one tested device wouldn't deliver events until
 * the call was made, despite a valid manifest meta-data entry.
 */
@AndroidEntryPoint
class FrictionWatchService : AccessibilityService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var launcher: LauncherAppsSource
    @Inject lateinit var ledger: FrictionSessionLedger
    @Inject lateinit var usageStats: UsageStatsSource

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob)

    @Volatile private var allowedPackages: Set<String> = emptySet()
    /** Packages with a launcher Activity — the set the user can choose to allowlist or
     *  friction-lock. Anything not in here (e.g. `com.android.systemui`) is a system
     *  overlay and should never trigger re-friction. */
    @Volatile private var launchablePackages: Set<String> = emptySet()

    /** Restricted Mode state, mirrored reactively so off-hours blocking is up to date. */
    @Volatile private var restrictedModeEnabled: Boolean = false
    @Volatile private var allowedWindows: List<TimeWindow> = emptyList()

    /** Per-app friction rules and today's drawer-launch counts, for the daily limits. */
    @Volatile private var appFriction: Map<String, List<FrictionRule>> = emptyMap()
    @Volatile private var appLaunchesToday: Map<String, Int> = emptyMap()

    /** The launchable app currently holding the foreground (IMEs/overlays don't count —
     *  a keyboard popping up mustn't look like the user left the app). */
    @Volatile private var lastForegroundPackage: String? = null

    /** The one scheduled minutes-limit re-check; at most one limited app is watched at
     *  a time — whichever launchable app was foregrounded last. */
    private var watchdog: Job? = null
    @Volatile private var watchdogPackage: String? = null

    private var allowedCollector: Job? = null
    private var restrictedCollector: Job? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                ledger.clearAll()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")

        // Apply the service config at runtime. The XML meta-data alone wasn't
        // enough to start event delivery on at least one tested device — without
        // this call onAccessibilityEvent was never invoked. Keep the values in sync
        // with res/xml/accessibility_service_config.xml.
        runCatching {
            val info = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 100
                flags = AccessibilityServiceInfo.DEFAULT
            }
            serviceInfo = info
        }.onFailure {
            Log.w(TAG, "setServiceInfo failed", it)
        }

        // Snapshot the launchable-app set once at startup. PackageManager queries
        // are too heavy for the onAccessibilityEvent hot path; install/uninstall is
        // rare enough that a startup-only snapshot is fine for v1 (the service is
        // also re-bound by the system after most install events).
        scope.launch {
            launchablePackages = launcher.installedLaunchableApps()
                .mapTo(HashSet()) { it.packageName }
        }

        // Allowlist is reactive — user can toggle apps in/out at runtime.
        allowedCollector = scope.launch {
            settings.allowedPackages.collect { allowed -> allowedPackages = allowed }
        }

        // Restricted Mode is reactive too — toggling it or its windows takes effect at once.
        restrictedCollector = scope.launch {
            settings.restrictedModeEnabled.collect { restrictedModeEnabled = it }
        }
        scope.launch {
            settings.allowedTimeWindows.collect { allowedWindows = it }
        }

        // Daily-limit inputs are reactive — adding/loosening a limit takes effect at once.
        scope.launch {
            settings.appFriction.collect { appFriction = it }
        }
        scope.launch {
            settings.appLaunchesToday.collect { appLaunchesToday = it }
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOffReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in launchablePackages) {
            lastForegroundPackage = pkg
            // The watched app lost the foreground — stop the pending minutes re-check.
            // (It re-arms via evaluateLimits when the app comes back.)
            if (pkg != watchdogPackage) {
                watchdog?.cancel()
                watchdogPackage = null
            }
        }

        val decision = refrictionDecision(
            packageName = pkg,
            ownPackageName = this.packageName,
            allowedPackages = allowedPackages,
            launchablePackages = launchablePackages,
            hasLimitRules = { p -> appFriction[p].orEmpty().any { it.type.kind == FrictionKind.LIMIT } },
            isInSession = ledger::isInSession,
            restrictedNow = isRestrictedNow(restrictedModeEnabled, allowedWindows, minuteOfDay()),
        )
        when (decision) {
            RefrictionDecision.SKIP -> Unit
            RefrictionDecision.TRIGGER -> startRefriction(pkg, overLimit = null)
            RefrictionDecision.EVALUATE_LIMITS -> evaluateLimits(pkg)
        }
    }

    /**
     * Decides a limit-carrying package's fate off the event thread (the minutes query
     * walks the whole day's usage events). Over a limit → relaunch with the verdict;
     * under it but out of session → the normal gate; in session and under a minutes
     * limit → arm the [watchdog] to re-check when the limit is projected to trip.
     *
     * `inSession` (fed to [exceededDailyLimit]) suppresses a DAILY_LAUNCHES verdict while
     * [ledger] already has [pkg] marked passed: that session already paid for its open,
     * so its own count increment must never be turned around and used to block it —
     * without this, the very open that pushes the count to the cap gets immediately
     * bounced by its own increment on the next event (which can be the app's own
     * transition into foreground). DAILY_MINUTES is unaffected, so it can still trip the
     * block mid-use as intended.
     */
    private fun evaluateLimits(pkg: String) {
        scope.launch {
            val rules = appFriction[pkg].orEmpty()
            // Without Usage Access minutes can't be measured; only the launches limit
            // can be enforced then, so skip the heavy query.
            val minutesToday = if (usageStats.hasUsageAccess()) {
                usageStats.foregroundMinutesToday()[pkg] ?: 0
            } else {
                0
            }
            val inSession = ledger.isInSession(pkg)
            val over = exceededDailyLimit(rules, appLaunchesToday[pkg] ?: 0, minutesToday, inSession)
            when {
                over != null -> startRefriction(pkg, over)
                !inSession -> startRefriction(pkg, overLimit = null)
                else -> minutesUntilLimit(rules, minutesToday)?.let { scheduleWatchdog(pkg, it) }
            }
        }
    }

    /**
     * Re-checks [pkg]'s minutes limit once [remainingMinutes] have elapsed. Foreground
     * time only accrues while the app is actually up, so if the user dipped out in the
     * meantime the re-check comes back under the limit and simply re-arms. If the app
     * lost the foreground entirely we bail — a stale check must never bounce the user
     * out of whatever they switched to.
     */
    private fun scheduleWatchdog(pkg: String, remainingMinutes: Int) {
        watchdog?.cancel()
        watchdogPackage = pkg
        watchdog = scope.launch {
            // Floor of one minute: usage events lag a little, so an at-the-boundary
            // reading could otherwise spin in a tight re-check loop.
            delay(remainingMinutes.coerceAtLeast(1).minutes)
            if (lastForegroundPackage == pkg) evaluateLimits(pkg)
        }
    }

    /** Relaunches [MainActivity] to re-gate [pkg]; [overLimit] carries an exceeded
     *  daily limit so the UI can show the block notice instead of a passable gate. */
    private fun startRefriction(pkg: String, overLimit: FrictionRule?) {
        Log.d(TAG, "Re-friction trigger for $pkg" + if (overLimit != null) " (over ${overLimit.type})" else "")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_REFRICTION_PACKAGE, pkg)
            if (overLimit != null) {
                putExtra(MainActivity.EXTRA_REFRICTION_LIMIT_TYPE, overLimit.type.name)
                putExtra(MainActivity.EXTRA_REFRICTION_LIMIT_PARAM, overLimit.param)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // No long-running work to interrupt.
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        runCatching { unregisterReceiver(screenOffReceiver) }
        allowedCollector?.cancel()
        restrictedCollector?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FrictionWatch"
    }
}

/** What [FrictionWatchService.onAccessibilityEvent] should do with a window-state change. */
internal enum class RefrictionDecision { SKIP, TRIGGER, EVALUATE_LIMITS }

/**
 * Pure filter chain extracted from [FrictionWatchService.onAccessibilityEvent] so it
 * can be unit-tested without the accessibility framework.
 *
 * Rules (in order):
 *  1. our own package — the gate itself, the launcher home, etc. → [RefrictionDecision.SKIP]
 *  2. non-launchable packages — system overlays, IMEs, lock screen → SKIP
 *  3. allowlisted apps — no friction by design, and never blocked by Restricted Mode → SKIP
 *  4. [restrictedNow] — in restricted time every *non-allowlisted* launchable app is bounced,
 *     ignoring the session (the point is to make non-allowed apps unusable off-hours)
 *     → [RefrictionDecision.TRIGGER]
 *  5. [hasLimitRules] — daily limits also ignore the session (an in-session pass mustn't
 *     outlive the limit), but the verdict needs a usage query too heavy for the event
 *     thread → [RefrictionDecision.EVALUATE_LIMITS]
 *  6. packages already in the current screen-on session → SKIP
 *  7. everything else → TRIGGER
 */
internal fun refrictionDecision(
    packageName: String,
    ownPackageName: String,
    allowedPackages: Set<String>,
    launchablePackages: Set<String>,
    hasLimitRules: (String) -> Boolean,
    isInSession: (String) -> Boolean,
    restrictedNow: Boolean,
): RefrictionDecision = when {
    packageName == ownPackageName -> RefrictionDecision.SKIP
    packageName !in launchablePackages -> RefrictionDecision.SKIP
    packageName in allowedPackages -> RefrictionDecision.SKIP
    restrictedNow -> RefrictionDecision.TRIGGER
    hasLimitRules(packageName) -> RefrictionDecision.EVALUATE_LIMITS
    isInSession(packageName) -> RefrictionDecision.SKIP
    else -> RefrictionDecision.TRIGGER
}

/**
 * Minutes of foreground use left before the app's DAILY_MINUTES limit trips, or null
 * when no such rule is assigned. Zero or negative means the limit is already hit —
 * the caller decides the scheduling floor.
 */
internal fun minutesUntilLimit(rules: List<FrictionRule>, minutesToday: Int): Int? =
    rules.firstOrNull { it.type == FrictionType.DAILY_MINUTES }
        ?.let { it.param - minutesToday }
