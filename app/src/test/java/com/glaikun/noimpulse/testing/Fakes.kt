package com.glaikun.noimpulse.testing

import androidx.test.core.app.ApplicationProvider
import com.glaikun.noimpulse.data.AccessibilityStatusSource
import com.glaikun.noimpulse.data.FrictionSessionLedger
import com.glaikun.noimpulse.data.LauncherAppsSource
import com.glaikun.noimpulse.data.SettingsRepository
import com.glaikun.noimpulse.data.UsageStatsSource
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.DailyUsage
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.TextSize
import com.glaikun.noimpulse.model.ThemeMode
import com.glaikun.noimpulse.model.TimeWindow
import com.glaikun.noimpulse.ui.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/**
 * Shared hand-written test doubles used by both [com.glaikun.noimpulse.ui.HomeViewModelTest]
 * and the `integration` scenario tests. Kept `internal` (module-visible) rather than exported
 * from a testFixtures artifact — this is a single-module app and both test source sets
 * already share `app/src/test`.
 */
internal class FakeUsageStatsSource(
    private val result: DailyUsage?,
    private val granted: Boolean = result != null,
    private val recents: List<String> = emptyList(),
    private val foregroundMinutes: Map<String, Int> = emptyMap(),
) : UsageStatsSource {
    override fun hasUsageAccess(): Boolean = granted
    override fun queryToday(): DailyUsage? = result
    override fun recentlyUsedPackages(): List<String> = recents
    override fun foregroundMinutesToday(): Map<String, Int> = foregroundMinutes
}

/** Usage source whose access can be flipped on at runtime, for refresh tests. */
internal class MutableUsageStatsSource : UsageStatsSource {
    @Volatile private var result: DailyUsage? = null
    @Volatile private var granted: Boolean = false

    fun grant(usage: DailyUsage) {
        result = usage
        granted = true
    }

    override fun hasUsageAccess(): Boolean = granted
    override fun queryToday(): DailyUsage? = if (granted) result else null
    override fun recentlyUsedPackages(): List<String> = emptyList()
    override fun foregroundMinutesToday(): Map<String, Int> = emptyMap()
}

internal class FakeLauncherAppsSource(
    @Volatile var defaultHome: Boolean = false,
    private val installed: List<AppEntry> = emptyList(),
    private val essentials: List<String> = emptyList(),
    private val alwaysAllowed: List<String> = emptyList(),
    private val homeScreen: List<AppEntry> = emptyList(),
) : LauncherAppsSource {
    override fun isDefaultHome(): Boolean = defaultHome
    override fun installedLaunchableApps(): List<AppEntry> = installed
    override fun appEntryFor(packageName: String): AppEntry? =
        installed.find { it.packageName == packageName } ?: AppEntry(packageName, packageName)
    override fun loadIcon(packageName: String): android.graphics.drawable.Drawable? = null
    override fun homeScreenApps(): List<AppEntry> = homeScreen
    override fun essentialPackages(): List<String> = essentials
    override fun alwaysAllowedPackages(): List<String> = alwaysAllowed
}

internal class FakeSettingsRepository(
    introSeen: Boolean = true,
    setupComplete: Boolean = false,
    allowed: Set<String> = emptySet(),
    drawerLaunches: Int = 0,
    appFriction: Map<String, List<FrictionRule>> = emptyMap(),
    appLaunches: Map<String, Int> = emptyMap(),
    restrictedModeEnabled: Boolean = false,
    allowedTimeWindows: List<TimeWindow> = emptyList(),
) : SettingsRepository {
    private val _introSeen = MutableStateFlow(introSeen)
    private val _setupComplete = MutableStateFlow(setupComplete)
    private val _allowed = MutableStateFlow(allowed)
    private val _drawerLaunches = MutableStateFlow(drawerLaunches)
    private val _appLaunches = MutableStateFlow(appLaunches)
    private val _appFriction = MutableStateFlow(appFriction)
    private val _restrictedModeEnabled = MutableStateFlow(restrictedModeEnabled)
    private val _allowedTimeWindows = MutableStateFlow(allowedTimeWindows)
    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    private val _textSize = MutableStateFlow(TextSize.DEFAULT)

    override val introSeen: Flow<Boolean> = _introSeen
    override val setupComplete: Flow<Boolean> = _setupComplete
    override val allowedPackages: Flow<Set<String>> = _allowed
    override val appFriction: Flow<Map<String, List<FrictionRule>>> = _appFriction
    override val drawerLaunchesToday: Flow<Int> = _drawerLaunches
    override val appLaunchesToday: Flow<Map<String, Int>> = _appLaunches
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

    override suspend fun recordAppLaunch(packageName: String) {
        val current = _appLaunches.value[packageName] ?: 0
        _appLaunches.value = _appLaunches.value + (packageName to current + 1)
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
internal class FakeAccessibilityStatusSource(
    @Volatile var enabled: Boolean = false,
) : AccessibilityStatusSource {
    override fun isFrictionWatchEnabled(): Boolean = enabled
}

/**
 * Builds a [HomeViewModel] with the test scheduler driving its I/O dispatcher, and starts
 * collecting [HomeViewModel.state] on the [TestScope.backgroundScope] so the
 * `WhileSubscribed` flow becomes active. Returns once the first combined state has been
 * produced ([runCurrent] settles all work scheduled at virtual time 0, without entering
 * the never-ending tick/poll delays).
 */
internal fun TestScope.homeViewModel(
    usage: DailyUsage?,
    launcher: LauncherAppsSource = FakeLauncherAppsSource(),
    settings: SettingsRepository = FakeSettingsRepository(setupComplete = true),
    accessibility: AccessibilityStatusSource = FakeAccessibilityStatusSource(),
    usageStats: UsageStatsSource = FakeUsageStatsSource(usage),
): HomeViewModel {
    val vm = HomeViewModel(
        app = ApplicationProvider.getApplicationContext(),
        usageStats = usageStats,
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

/**
 * Builds a [HomeViewModel] on real dispatchers (`Dispatchers.IO`, and `Dispatchers.Main`
 * left untouched) rather than a [TestScope]'s virtual clock. Used by the `integration` suite,
 * which renders [com.glaikun.noimpulse.ui.NoImpulseContent] — a real Compose composition
 * that keeps its own `vm.state`/`vm.screen` collectors alive for as long as it stays
 * composed, independent of any single test method's [TestScope]. Wiring that up to a
 * virtual-time [TestScope] made the scheduler's end-of-test idle-drain spin forever
 * chasing the ViewModel's infinite per-minute/poll ticker flows, since only
 * [TestScope.backgroundScope] jobs (not the composition's own collectors) are exempt
 * from that drain. Real dispatchers sidestep the problem entirely: Robolectric runs
 * everything on one thread, so `Dispatchers.Main.immediate` behaves synchronously, and
 * `ComposeContentTestRule`'s interactions/`waitForIdle()` already pump the Robolectric
 * main looper to observe the rest.
 *
 * [ledger] defaults to a fresh instance but can be supplied so a test can inspect or
 * mutate it directly (e.g. simulating the screen-off receiver's `clearAll()`) while the
 * ViewModel's own `markFrictionPassed`/re-friction calls go through the same instance.
 */
internal fun realHomeViewModel(
    usage: DailyUsage?,
    launcher: LauncherAppsSource = FakeLauncherAppsSource(),
    settings: SettingsRepository = FakeSettingsRepository(setupComplete = true),
    accessibility: AccessibilityStatusSource = FakeAccessibilityStatusSource(),
    usageStats: UsageStatsSource = FakeUsageStatsSource(usage),
    ledger: FrictionSessionLedger = FrictionSessionLedger(),
): HomeViewModel = HomeViewModel(
    app = ApplicationProvider.getApplicationContext(),
    usageStats = usageStats,
    launcher = launcher,
    settings = settings,
    accessibility = accessibility,
    frictionLedger = ledger,
    ioDispatcher = Dispatchers.IO,
)
