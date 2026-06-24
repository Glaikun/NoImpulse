package com.glaikun.noimpulse.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntroScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun continueButtonTriggersCallback() {
        var clicked = 0
        rule.setContent { IntroScreen(onContinue = { clicked++ }) }

        rule.onNodeWithTag("introContinue").assertIsDisplayed().performClick()

        assertEquals(1, clicked)
    }

    @Test
    fun openSourceTrustLineIsPresent() {
        rule.setContent { IntroScreen() }

        // The trust line — open source, on-device — is the load-bearing claim that
        // the rest of the consent flow (especially the accessibility pre-prompt)
        // depends on. If this disappears we should know.
        rule.onNodeWithText("Open source, on-device only.").assertIsDisplayed()
    }

    @Test
    fun launcherIntroHeadingIsPresent() {
        rule.setContent { IntroScreen() }

        rule.onNodeWithText("NoImpulse is a launcher.").assertIsDisplayed()
    }
}
