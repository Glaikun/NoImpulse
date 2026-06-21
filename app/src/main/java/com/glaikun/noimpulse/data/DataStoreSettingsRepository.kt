package com.glaikun.noimpulse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.glaikun.noimpulse.interfaces.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val setupComplete: Flow<Boolean> =
        dataStore.data.map { it[Keys.SETUP_COMPLETE] ?: false }

    override val allowedPackages: Flow<Set<String>> =
        dataStore.data.map { it[Keys.ALLOWED_PACKAGES] ?: emptySet() }

    override val drawerLaunchesToday: Flow<Int> =
        dataStore.data.map { prefs ->
            val today = LocalDate.now().toString()
            if (prefs[Keys.DRAWER_COUNTER_DATE] == today) {
                prefs[Keys.DRAWER_LAUNCHES_TODAY] ?: 0
            } else {
                0
            }
        }

    override suspend fun setSetupComplete(complete: Boolean) {
        dataStore.edit { it[Keys.SETUP_COMPLETE] = complete }
    }

    override suspend fun setAppAllowed(packageName: String, allowed: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ALLOWED_PACKAGES] ?: emptySet()
            prefs[Keys.ALLOWED_PACKAGES] =
                if (allowed) current + packageName else current - packageName
        }
    }

    override suspend fun recordDrawerLaunch() {
        val today = LocalDate.now().toString()
        dataStore.edit { prefs ->
            val sameDay = prefs[Keys.DRAWER_COUNTER_DATE] == today
            val current = if (sameDay) prefs[Keys.DRAWER_LAUNCHES_TODAY] ?: 0 else 0
            prefs[Keys.DRAWER_LAUNCHES_TODAY] = current + 1
            if (!sameDay) prefs[Keys.DRAWER_COUNTER_DATE] = today
        }
    }

    private object Keys {
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
        val DRAWER_LAUNCHES_TODAY = intPreferencesKey("drawer_launches_today")
        val DRAWER_COUNTER_DATE = stringPreferencesKey("drawer_counter_date")
    }
}
