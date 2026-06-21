package com.glaikun.noimpulse.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var fileCounter = 0

    /** Builds a repository over a fresh, test-scoped DataStore file. */
    private fun TestScope.newRepo(): SettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tmp.root, "settings_${fileCounter++}.preferences_pb")
        }
        return DataStoreSettingsRepository(dataStore)
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
}
