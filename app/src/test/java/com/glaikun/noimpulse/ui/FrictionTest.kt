package com.glaikun.noimpulse.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrictionTest {

    @Test
    fun `generated math problem's answer is the sum of its operands`() {
        repeat(100) {
            val p = generateMathProblem()
            assertEquals(p.a + p.b, p.answer)
        }
    }

    @Test
    fun `pickReflectionQuestions returns the requested count from the bank`() {
        val picked = pickReflectionQuestions(3)
        assertEquals(3, picked.size)
        assertTrue(reflectionBank.containsAll(picked))
    }

    @Test
    fun `reflectionsAllCorrect is true only when every designated answer is selected`() {
        val questions = listOf(
            ReflectionQuestion("a", true),
            ReflectionQuestion("b", false),
            ReflectionQuestion("c", true),
        )

        assertFalse(reflectionsAllCorrect(questions, emptyMap()))
        assertFalse(reflectionsAllCorrect(questions, mapOf(0 to true, 1 to false)))      // incomplete
        assertFalse(reflectionsAllCorrect(questions, mapOf(0 to true, 1 to true, 2 to true))) // one wrong
        assertTrue(reflectionsAllCorrect(questions, mapOf(0 to true, 1 to false, 2 to true)))
    }

    @Test
    fun `reflection bank has a mix of yes and no answers`() {
        assertTrue(reflectionBank.any { it.answer })
        assertTrue(reflectionBank.any { !it.answer })
    }
}
