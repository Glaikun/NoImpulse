package com.glaikun.noimpulse.data

import com.glaikun.noimpulse.model.DailyUsage

interface UsageStatsSource {
    /** True when the user has granted Usage Access (PACKAGE_USAGE_STATS). */
    fun hasUsageAccess(): Boolean

    /** Returns null if Usage Access is not granted. */
    fun queryToday(): DailyUsage?

    /** Package names ordered most-recently-used first. Empty if Usage Access is not granted. */
    fun recentlyUsedPackages(): List<String>

    /**
     * Today's foreground minutes per package, for the daily-minutes limit friction.
     * Empty if Usage Access is not granted.
     */
    fun foregroundMinutesToday(): Map<String, Int>
}
