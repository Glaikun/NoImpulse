package com.glaikun.noimpulse.interfaces

import com.glaikun.noimpulse.api.DailyUsage

interface UsageStatsSource {
    /** Returns null if PACKAGE_USAGE_STATS permission is not granted. */
    fun queryToday(): DailyUsage?
}