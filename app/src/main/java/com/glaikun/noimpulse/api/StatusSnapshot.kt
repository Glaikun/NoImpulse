package com.glaikun.noimpulse.api

data class StatusSnapshot(
    val usageGranted: Boolean,
    val isDefaultHome: Boolean,
    /** OS-provided usage stats — null when the user hasn't granted usage access. */
    val usage: DailyUsage?,
    /** Non-allowlisted apps launched via the drawer today. Persisted, no permission needed. */
    val drawerLaunchesToday: Int,
)
