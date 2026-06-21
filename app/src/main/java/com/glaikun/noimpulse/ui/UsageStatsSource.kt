package com.glaikun.noimpulse.ui

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.glaikun.noimpulse.MainActivity
import com.glaikun.noimpulse.api.DailyUsage
import com.glaikun.noimpulse.interfaces.UsageStatsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class SystemUsageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsageStatsSource {

    override fun queryToday(): DailyUsage? {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        Log.i(SystemUsageStatsSource::class.simpleName, "Request Query Today")
        if (mode != AppOpsManager.MODE_ALLOWED) return null

        val mgr = context.getSystemService(UsageStatsManager::class.java)
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val usageEvents = mgr.queryEvents(startOfDay, System.currentTimeMillis())

        var pickups = 0
        var screenOnMs = 0L
        var lastInteractiveMs = -1L

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            Log.i(SystemUsageStatsSource::class::simpleName.toString(), String.format("Event Triggered %s", event::class::simpleName))

            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    pickups++
                    lastInteractiveMs = event.timeStamp
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (lastInteractiveMs != -1L) {
                        screenOnMs += event.timeStamp - lastInteractiveMs
                        lastInteractiveMs = -1L
                    }
                }
            }
        }
        // If screen is still on, count time until now
        if (lastInteractiveMs != -1L) {
            screenOnMs += System.currentTimeMillis() - lastInteractiveMs
        }

        return DailyUsage(
            pickupCount = pickups,
            screenOnMinutes = (screenOnMs / 60_000).toInt(),
        )
    }
}
