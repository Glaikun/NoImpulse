package com.glaikun.noimpulse

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.ui.drawer.AppDrawerScreen
import com.glaikun.noimpulse.ui.AppScreen
import com.glaikun.noimpulse.ui.FrictionGate
import com.glaikun.noimpulse.ui.screens.HomeScreen
import com.glaikun.noimpulse.ui.HomeViewModel
import com.glaikun.noimpulse.ui.screens.IntroScreen
import com.glaikun.noimpulse.ui.screens.SetupScreen
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme
import com.glaikun.noimpulse.ui.tokensRequired
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "Creating")
        enableEdgeToEdge()

        // Intent received on cold start; onNewIntent handles subsequent ones.
        consumeRefrictionExtra(intent)

        setContent {
            NoImpulseTheme {
                // Re-read usage access / default-home / accessibility status whenever we come back.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }

                val state by vm.state.collectAsStateWithLifecycle()
                val screen by vm.screen.collectAsStateWithLifecycle()
                val installedApps by vm.installedApps.collectAsStateWithLifecycle()

                val roleLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { vm.refreshStatus() }

                when (val s = screen) {
                    AppScreen.Loading -> LoadingScreen()

                    AppScreen.Intro -> IntroScreen(onContinue = vm::completeIntro)

                    AppScreen.Setup -> SetupScreen(
                        state = state,
                        installedApps = installedApps,
                        onGrantUsageAccess = ::openUsageAccessSettings,
                        onSetDefaultHome = { requestDefaultHome(roleLauncher) },
                        onGrantAccessibility = ::openAccessibilitySettings,
                        onToggleApp = vm::setAppAllowed,
                        onFinish = vm::completeSetup,
                    )

                    AppScreen.Home -> HomeScreen(
                        state = state,
                        onGrantUsageAccess = ::openUsageAccessSettings,
                        onLaunchApp = ::launchApp,
                        onOpenDrawer = vm::openDrawer,
                        loadIcon = vm::loadIcon,
                    )

                    AppScreen.Drawer -> {
                        BackHandler { vm.closeDrawer() }
                        AppDrawerScreen(
                            installedApps = installedApps,
                            allowedPackages = state.allowedApps.mapTo(HashSet()) { it.packageName },
                            drawerLaunchesToday = state.drawerLaunchesToday,
                            onLaunchApp = { pkg ->
                                vm.closeDrawer()
                                launchApp(pkg)
                            },
                            onLaunchAfterChallenge = { pkg ->
                                // ORDER MATTERS: mark the ledger before launching so the
                                // foreground-change event the watcher sees next is for an
                                // already-in-session package.
                                vm.markFrictionPassed(pkg)
                                vm.recordDrawerLaunch(pkg)
                                vm.closeDrawer()
                                launchApp(pkg)
                            },
                            loadIcon = vm::loadIcon,
                            onSetAppAllowed = vm::setAppAllowed,
                            appFriction = state.appFriction,
                            onAddAppFriction = vm::addAppFriction,
                            onRemoveAppFriction = vm::removeAppFriction,
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
                        FrictionGate(
                            app = app,
                            rules = state.appFriction[pkg].orEmpty(),
                            drawerLaunchesToday = state.drawerLaunchesToday,
                            tokenCount = tokensRequired(state.drawerLaunchesToday),
                            onCancel = vm::resolveRefriction,
                            onComplete = {
                                vm.markFrictionPassed(pkg)
                                launchApp(pkg)
                                vm.resolveRefriction()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a new launch with EXTRA_REFRICTION_PACKAGE arrives here.
        setIntent(intent)
        consumeRefrictionExtra(intent)
    }

    /** Reads the re-friction extra and forwards it to the FSM if present. */
    private fun consumeRefrictionExtra(intent: Intent?) {
        val pkg = intent?.getStringExtra(EXTRA_REFRICTION_PACKAGE) ?: return
        intent.removeExtra(EXTRA_REFRICTION_PACKAGE)
        Log.d(TAG, "Re-friction requested for $pkg")
        vm.requestRefriction(pkg)
    }

    /** Opens the system Usage Access screen so the user can grant PACKAGE_USAGE_STATS. */
    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            // Best-effort deep link to this app's entry; ignored where unsupported.
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    /** Opens the system Accessibility settings so the user can enable FrictionWatchService. */
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** Prompts the user to make NoImpulse the default home app. */
    private fun requestDefaultHome(launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null &&
                rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !rm.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        // Pre-Q, or the role is unavailable/already held: open the home picker.
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    /** Launches an allowlisted app by package name. */
    private fun launchApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
    }

    companion object {
        private const val TAG = "MainActivity"

        /** Intent extra used by [com.glaikun.noimpulse.services.FrictionWatchService] to
         *  request that the gate be re-shown for the named package. */
        const val EXTRA_REFRICTION_PACKAGE = "com.glaikun.noimpulse.REFRICTION_PACKAGE"
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
