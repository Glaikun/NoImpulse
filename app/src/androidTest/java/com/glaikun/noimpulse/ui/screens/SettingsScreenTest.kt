package com.glaikun.noimpulse.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.model.TextSize
import com.glaikun.noimpulse.model.ThemeMode
import com.glaikun.noimpulse.model.TimeWindow
import com.glaikun.noimpulse.ui.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun permissionTileShowsGrantedStatus() {
        rule.setContent {
            SettingsScreen(state = HomeViewModel.UiState(usageAccessGranted = true))
        }

        rule.onNodeWithText("✓ Granted").assertIsDisplayed()   // usage access
    }

    @Test
    fun switchLauncherOpensUuidGate() {
        rule.setContent {
            SettingsScreen(state = HomeViewModel.UiState())
        }

        // The gate isn't shown until the button is tapped.
        rule.onNodeWithTag("switchLauncher").performClick()
        rule.onNodeWithTag("uuidInput").assertIsDisplayed()
        rule.onNodeWithTag("uuidConfirm").assertIsDisplayed()
    }

    @Test
    fun togglingRestrictedModeFiresCallback() {
        var enabled: Boolean? = null
        rule.setContent {
            SettingsScreen(
                state = HomeViewModel.UiState(restrictedModeEnabled = false),
                onSetRestrictedModeEnabled = { enabled = it },
            )
        }

        rule.onNodeWithTag("restrictedModeSwitch").performClick()
        assertEquals(true, enabled)
    }

    @Test
    fun addingAnAllowedWindowFiresCallback() {
        var added: TimeWindow? = null
        rule.setContent {
            SettingsScreen(
                state = HomeViewModel.UiState(restrictedModeEnabled = true),
                onAddAllowedWindow = { added = it },
            )
        }

        rule.onNodeWithTag("addAllowedWindow").performClick()
        rule.onNodeWithTag("confirmAddWindow").performClick()

        // Default window offered by the dialog is 09:00–17:00.
        assertEquals(TimeWindow(9 * 60, 17 * 60), added)
    }

    @Test
    fun removingAnAllowedWindowFiresCallback() {
        var removed: TimeWindow? = null
        val window = TimeWindow(9 * 60, 17 * 60)
        rule.setContent {
            SettingsScreen(
                state = HomeViewModel.UiState(
                    restrictedModeEnabled = true,
                    allowedWindows = listOf(window),
                ),
                onRemoveAllowedWindow = { removed = it },
            )
        }

        rule.onNodeWithText("Remove").performClick()
        assertEquals(window, removed)
    }

    @Test
    fun allowedTimesHiddenWhenRestrictedModeOff() {
        rule.setContent {
            SettingsScreen(state = HomeViewModel.UiState(restrictedModeEnabled = false))
        }

        // The "Add allowed time" affordance only shows once the mode is on.
        rule.onAllNodesWithTag("addAllowedWindow").assertCountEquals(0)
    }

    @Test
    fun turningOffRestrictedModeWithWindowsOpensUuidGateAndDoesNotDisableImmediately() {
        var enabledChange: Boolean? = null
        rule.setContent {
            SettingsScreen(
                state = HomeViewModel.UiState(
                    restrictedModeEnabled = true,
                    allowedWindows = listOf(TimeWindow(9 * 60, 17 * 60)),
                ),
                onSetRestrictedModeEnabled = { enabledChange = it },
            )
        }

        rule.onNodeWithTag("restrictedModeSwitch").performClick()

        // The gate appears and the mode is NOT disabled until the UUID is typed.
        rule.onNodeWithTag("uuidInput").assertIsDisplayed()
        rule.onNodeWithTag("uuidConfirm").assertIsDisplayed()
        assertNull(enabledChange)
    }

    @Test
    fun turningOffRestrictedModeWithNoWindowsDisablesDirectly() {
        var enabledChange: Boolean? = null
        rule.setContent {
            SettingsScreen(
                state = HomeViewModel.UiState(
                    restrictedModeEnabled = true,
                    allowedWindows = emptyList(),
                ),
                onSetRestrictedModeEnabled = { enabledChange = it },
            )
        }

        rule.onNodeWithTag("restrictedModeSwitch").performClick()

        // No schedule to protect, so no gate — it disables immediately.
        rule.onAllNodesWithTag("uuidInput").assertCountEquals(0)
        assertEquals(false, enabledChange)
    }

    @Test
    fun selectingThemeModeFiresCallback() {
        var mode: ThemeMode? = null
        rule.setContent {
            SettingsScreen(
                state = HomeViewModel.UiState(themeMode = ThemeMode.DARK),
                onSetThemeMode = { mode = it },
            )
        }

        rule.onNodeWithTag("themeMode_LIGHT").performClick()
        assertEquals(ThemeMode.LIGHT, mode)
    }

    @Test
    fun selectingTextSizeFiresCallback() {
        var size: TextSize? = null
        rule.setContent {
            SettingsScreen(
                state = HomeViewModel.UiState(textSize = TextSize.DEFAULT),
                onSetTextSize = { size = it },
            )
        }

        rule.onNodeWithTag("textSize_LARGEST").performClick()
        assertEquals(TextSize.LARGEST, size)
    }
}
