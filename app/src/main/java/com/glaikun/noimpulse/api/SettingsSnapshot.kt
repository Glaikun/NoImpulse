package com.glaikun.noimpulse.api

data class SettingsSnapshot(
    val setupComplete: Boolean,
    val allowedApps: List<AppEntry>,
    val appFriction: Map<String, List<FrictionRule>> = emptyMap(),
)
