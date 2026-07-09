package com.glaikun.noimpulse.integration

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.testing.FakeLauncherAppsSource
import com.glaikun.noimpulse.testing.FakeSettingsRepository
import com.glaikun.noimpulse.testing.realHomeViewModel
import com.glaikun.noimpulse.testing.settle
import com.glaikun.noimpulse.ui.NoImpulseContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Confirms the integration between allowlist state and the drawer's launch routing
 * through the real [NoImpulseContent] composition root: a non-allowlisted app is gated by
 * the friction challenge, and once allowlisted the same app launches directly.
 *
 * The allowlist-add gate itself (long-press -> bottom sheet -> UUID + reflection
 * dialog) is exercised thoroughly by the existing hand-fed-state instrumentation
 * tests in `AppDrawerScreenTest` — `ModalBottomSheet`'s dismiss animation never
 * settles under Robolectric (a known Compose-on-Robolectric limitation, not a
 * product bug), so this test drives that one step directly through the ViewModel
 * and keeps the rest of the flow — the part actually worth an integration check — on real UI.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26], qualifiers = "w360dp-h640dp")
class AllowlistIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `allowlisting an app removes its friction gate on the next drawer tap`() {
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

        // Not allowlisted yet — tapping gates behind the baseline token challenge, not
        // a direct launch.
        composeRule.onNodeWithTag("drawerApp_${twitter.packageName}").performClick()
        composeRule.settle()
        assertTrue(composeRule.onAllNodesWithTag("challengeInput").fetchSemanticsNodes().isNotEmpty())
        assertNull(launched)

        // Cancel out of the gate, then allowlist the app the way completing the
        // (separately tested) UUID + reflection gate would.
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.settle()
        vm.setAppAllowed(twitter.packageName, true)
        composeRule.settle()

        composeRule.onNodeWithTag("drawerApp_${twitter.packageName}").performClick()
        composeRule.settle()
        assertEquals(twitter.packageName, launched)
    }
}
