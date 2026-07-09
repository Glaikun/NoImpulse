package com.glaikun.noimpulse.integration

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import com.glaikun.noimpulse.testing.FakeLauncherAppsSource
import com.glaikun.noimpulse.testing.FakeSettingsRepository
import com.glaikun.noimpulse.testing.allTextsMatching
import com.glaikun.noimpulse.testing.realHomeViewModel
import com.glaikun.noimpulse.testing.settle
import com.glaikun.noimpulse.testing.textContaining
import com.glaikun.noimpulse.ui.AppScreen
import com.glaikun.noimpulse.ui.NoImpulseContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val HEX_TOKEN_REGEX = Regex("^[0-9A-F]{6}$")

/**
 * Drives the friction gate on a non-allowlisted app through the real [NoImpulseContent]
 * composition: the baseline token challenge alone, and stacked with a MATH rule.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26], qualifiers = "w360dp-h640dp")
class FrictionGateIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `completing the baseline token challenge launches the app and closes the drawer`() {
        val twitter = AppEntry("Twitter", "com.twitter.android")
        var launched: String? = null
        val vm = realHomeViewModel(
            usage = null,
            launcher = FakeLauncherAppsSource(installed = listOf(twitter)),
            settings = FakeSettingsRepository(setupComplete = true),
        )

        composeRule.setContent { NoImpulseContent(vm = vm, onLaunchApp = { launched = it }) }
        composeRule.mainClock.autoAdvance = false
        composeRule.settle()

        composeRule.onNodeWithTag("openDrawer").performClick()
        composeRule.settle()
        composeRule.onNodeWithTag("drawerApp_${twitter.packageName}").performClick()
        composeRule.settle()

        val token = composeRule.allTextsMatching(HEX_TOKEN_REGEX).single()
        composeRule.onNodeWithTag("challengeInput").performTextInput(token)
        composeRule.settle()
        composeRule.onNodeWithTag("challengeLaunch").performClick()
        composeRule.settle()

        assertEquals(twitter.packageName, launched)
        assertEquals(AppScreen.Home, vm.screen.value)
    }

    @Test
    fun `a stacked MATH rule requires both the token step and the math step before launching`() {
        val twitter = AppEntry("Twitter", "com.twitter.android")
        var launched: String? = null
        val vm = realHomeViewModel(
            usage = null,
            launcher = FakeLauncherAppsSource(installed = listOf(twitter)),
            settings = FakeSettingsRepository(
                setupComplete = true,
                appFriction = mapOf(twitter.packageName to listOf(FrictionRule(FrictionType.MATH, 1))),
            ),
        )

        composeRule.setContent { NoImpulseContent(vm = vm, onLaunchApp = { launched = it }) }
        composeRule.mainClock.autoAdvance = false
        composeRule.settle()

        composeRule.onNodeWithTag("openDrawer").performClick()
        composeRule.settle()
        composeRule.onNodeWithTag("drawerApp_${twitter.packageName}").performClick()
        composeRule.settle()

        // Step 1: baseline token challenge.
        val token = composeRule.allTextsMatching(HEX_TOKEN_REGEX).single()
        composeRule.onNodeWithTag("challengeInput").performTextInput(token)
        composeRule.settle()
        composeRule.onNodeWithTag("challengeLaunch").performClick()
        composeRule.settle()

        // Token step passed but the stacked MATH step hasn't — no launch yet.
        assertNull(launched)

        // Step 2: the math problem the token step advanced into.
        val problem = composeRule.textContaining("Solve to open:")
        val (a, b) = Regex("(\\d+) \\+ (\\d+)").find(problem)!!.destructured
        composeRule.onNodeWithTag("challengeInput").performTextInput((a.toInt() + b.toInt()).toString())
        composeRule.settle()
        composeRule.onNodeWithTag("challengeLaunch").performClick()
        composeRule.settle()

        assertEquals(twitter.packageName, launched)
    }
}
