package com.glaikun.noimpulse.model

/**
 * How a friction acts: a [CHALLENGE] is a dialog step the FrictionGate runs; a
 * [LIMIT] is a precondition checked before the gate — once exceeded, the app is
 * blocked until the day rolls over.
 */
enum class FrictionKind { CHALLENGE, LIMIT }

/** The kinds of friction a user can assign to opening a non-allowlisted app. */
enum class FrictionType(val kind: FrictionKind) {
    TIMED_WAIT(FrictionKind.CHALLENGE),
    TOKENS(FrictionKind.CHALLENGE),
    MATH(FrictionKind.CHALLENGE),
    REFLECTION(FrictionKind.CHALLENGE),
    DAILY_MINUTES(FrictionKind.LIMIT),
    DAILY_LAUNCHES(FrictionKind.LIMIT),
}

/**
 * A per-app opening-friction assignment. [param] is interpreted per [type]:
 * - [FrictionType.TIMED_WAIT] — seconds to wait
 * - [FrictionType.TOKENS] — number of tokens to type
 * - [FrictionType.MATH] — number of problems to solve
 * - [FrictionType.REFLECTION] — number of reflection questions to answer
 * - [FrictionType.DAILY_MINUTES] — max foreground minutes per day
 * - [FrictionType.DAILY_LAUNCHES] — max gated opens per day (drawer launches and passed re-friction gates)
 */
data class FrictionRule(val type: FrictionType, val param: Int)
