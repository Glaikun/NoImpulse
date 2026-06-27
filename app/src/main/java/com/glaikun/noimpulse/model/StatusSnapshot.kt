package com.glaikun.noimpulse.model

data class StatusSnapshot(
    val usageGranted: Boolean,
    val isDefaultHome: Boolean,
    /** True when our FrictionWatchService is enabled in system accessibility settings. */
    val accessibilityGranted: Boolean,
    /** OS-provided usage stats — null when the user hasn't granted usage access. */
    val usage: DailyUsage?,
    /** Non-allowlisted apps launched via the drawer today. Persisted, no permission needed. */
    val drawerLaunchesToday: Int,
)
