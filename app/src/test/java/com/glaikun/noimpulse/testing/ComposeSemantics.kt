package com.glaikun.noimpulse.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * Reads the text of the single node whose displayed text matches [regex]. Used by the
 * integration suite to pull randomly generated challenge secrets (UUIDs, math problems) straight
 * off the rendered UI, rather than adding test-only `testTag`s to production dialogs
 * purely to expose a value no existing test needed to read before.
 */
internal fun ComposeContentTestRule.textMatching(regex: Regex): String =
    onNode(hasTextMatching(regex)).fetchTextMatching(regex)

/**
 * Reads every node's text that matches [regex], in tree order. Used for the launch
 * challenge's N hex tokens, which repeat the same untagged `Text` composable.
 */
internal fun ComposeContentTestRule.allTextsMatching(regex: Regex): List<String> =
    onAllNodes(hasTextMatching(regex)).fetchSemanticsNodes().flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            .map { it.text }
            .filter { regex.matches(it) }
    }

/**
 * Advances both Compose's test clock and the Robolectric main looper by a bounded
 * number of frames, instead of `waitForIdle()`'s "loop until truly idle" wait.
 *
 * Two Robolectric-only quirks make `waitForIdle()` unusable once a dialog/sheet or a
 * focused text field is on screen: (1) `ModalBottomSheet` and `AlertDialog` open a
 * second compose root whose transition/cursor-blink animation never reports idle, so
 * `waitForIdle()` spins until Espresso's `AppNotIdleException` fires at 60s; (2)
 * `combinedClickable`'s tap/long-press disambiguation runs a real coroutine delay on
 * the *Robolectric main looper*, not on Compose's frame clock, so with
 * `mainClock.autoAdvance` off a plain `performClick()` on a drawer app icon silently
 * never fires `onClick` unless that looper is pumped too. `settle()` drives both,
 * bounded, every time — call it after every interaction once
 * [ComposeContentTestRule.mainClock]'s `autoAdvance` is `false` (set that once, right
 * after `setContent`, and leave it off for the rest of the test).
 */
internal fun ComposeContentTestRule.settle(frames: Int = 30) {
    repeat(frames) {
        mainClock.advanceTimeByFrame()
        // combinedClickable's tap/long-press disambiguation runs a real coroutine delay
        // on the main looper, not on Compose's frame clock — pump it too, one bounded
        // pass at a time (never Espresso's "loop until truly idle" wait).
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
}

/**
 * Reads the full text of the single node whose text contains [substring] — e.g. the math
 * challenge's "Solve to open: 5 + 7 = ?" sentence, so the operands can be parsed out.
 */
internal fun ComposeContentTestRule.textContaining(substring: String): String =
    onNode(hasTextContaining(substring)).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text).orEmpty()
        .first { it.text.contains(substring) }
        .text

private fun hasTextContaining(substring: String): SemanticsMatcher =
    SemanticsMatcher("text contains $substring") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.contains(substring) }
    }

private fun hasTextMatching(regex: Regex): SemanticsMatcher =
    SemanticsMatcher("text matches $regex") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { regex.matches(it.text) }
    }

private fun SemanticsNodeInteraction.fetchTextMatching(regex: Regex): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty()
        .first { regex.matches(it.text) }
        .text
