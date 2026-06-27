package com.glaikun.noimpulse.data

/**
 * Read-only view of whether NoImpulse's [com.glaikun.noimpulse.services.FrictionWatchService]
 * is currently enabled in system accessibility settings. The user can toggle this
 * outside the app, so we re-check on every `ON_RESUME` of the activity.
 */
interface AccessibilityStatusSource {
    /** True when our accessibility service is enabled in system settings. */
    fun isFrictionWatchEnabled(): Boolean
}
