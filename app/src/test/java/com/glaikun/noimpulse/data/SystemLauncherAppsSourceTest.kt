package com.glaikun.noimpulse.data

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import com.glaikun.noimpulse.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SystemLauncherAppsSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = SystemLauncherAppsSource(context)

    @Test
    fun `appEntryFor returns null for an unknown package`() {
        assertNull(source.appEntryFor("com.does.not.exist"))
    }

    @Test
    fun `appEntryFor resolves an installed package label`() {
        installApp(packageName = "com.example.foo", label = "Foo App")

        assertEquals(AppEntry("Foo App", "com.example.foo"), source.appEntryFor("com.example.foo"))
    }

    @Test
    fun `installedLaunchableApps excludes our own package`() {
        // Our own MainActivity registers a LAUNCHER filter, so it must be filtered out.
        assertTrue(source.installedLaunchableApps().none { it.packageName == context.packageName })
    }

    @Test
    fun `alwaysAllowedPackages includes the default clock and contacts apps`() {
        registerDefault(Intent(AlarmClock.ACTION_SHOW_ALARMS), "com.android.deskclock")
        registerDefault(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS),
            "com.android.contacts",
        )

        val locked = source.alwaysAllowedPackages()

        assertTrue("com.android.deskclock" in locked)
        assertTrue("com.android.contacts" in locked)
    }

    @Test
    fun `installedAuthenticatorPackages returns only installed known authenticators`() {
        installApp(packageName = "com.beemdevelopment.aegis", label = "Aegis")
        installApp(packageName = "com.example.notanauthenticator", label = "Other")

        assertEquals(listOf("com.beemdevelopment.aegis"), source.installedAuthenticatorPackages())
    }

    @Test
    fun `installedAuthenticatorPackages is empty when none are installed`() {
        assertTrue(source.installedAuthenticatorPackages().isEmpty())
    }

    /** Registers [packageName] as the resolved default handler for [intent]. */
    private fun registerDefault(intent: Intent, packageName: String) {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "MainActivity"
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    private fun installApp(packageName: String, label: String) {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = label
        }
        val pkgInfo = PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = appInfo
        }
        shadowOf(context.packageManager).installPackage(pkgInfo)
    }
}
