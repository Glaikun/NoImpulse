package com.glaikun.noimpulse.interfaces

import kotlinx.coroutines.flow.Flow

/** On-device settings, backed by DataStore. Single source of truth for user prefs. */
interface SettingsRepository {
    /** Whether first-run setup has been completed. */
    val setupComplete: Flow<Boolean>

    /** Package names the user has allowed onto the home screen. */
    val allowedPackages: Flow<Set<String>>

    suspend fun setSetupComplete(complete: Boolean)

    suspend fun setAppAllowed(packageName: String, allowed: Boolean)
}
