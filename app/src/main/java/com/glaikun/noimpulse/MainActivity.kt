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
import androidx.activity.viewModels
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import com.glaikun.noimpulse.ui.HomeViewModel
import com.glaikun.noimpulse.ui.NoImpulseContent
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
            val roleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { vm.refreshStatus() }

            NoImpulseContent(
                vm = vm,
                onGrantUsageAccess = ::openUsageAccessSettings,
                onSetDefaultHome = { requestDefaultHome(roleLauncher) },
                onGrantAccessibility = ::openAccessibilitySettings,
                onSwitchLauncher = { requestDefaultHome(roleLauncher) },
                onLaunchApp = ::launchApp,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a new launch with EXTRA_REFRICTION_PACKAGE arrives here.
        setIntent(intent)
        consumeRefrictionExtra(intent)
    }

    /** Reads the re-friction extras and forwards them to the FSM if present. */
    private fun consumeRefrictionExtra(intent: Intent?) {
        val pkg = intent?.getStringExtra(EXTRA_REFRICTION_PACKAGE) ?: return
        intent.removeExtra(EXTRA_REFRICTION_PACKAGE)
        val overLimit = readLimitExtras(intent)
        Log.d(TAG, "Re-friction requested for $pkg")
        vm.requestRefriction(pkg, overLimit)
    }

    /** The exceeded daily limit the watcher attached, or null when absent/malformed. */
    private fun readLimitExtras(intent: Intent): FrictionRule? {
        val typeName = intent.getStringExtra(EXTRA_REFRICTION_LIMIT_TYPE) ?: return null
        val param = intent.getIntExtra(EXTRA_REFRICTION_LIMIT_PARAM, -1)
        intent.removeExtra(EXTRA_REFRICTION_LIMIT_TYPE)
        intent.removeExtra(EXTRA_REFRICTION_LIMIT_PARAM)
        if (param < 0) return null
        val type = runCatching { FrictionType.valueOf(typeName) }.getOrNull() ?: return null
        return FrictionRule(type, param)
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

        /** Optional companions to [EXTRA_REFRICTION_PACKAGE]: the daily limit the package
         *  has exceeded ([com.glaikun.noimpulse.model.FrictionType] name + its param), so
         *  the block notice shows without waiting for a fresh usage read. */
        const val EXTRA_REFRICTION_LIMIT_TYPE = "com.glaikun.noimpulse.REFRICTION_LIMIT_TYPE"
        const val EXTRA_REFRICTION_LIMIT_PARAM = "com.glaikun.noimpulse.REFRICTION_LIMIT_PARAM"
    }
}
