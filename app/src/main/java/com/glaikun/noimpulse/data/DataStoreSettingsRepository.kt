package com.glaikun.noimpulse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import com.glaikun.noimpulse.model.TextSize
import com.glaikun.noimpulse.model.ThemeMode
import com.glaikun.noimpulse.model.TimeWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) : SettingsRepository {

    /** Production entry point: Hilt supplies the DataStore and the real clock rides
     *  along. Tests use the primary constructor with a controllable clock so the
     *  day-keyed counters can be exercised across a midnight boundary. */
    @Inject constructor(dataStore: DataStore<Preferences>) :
        this(dataStore, Clock.systemDefaultZone())

    override val introSeen: Flow<Boolean> =
        dataStore.data.map { it[Keys.INTRO_SEEN] ?: false }

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

    override val restrictedModeEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.RESTRICTED_MODE_ENABLED] ?: false }

    override val allowedTimeWindows: Flow<List<TimeWindow>> =
        dataStore.data.map { prefs ->
            (prefs[Keys.ALLOWED_TIME_WINDOWS] ?: emptySet())
                .mapNotNull(::decodeWindow)
                .sortedBy { it.startMinute }
        }

    override val themeMode: Flow<ThemeMode> =
        dataStore.data.map { prefs ->
            prefs[Keys.THEME_MODE]?.let { name ->
                ThemeMode.entries.find { it.name == name }
            } ?: ThemeMode.DARK
        }

    override val textSize: Flow<TextSize> =
        dataStore.data.map { prefs ->
            prefs[Keys.TEXT_SIZE]?.let { name ->
                TextSize.entries.find { it.name == name }
            } ?: TextSize.DEFAULT
        }

    // The day-keyed flows combine with midnightTicks() because their `map` bodies only
    // re-run when DataStore emits (i.e. on a write). Without the tick, a device idling
    // across midnight keeps serving yesterday's counts — a limit-blocked app would stay
    // blocked past the "resets at midnight" promise until some unrelated settings write.
    override val drawerLaunchesToday: Flow<Int> =
        combine(dataStore.data, midnightTicks()) { prefs, _ ->
            val today = LocalDate.now(clock).toString()
            if (prefs[Keys.DRAWER_COUNTER_DATE] == today) {
                prefs[Keys.DRAWER_LAUNCHES_TODAY] ?: 0
            } else {
                0
            }
        }

    override val appLaunchesToday: Flow<Map<String, Int>> =
        combine(dataStore.data, midnightTicks()) { prefs, _ ->
            val today = LocalDate.now(clock).toString()
            if (prefs[Keys.APP_LAUNCHES_DATE] == today) {
                (prefs[Keys.APP_LAUNCHES_TODAY] ?: emptySet())
                    .mapNotNull(::decodeLaunchCount)
                    .toMap()
            } else {
                emptyMap()
            }
        }

    /** Emits immediately, then once shortly after each local midnight. */
    private fun midnightTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(millisUntilNextMidnight(clock))
        }
    }

    override suspend fun setIntroSeen(seen: Boolean) {
        dataStore.edit { it[Keys.INTRO_SEEN] = seen }
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
        val today = LocalDate.now(clock).toString()
        dataStore.edit { prefs ->
            val sameDay = prefs[Keys.DRAWER_COUNTER_DATE] == today
            val current = if (sameDay) prefs[Keys.DRAWER_LAUNCHES_TODAY] ?: 0 else 0
            prefs[Keys.DRAWER_LAUNCHES_TODAY] = current + 1
            if (!sameDay) prefs[Keys.DRAWER_COUNTER_DATE] = today
        }
    }

    override suspend fun recordAppLaunch(packageName: String) {
        val today = LocalDate.now(clock).toString()
        dataStore.edit { prefs ->
            val sameDay = prefs[Keys.APP_LAUNCHES_DATE] == today
            val counts = if (sameDay) {
                (prefs[Keys.APP_LAUNCHES_TODAY] ?: emptySet())
                    .mapNotNull(::decodeLaunchCount)
                    .toMap()
            } else {
                emptyMap()
            }
            val updated = counts + (packageName to (counts[packageName] ?: 0) + 1)
            prefs[Keys.APP_LAUNCHES_TODAY] =
                updated.mapTo(HashSet()) { (pkg, count) -> encodeLaunchCount(pkg, count) }
            if (!sameDay) prefs[Keys.APP_LAUNCHES_DATE] = today
        }
    }

    override suspend fun setRestrictedModeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.RESTRICTED_MODE_ENABLED] = enabled }
    }

    override suspend fun addAllowedWindow(window: TimeWindow) {
        dataStore.edit { prefs ->
            prefs[Keys.ALLOWED_TIME_WINDOWS] =
                (prefs[Keys.ALLOWED_TIME_WINDOWS] ?: emptySet()) + encodeWindow(window)
        }
    }

    override suspend fun removeAllowedWindow(window: TimeWindow) {
        dataStore.edit { prefs ->
            prefs[Keys.ALLOWED_TIME_WINDOWS] =
                (prefs[Keys.ALLOWED_TIME_WINDOWS] ?: emptySet()) - encodeWindow(window)
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setTextSize(size: TextSize) {
        dataStore.edit { it[Keys.TEXT_SIZE] = size.name }
    }

    private object Keys {
        val INTRO_SEEN = booleanPreferencesKey("intro_seen")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
        val APP_FRICTION = stringSetPreferencesKey("app_friction")
        val DRAWER_LAUNCHES_TODAY = intPreferencesKey("drawer_launches_today")
        val DRAWER_COUNTER_DATE = stringPreferencesKey("drawer_counter_date")
        val APP_LAUNCHES_TODAY = stringSetPreferencesKey("app_launches_today")
        val APP_LAUNCHES_DATE = stringPreferencesKey("app_launches_date")
        val RESTRICTED_MODE_ENABLED = booleanPreferencesKey("restricted_mode_enabled")
        val ALLOWED_TIME_WINDOWS = stringSetPreferencesKey("allowed_time_windows")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEXT_SIZE = stringPreferencesKey("text_size")
    }
}

/**
 * Milliseconds from the clock's now until the next local midnight. Always positive:
 * the next midnight is strictly after now, and `atStartOfDay(zone)` resolves DST gaps
 * to the first valid instant of the day.
 */
internal fun millisUntilNextMidnight(clock: Clock): Long {
    val nextMidnight = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant()
    return Duration.between(clock.instant(), nextMidnight).toMillis()
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

/** Encodes a per-app launch count as "package|count". Package names never contain '|'. */
private fun encodeLaunchCount(packageName: String, count: Int): String = "$packageName|$count"

/** Inverse of [encodeLaunchCount]; returns null for malformed entries. */
private fun decodeLaunchCount(encoded: String): Pair<String, Int>? {
    val parts = encoded.split('|')
    if (parts.size != 2) return null
    val count = parts[1].toIntOrNull() ?: return null
    return parts[0] to count
}

/** Encodes a window as "start|end" (minutes since midnight). */
private fun encodeWindow(window: TimeWindow): String = "${window.startMinute}|${window.endMinute}"

/** Inverse of [encodeWindow]; returns null for malformed entries. */
private fun decodeWindow(encoded: String): TimeWindow? {
    val parts = encoded.split('|')
    if (parts.size != 2) return null
    val start = parts[0].toIntOrNull() ?: return null
    val end = parts[1].toIntOrNull() ?: return null
    return TimeWindow(start, end)
}
