package com.glaikun.noimpulse

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glaikun.noimpulse.ui.HomeScreen
import com.glaikun.noimpulse.ui.HomeViewModel
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
                // Re-read usage access whenever we come back (e.g. from the grant screen).
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshUsage() }
                val state by vm.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onGrantUsageAccess = ::openUsageAccessSettings,
                )
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
}
