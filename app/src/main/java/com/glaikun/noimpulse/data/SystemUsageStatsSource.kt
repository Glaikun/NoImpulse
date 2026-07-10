package com.glaikun.noimpulse.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.glaikun.noimpulse.model.DailyUsage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class SystemUsageStatsSource(
    private val context: Context,
    private val clock: Clock,
) : UsageStatsSource {

    /** Production entry point: Hilt supplies the context and the real clock rides
     *  along. Tests use the primary constructor with a fixed clock so the "today"
     *  window doesn't depend on when the test happens to run (midnight flakiness). */
    @Inject constructor(@ApplicationContext context: Context) :
        this(context, Clock.systemDefaultZone())

    // AppOps is the standard way to check Usage Access; the APIs are flagged
    // deprecated but have no public replacement for this purpose.
    @Suppress("DEPRECATION")
    override fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun queryToday(): DailyUsage? {
        if (!hasUsageAccess()) return null

        val mgr = context.getSystemService(UsageStatsManager::class.java)
        val usageEvents = mgr.queryEvents(startOfDayMillis(), clock.millis())

        var unlocks = 0
        var screenOnMs = 0L
        var lastInteractiveMs = -1L

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                // "Pickups" = device unlocks.
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
                // Screen-on time = interval between the display turning on and off.
                UsageEvents.Event.SCREEN_INTERACTIVE -> lastInteractiveMs = event.timeStamp
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (lastInteractiveMs != -1L) {
                        screenOnMs += event.timeStamp - lastInteractiveMs
                        lastInteractiveMs = -1L
                    }
                }
            }
        }
        // If the screen is still on, count time until now.
        if (lastInteractiveMs != -1L) {
            screenOnMs += clock.millis() - lastInteractiveMs
        }

        return DailyUsage(
            pickupCount = unlocks,
            screenOnMinutes = (screenOnMs / 60_000).toInt(),
        )
    }

    // Known undercount: an app already in the foreground at midnight has no
    // MOVE_TO_FOREGROUND event inside today's window, so its stretch from midnight to
    // the first MOVE_TO_BACKGROUND is dropped (the unmatched background event is skipped
    // below). Errs lenient — never blocks on time it can't attribute — so it's accepted.
    @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND/BACKGROUND: the minSdk-26 event pair.
    override fun foregroundMinutesToday(): Map<String, Int> {
        if (!hasUsageAccess()) return emptyMap()

        val mgr = context.getSystemService(UsageStatsManager::class.java)
        val usageEvents = mgr.queryEvents(startOfDayMillis(), clock.millis())

        val foregroundMs = HashMap<String, Long>()
        val foregroundSince = HashMap<String, Long>()

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundSince[pkg] = event.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    foregroundSince.remove(pkg)?.let { since ->
                        foregroundMs.merge(pkg, event.timeStamp - since, Long::plus)
                    }
                }
            }
        }
        // Apps still in the foreground count up to now.
        val now = clock.millis()
        foregroundSince.forEach { (pkg, since) -> foregroundMs.merge(pkg, now - since, Long::plus) }

        return foregroundMs.mapValues { (_, ms) -> (ms / 60_000).toInt() }
    }

    override fun recentlyUsedPackages(): List<String> {
        if (!hasUsageAccess()) return emptyList()

        val mgr = context.getSystemService(UsageStatsManager::class.java)
        val end = clock.millis()
        val start = end - RECENT_WINDOW_MS
        val stats = mgr.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
            ?: return emptyList()

        return stats
            .groupBy { it.packageName }
            .mapValues { (_, buckets) -> buckets.maxOf { it.lastTimeUsed } }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    /** Midnight today in epoch millis — the lower bound of every "today" query. */
    private fun startOfDayMillis(): Long = LocalDate.now(clock)
        .atStartOfDay(clock.zone)
        .toInstant()
        .toEpochMilli()

    private companion object {
        const val RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000   // 30 days
    }
}
