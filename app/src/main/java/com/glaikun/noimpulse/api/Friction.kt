package com.glaikun.noimpulse.api

/** The kinds of friction a user can assign to opening a non-allowlisted app. */
enum class FrictionType { TIMED_WAIT, TOKENS, MATH, REFLECTION }

/**
 * A per-app opening-friction assignment. [param] is interpreted per [type]:
 * - [FrictionType.TIMED_WAIT] — seconds to wait
 * - [FrictionType.TOKENS] — number of tokens to type
 * - [FrictionType.MATH] — number of problems to solve
 * - [FrictionType.REFLECTION] — number of reflection questions to answer
 */
data class FrictionRule(val type: FrictionType, val param: Int)
