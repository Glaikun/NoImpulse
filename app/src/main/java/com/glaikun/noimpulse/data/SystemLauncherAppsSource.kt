package com.glaikun.noimpulse.data

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import com.glaikun.noimpulse.api.AppEntry
import com.glaikun.noimpulse.interfaces.LauncherAppsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// The int-flag PackageManager overloads are deprecated on API 33+ but remain the
// simplest cross-version option; there is no behavioural difference here.
@Suppress("DEPRECATION")
class SystemLauncherAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LauncherAppsSource {

    private val pm: PackageManager get() = context.packageManager

    override fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return rm.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    override fun installedLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { (_, pkg) -> pkg != context.packageName }   // hide ourselves
            .distinctBy { (_, pkg) -> pkg }
            .map { (label, pkg) -> AppEntry(label, pkg) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override fun appEntryFor(packageName: String): AppEntry? = try {
        val info = pm.getApplicationInfo(packageName, 0)
        AppEntry(pm.getApplicationLabel(info).toString(), packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    override fun essentialPackages(): List<String> = listOfNotNull(
        resolveDefaultPackage(Intent(Settings.ACTION_SETTINGS)),
        resolveDefaultPackage(Intent(Intent.ACTION_DIAL)),
        Telephony.Sms.getDefaultSmsPackage(context),
        resolveDefaultPackage(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))),
        resolveDefaultPackage(Intent(AlarmClock.ACTION_SHOW_ALARMS)),
        resolveDefaultPackage(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        resolveDefaultPackage(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)),
    ).distinct()

    private fun resolveDefaultPackage(intent: Intent): String? =
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
}
