package com.glaikun.noimpulse.model

data class SettingsSnapshot(
    val introSeen: Boolean,
    val setupComplete: Boolean,
    val allowedApps: List<AppEntry>,
    val appFriction: Map<String, List<FrictionRule>> = emptyMap(),
    val restrictedModeEnabled: Boolean = false,
    val allowedWindows: List<TimeWindow> = emptyList(),
)
