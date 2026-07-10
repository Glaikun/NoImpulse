package com.glaikun.noimpulse.data

import com.glaikun.noimpulse.model.AppEntry

/** Read-only view of the device's launchable apps and home-launcher status. */
interface LauncherAppsSource {
    /** True when this app currently holds the default-home role. */
    fun isDefaultHome(): Boolean

    /** All launchable apps on the device, sorted by label. */
    fun installedLaunchableApps(): List<AppEntry>

    /** Resolves a single package to its [AppEntry], or null if not installed/launchable. */
    fun appEntryFor(packageName: String): AppEntry?

    /** The app's launcher icon, or null if it can't be resolved. */
    fun loadIcon(packageName: String): android.graphics.drawable.Drawable?

    /**
     * The fixed set of apps shown on the home screen — phone, messages, camera, maps,
     * in that order — resolved from the device's current defaults. Entries that can't be
     * resolved on this device are omitted.
     */
    fun homeScreenApps(): List<AppEntry>

    /**
     * Package names of the device's default apps for everyday categories — settings,
     * dialer, SMS, maps, clock, camera, gallery. Packages that can't be resolved on this
     * device are omitted. Caller is responsible for cross-checking against launchable apps.
     */
    fun essentialPackages(): List<String>

    /**
     * The non-negotiable core: phone, settings, messages, camera, maps, clock, contacts
     * (the device defaults). These stay allowlisted and friction-free at all times — they
     * can't be removed from the allowlist, can't have friction added, and aren't blocked
     * by Restricted Mode. Packages that can't be resolved on this device are omitted.
     */
    fun alwaysAllowedPackages(): List<String>

    /**
     * Installed 2FA authenticator apps, matched against a best-effort list of known
     * packages (there is no system role/intent for "the authenticator"). Used to seed
     * the allowlist once per app — friction on an authenticator can lock the user out
     * of *other* accounts, so they start friction-free but stay fully user-controlled.
     */
    fun installedAuthenticatorPackages(): List<String>
}
