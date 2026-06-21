package com.glaikun.noimpulse.ui

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.glaikun.noimpulse.api.DailyUsage
import com.glaikun.noimpulse.interfaces.UsageStatsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class SystemUsageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsageStatsSource {

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
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val usageEvents = mgr.queryEvents(startOfDay, System.currentTimeMillis())

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
            screenOnMs += System.currentTimeMillis() - lastInteractiveMs
        }

        return DailyUsage(
            pickupCount = unlocks,
            screenOnMinutes = (screenOnMs / 60_000).toInt(),
        )
    }
}
