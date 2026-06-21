package com.glaikun.noimpulse

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glaikun.noimpulse.ui.HomeScreen
import com.glaikun.noimpulse.ui.HomeViewModel
import com.glaikun.noimpulse.ui.SetupScreen
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(MainActivity::class.simpleName, "Creating")
        enableEdgeToEdge()
        setContent {
            NoImpulseTheme {
                val vm: HomeViewModel = hiltViewModel()
                // Re-read usage access / default-home status whenever we come back.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }
                val state by vm.state.collectAsStateWithLifecycle()

                val roleLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { vm.refreshStatus() }

                when (state.setupComplete) {
                    null -> LoadingScreen()
                    false -> {
                        val installedApps by vm.installedApps.collectAsStateWithLifecycle()
                        SetupScreen(
                            state = state,
                            installedApps = installedApps,
                            onGrantUsageAccess = ::openUsageAccessSettings,
                            onSetDefaultHome = { requestDefaultHome(roleLauncher) },
                            onToggleApp = vm::setAppAllowed,
                            onFinish = vm::completeSetup,
                        )
                    }
                    true -> HomeScreen(
                        state = state,
                        onGrantUsageAccess = ::openUsageAccessSettings,
                        onLaunchApp = ::launchApp,
                    )
                }
            }
        }
    }

    /** Opens the system Usage Access screen so the user can grant PACKAGE_USAGE_STATS. */
    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            // Best-effort deep link to this app's entry; ignored where unsupported.
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
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
