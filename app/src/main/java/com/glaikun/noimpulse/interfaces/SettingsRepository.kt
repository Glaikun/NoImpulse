package com.glaikun.noimpulse.interfaces

import com.glaikun.noimpulse.api.FrictionRule
import kotlinx.coroutines.flow.Flow

/** On-device settings, backed by DataStore. Single source of truth for user prefs. */
interface SettingsRepository {
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

    suspend fun setSetupComplete(complete: Boolean)

    suspend fun setAppAllowed(packageName: String, allowed: Boolean)

    /** Adds an opening-friction [rule] to [packageName] (no-op if it already has it). */
    suspend fun addAppFriction(packageName: String, rule: FrictionRule)

    /** Removes one opening-friction [rule] from [packageName]. */
    suspend fun removeAppFriction(packageName: String, rule: FrictionRule)

    /** Atomically: reset the counter to 1 if the stored date isn't today, else increment. */
    suspend fun recordDrawerLaunch()
}
