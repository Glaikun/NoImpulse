package com.glaikun.noimpulse.data

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.glaikun.noimpulse.interfaces.AccessibilityStatusSource
import com.glaikun.noimpulse.services.FrictionWatchService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` to decide whether our
 * service is enabled. We deliberately avoid `AccessibilityManager.getEnabledAccessibilityServiceList`
 * because that only returns services matching specific feedback types — our
 * `feedbackGeneric` service can fall through unless we read the secure setting
 * directly.
 *
 * The secure setting is a colon-separated list of "pkg/ServiceClassName" entries.
 */
class SystemAccessibilityStatusSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : AccessibilityStatusSource {

    override fun isFrictionWatchEnabled(): Boolean {
        val expected = ComponentName(context, FrictionWatchService::class.java)
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return raw.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }
}
