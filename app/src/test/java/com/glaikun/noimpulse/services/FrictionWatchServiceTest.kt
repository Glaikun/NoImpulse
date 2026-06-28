package com.glaikun.noimpulse.services

import com.glaikun.noimpulse.data.FrictionSessionLedger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [shouldTriggerRefriction], the pure filter chain that
 * [FrictionWatchService] uses inside `onAccessibilityEvent`.
 */
class FrictionWatchServiceTest {

    private val own = "com.glaikun.noimpulse"
    private val allowed = setOf("com.android.dialer", "com.android.deskclock")
    private val launchable = setOf(
        "com.glaikun.noimpulse",
        "com.android.dialer",
        "com.android.deskclock",
        "com.twitter.android",
        "com.instagram.android",
    )

    private fun decide(
        pkg: String,
        inSession: (String) -> Boolean = { false },
        restrictedNow: Boolean = false,
    ): Boolean = shouldTriggerRefriction(
        packageName = pkg,
        ownPackageName = own,
        allowedPackages = allowed,
        launchablePackages = launchable,
        isInSession = inSession,
        restrictedNow = restrictedNow,
    )

    @Test
    fun `non-allowlisted launchable not in session triggers`() {
        assertTrue(decide("com.twitter.android"))
    }

    @Test
    fun `our own package is skipped`() {
        assertFalse(decide(own))
    }

    @Test
    fun `allowlisted package is skipped even when launchable`() {
        assertFalse(decide("com.android.dialer"))
    }

    @Test
    fun `non-launchable package is skipped (system overlay)`() {
        assertFalse(decide("com.android.systemui"))
    }

    @Test
    fun `IME package is skipped (not in launchable set)`() {
        assertFalse(decide("com.google.android.inputmethod.latin"))
    }

    @Test
    fun `package already in session is skipped`() {
        assertFalse(decide("com.twitter.android", inSession = { it == "com.twitter.android" }))
    }

    @Test
    fun `package out of session triggers even when others are in session`() {
        assertTrue(decide("com.instagram.android", inSession = { it == "com.twitter.android" }))
    }

    @Test
    fun `integration with FrictionSessionLedger`() {
        val ledger = FrictionSessionLedger()
        assertTrue(decide("com.twitter.android", inSession = ledger::isInSession))

        ledger.markPassed("com.twitter.android")
        assertFalse(decide("com.twitter.android", inSession = ledger::isInSession))

        ledger.clearAll()
        assertTrue(decide("com.twitter.android", inSession = ledger::isInSession))
    }

    @Test
    fun `skip-rule precedence — own beats everything else`() {
        // Allowed and launchable and in-session — own check still wins (false).
        val result = shouldTriggerRefriction(
            packageName = own,
            ownPackageName = own,
            allowedPackages = setOf(own),
            launchablePackages = setOf(own),
            isInSession = { true },
            restrictedNow = true,
        )
        assertFalse(result)
    }

    @Test
    fun `skip-rule precedence — allowlist beats launchable miss`() {
        // Allowlisted but not launchable: still skipped via allowlist rule. The result
        // is the same (false) either way; this test just documents intent.
        val result = shouldTriggerRefriction(
            packageName = "com.weird.allowlisted.but.no.launcher",
            ownPackageName = own,
            allowedPackages = setOf("com.weird.allowlisted.but.no.launcher"),
            launchablePackages = emptySet(),
            isInSession = { false },
            restrictedNow = false,
        )
        assertFalse(result)
    }

    // ── Restricted time forces re-trigger past the allowlist + session ───────────

    @Test
    fun `restricted time triggers for an allowlisted app`() {
        assertTrue(decide("com.android.dialer", restrictedNow = true))
    }

    @Test
    fun `restricted time triggers even for an in-session app`() {
        assertTrue(decide("com.twitter.android", inSession = { true }, restrictedNow = true))
    }

    @Test
    fun `restricted time still skips our own package`() {
        assertFalse(decide(own, restrictedNow = true))
    }

    @Test
    fun `restricted time still skips non-launchable system overlays`() {
        assertFalse(decide("com.android.systemui", restrictedNow = true))
    }
}
