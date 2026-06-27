package com.glaikun.noimpulse.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.ui.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                // accessibilityGranted = true so only the usage-access tile shows a Grant button
                state = HomeViewModel.UiState(
                    usageAccessGranted = false,
                    accessibilityGranted = true,
                ),
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

    @Test
    fun accessibilityTileShowsWhyLinkWhenUngranted() {
        rule.setContent {
            SetupScreen(
                state = HomeViewModel.UiState(accessibilityGranted = false),
                installedApps = emptyList(),
            )
        }

        rule.onNodeWithTag("accessibilityWhy").assertIsDisplayed()
    }

    @Test
    fun accessibilityTileHidesWhyLinkWhenGranted() {
        rule.setContent {
            SetupScreen(
                state = HomeViewModel.UiState(accessibilityGranted = true),
                installedApps = emptyList(),
            )
        }

        // Once granted, the "Why?" link disappears (the tile collapses to ✓ Granted).
        rule.onAllNodesWithTag("accessibilityWhy").assertCountEquals(0)
    }

    @Test
    fun tappingWhyLinkOpensPrePromptDialog() {
        var grantCalled = false
        rule.setContent {
            SetupScreen(
                state = HomeViewModel.UiState(accessibilityGranted = false),
                installedApps = emptyList(),
                onGrantAccessibility = { grantCalled = true },
            )
        }

        // The pre-prompt isn't visible before we tap.
        rule.onAllNodesWithTag("accessibilityOpenSettings").assertCountEquals(0)

        rule.onNodeWithTag("accessibilityWhy").performClick()

        // Now the dialog is up — confirm + dismiss buttons present.
        rule.onNodeWithTag("accessibilityOpenSettings").assertIsDisplayed()
        rule.onNodeWithTag("accessibilityMaybeLater").assertIsDisplayed()
        // Tapping "Why?" alone must not deep-link to system settings yet.
        assertFalse(grantCalled)
    }

    @Test
    fun openSystemSettingsFiresGrantCallback() {
        var grantCalled = false
        rule.setContent {
            SetupScreen(
                state = HomeViewModel.UiState(accessibilityGranted = false),
                installedApps = emptyList(),
                onGrantAccessibility = { grantCalled = true },
            )
        }

        rule.onNodeWithTag("accessibilityWhy").performClick()
        rule.onNodeWithTag("accessibilityOpenSettings").performClick()

        assertTrue(grantCalled)
    }

    @Test
    fun maybeLaterDismissesWithoutGranting() {
        var grantCalled = false
        var finished = false
        rule.setContent {
            SetupScreen(
                state = HomeViewModel.UiState(accessibilityGranted = false),
                installedApps = emptyList(),
                onGrantAccessibility = { grantCalled = true },
                onFinish = { finished = true },
            )
        }

        rule.onNodeWithTag("accessibilityWhy").performClick()
        rule.onNodeWithTag("accessibilityMaybeLater").performClick()

        // Dialog gone, callback not fired, and Finish still works (soft-skip path).
        rule.onAllNodesWithTag("accessibilityOpenSettings").assertCountEquals(0)
        assertFalse(grantCalled)
        rule.onNodeWithText("Finish").performClick()
        assertTrue(finished)
    }
}
