package com.glaikun.noimpulse.interfaces

import kotlinx.coroutines.flow.Flow

/** On-device settings, backed by DataStore. Single source of truth for user prefs. */
interface SettingsRepository {
    /** Whether first-run setup has been completed. */
    val setupComplete: Flow<Boolean>

    /** Package names the user has allowed onto the home screen. */
    val allowedPackages: Flow<Set<String>>

    /**
     * Today's count of drawer-launches (non-allowlisted apps opened via the friction
     * drawer). Emits `0` when the persisted date is stale — the next [recordDrawerLaunch]
     * will atomically reset and record today's first launch.
     */
    val drawerLaunchesToday: Flow<Int>

    suspend fun setSetupComplete(complete: Boolean)

    suspend fun setAppAllowed(packageName: String, allowed: Boolean)

    /** Atomically: reset the counter to 1 if the stored date isn't today, else increment. */
    suspend fun recordDrawerLaunch()
}
