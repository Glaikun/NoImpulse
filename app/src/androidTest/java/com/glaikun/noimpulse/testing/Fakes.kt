package com.glaikun.noimpulse.testing

import com.glaikun.noimpulse.data.AccessibilityStatusSource
import com.glaikun.noimpulse.data.LauncherAppsSource
import com.glaikun.noimpulse.data.SettingsRepository
import com.glaikun.noimpulse.data.UsageStatsSource
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.DailyUsage
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.TextSize
import com.glaikun.noimpulse.model.ThemeMode
import com.glaikun.noimpulse.model.TimeWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written test doubles for [TestAppModule], the real-device e2e suite's Hilt
 * replacement for `AppModule`. A separate copy from `app/src/test`'s `testing/Fakes.kt`
 * — `test` and `androidTest` are different source sets/compilation units with no shared
 * folder configured, so this is deliberate, small duplication rather than a build change.
 */
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
    introSeen: Boolean = false,
    setupComplete: Boolean = false,
    allowed: Set<String> = emptySet(),
) : SettingsRepository {
    private val _introSeen = MutableStateFlow(introSeen)
    private val _setupComplete = MutableStateFlow(setupComplete)
    private val _allowed = MutableStateFlow(allowed)
    private val _drawerLaunches = MutableStateFlow(0)
    private val _appLaunches = MutableStateFlow<Map<String, Int>>(emptyMap())
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

internal class FakeUsageStatsSource(
    private val granted: Boolean = false,
) : UsageStatsSource {
    override fun hasUsageAccess(): Boolean = granted
    override fun queryToday(): DailyUsage? = null
    override fun recentlyUsedPackages(): List<String> = emptyList()
    override fun foregroundMinutesToday(): Map<String, Int> = emptyMap()
}

internal class FakeAccessibilityStatusSource(
    private val enabled: Boolean = false,
) : AccessibilityStatusSource {
    override fun isFrictionWatchEnabled(): Boolean = enabled
}
