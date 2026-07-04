package com.glaikun.noimpulse.ui

import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrictionGateTest {

    private val math = FrictionRule(FrictionType.MATH, 1)
    private val reflection = FrictionRule(FrictionType.REFLECTION, 3)
    private val minutesLimit = FrictionRule(FrictionType.DAILY_MINUTES, 30)
    private val launchesLimit = FrictionRule(FrictionType.DAILY_LAUNCHES, 5)

    @Test
    fun `baseline token challenge always runs first, sized by tokenCount`() {
        assertEquals(FrictionRule(FrictionType.TOKENS, 3), frictionSequence(emptyList(), 3).first())
        assertEquals(FrictionRule(FrictionType.TOKENS, 5), frictionSequence(listOf(math), 5).first())
    }

    @Test
    fun `with no rules the baseline is the only step`() {
        assertEquals(listOf(FrictionRule(FrictionType.TOKENS, 2)), frictionSequence(emptyList(), 2))
    }

    @Test
    fun `assigned rules stack after the baseline in order`() {
        assertEquals(
            listOf(FrictionRule(FrictionType.TOKENS, 1), math, reflection),
            frictionSequence(listOf(math, reflection), 1),
        )
    }

    @Test
    fun `limit rules are excluded from the dialog sequence`() {
        assertEquals(
            listOf(FrictionRule(FrictionType.TOKENS, 1), math),
            frictionSequence(listOf(minutesLimit, math, launchesLimit), 1),
        )
    }

    // ── exceededDailyLimit ───────────────────────────────────────────────────

    @Test
    fun `under every limit returns null`() {
        assertNull(exceededDailyLimit(listOf(minutesLimit, launchesLimit), launchesToday = 4, minutesToday = 29))
    }

    @Test
    fun `reaching the minutes limit returns the minutes rule`() {
        assertEquals(minutesLimit, exceededDailyLimit(listOf(minutesLimit), launchesToday = 0, minutesToday = 30))
    }

    @Test
    fun `going past the minutes limit still returns the minutes rule`() {
        assertEquals(minutesLimit, exceededDailyLimit(listOf(minutesLimit), launchesToday = 0, minutesToday = 45))
    }

    @Test
    fun `launch count never trips a minutes-only limit`() {
        assertNull(exceededDailyLimit(listOf(minutesLimit), launchesToday = 100, minutesToday = 29))
    }

    @Test
    fun `reaching the launches limit returns the launches rule`() {
        assertEquals(launchesLimit, exceededDailyLimit(listOf(launchesLimit), launchesToday = 5, minutesToday = 0))
    }

    @Test
    fun `challenge rules never trip the limit check`() {
        assertNull(exceededDailyLimit(listOf(math, reflection), launchesToday = 100, minutesToday = 100))
    }

    @Test
    fun `no rules means no limit`() {
        assertNull(exceededDailyLimit(emptyList(), launchesToday = 100, minutesToday = 100))
    }
}
