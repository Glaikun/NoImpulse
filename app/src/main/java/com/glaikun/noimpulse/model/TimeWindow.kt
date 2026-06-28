package com.glaikun.noimpulse.model

/**
 * A time-of-day window used by Restricted Mode, expressed as minutes since midnight in
 * `[0, 1440)`. [end] is exclusive. A window where [start] >= [end] wraps past midnight
 * (e.g. 22:00–06:00); a window where they are equal covers the whole day.
 */
data class TimeWindow(val startMinute: Int, val endMinute: Int) {

    /** True if [minuteOfDay] (minutes since midnight) falls inside this window. */
    fun contains(minuteOfDay: Int): Boolean =
        when {
            startMinute == endMinute -> true                       // whole day
            startMinute < endMinute -> minuteOfDay in startMinute until endMinute
            else -> minuteOfDay >= startMinute || minuteOfDay < endMinute  // wraps midnight
        }
}
