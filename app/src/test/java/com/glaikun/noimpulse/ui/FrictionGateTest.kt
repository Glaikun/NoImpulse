package com.glaikun.noimpulse.ui

import com.glaikun.noimpulse.api.FrictionRule
import com.glaikun.noimpulse.api.FrictionType
import org.junit.Assert.assertEquals
import org.junit.Test

class FrictionGateTest {

    private val math = FrictionRule(FrictionType.MATH, 1)
    private val reflection = FrictionRule(FrictionType.REFLECTION, 3)

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
}
