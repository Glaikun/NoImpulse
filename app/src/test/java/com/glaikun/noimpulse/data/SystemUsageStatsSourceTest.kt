package com.glaikun.noimpulse.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SystemUsageStatsSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Pinned to midday so fabricated "N minutes ago" timestamps always fall inside
    // the source's today-window — a real clock made these tests flake near midnight.
    private val clock = Clock.fixed(Instant.parse("2026-07-04T12:00:00Z"), ZoneOffset.UTC)
    private val now = clock.millis()
    private val source = SystemUsageStatsSource(context, clock)

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

    // ── foregroundMinutesToday (daily-minutes limit) ──────────────────────────

    @Test
    fun `foregroundMinutesToday is empty when the op is denied`() {
        setUsageAccess(AppOpsManager.MODE_ERRORED)

        assertTrue(source.foregroundMinutesToday().isEmpty())
    }

    @Test
    fun `foregroundMinutesToday sums completed foreground sessions per package`() {
        setUsageAccess(AppOpsManager.MODE_ALLOWED)
        // Twitter: two sessions of 3 and 2 minutes; Maps: one of 1 minute.
        movedToForeground("com.twitter", now - minutes(10))
        movedToBackground("com.twitter", now - minutes(7))
        movedToForeground("com.maps", now - minutes(6))
        movedToBackground("com.maps", now - minutes(5))
        movedToForeground("com.twitter", now - minutes(4))
        movedToBackground("com.twitter", now - minutes(2))

        assertEquals(
            mapOf("com.twitter" to 5, "com.maps" to 1),
            source.foregroundMinutesToday(),
        )
    }

    @Test
    fun `an app still in the foreground counts up to now`() {
        setUsageAccess(AppOpsManager.MODE_ALLOWED)
        movedToForeground("com.twitter", now - minutes(3))

        assertEquals(mapOf("com.twitter" to 3), source.foregroundMinutesToday())
    }

    @Test
    fun `a background event with no matching foreground contributes nothing`() {
        setUsageAccess(AppOpsManager.MODE_ALLOWED)
        // e.g. the session started yesterday, before today's query window.
        movedToBackground("com.twitter", now - minutes(2))

        assertTrue(source.foregroundMinutesToday().isEmpty())
    }

    private fun minutes(n: Long): Long = n * 60_000

    @Suppress("DEPRECATION") // Same MOVE_TO_* event pair the production code reads on minSdk 26.
    private fun movedToForeground(packageName: String, timestamp: Long) =
        addEvent(packageName, timestamp, UsageEvents.Event.MOVE_TO_FOREGROUND)

    @Suppress("DEPRECATION")
    private fun movedToBackground(packageName: String, timestamp: Long) =
        addEvent(packageName, timestamp, UsageEvents.Event.MOVE_TO_BACKGROUND)

    private fun addEvent(packageName: String, timestamp: Long, eventType: Int) {
        val mgr = context.getSystemService(UsageStatsManager::class.java)
        shadowOf(mgr).addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(packageName)
                .setTimeStamp(timestamp)
                .setEventType(eventType)
                .build(),
        )
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
