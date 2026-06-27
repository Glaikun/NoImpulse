package com.glaikun.noimpulse.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
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
