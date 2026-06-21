package com.glaikun.noimpulse.ui

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SystemUsageStatsSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = SystemUsageStatsSource(context)

    @Test
    fun `hasUsageAccess is false and queryToday null when the op is denied`() {
        setUsageAccess(AppOpsManager.MODE_ERRORED)

        assertFalse(source.hasUsageAccess())
        assertNull(source.queryToday())
    }

    @Test
    fun `hasUsageAccess true and queryToday non-null once the op is allowed`() {
        setUsageAccess(AppOpsManager.MODE_ALLOWED)

        assertTrue(source.hasUsageAccess())
        // No events injected, so counts are zero — but the result must be non-null.
        assertNotNull(source.queryToday())
    }

    private fun setUsageAccess(mode: Int) {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            mode,
        )
    }
}
