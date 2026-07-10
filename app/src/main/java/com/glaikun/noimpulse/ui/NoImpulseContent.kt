package com.glaikun.noimpulse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.ThemeMode
import com.glaikun.noimpulse.ui.drawer.AppDrawerScreen
import com.glaikun.noimpulse.ui.screens.HomeScreen
import com.glaikun.noimpulse.ui.screens.IntroScreen
import com.glaikun.noimpulse.ui.screens.SettingsScreen
import com.glaikun.noimpulse.ui.screens.SetupScreen
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme

/**
 * The app's full composition root: theme, the [AppScreen] FSM render, and the
 * restricted-time overlay. Extracted out of `MainActivity` so it can be rendered in a
 * test against a real [HomeViewModel] without an Activity/Hilt/PackageManager.
 * Activity-only concerns (system settings, the default-home role request, launching
 * another app's `Intent`) are passed in as plain lambdas. Named apart from the
 * `NoImpulseApp` application class (`../NoImpulseApp.kt`) to keep the two distinct.
 */
@Composable
fun NoImpulseContent(
    vm: HomeViewModel,
    onGrantUsageAccess: () -> Unit = {},
    onSetDefaultHome: () -> Unit = {},
    onGrantAccessibility: () -> Unit = {},
    onSwitchLauncher: () -> Unit = {},
    onLaunchApp: (String) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val darkTheme = when (state.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    NoImpulseTheme(darkTheme = darkTheme, textScale = state.textSize.scale) {
        // Re-read usage access / default-home / accessibility status whenever we come back.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }

        val screen by vm.screen.collectAsStateWithLifecycle()
        val installedApps by vm.installedApps.collectAsStateWithLifecycle()

        // Shown when a non-allowlisted app launch is attempted during restricted time.
        // Single chokepoint so every launch path (home, drawer, re-friction) is covered;
        // allowlisted apps (including the always-allowed core) stay reachable off-hours.
        var restrictedNotice by remember { mutableStateOf(false) }
        val attemptLaunch: (String) -> Unit = { pkg ->
            val current = vm.state.value
            val isAllowed = current.allowedApps.any { it.packageName == pkg }
            if (current.isRestrictedNow && !isAllowed) restrictedNotice = true else onLaunchApp(pkg)
        }

        when (val s = screen) {
            AppScreen.Loading -> LoadingScreen()

            AppScreen.Intro -> IntroScreen(onContinue = vm::completeIntro)

            AppScreen.Setup -> SetupScreen(
                state = state,
                installedApps = installedApps,
                onGrantUsageAccess = onGrantUsageAccess,
                onSetDefaultHome = onSetDefaultHome,
                onGrantAccessibility = onGrantAccessibility,
                onToggleApp = vm::setAppAllowed,
                onFinish = vm::completeSetup,
            )

            AppScreen.Home -> HomeScreen(
                state = state,
                onGrantUsageAccess = onGrantUsageAccess,
                onLaunchApp = attemptLaunch,
                onOpenDrawer = vm::openDrawer,
                loadIcon = vm::loadIcon,
            )

            AppScreen.Settings -> {
                BackHandler { vm.closeSettings() }
                SettingsScreen(
                    state = state,
                    onGrantUsageAccess = onGrantUsageAccess,
                    onSetDefaultHome = onSetDefaultHome,
                    onGrantAccessibility = onGrantAccessibility,
                    onSwitchLauncher = onSwitchLauncher,
                    onSetRestrictedModeEnabled = vm::setRestrictedModeEnabled,
                    onAddAllowedWindow = vm::addAllowedWindow,
                    onRemoveAllowedWindow = vm::removeAllowedWindow,
                    onSetThemeMode = vm::setThemeMode,
                    onSetTextSize = vm::setTextSize,
                )
            }

            AppScreen.Drawer -> {
                BackHandler { vm.closeDrawer() }
                val lockedPackages by vm.lockedPackages.collectAsStateWithLifecycle()
                AppDrawerScreen(
                    installedApps = installedApps,
                    allowedPackages = state.allowedApps.mapTo(HashSet()) { it.packageName },
                    lockedPackages = lockedPackages,
                    drawerLaunchesToday = state.drawerLaunchesToday,
                    isRestrictedNow = state.isRestrictedNow,
                    onLaunchApp = { pkg ->
                        vm.closeDrawer()
                        attemptLaunch(pkg)
                    },
                    onLaunchAfterChallenge = { pkg ->
                        // ORDER MATTERS: mark the ledger before launching so the
                        // foreground-change event the watcher sees next is for an
                        // already-in-session package.
                        vm.markFrictionPassed(pkg)
                        vm.recordDrawerLaunch(pkg)
                        vm.closeDrawer()
                        attemptLaunch(pkg)
                    },
                    onRestrictedTap = { restrictedNotice = true },
                    loadIcon = vm::loadIcon,
                    onSetAppAllowed = vm::setAppAllowed,
                    appFriction = state.appFriction,
                    appLaunchesToday = state.appLaunchesToday,
                    appUsageMinutesToday = state.appUsageMinutesToday,
                    onAddAppFriction = vm::addAppFriction,
                    onRemoveAppFriction = vm::removeAppFriction,
                    onOpenSettings = vm::openSettings,
                )
            }

            is AppScreen.Refriction -> {
                // Re-friction launched by FrictionWatchService when the user
                // returned to a friction-locked app via Recents. We render the
                // gate over our own launcher chrome; the target app keeps its
                // in-memory state behind us.
                val pkg = s.packageName
                val app = installedApps.firstOrNull { it.packageName == pkg }
                    ?: AppEntry(pkg, pkg)
                BackHandler { vm.resolveRefriction() }
                // Prefer the watcher's verdict carried in the intent: the cached
                // state is stale right after a long app session, and the gate must
                // not flash passable when the app is actually over its limit. The
                // state-computed check stays as a fallback for verdict-less paths.
                val overLimit = s.overLimit ?: exceededDailyLimit(
                    rules = state.appFriction[pkg].orEmpty(),
                    launchesToday = state.appLaunchesToday[pkg] ?: 0,
                    minutesToday = state.appUsageMinutesToday[pkg] ?: 0,
                )
                when {
                    state.isRestrictedNow ->
                        // Restricted time: there's no gate to pass — just show the
                        // notice and send the user back to the launcher home.
                        RestrictedTimeDialog(onDismiss = vm::resolveRefriction)
                    overLimit != null ->
                        // Over a daily limit: blocked until midnight, same shape.
                        DailyLimitDialog(app = app, rule = overLimit, onDismiss = vm::resolveRefriction)
                    else -> FrictionGate(
                        app = app,
                        rules = state.appFriction[pkg].orEmpty(),
                        drawerLaunchesToday = state.drawerLaunchesToday,
                        tokenCount = tokensRequired(state.drawerLaunchesToday),
                        onCancel = vm::resolveRefriction,
                        onComplete = {
                            vm.markFrictionPassed(pkg)
                            // A passed re-friction gate is still an "open" — count it so
                            // Recents round-trips can't sidestep a daily-launches limit.
                            vm.recordRefrictionPass(pkg)
                            onLaunchApp(pkg)
                            vm.resolveRefriction()
                        },
                    )
                }
            }
        }

        if (restrictedNotice) {
            RestrictedTimeDialog(onDismiss = { restrictedNotice = false })
        }
    }
}

@Composable
private fun LoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
