package com.glaikun.noimpulse.interfaces

import com.glaikun.noimpulse.api.DailyUsage

interface UsageStatsSource {
    /** True when the user has granted Usage Access (PACKAGE_USAGE_STATS). */
    fun hasUsageAccess(): Boolean

    /** Returns null if Usage Access is not granted. */
    fun queryToday(): DailyUsage?

    /** Package names ordered most-recently-used first. Empty if Usage Access is not granted. */
    fun recentlyUsedPackages(): List<String>
}