package com.glaikun.noimpulse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import com.glaikun.noimpulse.model.TextSize
import com.glaikun.noimpulse.model.ThemeMode
import com.glaikun.noimpulse.model.TimeWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var fileCounter = 0

    // Duplicated key strings to keep the repository's Keys object private. If a name
    // ever changes, both production and these tests must move together — that's the
    // intended coupling.
    private val drawerCountKey = intPreferencesKey("drawer_launches_today")
    private val drawerDateKey = stringPreferencesKey("drawer_counter_date")
    private val appLaunchesKey = stringSetPreferencesKey("app_launches_today")
    private val appLaunchesDateKey = stringPreferencesKey("app_launches_date")

    /** Builds a repository over a fresh, test-scoped DataStore file. */
    private fun TestScope.newRepo(
        clock: Clock = Clock.systemDefaultZone(),
    ): SettingsRepository = newRepoWithStore(clock).first

    /** Same as [newRepo] but also returns the underlying DataStore for preseeding. */
    private fun TestScope.newRepoWithStore(
        clock: Clock = Clock.systemDefaultZone(),
    ): Pair<SettingsRepository, DataStore<Preferences>> {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tmp.root, "settings_${fileCounter++}.preferences_pb")
        }
        return DataStoreSettingsRepository(dataStore, clock) to dataStore
    }

    @Test
    fun `setupComplete defaults to false`() = runTest {
        assertFalse(newRepo().setupComplete.first())
    }

    @Test
    fun `setSetupComplete persists true`() = runTest {
        val repo = newRepo()
        repo.setSetupComplete(true)
        assertTrue(repo.setupComplete.first())
    }

    @Test
    fun `introSeen defaults to false`() = runTest {
        assertFalse(newRepo().introSeen.first())
    }

    @Test
    fun `setIntroSeen persists true`() = runTest {
        val repo = newRepo()
        repo.setIntroSeen(true)
        assertTrue(repo.introSeen.first())
    }

    @Test
    fun `introSeen and setupComplete are independent flags`() = runTest {
        val repo = newRepo()
        repo.setIntroSeen(true)
        assertTrue(repo.introSeen.first())
        assertFalse(repo.setupComplete.first())
    }

    @Test
    fun `allowedPackages defaults to empty`() = runTest {
        assertTrue(newRepo().allowedPackages.first().isEmpty())
    }

    @Test
    fun `setAppAllowed adds and removes a package`() = runTest {
        val repo = newRepo()

        repo.setAppAllowed("com.maps", true)
        repo.setAppAllowed("com.phone", true)
        assertEquals(setOf("com.maps", "com.phone"), repo.allowedPackages.first())

        repo.setAppAllowed("com.maps", false)
        assertEquals(setOf("com.phone"), repo.allowedPackages.first())
    }

    @Test
    fun `setAppAllowed is idempotent`() = runTest {
        val repo = newRepo()
        repo.setAppAllowed("com.maps", true)
        repo.setAppAllowed("com.maps", true)
        assertEquals(setOf("com.maps"), repo.allowedPackages.first())
    }

    // ── Per-app friction ─────────────────────────────────────────────────────

    @Test
    fun `appFriction defaults to empty`() = runTest {
        assertTrue(newRepo().appFriction.first().isEmpty())
    }

    @Test
    fun `app frictions stack per app and remove individually`() = runTest {
        val repo = newRepo()

        repo.addAppFriction("com.maps", FrictionRule(FrictionType.TIMED_WAIT, 30))
        repo.addAppFriction("com.maps", FrictionRule(FrictionType.MATH, 1))
        repo.addAppFriction("com.twitter", FrictionRule(FrictionType.REFLECTION, 3))

        assertEquals(
            // sorted for a deterministic assertion — stored order isn't guaranteed
            listOf(FrictionRule(FrictionType.MATH, 1), FrictionRule(FrictionType.TIMED_WAIT, 30)),
            repo.appFriction.first()["com.maps"]!!.sortedBy { it.type.name },
        )
        assertEquals(
            listOf(FrictionRule(FrictionType.REFLECTION, 3)),
            repo.appFriction.first()["com.twitter"],
        )

        // Adding a duplicate is a no-op (set semantics).
        repo.addAppFriction("com.maps", FrictionRule(FrictionType.MATH, 1))
        assertEquals(2, repo.appFriction.first()["com.maps"]!!.size)

        // Remove one rule; the other remains.
        repo.removeAppFriction("com.maps", FrictionRule(FrictionType.TIMED_WAIT, 30))
        assertEquals(
            listOf(FrictionRule(FrictionType.MATH, 1)),
            repo.appFriction.first()["com.maps"],
        )

        // Removing the last rule drops the app from the map.
        repo.removeAppFriction("com.maps", FrictionRule(FrictionType.MATH, 1))
        assertNull(repo.appFriction.first()["com.maps"])
    }

    @Test
    fun `appFriction skips malformed entries`() = runTest {
        val (repo, store) = newRepoWithStore()
        store.edit { prefs ->
            prefs[stringSetPreferencesKey("app_friction")] = setOf(
                "com.maps|TIMED_WAIT|30",   // valid
                "com.bad|NOPE|5",           // unknown type
                "com.bad2|MATH|x",          // non-int param
                "garbage",                  // wrong shape
            )
        }

        assertEquals(
            mapOf("com.maps" to listOf(FrictionRule(FrictionType.TIMED_WAIT, 30))),
            repo.appFriction.first(),
        )
    }

    // ── Restricted Mode ──────────────────────────────────────────────────────

    @Test
    fun `restrictedModeEnabled defaults to false`() = runTest {
        assertFalse(newRepo().restrictedModeEnabled.first())
    }

    @Test
    fun `setRestrictedModeEnabled persists`() = runTest {
        val repo = newRepo()
        repo.setRestrictedModeEnabled(true)
        assertTrue(repo.restrictedModeEnabled.first())
        repo.setRestrictedModeEnabled(false)
        assertFalse(repo.restrictedModeEnabled.first())
    }

    @Test
    fun `allowedTimeWindows defaults to empty`() = runTest {
        assertTrue(newRepo().allowedTimeWindows.first().isEmpty())
    }

    @Test
    fun `allowed windows add, sort by start, and remove individually`() = runTest {
        val repo = newRepo()
        val morning = TimeWindow(7 * 60, 9 * 60)
        val evening = TimeWindow(18 * 60, 22 * 60)

        repo.addAllowedWindow(evening)
        repo.addAllowedWindow(morning)
        // Sorted by start minute regardless of insertion order.
        assertEquals(listOf(morning, evening), repo.allowedTimeWindows.first())

        // Adding a duplicate is a no-op (set semantics).
        repo.addAllowedWindow(morning)
        assertEquals(2, repo.allowedTimeWindows.first().size)

        repo.removeAllowedWindow(morning)
        assertEquals(listOf(evening), repo.allowedTimeWindows.first())
    }

    @Test
    fun `allowedTimeWindows skips malformed entries`() = runTest {
        val (repo, store) = newRepoWithStore()
        store.edit { prefs ->
            prefs[stringSetPreferencesKey("allowed_time_windows")] = setOf(
                "540|1020",   // valid 09:00–17:00
                "x|1020",     // non-int start
                "540",        // wrong shape
                "garbage",
            )
        }

        assertEquals(listOf(TimeWindow(540, 1020)), repo.allowedTimeWindows.first())
    }

    // ── Appearance (theme + text size) ───────────────────────────────────────

    @Test
    fun `themeMode defaults to dark`() = runTest {
        assertEquals(ThemeMode.DARK, newRepo().themeMode.first())
    }

    @Test
    fun `setThemeMode persists`() = runTest {
        val repo = newRepo()
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.first())
    }

    @Test
    fun `themeMode falls back to dark for an unknown stored value`() = runTest {
        val (repo, store) = newRepoWithStore()
        store.edit { it[stringPreferencesKey("theme_mode")] = "PUCE" }
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
    }

    @Test
    fun `textSize defaults to default`() = runTest {
        assertEquals(TextSize.DEFAULT, newRepo().textSize.first())
    }

    @Test
    fun `setTextSize persists`() = runTest {
        val repo = newRepo()
        repo.setTextSize(TextSize.LARGEST)
        assertEquals(TextSize.LARGEST, repo.textSize.first())
    }

    @Test
    fun `textSize falls back to default for an unknown stored value`() = runTest {
        val (repo, store) = newRepoWithStore()
        store.edit { it[stringPreferencesKey("text_size")] = "HUGE" }
        assertEquals(TextSize.DEFAULT, repo.textSize.first())
    }

    // ── Drawer-launch counter ────────────────────────────────────────────────

    @Test
    fun `drawerLaunchesToday defaults to 0`() = runTest {
        assertEquals(0, newRepo().drawerLaunchesToday.first())
    }

    @Test
    fun `first recordDrawerLaunch sets count to 1 with today's date`() = runTest {
        val (repo, store) = newRepoWithStore()
        repo.recordDrawerLaunch()

        assertEquals(1, repo.drawerLaunchesToday.first())
        val prefs = store.data.first()
        assertEquals(LocalDate.now().toString(), prefs[drawerDateKey])
        assertEquals(1, prefs[drawerCountKey])
    }

    @Test
    fun `subsequent recordDrawerLaunch calls increment within the same day`() = runTest {
        val repo = newRepo()
        repo.recordDrawerLaunch()
        repo.recordDrawerLaunch()
        repo.recordDrawerLaunch()

        assertEquals(3, repo.drawerLaunchesToday.first())
    }

    @Test
    fun `drawerLaunchesToday emits 0 when stored date is yesterday`() = runTest {
        val (repo, store) = newRepoWithStore()
        val yesterday = LocalDate.now().minusDays(1).toString()
        store.edit { prefs ->
            prefs[drawerCountKey] = 7
            prefs[drawerDateKey] = yesterday
        }

        assertEquals(0, repo.drawerLaunchesToday.first())
    }

    @Test
    fun `recordDrawerLaunch resets count when stored date is yesterday`() = runTest {
        val (repo, store) = newRepoWithStore()
        val yesterday = LocalDate.now().minusDays(1).toString()
        store.edit { prefs ->
            prefs[drawerCountKey] = 7
            prefs[drawerDateKey] = yesterday
        }

        repo.recordDrawerLaunch()

        assertEquals(1, repo.drawerLaunchesToday.first())   // reset, not 8
        val prefs = store.data.first()
        assertEquals(LocalDate.now().toString(), prefs[drawerDateKey])
        assertEquals(1, prefs[drawerCountKey])
    }

    // ── Per-app launch counter (daily-launches limit) ────────────────────────

    @Test
    fun `appLaunchesToday defaults to empty`() = runTest {
        assertTrue(newRepo().appLaunchesToday.first().isEmpty())
    }

    @Test
    fun `recordAppLaunch counts per package within the same day`() = runTest {
        val repo = newRepo()
        repo.recordAppLaunch("com.twitter")
        repo.recordAppLaunch("com.twitter")
        repo.recordAppLaunch("com.maps")

        assertEquals(mapOf("com.twitter" to 2, "com.maps" to 1), repo.appLaunchesToday.first())
    }

    @Test
    fun `appLaunchesToday emits empty when stored date is yesterday`() = runTest {
        val (repo, store) = newRepoWithStore()
        store.edit { prefs ->
            prefs[appLaunchesKey] = setOf("com.twitter|5")
            prefs[appLaunchesDateKey] = LocalDate.now().minusDays(1).toString()
        }

        assertTrue(repo.appLaunchesToday.first().isEmpty())
    }

    @Test
    fun `recordAppLaunch resets counts when stored date is yesterday`() = runTest {
        val (repo, store) = newRepoWithStore()
        store.edit { prefs ->
            prefs[appLaunchesKey] = setOf("com.twitter|5")
            prefs[appLaunchesDateKey] = LocalDate.now().minusDays(1).toString()
        }

        repo.recordAppLaunch("com.twitter")

        assertEquals(mapOf("com.twitter" to 1), repo.appLaunchesToday.first())   // reset, not 6
    }

    @Test
    fun `appLaunchesToday skips malformed entries`() = runTest {
        val (repo, store) = newRepoWithStore()
        store.edit { prefs ->
            prefs[appLaunchesKey] = setOf("com.twitter|3", "com.bad|x", "garbage")
            prefs[appLaunchesDateKey] = LocalDate.now().toString()
        }

        assertEquals(mapOf("com.twitter" to 3), repo.appLaunchesToday.first())
    }

    // ── Midnight rollover ────────────────────────────────────────────────────

    @Test
    fun `day-keyed counters reset at midnight without any write`() = runTest {
        // One minute to midnight — the ticker's first delay is exactly 60s.
        val clock = MutableClock(Instant.parse("2026-07-09T23:59:00Z"), ZoneId.of("UTC"))
        val repo = newRepo(clock)
        repo.recordDrawerLaunch()
        repo.recordAppLaunch("com.twitter")

        val drawerValues = mutableListOf<Int>()
        val appValues = mutableListOf<Map<String, Int>>()
        backgroundScope.launch { repo.drawerLaunchesToday.collect { drawerValues += it } }
        backgroundScope.launch { repo.appLaunchesToday.collect { appValues += it } }
        runCurrent()
        assertEquals(1, drawerValues.last())
        assertEquals(mapOf("com.twitter" to 1), appValues.last())

        // Midnight passes with no DataStore write: the tick alone must refresh both
        // flows, or a limit-blocked app would stay blocked past its promised reset.
        clock.advanceBy(Duration.ofMinutes(2))
        advanceTimeBy(60_001)
        runCurrent()

        assertEquals(0, drawerValues.last())
        assertTrue(appValues.last().isEmpty())
    }

    @Test
    fun `millisUntilNextMidnight measures to the next local midnight`() {
        val clock = Clock.fixed(Instant.parse("2026-07-09T23:59:00Z"), ZoneId.of("UTC"))
        assertEquals(60_000L, millisUntilNextMidnight(clock))
    }

    @Test
    fun `millisUntilNextMidnight at exactly midnight is a full day`() {
        val clock = Clock.fixed(Instant.parse("2026-07-09T00:00:00Z"), ZoneId.of("UTC"))
        assertEquals(24L * 60 * 60 * 1000, millisUntilNextMidnight(clock))
    }
}

/** A [Clock] the test can move forward, for exercising the midnight rollover. */
private class MutableClock(private var instant: Instant, private val zone: ZoneId) : Clock() {
    fun advanceBy(duration: Duration) {
        instant += duration
    }

    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant
}
