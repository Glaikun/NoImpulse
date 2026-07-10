package com.glaikun.noimpulse.services

import com.glaikun.noimpulse.data.FrictionSessionLedger
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [refrictionDecision], the pure filter chain that [FrictionWatchService]
 * uses inside `onAccessibilityEvent`, and [minutesUntilLimit], which sizes the
 * minutes-limit watchdog delay.
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
        hasLimitRules: (String) -> Boolean = { false },
        inSession: (String) -> Boolean = { false },
        restrictedNow: Boolean = false,
    ): RefrictionDecision = refrictionDecision(
        packageName = pkg,
        ownPackageName = own,
        allowedPackages = allowed,
        launchablePackages = launchable,
        hasLimitRules = hasLimitRules,
        isInSession = inSession,
        restrictedNow = restrictedNow,
    )

    @Test
    fun `non-allowlisted launchable not in session triggers`() {
        assertEquals(RefrictionDecision.TRIGGER, decide("com.twitter.android"))
    }

    @Test
    fun `our own package is skipped`() {
        assertEquals(RefrictionDecision.SKIP, decide(own))
    }

    @Test
    fun `allowlisted package is skipped even when launchable`() {
        assertEquals(RefrictionDecision.SKIP, decide("com.android.dialer"))
    }

    @Test
    fun `non-launchable package is skipped (system overlay)`() {
        assertEquals(RefrictionDecision.SKIP, decide("com.android.systemui"))
    }

    @Test
    fun `IME package is skipped (not in launchable set)`() {
        assertEquals(RefrictionDecision.SKIP, decide("com.google.android.inputmethod.latin"))
    }

    @Test
    fun `package already in session is skipped`() {
        assertEquals(
            RefrictionDecision.SKIP,
            decide("com.twitter.android", inSession = { it == "com.twitter.android" }),
        )
    }

    @Test
    fun `package out of session triggers even when others are in session`() {
        assertEquals(
            RefrictionDecision.TRIGGER,
            decide("com.instagram.android", inSession = { it == "com.twitter.android" }),
        )
    }

    @Test
    fun `integration with FrictionSessionLedger`() {
        val ledger = FrictionSessionLedger()
        assertEquals(
            RefrictionDecision.TRIGGER,
            decide("com.twitter.android", inSession = ledger::isInSession),
        )

        ledger.markPassed("com.twitter.android")
        assertEquals(
            RefrictionDecision.SKIP,
            decide("com.twitter.android", inSession = ledger::isInSession),
        )

        ledger.clearAll()
        assertEquals(
            RefrictionDecision.TRIGGER,
            decide("com.twitter.android", inSession = ledger::isInSession),
        )
    }

    @Test
    fun `skip-rule precedence — own beats everything else`() {
        // Allowed and launchable and in-session — own check still wins (SKIP).
        val result = refrictionDecision(
            packageName = own,
            ownPackageName = own,
            allowedPackages = setOf(own),
            launchablePackages = setOf(own),
            hasLimitRules = { true },
            isInSession = { true },
            restrictedNow = true,
        )
        assertEquals(RefrictionDecision.SKIP, result)
    }

    @Test
    fun `skip-rule precedence — allowlist beats launchable miss`() {
        // Allowlisted but not launchable: still skipped via allowlist rule. The result
        // is the same (SKIP) either way; this test just documents intent.
        val result = refrictionDecision(
            packageName = "com.weird.allowlisted.but.no.launcher",
            ownPackageName = own,
            allowedPackages = setOf("com.weird.allowlisted.but.no.launcher"),
            launchablePackages = emptySet(),
            hasLimitRules = { false },
            isInSession = { false },
            restrictedNow = false,
        )
        assertEquals(RefrictionDecision.SKIP, result)
    }

    // ── Restricted time blocks non-allowed apps but spares the allowlist ─────────

    @Test
    fun `restricted time still skips an allowlisted app`() {
        // Allowlisted apps stay usable off-hours — restriction only targets non-allowed apps.
        assertEquals(RefrictionDecision.SKIP, decide("com.android.dialer", restrictedNow = true))
    }

    @Test
    fun `restricted time triggers for a non-allowlisted app even when in session`() {
        assertEquals(
            RefrictionDecision.TRIGGER,
            decide("com.twitter.android", inSession = { true }, restrictedNow = true),
        )
    }

    @Test
    fun `restricted time still skips our own package`() {
        assertEquals(RefrictionDecision.SKIP, decide(own, restrictedNow = true))
    }

    @Test
    fun `restricted time still skips non-launchable system overlays`() {
        assertEquals(RefrictionDecision.SKIP, decide("com.android.systemui", restrictedNow = true))
    }

    // ── Daily limits bypass the session short-circuit ─────────────────────────────

    @Test
    fun `limit-carrying package defers to limit evaluation even when in session`() {
        assertEquals(
            RefrictionDecision.EVALUATE_LIMITS,
            decide("com.twitter.android", hasLimitRules = { true }, inSession = { true }),
        )
    }

    @Test
    fun `limit-carrying package out of session also defers to limit evaluation`() {
        assertEquals(
            RefrictionDecision.EVALUATE_LIMITS,
            decide("com.twitter.android", hasLimitRules = { true }),
        )
    }

    @Test
    fun `restricted time beats the limit check`() {
        assertEquals(
            RefrictionDecision.TRIGGER,
            decide("com.twitter.android", hasLimitRules = { true }, restrictedNow = true),
        )
    }

    @Test
    fun `allowlisted package is skipped even with limit rules`() {
        assertEquals(
            RefrictionDecision.SKIP,
            decide("com.android.dialer", hasLimitRules = { true }),
        )
    }

    @Test
    fun `non-launchable package is skipped even with limit rules`() {
        assertEquals(
            RefrictionDecision.SKIP,
            decide("com.android.systemui", hasLimitRules = { true }),
        )
    }

    // ── minutesUntilLimit ─────────────────────────────────────────────────────────

    private val minutesLimit = FrictionRule(FrictionType.DAILY_MINUTES, 30)
    private val launchesLimit = FrictionRule(FrictionType.DAILY_LAUNCHES, 3)
    private val math = FrictionRule(FrictionType.MATH, 1)

    @Test
    fun `no rules means no minutes horizon`() {
        assertNull(minutesUntilLimit(emptyList(), minutesToday = 10))
    }

    @Test
    fun `challenge and launches rules alone give no minutes horizon`() {
        assertNull(minutesUntilLimit(listOf(math, launchesLimit), minutesToday = 10))
    }

    @Test
    fun `under the limit returns the remaining minutes`() {
        assertEquals(25, minutesUntilLimit(listOf(math, minutesLimit), minutesToday = 5))
    }

    @Test
    fun `at the limit returns zero`() {
        assertEquals(0, minutesUntilLimit(listOf(minutesLimit), minutesToday = 30))
    }

    @Test
    fun `over the limit returns a negative remainder`() {
        assertEquals(-10, minutesUntilLimit(listOf(minutesLimit), minutesToday = 40))
    }
}
