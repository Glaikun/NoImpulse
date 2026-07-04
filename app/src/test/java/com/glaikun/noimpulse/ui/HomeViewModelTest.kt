package com.glaikun.noimpulse.ui

import androidx.test.core.app.ApplicationProvider
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.DailyUsage
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import com.glaikun.noimpulse.model.TextSize
import com.glaikun.noimpulse.model.ThemeMode
import com.glaikun.noimpulse.model.TimeWindow
import com.glaikun.noimpulse.data.FrictionSessionLedger
import com.glaikun.noimpulse.data.AccessibilityStatusSource
import com.glaikun.noimpulse.data.LauncherAppsSource
import com.glaikun.noimpulse.data.SettingsRepository
import com.glaikun.noimpulse.data.UsageStatsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
        accessibility: AccessibilityStatusSource = FakeAccessibilityStatusSource(),
    ): HomeViewModel {
        val vm = HomeViewModel(
            app = ApplicationProvider.getApplicationContext(),
            usageStats = FakeUsageStatsSource(usage),
            launcher = launcher,
            settings = settings,
            accessibility = accessibility,
            frictionLedger = FrictionSessionLedger(),
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
            accessibility = FakeAccessibilityStatusSource(),
            frictionLedger = FrictionSessionLedger(),
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

    // ── Always-allowed core (phone/settings/messages/camera/maps) ───────────────

    @Test
    fun `always-allowed core is force-added to the allowlist on start`() {
        runTest {
            val launcher = FakeLauncherAppsSource(
                installed = listOf(AppEntry("Phone", "com.phone"), AppEntry("Maps", "com.maps")),
                alwaysAllowed = listOf("com.phone", "com.maps"),
            )
            val vm = activeViewModel(null, launcher = launcher, settings = FakeSettingsRepository())
            runCurrent()

            assertEquals(
                setOf("com.phone", "com.maps"),
                vm.state.value.allowedApps.mapTo(HashSet()) { it.packageName },
            )
        }
    }

    @Test
    fun `a locked app cannot be removed from the allowlist`() {
        runTest {
            val launcher = FakeLauncherAppsSource(
                installed = listOf(AppEntry("Phone", "com.phone")),
                alwaysAllowed = listOf("com.phone"),
            )
            val vm = activeViewModel(null, launcher = launcher, settings = FakeSettingsRepository())
            runCurrent()

            vm.setAppAllowed("com.phone", false)   // guarded — must be a no-op
            runCurrent()

            assertTrue("com.phone" in vm.state.value.allowedApps.map { it.packageName })
        }
    }

    @Test
    fun `a locked app cannot have friction added`() {
        runTest {
            val launcher = FakeLauncherAppsSource(
                installed = listOf(AppEntry("Phone", "com.phone")),
                alwaysAllowed = listOf("com.phone"),
            )
            val vm = activeViewModel(null, launcher = launcher, settings = FakeSettingsRepository())
            runCurrent()

            vm.addAppFriction("com.phone", FrictionRule(FrictionType.MATH, 1))   // guarded
            runCurrent()

            assertNull(vm.state.value.appFriction["com.phone"])
        }
    }

    @Test
    fun `lockedPackages exposes the always-allowed core`() {
        runTest {
            val launcher = FakeLauncherAppsSource(alwaysAllowed = listOf("com.phone", "com.maps"))
            val vm = activeViewModel(null, launcher = launcher, settings = FakeSettingsRepository())
            backgroundScope.launch { vm.lockedPackages.collect {} }
            runCurrent()

            assertEquals(setOf("com.phone", "com.maps"), vm.lockedPackages.value)
        }
    }

    // ── Restricted Mode ───────────────────────────────────────────────────────

    @Test
    fun `restricted mode defaults to off and not restricted`() = runTest {
        val vm = activeViewModel(null, settings = FakeSettingsRepository())
        assertFalse(vm.state.value.restrictedModeEnabled)
        assertFalse(vm.state.value.isRestrictedNow)
    }

    @Test
    fun `enabling restricted mode with no windows is not restricted — no schedule yet`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = activeViewModel(null, settings = settings)

        vm.setRestrictedModeEnabled(true)
        runCurrent()

        assertTrue(vm.state.value.restrictedModeEnabled)
        assertFalse(vm.state.value.isRestrictedNow)
    }

    @Test
    fun `a whole-day allowed window keeps it unrestricted`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = activeViewModel(null, settings = settings)

        vm.setRestrictedModeEnabled(true)
        vm.addAllowedWindow(TimeWindow(0, 0))   // 00:00–00:00 covers the whole day
        runCurrent()

        assertEquals(listOf(TimeWindow(0, 0)), vm.state.value.allowedWindows)
        assertFalse(vm.state.value.isRestrictedNow)
    }

    @Test
    fun `removing the last allowed window lifts the restriction so apps can open`() = runTest {
        val vm = activeViewModel(null, settings = FakeSettingsRepository())
        // A window that never covers the current time (starts two hours from now),
        // so enabling the mode restricts apps regardless of when the test runs.
        val now = minuteOfDay()
        val notNow = TimeWindow((now + 120) % 1440, (now + 180) % 1440)

        vm.setRestrictedModeEnabled(true)
        vm.addAllowedWindow(notNow)
        runCurrent()
        assertTrue(vm.state.value.isRestrictedNow)

        vm.removeAllowedWindow(notNow)
        runCurrent()

        assertTrue(vm.state.value.allowedWindows.isEmpty())
        assertFalse(vm.state.value.isRestrictedNow)   // nothing to enforce — apps open
    }

    @Test
    fun `removing the active window with another remaining restricts immediately`() = runTest {
        val vm = activeViewModel(null, settings = FakeSettingsRepository())
        val now = minuteOfDay()
        val notNow = TimeWindow((now + 120) % 1440, (now + 180) % 1440)

        vm.setRestrictedModeEnabled(true)
        vm.addAllowedWindow(TimeWindow(0, 0))   // whole day — always active
        vm.addAllowedWindow(notNow)
        runCurrent()
        assertFalse(vm.state.value.isRestrictedNow)

        vm.removeAllowedWindow(TimeWindow(0, 0))
        runCurrent()

        // Only the not-now window remains, so restriction kicks in straight away.
        assertTrue(vm.state.value.isRestrictedNow)
    }

    // ── Appearance ────────────────────────────────────────────────────────────

    @Test
    fun `theme and text size default to dark and default`() = runTest {
        val vm = activeViewModel(null, settings = FakeSettingsRepository())
        assertEquals(ThemeMode.DARK, vm.state.value.themeMode)
        assertEquals(TextSize.DEFAULT, vm.state.value.textSize)
    }

    @Test
    fun `setThemeMode and setTextSize propagate to state`() = runTest {
        val vm = activeViewModel(null, settings = FakeSettingsRepository())

        vm.setThemeMode(ThemeMode.LIGHT)
        vm.setTextSize(TextSize.LARGEST)
        runCurrent()

        assertEquals(ThemeMode.LIGHT, vm.state.value.themeMode)
        assertEquals(TextSize.LARGEST, vm.state.value.textSize)
    }

    @Test
    fun `app frictions stack and can be removed individually`() = runTest {
        val vm = activeViewModel(null, settings = FakeSettingsRepository())
        assertTrue(vm.state.value.appFriction.isEmpty())

        vm.addAppFriction("com.maps", FrictionRule(FrictionType.TIMED_WAIT, 30))
        vm.addAppFriction("com.maps", FrictionRule(FrictionType.MATH, 1))
        runCurrent()
        assertEquals(
            listOf(FrictionRule(FrictionType.TIMED_WAIT, 30), FrictionRule(FrictionType.MATH, 1)),
            vm.state.value.appFriction["com.maps"],
        )

        vm.removeAppFriction("com.maps", FrictionRule(FrictionType.TIMED_WAIT, 30))
        runCurrent()
        assertEquals(
            listOf(FrictionRule(FrictionType.MATH, 1)),
            vm.state.value.appFriction["com.maps"],
        )

        vm.removeAppFriction("com.maps", FrictionRule(FrictionType.MATH, 1))
        runCurrent()
        assertTrue(vm.state.value.appFriction.isEmpty())
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
            accessibility = FakeAccessibilityStatusSource(),
            frictionLedger = FrictionSessionLedger(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch { vm.installedApps.collect {} }
        runCurrent()

        assertEquals(
            listOf("com.c", "com.a", "com.b"),    // recents in order, then the rest alphabetical
            vm.installedApps.value.map { it.packageName },
        )
    }

    // ── Drawer friction ──────────────────────────────────────────────────────

    @Test
    fun `tokensRequired follows the step curve`() {
        assertEquals(1, tokensRequired(0))
        assertEquals(1, tokensRequired(1))
        assertEquals(2, tokensRequired(2))
        assertEquals(2, tokensRequired(3))
        assertEquals(3, tokensRequired(4))
        assertEquals(3, tokensRequired(5))
        assertEquals(4, tokensRequired(6))
        assertEquals(4, tokensRequired(7))
        assertEquals(5, tokensRequired(8))
        assertEquals(5, tokensRequired(50))
    }

    @Test
    fun `generateTokens returns the requested count of 6-char hex strings`() {
        val tokens = generateTokens(3)
        assertEquals(3, tokens.size)
        val hex = Regex("[0-9A-F]{6}")
        tokens.forEach { token ->
            assertEquals(6, token.length)
            assertTrue("Expected hex chars, got: $token", token.matches(hex))
        }
    }

    @Test
    fun `recordDrawerLaunch increments counter for non-allowlisted package`() = runTest {
        val settings = FakeSettingsRepository(setupComplete = true)
        val vm = activeViewModel(null, settings = settings)
        assertEquals(0, vm.state.value.drawerLaunchesToday)

        vm.recordDrawerLaunch("com.twitter")
        runCurrent()

        assertEquals(1, vm.state.value.drawerLaunchesToday)
    }

    @Test
    fun `recordDrawerLaunch does NOT increment for allowlisted package`() = runTest {
        val launcher = FakeLauncherAppsSource(
            installed = listOf(AppEntry("Twitter", "com.twitter")),
        )
        val settings = FakeSettingsRepository(
            setupComplete = true,
            allowed = setOf("com.twitter"),
        )
        val vm = activeViewModel(null, launcher = launcher, settings = settings)
        assertEquals(0, vm.state.value.drawerLaunchesToday)

        vm.recordDrawerLaunch("com.twitter")
        runCurrent()

        assertEquals(0, vm.state.value.drawerLaunchesToday)
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
    fun `initial state has empty time and screen Loading before settings emit`() {
        // With no collector the WhileSubscribed flow is idle, so state holds defaults.
        val vm = HomeViewModel(
            app = ApplicationProvider.getApplicationContext(),
            usageStats = FakeUsageStatsSource(DailyUsage(1, 10)),
            launcher = FakeLauncherAppsSource(),
            settings = FakeSettingsRepository(setupComplete = true),
            accessibility = FakeAccessibilityStatusSource(),
            frictionLedger = FrictionSessionLedger(),
            ioDispatcher = testDispatcher,
        )
        assertEquals("", vm.state.value.time)
        assertNull(vm.state.value.pickupCount)
        assertEquals(AppScreen.Loading, vm.screen.value)
    }

    // ── FSM bootstrap (Loading → first DataStore emission) ────────────────────

    @Test
    fun `fresh install boots to Intro screen`() = runTest {
        val vm = activeViewModel(
            usage = null,
            settings = FakeSettingsRepository(introSeen = false, setupComplete = false),
        )
        assertEquals(AppScreen.Intro, vm.screen.value)
    }

    @Test
    fun `intro acknowledged but setup incomplete boots to Setup`() = runTest {
        val vm = activeViewModel(
            usage = null,
            settings = FakeSettingsRepository(introSeen = true, setupComplete = false),
        )
        assertEquals(AppScreen.Setup, vm.screen.value)
    }

    @Test
    fun `intro and setup both done boots to Home`() = runTest {
        val vm = activeViewModel(
            usage = null,
            settings = FakeSettingsRepository(introSeen = true, setupComplete = true),
        )
        assertEquals(AppScreen.Home, vm.screen.value)
    }

    // ── FSM transitions ──────────────────────────────────────────────────────

    @Test
    fun `completeIntro persists introSeen and moves screen to Setup`() = runTest {
        val settings = FakeSettingsRepository(introSeen = false, setupComplete = false)
        val vm = activeViewModel(usage = null, settings = settings)
        assertEquals(AppScreen.Intro, vm.screen.value)

        vm.completeIntro()
        runCurrent()

        assertTrue(settings.introSeen.first())
        assertEquals(AppScreen.Setup, vm.screen.value)
    }

    @Test
    fun `completeSetup persists setupComplete and moves screen to Home`() = runTest {
        val settings = FakeSettingsRepository(introSeen = true, setupComplete = false)
        val vm = activeViewModel(usage = null, settings = settings)
        assertEquals(AppScreen.Setup, vm.screen.value)

        vm.completeSetup()
        runCurrent()

        assertTrue(settings.setupComplete.first())
        assertEquals(AppScreen.Home, vm.screen.value)
    }

    @Test
    fun `openDrawer from Home moves screen to Drawer`() = runTest {
        val vm = activeViewModel(
            usage = null,
            settings = FakeSettingsRepository(introSeen = true, setupComplete = true),
        )
        assertEquals(AppScreen.Home, vm.screen.value)

        vm.openDrawer()
        assertEquals(AppScreen.Drawer, vm.screen.value)
    }

    @Test
    fun `closeDrawer from Drawer returns to Home`() = runTest {
        val vm = activeViewModel(
            usage = null,
            settings = FakeSettingsRepository(introSeen = true, setupComplete = true),
        )
        vm.openDrawer()
        assertEquals(AppScreen.Drawer, vm.screen.value)

        vm.closeDrawer()
        assertEquals(AppScreen.Home, vm.screen.value)
    }

    @Test
    fun `requestRefriction moves screen to Refriction with package`() = runTest {
        val vm = activeViewModel(
            usage = null,
            settings = FakeSettingsRepository(introSeen = true, setupComplete = true),
        )

        vm.requestRefriction("com.twitter.android")

        assertEquals(AppScreen.Refriction("com.twitter.android"), vm.screen.value)
    }

    @Test
    fun `resolveRefriction returns screen to Home`() = runTest {
        val vm = activeViewModel(
            usage = null,
            settings = FakeSettingsRepository(introSeen = true, setupComplete = true),
        )
        vm.requestRefriction("com.twitter.android")
        assertEquals(AppScreen.Refriction("com.twitter.android"), vm.screen.value)

        vm.resolveRefriction()

        assertEquals(AppScreen.Home, vm.screen.value)
    }

    // ── Friction session ledger wiring ──────────────────────────────────────

    @Test
    fun `markFrictionPassed delegates to the injected ledger synchronously`() = runTest {
        val ledger = FrictionSessionLedger()
        val vm = HomeViewModel(
            app = ApplicationProvider.getApplicationContext(),
            usageStats = FakeUsageStatsSource(null),
            launcher = FakeLauncherAppsSource(),
            settings = FakeSettingsRepository(introSeen = true, setupComplete = true),
            accessibility = FakeAccessibilityStatusSource(),
            frictionLedger = ledger,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertFalse(ledger.isInSession("com.twitter.android"))
        vm.markFrictionPassed("com.twitter.android")
        // No runCurrent() — must be synchronous so the foreground change after
        // launching the app sees an in-session package.
        assertTrue(ledger.isInSession("com.twitter.android"))
    }

    @Test
    fun `accessibilityGranted reflects status source`() = runTest {
        val vm = activeViewModel(
            usage = null,
            accessibility = FakeAccessibilityStatusSource(enabled = true),
        )
        assertTrue(vm.state.value.accessibilityGranted)
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
    private val alwaysAllowed: List<String> = emptyList(),
) : LauncherAppsSource {
    override fun isDefaultHome(): Boolean = defaultHome
    override fun installedLaunchableApps(): List<AppEntry> = installed
    override fun appEntryFor(packageName: String): AppEntry? =
        installed.find { it.packageName == packageName } ?: AppEntry(packageName, packageName)
    override fun loadIcon(packageName: String): android.graphics.drawable.Drawable? = null
    override fun homeScreenApps(): List<AppEntry> = emptyList()
    override fun essentialPackages(): List<String> = essentials
    override fun alwaysAllowedPackages(): List<String> = alwaysAllowed
}

private class FakeSettingsRepository(
    introSeen: Boolean = true,
    setupComplete: Boolean = false,
    allowed: Set<String> = emptySet(),
    drawerLaunches: Int = 0,
) : SettingsRepository {
    private val _introSeen = MutableStateFlow(introSeen)
    private val _setupComplete = MutableStateFlow(setupComplete)
    private val _allowed = MutableStateFlow(allowed)
    private val _drawerLaunches = MutableStateFlow(drawerLaunches)
    private val _appFriction = MutableStateFlow<Map<String, List<FrictionRule>>>(emptyMap())
    private val _restrictedModeEnabled = MutableStateFlow(false)
    private val _allowedTimeWindows = MutableStateFlow<List<TimeWindow>>(emptyList())
    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    private val _textSize = MutableStateFlow(TextSize.DEFAULT)

    override val introSeen: Flow<Boolean> = _introSeen
    override val setupComplete: Flow<Boolean> = _setupComplete
    override val allowedPackages: Flow<Set<String>> = _allowed
    override val appFriction: Flow<Map<String, List<FrictionRule>>> = _appFriction
    override val drawerLaunchesToday: Flow<Int> = _drawerLaunches
    override val restrictedModeEnabled: Flow<Boolean> = _restrictedModeEnabled
    override val allowedTimeWindows: Flow<List<TimeWindow>> = _allowedTimeWindows
    override val themeMode: Flow<ThemeMode> = _themeMode
    override val textSize: Flow<TextSize> = _textSize

    override suspend fun setIntroSeen(seen: Boolean) {
        _introSeen.value = seen
    }

    override suspend fun setSetupComplete(complete: Boolean) {
        _setupComplete.value = complete
    }

    override suspend fun setAppAllowed(packageName: String, allowed: Boolean) {
        _allowed.value =
            if (allowed) _allowed.value + packageName else _allowed.value - packageName
    }

    override suspend fun addAppFriction(packageName: String, rule: FrictionRule) {
        val current = _appFriction.value[packageName].orEmpty()
        if (rule !in current) {
            _appFriction.value = _appFriction.value + (packageName to (current + rule))
        }
    }

    override suspend fun removeAppFriction(packageName: String, rule: FrictionRule) {
        val updated = _appFriction.value[packageName].orEmpty() - rule
        _appFriction.value =
            if (updated.isEmpty()) _appFriction.value - packageName
            else _appFriction.value + (packageName to updated)
    }

    override suspend fun recordDrawerLaunch() {
        _drawerLaunches.value = _drawerLaunches.value + 1
    }

    override suspend fun setRestrictedModeEnabled(enabled: Boolean) {
        _restrictedModeEnabled.value = enabled
    }

    override suspend fun addAllowedWindow(window: TimeWindow) {
        if (window !in _allowedTimeWindows.value) {
            _allowedTimeWindows.value = _allowedTimeWindows.value + window
        }
    }

    override suspend fun removeAllowedWindow(window: TimeWindow) {
        _allowedTimeWindows.value = _allowedTimeWindows.value - window
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    override suspend fun setTextSize(size: TextSize) {
        _textSize.value = size
    }
}

/** Test double for [AccessibilityStatusSource]. Defaults to "not granted". */
private class FakeAccessibilityStatusSource(
    @Volatile var enabled: Boolean = false,
) : AccessibilityStatusSource {
    override fun isFrictionWatchEnabled(): Boolean = enabled
}
