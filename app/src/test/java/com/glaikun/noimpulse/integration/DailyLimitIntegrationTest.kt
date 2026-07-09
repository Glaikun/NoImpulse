package com.glaikun.noimpulse.integration

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import com.glaikun.noimpulse.testing.FakeLauncherAppsSource
import com.glaikun.noimpulse.testing.FakeSettingsRepository
import com.glaikun.noimpulse.testing.realHomeViewModel
import com.glaikun.noimpulse.testing.settle
import com.glaikun.noimpulse.ui.NoImpulseContent
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Confirms an app that has already hit a configured daily limit is blocked outright —
 * the drawer shows the block notice instead of any challenge, through the real
 * [NoImpulseContent] composition.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26], qualifiers = "w360dp-h640dp")
class DailyLimitIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `an app already at its daily launch limit shows the block notice instead of the gate`() {
        val twitter = AppEntry("Twitter", "com.twitter.android")
        var launched: String? = null
        val vm = realHomeViewModel(
            usage = null,
            launcher = FakeLauncherAppsSource(installed = listOf(twitter)),
            settings = FakeSettingsRepository(
                setupComplete = true,
                appFriction = mapOf(
                    twitter.packageName to listOf(FrictionRule(FrictionType.DAILY_LAUNCHES, 1)),
                ),
                appLaunches = mapOf(twitter.packageName to 1),
            ),
        )

        composeRule.setContent { NoImpulseContent(vm = vm, onLaunchApp = { launched = it }) }
        composeRule.mainClock.autoAdvance = false
        composeRule.settle()

        composeRule.onNodeWithTag("openDrawer").performClick()
        composeRule.settle()
        composeRule.onNodeWithTag("drawerApp_${twitter.packageName}").performClick()
        composeRule.settle()

        assertTrue(composeRule.onAllNodesWithTag("dailyLimitNotice").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag("challengeInput").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("dailyLimitOk").performClick()
        composeRule.settle()
        assertNull(launched)
    }
}
