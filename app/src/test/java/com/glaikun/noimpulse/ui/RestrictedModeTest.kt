package com.glaikun.noimpulse.ui

import com.glaikun.noimpulse.model.TimeWindow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for the Restricted Mode logic: [TimeWindow.contains] (including
 * windows that wrap past midnight) and [isRestrictedNow].
 */
class RestrictedModeTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    // ── TimeWindow.contains ──────────────────────────────────────────────────

    @Test
    fun `same-day window includes its start and excludes its end`() {
        val window = TimeWindow(at(9), at(17))
        assertTrue(window.contains(at(9)))       // start is inclusive
        assertTrue(window.contains(at(12)))
        assertFalse(window.contains(at(17)))     // end is exclusive
        assertFalse(window.contains(at(8, 59)))
    }

    @Test
    fun `wrap-around window covers the overnight range`() {
        val window = TimeWindow(at(22), at(6))   // 22:00 – 06:00
        assertTrue(window.contains(at(23)))
        assertTrue(window.contains(at(0)))
        assertTrue(window.contains(at(5, 59)))
        assertFalse(window.contains(at(6)))      // end is exclusive
        assertFalse(window.contains(at(12)))
    }

    @Test
    fun `equal start and end covers the whole day`() {
        val window = TimeWindow(at(0), at(0))
        assertTrue(window.contains(at(0)))
        assertTrue(window.contains(at(13, 37)))
    }

    // ── isRestrictedNow ──────────────────────────────────────────────────────

    @Test
    fun `disabled mode is never restricted`() {
        assertFalse(isRestrictedNow(enabled = false, allowedWindows = emptyList(), minuteOfDay = at(3)))
    }

    @Test
    fun `enabled inside an allowed window is not restricted`() {
        val windows = listOf(TimeWindow(at(9), at(17)))
        assertFalse(isRestrictedNow(enabled = true, allowedWindows = windows, minuteOfDay = at(10)))
    }

    @Test
    fun `enabled outside every allowed window is restricted`() {
        val windows = listOf(TimeWindow(at(9), at(17)))
        assertTrue(isRestrictedNow(enabled = true, allowedWindows = windows, minuteOfDay = at(20)))
    }

    @Test
    fun `enabled with no windows is not restricted — there is no schedule to enforce`() {
        assertFalse(isRestrictedNow(enabled = true, allowedWindows = emptyList(), minuteOfDay = at(12)))
    }

    @Test
    fun `removing the active window stays unrestricted when another window still covers now`() {
        val morningToMidnight = TimeWindow(at(7), at(0))    // 07:00 – 12:00am (midnight)
        val afternoon = TimeWindow(at(16), at(20))          // 04:00pm – 08:00pm
        val sixPm = at(18)

        // At 6pm both windows cover the current time.
        val windows = listOf(morningToMidnight, afternoon)
        assertFalse(isRestrictedNow(enabled = true, allowedWindows = windows, minuteOfDay = sixPm))

        // Remove the 4–8pm window we're currently in — 7am–midnight still covers 6pm,
        // so apps must NOT become restricted.
        val remaining = windows - afternoon
        assertFalse(isRestrictedNow(enabled = true, allowedWindows = remaining, minuteOfDay = sixPm))
    }

    @Test
    fun `multiple windows — inside any one is enough to be unrestricted`() {
        val windows = listOf(TimeWindow(at(7), at(9)), TimeWindow(at(18), at(22)))
        assertFalse(isRestrictedNow(enabled = true, allowedWindows = windows, minuteOfDay = at(20)))
        assertTrue(isRestrictedNow(enabled = true, allowedWindows = windows, minuteOfDay = at(12)))
    }
}
