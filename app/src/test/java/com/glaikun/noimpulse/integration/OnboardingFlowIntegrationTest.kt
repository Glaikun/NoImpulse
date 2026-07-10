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
import com.glaikun.noimpulse.ui.AppScreen
import com.glaikun.noimpulse.ui.NoImpulseContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Drives the real onboarding flow through [NoImpulseContent] end to end: a fresh install
 * boots to Intro, then Setup, and finishing setup with one app allowed lands on Home
 * with that app reachable from the home screen grid.
 *
 * Built on real dispatchers (see [com.glaikun.noimpulse.testing.realHomeViewModel]) —
 * no [kotlinx.coroutines.test.TestScope] — so the manual clock in
 * [com.glaikun.noimpulse.testing.settle] is what synchronizes the test with the
 * ViewModel's async work. The `qualifiers` on [Config] give Robolectric a real screen
 * size; without it, `LazyColumn`/`LazyVerticalGrid` viewports collapse to zero height
 * and silently swallow clicks on their items.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26], qualifiers = "w360dp-h640dp")
class OnboardingFlowIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `fresh install walks Intro then Setup then lands on Home with the chosen app allowed`() {
        val calculator = AppEntry("Calculator", "com.android.calculator2")
        val vm = realHomeViewModel(
            usage = null,
            launcher = FakeLauncherAppsSource(
                installed = listOf(calculator),
                homeScreen = listOf(calculator),
            ),
            settings = FakeSettingsRepository(introSeen = false, setupComplete = false),
        )

        composeRule.setContent { NoImpulseContent(vm = vm) }
        composeRule.mainClock.autoAdvance = false
        composeRule.settle()

        composeRule.onNodeWithTag("introContinue").performClick()
        composeRule.settle()
        assertEquals(AppScreen.Setup, vm.screen.value)

        composeRule.onNodeWithTag("appToggle_${calculator.packageName}").performClick()
        composeRule.settle()
        assertTrue(vm.state.value.allowedApps.any { it.packageName == calculator.packageName })

        composeRule.onNodeWithText("Finish").performClick()
        composeRule.settle()
        assertTrue(vm.state.value.setupComplete)
        assertEquals(AppScreen.Home, vm.screen.value)
        assertTrue(composeRule.onAllNodesWithTag("appIcon_${calculator.packageName}").fetchSemanticsNodes().isNotEmpty())
    }
}
