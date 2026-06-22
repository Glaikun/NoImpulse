package com.glaikun.noimpulse.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glaikun.noimpulse.api.AppEntry
import com.glaikun.noimpulse.api.DailyUsage
import com.glaikun.noimpulse.api.SettingsSnapshot
import com.glaikun.noimpulse.api.StatusSnapshot
import com.glaikun.noimpulse.di.IoDispatcher
import com.glaikun.noimpulse.interfaces.LauncherAppsSource
import com.glaikun.noimpulse.interfaces.SettingsRepository
import com.glaikun.noimpulse.interfaces.UsageStatsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class HomeViewModel @Inject constructor(
    app: Application,
    private val usageStats: UsageStatsSource,
    private val launcher: LauncherAppsSource,
    private val settings: SettingsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(app) {

    data class UiState(
        val time: String = "",
        val date: String = "",
        val batteryPercent: Int = -1,
        val setupComplete: Boolean? = null,  // null = still loading from DataStore
        val usageAccessGranted: Boolean = false,
        val isDefaultHome: Boolean = false,
        val pickupCount: Int? = null,        // null = usage access not granted
        val screenOnMinutes: Int? = null,
        val allowedApps: List<AppEntry> = emptyList(),
        val homeApps: List<AppEntry> = emptyList(),
        val drawerLaunchesToday: Int = 0,
    )

    /** Fires an immediate status re-read (seeded so the first subscriber loads at once). */
    private val statusRefresh = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).apply { tryEmit(Unit) }

    /** Re-reads usage access + default-home status now — call after returning from settings. */
    fun refreshStatus() {
        statusRefresh.tryEmit(Unit)
    }

    /**
     * Each source streams at its own natural cadence and they are combined into the
     * UI state. Collection (and therefore every loop/receiver below) is alive only
     * while the screen is observing, thanks to [SharingStarted.WhileSubscribed].
     */
    val state: StateFlow<UiState> =
        combine(
            minuteTicks(),
            batteryPercent(),
            statusSnapshots(),
            settingsSnapshots(),
            homeApps(),
        ) { _, battery, status, settingsSnap, homeApps ->
            UiState(
                time = formatTime(),
                date = formatDate(),
                batteryPercent = battery,
                setupComplete = settingsSnap.setupComplete,
                usageAccessGranted = status.usageGranted,
                isDefaultHome = status.isDefaultHome,
                pickupCount = status.usage?.pickupCount,
                screenOnMinutes = status.usage?.screenOnMinutes,
                allowedApps = settingsSnap.allowedApps,
                homeApps = homeApps,
                drawerLaunchesToday = status.drawerLaunchesToday,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState(),
        )

    /**
     * All launchable apps for the picker, most-recently-used first (then alphabetical).
     * Re-reads on [statusRefresh] so it re-sorts once usage access is granted on resume,
     * and only runs while a screen observes it.
     */
    val installedApps: StateFlow<List<AppEntry>> =
        statusRefresh
            .map {
                val apps = launcher.installedLaunchableApps()       // already labelled
                val rank = usageStats.recentlyUsedPackages()
                    .withIndex()
                    .associate { (index, pkg) -> pkg to index }
                apps.sortedWith(
                    compareBy(
                        { rank[it.packageName] ?: Int.MAX_VALUE },  // recents first, in order
                        { it.label.lowercase() },                   // then alphabetical
                    ),
                )
            }
            .flowOn(ioDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        seedEssentialsIfFresh()
    }

    /** The greyscale-rendered launcher icon for [packageName] (cached in the source). */
    fun loadIcon(packageName: String): android.graphics.drawable.Drawable? =
        launcher.loadIcon(packageName)

    fun completeSetup() {
        viewModelScope.launch { settings.setSetupComplete(true) }
    }

    fun setAppAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { settings.setAppAllowed(packageName, allowed) }
    }

    /**
     * Bumps the drawer-launch counter, but ONLY for non-allowlisted packages —
     * launching an app that's already on the home screen via the drawer shouldn't
     * count against the friction budget. Caller still needs to invoke `launchApp`
     * separately; this is just the persistence side.
     */
    fun recordDrawerLaunch(packageName: String) {
        viewModelScope.launch {
            val allowed = settings.allowedPackages.first()
            if (packageName !in allowed) {
                settings.recordDrawerLaunch()
            }
        }
    }

    /**
     * On first launch (setup incomplete and the allowlist still empty) pre-allow the
     * device's default settings/dialer/SMS/maps/clock so the home screen has something
     * usable out of the box. Idempotent — once the allowlist is non-empty it no-ops,
     * so users who turn an essential off won't have it silently re-added.
     */
    private fun seedEssentialsIfFresh() {
        viewModelScope.launch(ioDispatcher) {
            if (settings.setupComplete.first()) return@launch
            if (settings.allowedPackages.first().isNotEmpty()) return@launch
            val launchable = launcher.installedLaunchableApps()
                .mapTo(HashSet()) { it.packageName }
            launcher.essentialPackages()
                .filter { it in launchable }
                .forEach { settings.setAppAllowed(it, true) }
        }
    }

    /** Emits immediately, then once on every wall-clock minute boundary (drift-free). */
    private fun minuteTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            val now = LocalTime.now()
            val msToNextMinute = (60L - now.second) * 1_000L - now.nano / 1_000_000L
            delay(msToNextMinute.milliseconds)
        }
    }

    /** Emits the current battery percentage, then on every battery-change broadcast. */
    private fun batteryPercent(): Flow<Int> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(readBattery(intent))
            }
        }
        val sticky = getApplication<Application>()
            .registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        // Seed combine with a value straight away so it can produce its first state.
        trySend(sticky?.let { readBattery(it) } ?: -1)
        awaitClose { getApplication<Application>().unregisterReceiver(receiver) }
    }

    /**
     * Re-reads usage access, default-home status and stats off the main thread on every
     * [statusRefresh] signal and on a periodic poll, whichever comes first. The drawer
     * counter is layered in reactively via [combine] so a [recordDrawerLaunch] propagates
     * immediately rather than on the next poll tick.
     */
    private fun statusSnapshots(): Flow<StatusSnapshot> =
        combine(
            merge(statusRefresh, statusPollTicks())
                .map {
                    Triple(
                        usageStats.hasUsageAccess(),
                        launcher.isDefaultHome(),
                        usageStats.queryToday(),
                    )
                }
                .flowOn(ioDispatcher),
            settings.drawerLaunchesToday,
        ) { (granted, isDefault, usage), drawerCount ->
            StatusSnapshot(
                usageGranted = granted,
                isDefaultHome = isDefault,
                usage = usage,
                drawerLaunchesToday = drawerCount,
            )
        }

    private fun statusPollTicks(): Flow<Unit> = flow {
        while (true) {
            delay(USAGE_POLL_INTERVAL_MS.milliseconds)
            emit(Unit)
        }
    }

    /**
     * The fixed home-screen apps (phone, messages, camera, maps). Re-resolved on every
     * [statusRefresh] so changing a system default app reflects on resume.
     */
    private fun homeApps(): Flow<List<AppEntry>> =
        statusRefresh.map { launcher.homeScreenApps() }.flowOn(ioDispatcher)

    /** Resolves the persisted allowlist (package names) into displayable [AppEntry]s. */
    private fun settingsSnapshots(): Flow<SettingsSnapshot> =
        combine(settings.setupComplete, settings.allowedPackages) { complete, pkgs ->
            SettingsSnapshot(
                setupComplete = complete,
                allowedApps = pkgs.mapNotNull { launcher.appEntryFor(it) }
                    .sortedBy { it.label.lowercase() },
            )
        }.flowOn(ioDispatcher)

    private fun readBattery(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level == -1 || scale == 0) -1 else (level * 100) / scale
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val USAGE_POLL_INTERVAL_MS = 30_000L

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

        internal fun formatTime(): String = LocalTime.now().format(TIME_FORMAT)
        internal fun formatDate(): String = LocalDate.now().format(DATE_FORMAT)
    }
}

/** Formats a minute count into a human-readable string, e.g. "2h 15m". */
internal fun formatHours(minutes: Int): String = "${minutes / 60}h ${minutes % 60}m"

/**
 * Number of UUID-tokens the user must retype to open a non-allowlisted app from the
 * drawer, given how many drawer-launches they've already done today. 0–1 → 1 token,
 * 2–3 → 2, 4–5 → 3, 6–7 → 4, 8+ → 5. The base of 1 means the first launch is never free.
 */
internal fun tokensRequired(drawerLaunchesToday: Int): Int =
    (drawerLaunchesToday / 2 + 1).coerceIn(1, 5)
