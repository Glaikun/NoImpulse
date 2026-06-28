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
import com.glaikun.noimpulse.model.TimeWindow
import com.glaikun.noimpulse.ui.isRestrictedNow
import com.glaikun.noimpulse.ui.minuteOfDay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        if (!shouldTriggerRefriction(
                packageName = pkg,
                ownPackageName = this.packageName,
                allowedPackages = allowedPackages,
                launchablePackages = launchablePackages,
                isInSession = ledger::isInSession,
                restrictedNow = isRestrictedNow(restrictedModeEnabled, allowedWindows, minuteOfDay()),
            )
        ) return

        Log.d(TAG, "Re-friction trigger for $pkg")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_REFRICTION_PACKAGE, pkg)
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

/**
 * Pure filter chain extracted from [FrictionWatchService.onAccessibilityEvent] so it
 * can be unit-tested without the accessibility framework. Returns true when a window-
 * state-change for [packageName] should trigger re-friction.
 *
 * Skip rules (in order):
 *  1. our own package — the gate itself, the launcher home, etc.
 *  2. non-launchable packages — system overlays, IMEs, lock screen.
 *  3. [restrictedNow] — when in restricted time, every other launchable app is bounced,
 *     ignoring the allowlist and the session (the point is to make apps unusable).
 *  4. allowlisted apps — no friction by design.
 *  5. packages already in the current screen-on session.
 */
internal fun shouldTriggerRefriction(
    packageName: String,
    ownPackageName: String,
    allowedPackages: Set<String>,
    launchablePackages: Set<String>,
    isInSession: (String) -> Boolean,
    restrictedNow: Boolean,
): Boolean = when {
    packageName == ownPackageName -> false
    packageName !in launchablePackages -> false
    restrictedNow -> true
    packageName in allowedPackages -> false
    isInSession(packageName) -> false
    else -> true
}
