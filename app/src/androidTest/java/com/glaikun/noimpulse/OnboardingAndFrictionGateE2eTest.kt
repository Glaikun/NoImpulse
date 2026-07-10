package com.glaikun.noimpulse

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val HEX_TOKEN_REGEX = Regex("^[0-9A-F]{6}$")

/**
 * The one real-device e2e test: launches the actual [MainActivity] with its actual
 * Hilt dependency graph (see [TestAppModule] for the fakes standing in for
 * system-facing sources), and walks fresh-install onboarding straight into gating a
 * non-allowlisted app behind the friction challenge.
 *
 * Everything below this — the screen-to-screen flow, the friction dialogs, the
 * daily-limit/restricted-mode notices — is already covered thoroughly by the JVM
 * integration suite (`app/src/test/.../integration/`), which runs in seconds with no
 * device attached. This test exists to catch the one class of bug that tier
 * structurally can't: a broken Hilt wiring, a manifest/permission problem, or an
 * Activity that doesn't actually come up when the OS launches it for real.
 *
 * Requires a connected device or emulator — run via
 * `./gradlew :app:connectedDebugAndroidTest`. Expect it to take noticeably longer
 * than the JVM suites; that's the trade for running against the real framework.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnboardingAndFrictionGateE2eTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun freshInstallOnboardsThenGatesTheTestAppBehindFriction() {
        val pkg = TestAppModule.testApp.packageName

        composeRule.onNodeWithTag("introContinue").performClick()
        composeRule.onNodeWithText("Finish").performClick()

        composeRule.onNodeWithTag("openDrawer").performClick()
        composeRule.onNodeWithTag("drawerApp_$pkg").performClick()

        val token = composeRule.onNode(hasHexToken()).fetchTokenText()
        composeRule.onNodeWithTag("challengeInput").performTextInput(token)
        composeRule.onNodeWithTag("challengeLaunch").performClick()

        // Back on Home — the drawer closed, and the fake app's launch (which resolves
        // to no real Intent, since its package doesn't exist) didn't crash the Activity.
        composeRule.onNodeWithTag("openDrawer").assertIsDisplayed()
    }
}

private fun hasHexToken(): SemanticsMatcher =
    SemanticsMatcher("hex token") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { HEX_TOKEN_REGEX.matches(it.text) }
    }

private fun SemanticsNodeInteraction.fetchTokenText(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty()
        .first { HEX_TOKEN_REGEX.matches(it.text) }
        .text
