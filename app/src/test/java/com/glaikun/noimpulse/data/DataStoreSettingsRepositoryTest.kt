package com.glaikun.noimpulse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glaikun.noimpulse.interfaces.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
