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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

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

    /** Builds a repository over a fresh, test-scoped DataStore file. */
    private fun TestScope.newRepo(): SettingsRepository = newRepoWithStore().first

    /** Same as [newRepo] but also returns the underlying DataStore for preseeding. */
    private fun TestScope.newRepoWithStore(): Pair<SettingsRepository, DataStore<Preferences>> {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tmp.root, "settings_${fileCounter++}.preferences_pb")
        }
        return DataStoreSettingsRepository(dataStore) to dataStore
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
}
