package com.glaikun.noimpulse.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.api.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun finishButtonTriggersOnFinish() {
        var finished = false
        rule.setContent {
            SetupScreen(
                state = HomeViewModel.UiState(setupComplete = false),
                installedApps = emptyList(),
                onFinish = { finished = true },
            )
        }

        rule.onNodeWithText("Finish").performClick()
        assertTrue(finished)
    }

    @Test
    fun grantedStepShowsStatusUngrantedStepShowsActionButton() {
        rule.setContent {
            SetupScreen(
                state = HomeViewModel.UiState(usageAccessGranted = true, isDefaultHome = false),
                installedApps = emptyList(),
            )
        }

        rule.onNodeWithText("✓ Granted").assertIsDisplayed()   // usage access
        rule.onNodeWithText("Set").assertIsDisplayed()         // default home action
    }

    @Test
    fun grantUsageAccessButtonTriggersCallback() {
        var clicked = false
        rule.setContent {
            SetupScreen(
                state = HomeViewModel.UiState(usageAccessGranted = false),
                installedApps = emptyList(),
                onGrantUsageAccess = { clicked = true },
            )
        }

        rule.onNodeWithText("Grant").performClick()
        assertTrue(clicked)
    }

    @Test
    fun togglingAnAppRowCallsOnToggleApp() {
        var toggled: Pair<String, Boolean>? = null
        rule.setContent {
            SetupScreen(
                state = HomeViewModel.UiState(setupComplete = false),
                installedApps = listOf(AppEntry("Maps", "com.maps")),
                onToggleApp = { pkg, allowed -> toggled = pkg to allowed },
            )
        }

        rule.onNodeWithTag("appToggle_com.maps").performClick()
        assertEquals("com.maps" to true, toggled)
    }
}
