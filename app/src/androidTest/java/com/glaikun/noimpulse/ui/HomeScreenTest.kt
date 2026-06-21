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
class HomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsUsagePromptAndGrantButtonTriggersCallback() {
        var granted = false
        rule.setContent {
            HomeScreen(
                state = HomeViewModel.UiState(setupComplete = true, usageAccessGranted = false),
                onGrantUsageAccess = { granted = true },
            )
        }

        rule.onNodeWithText("Grant access").assertIsDisplayed().performClick()
        assertTrue(granted)
    }

    @Test
    fun showsStatsWhenUsageAccessGranted() {
        rule.setContent {
            HomeScreen(
                state = HomeViewModel.UiState(
                    usageAccessGranted = true,
                    pickupCount = 5,
                    screenOnMinutes = 90,
                ),
            )
        }

        rule.onNodeWithText("Today's Pickups").assertIsDisplayed()
        rule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun tappingAnAllowedAppLaunchesIt() {
        var launched: String? = null
        rule.setContent {
            HomeScreen(
                state = HomeViewModel.UiState(
                    usageAccessGranted = true,
                    allowedApps = listOf(AppEntry("Maps", "com.maps")),
                ),
                onLaunchApp = { launched = it },
            )
        }

        rule.onNodeWithTag("appIcon_com.maps").performClick()
        assertEquals("com.maps", launched)
    }
}
