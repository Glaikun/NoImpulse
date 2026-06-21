package com.glaikun.noimpulse.interfaces

import com.glaikun.noimpulse.api.AppEntry

/** Read-only view of the device's launchable apps and home-launcher status. */
interface LauncherAppsSource {
    /** True when this app currently holds the default-home role. */
    fun isDefaultHome(): Boolean

    /** All launchable apps on the device, sorted by label. */
    fun installedLaunchableApps(): List<AppEntry>

    /** Resolves a single package to its [AppEntry], or null if not installed/launchable. */
    fun appEntryFor(packageName: String): AppEntry?

    /**
     * Package names of the device's default apps for everyday categories — settings,
     * dialer, SMS, maps, clock, camera, gallery. Packages that can't be resolved on this
     * device are omitted. Caller is responsible for cross-checking against launchable apps.
     */
    fun essentialPackages(): List<String>
}
