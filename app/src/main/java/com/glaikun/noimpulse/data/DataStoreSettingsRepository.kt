package com.glaikun.noimpulse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.glaikun.noimpulse.api.FrictionRule
import com.glaikun.noimpulse.api.FrictionType
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

    override val appFriction: Flow<Map<String, List<FrictionRule>>> =
        dataStore.data.map { prefs ->
            (prefs[Keys.APP_FRICTION] ?: emptySet())
                .mapNotNull(::decodeFriction)
                .groupBy({ it.first }, { it.second })
        }

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

    override suspend fun addAppFriction(packageName: String, rule: FrictionRule) {
        dataStore.edit { prefs ->
            prefs[Keys.APP_FRICTION] =
                (prefs[Keys.APP_FRICTION] ?: emptySet()) + encodeFriction(packageName, rule)
        }
    }

    override suspend fun removeAppFriction(packageName: String, rule: FrictionRule) {
        dataStore.edit { prefs ->
            prefs[Keys.APP_FRICTION] =
                (prefs[Keys.APP_FRICTION] ?: emptySet()) - encodeFriction(packageName, rule)
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
        val APP_FRICTION = stringSetPreferencesKey("app_friction")
        val DRAWER_LAUNCHES_TODAY = intPreferencesKey("drawer_launches_today")
        val DRAWER_COUNTER_DATE = stringPreferencesKey("drawer_counter_date")
    }
}

/** Encodes a rule as "package|TYPE|param". Package names never contain '|'. */
private fun encodeFriction(packageName: String, rule: FrictionRule): String =
    "$packageName|${rule.type.name}|${rule.param}"

/** Inverse of [encodeFriction]; returns null for malformed or unknown entries. */
private fun decodeFriction(encoded: String): Pair<String, FrictionRule>? {
    val parts = encoded.split('|')
    if (parts.size != 3) return null
    val type = FrictionType.entries.find { it.name == parts[1] } ?: return null
    val param = parts[2].toIntOrNull() ?: return null
    return parts[0] to FrictionRule(type, param)
}
