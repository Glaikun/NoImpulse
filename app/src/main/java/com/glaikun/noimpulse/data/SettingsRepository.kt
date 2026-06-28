package com.glaikun.noimpulse.data

import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.TextSize
import com.glaikun.noimpulse.model.ThemeMode
import com.glaikun.noimpulse.model.TimeWindow
import kotlinx.coroutines.flow.Flow

/** On-device settings, backed by DataStore. Single source of truth for user prefs. */
interface SettingsRepository {
    /** Whether the user has acknowledged the pre-setup intro screen. */
    val introSeen: Flow<Boolean>

    /** Whether first-run setup has been completed. */
    val setupComplete: Flow<Boolean>

    /** Package names the user has allowed onto the home screen. */
    val allowedPackages: Flow<Set<String>>

    /** Per-app opening-friction rules, keyed by package name. An app may have several. */
    val appFriction: Flow<Map<String, List<FrictionRule>>>

    /**
     * Today's count of drawer-launches (non-allowlisted apps opened via the friction
     * drawer). Emits `0` when the persisted date is stale — the next [recordDrawerLaunch]
     * will atomically reset and record today's first launch.
     */
    val drawerLaunchesToday: Flow<Int>

    /** Whether Restricted Mode is on. When on, apps are unusable outside [allowedTimeWindows]. */
    val restrictedModeEnabled: Flow<Boolean>

    /** The time-of-day windows during which apps stay usable while Restricted Mode is on. */
    val allowedTimeWindows: Flow<List<TimeWindow>>

    /** The chosen colour scheme. */
    val themeMode: Flow<ThemeMode>

    /** The accessibility text-size choice. */
    val textSize: Flow<TextSize>

    suspend fun setIntroSeen(seen: Boolean)

    suspend fun setSetupComplete(complete: Boolean)

    suspend fun setAppAllowed(packageName: String, allowed: Boolean)

    /** Adds an opening-friction [rule] to [packageName] (no-op if it already has it). */
    suspend fun addAppFriction(packageName: String, rule: FrictionRule)

    /** Removes one opening-friction [rule] from [packageName]. */
    suspend fun removeAppFriction(packageName: String, rule: FrictionRule)

    /** Atomically: reset the counter to 1 if the stored date isn't today, else increment. */
    suspend fun recordDrawerLaunch()

    suspend fun setRestrictedModeEnabled(enabled: Boolean)

    /** Adds an allowed [window] (no-op if it already exists). */
    suspend fun addAllowedWindow(window: TimeWindow)

    /** Removes an allowed [window]. */
    suspend fun removeAllowedWindow(window: TimeWindow)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setTextSize(size: TextSize)
}
