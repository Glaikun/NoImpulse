package com.glaikun.noimpulse.ui

import androidx.test.core.app.ApplicationProvider
import com.glaikun.noimpulse.api.AppEntry
import com.glaikun.noimpulse.api.DailyUsage
import com.glaikun.noimpulse.interfaces.LauncherAppsSource
import com.glaikun.noimpulse.interfaces.SettingsRepository
import com.glaikun.noimpulse.interfaces.UsageStatsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Builds the ViewModel with the test scheduler driving its I/O dispatcher, and
     * starts collecting [HomeViewModel.state] on the [backgroundScope] so the
     * `WhileSubscribed` flow becomes active. Returns once the first combined state
     * has been produced ([runCurrent] settles all work scheduled at virtual time 0,
     * without entering the never-ending tick/poll delays).
     */
    private fun TestScope.activeViewModel(
        usage: DailyUsage?,
        launcher: LauncherAppsSource = FakeLauncherAppsSource(),
        settings: SettingsRepository = FakeSettingsRepository(setupComplete = true),
    ): HomeViewModel {
        val vm = HomeViewModel(
            app = ApplicationProvider.getApplicationContext(),
            usageStats = FakeUsageStatsSource(usage),
            launcher = launcher,
            settings = settings,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    // ── Pure formatting helpers ───────────────────────────────────────────────

    @Test
    fun `formatTime returns HH colon mm string`() {
        val result = HomeViewModel.formatTime()
        assertTrue(
            "Expected HH:mm format but got: $result",
            result.matches(Regex("\\d{2}:\\d{2}")),
        )
    }

    @Test
    fun `formatDate returns non-empty string containing day and month`() {
        val result = HomeViewModel.formatDate()
        assertTrue("Expected non-empty date string but got: $result", result.isNotBlank())
    }

    @Test
    fun `formatHours 90 minutes returns 1h 30m`() {
        assertEquals("1h 30m", formatHours(90))
    }

    @Test
    fun `formatHours 0 minutes returns 0h 0m`() {
        assertEquals("0h 0m", formatHours(0))
    }

    @Test
    fun `formatHours 60 minutes returns 1h 0m`() {
        assertEquals("1h 0m", formatHours(60))
    }

    @Test
    fun `formatHours 135 minutes returns 2h 15m`() {
        assertEquals("2h 15m", formatHours(135))
    }

    // ── ViewModel state with fake usage source ────────────────────────────────

    @Test
    fun `state pickupCount reflects fake source value`() = runTest {
        val vm = activeViewModel(DailyUsage(pickupCount = 7, screenOnMinutes = 90))
        assertEquals(7, vm.state.value.pickupCount)
    }

    @Test
    fun `state screenOnMinutes reflects fake source value`() = runTest {
        val vm = activeViewModel(DailyUsage(pickupCount = 3, screenOnMinutes = 45))
        assertEquals(45, vm.state.value.screenOnMinutes)
    }

    @Test
    fun `state pickupCount and screenOnMinutes are null when source returns null`() = runTest {
        val vm = activeViewModel(null)
        assertNull(vm.state.value.pickupCount)
        assertNull(vm.state.value.screenOnMinutes)
    }

    @Test
    fun `usageAccessGranted is true when access is granted`() = runTest {
        val vm = activeViewModel(DailyUsage(pickupCount = 0, screenOnMinutes = 0))
        assertTrue(vm.state.value.usageAccessGranted)
    }

    @Test
    fun `usageAccessGranted is false when access is not granted`() = runTest {
        val vm = activeViewModel(null)
        assertFalse(vm.state.value.usageAccessGranted)
    }

    @Test
    fun `refreshStatus picks up access granted after the fact`() = runTest {
        val source = MutableUsageStatsSource()        // starts ungranted
        val vm = HomeViewModel(
            app = ApplicationProvider.getApplicationContext(),
            usageStats = source,
            launcher = FakeLauncherAppsSource(),
            settings = FakeSettingsRepository(setupComplete = true),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertFalse(vm.state.value.usageAccessGranted)

        source.grant(DailyUsage(pickupCount = 2, screenOnMinutes = 20))
        vm.refreshStatus()
        runCurrent()

        assertTrue(vm.state.value.usageAccessGranted)
        assertEquals(2, vm.state.value.pickupCount)
    }

    @Test
    fun `isDefaultHome reflects launcher source`() = runTest {
        val vm = activeViewModel(null, launcher = FakeLauncherAppsSource(defaultHome = true))
        assertTrue(vm.state.value.isDefaultHome)
    }

    // ── Setup + allowlist ─────────────────────────────────────────────────────

    @Test
    fun `setupComplete reflects settings repository`() = runTest {
        val vm = activeViewModel(null, settings = FakeSettingsRepository(setupComplete = false))
        assertEquals(false, vm.state.value.setupComplete)
    }

    @Test
    fun `completeSetup persists setupComplete true`() = runTest {
        val settings = FakeSettingsRepository(setupComplete = false)
        val vm = activeViewModel(null, settings = settings)
        assertEquals(false, vm.state.value.setupComplete)

        vm.completeSetup()
        runCurrent()

        assertEquals(true, vm.state.value.setupComplete)
    }

    @Test
    fun `setAppAllowed adds the app to allowedApps`() = runTest {
        val launcher = FakeLauncherAppsSource(installed = listOf(AppEntry("Maps", "com.maps")))
        val vm = activeViewModel(null, launcher = launcher, settings = FakeSettingsRepository())
        assertTrue(vm.state.value.allowedApps.isEmpty())

        vm.setAppAllowed("com.maps", true)
        runCurrent()

        assertEquals(listOf("com.maps"), vm.state.value.allowedApps.map { it.packageName })
    }

    @Test
    fun `essentials are pre-allowed on first launch when allowlist is empty`() = runTest {
        val launcher = FakeLauncherAppsSource(
            installed = listOf(
                AppEntry("Settings", "com.settings"),
                AppEntry("Phone", "com.phone"),
                AppEntry("Other", "com.other"),
            ),
            essentials = listOf("com.settings", "com.phone", "com.missing"),
        )
        val settings = FakeSettingsRepository(setupComplete = false)
        val vm = activeViewModel(null, launcher = launcher, settings = settings)

        assertEquals(
            setOf("com.settings", "com.phone"),    // 'com.missing' skipped (not launchable)
            vm.state.value.allowedApps.map { it.packageName }.toSet(),
        )
    }

    @Test
    fun `essentials are not re-seeded when allowlist already has entries`() = runTest {
        val launcher = FakeLauncherAppsSource(
            installed = listOf(
                AppEntry("Settings", "com.settings"),
                AppEntry("Maps", "com.maps"),
            ),
            essentials = listOf("com.settings"),
        )
        val settings = FakeSettingsRepository(setupComplete = false, allowed = setOf("com.maps"))
        val vm = activeViewModel(null, launcher = launcher, settings = settings)

        assertEquals(
            setOf("com.maps"),                     // 'com.settings' NOT added — user's allowlist preserved
            vm.state.value.allowedApps.map { it.packageName }.toSet(),
        )
    }

    @Test
    fun `essentials are not seeded once setup is complete`() = runTest {
        val launcher = FakeLauncherAppsSource(
            installed = listOf(AppEntry("Settings", "com.settings")),
            essentials = listOf("com.settings"),
        )
        val settings = FakeSettingsRepository(setupComplete = true)
        val vm = activeViewModel(null, launcher = launcher, settings = settings)

        assertTrue(vm.state.value.allowedApps.isEmpty())
    }

    @Test
    fun `installedApps lists recently used first then alphabetical`() = runTest {
        val launcher = FakeLauncherAppsSource(
            installed = listOf(
                AppEntry("Alpha", "com.a"),
                AppEntry("Bravo", "com.b"),
                AppEntry("Charlie", "com.c"),
            ),
        )
        val usage = FakeUsageStatsSource(
            result = DailyUsage(0, 0),
            recents = listOf("com.c", "com.a"),   // most-recent-first
        )
        val vm = HomeViewModel(
            app = ApplicationProvider.getApplicationContext(),
            usageStats = usage,
            launcher = launcher,
            settings = FakeSettingsRepository(setupComplete = true),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch { vm.installedApps.collect {} }
        runCurrent()

        assertEquals(
            listOf("com.c", "com.a", "com.b"),    // recents in order, then the rest alphabetical
            vm.installedApps.value.map { it.packageName },
        )
    }

    @Test
    fun `state time is populated after first tick`() = runTest {
        val vm = activeViewModel(null)
        assertTrue(vm.state.value.time.isNotBlank())
    }

    @Test
    fun `state date is populated after first tick`() = runTest {
        val vm = activeViewModel(null)
        assertTrue(vm.state.value.date.isNotBlank())
    }

    @Test
    fun `initial state is loading with empty time and null setupComplete`() {
        // With no collector the WhileSubscribed flow is idle, so state holds defaults.
        val vm = HomeViewModel(
            app = ApplicationProvider.getApplicationContext(),
            usageStats = FakeUsageStatsSource(DailyUsage(1, 10)),
            launcher = FakeLauncherAppsSource(),
            settings = FakeSettingsRepository(setupComplete = true),
            ioDispatcher = testDispatcher,
        )
        assertEquals("", vm.state.value.time)
        assertNull(vm.state.value.pickupCount)
        assertNull(vm.state.value.setupComplete)   // null = still loading
    }
}

// ── Test double ──────────────────────────────────────────────────────────────

private class FakeUsageStatsSource(
    private val result: DailyUsage?,
    private val granted: Boolean = result != null,
    private val recents: List<String> = emptyList(),
) : UsageStatsSource {
    override fun hasUsageAccess(): Boolean = granted
    override fun queryToday(): DailyUsage? = result
    override fun recentlyUsedPackages(): List<String> = recents
}

/** Usage source whose access can be flipped on at runtime, for refresh tests. */
private class MutableUsageStatsSource : UsageStatsSource {
    @Volatile private var result: DailyUsage? = null
    @Volatile private var granted: Boolean = false

    fun grant(usage: DailyUsage) {
        result = usage
        granted = true
    }

    override fun hasUsageAccess(): Boolean = granted
    override fun queryToday(): DailyUsage? = if (granted) result else null
    override fun recentlyUsedPackages(): List<String> = emptyList()
}

private class FakeLauncherAppsSource(
    @Volatile var defaultHome: Boolean = false,
    private val installed: List<AppEntry> = emptyList(),
    private val essentials: List<String> = emptyList(),
) : LauncherAppsSource {
    override fun isDefaultHome(): Boolean = defaultHome
    override fun installedLaunchableApps(): List<AppEntry> = installed
    override fun appEntryFor(packageName: String): AppEntry? =
        installed.find { it.packageName == packageName } ?: AppEntry(packageName, packageName)
    override fun essentialPackages(): List<String> = essentials
}

private class FakeSettingsRepository(
    setupComplete: Boolean = false,
    allowed: Set<String> = emptySet(),
) : SettingsRepository {
    private val _setupComplete = MutableStateFlow(setupComplete)
    private val _allowed = MutableStateFlow(allowed)

    override val setupComplete: Flow<Boolean> = _setupComplete
    override val allowedPackages: Flow<Set<String>> = _allowed

    override suspend fun setSetupComplete(complete: Boolean) {
        _setupComplete.value = complete
    }

    override suspend fun setAppAllowed(packageName: String, allowed: Boolean) {
        _allowed.value =
            if (allowed) _allowed.value + packageName else _allowed.value - packageName
    }
}
