package com.glaikun.noimpulse.integration

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.TimeWindow
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
import java.time.LocalTime

/**
 * Confirms Restricted Mode blocks a non-allowlisted app while an allowlisted one stays
 * reachable, through the real [NoImpulseContent] composition. The allowed window is
 * computed relative to wall-clock time (same idiom as `HomeViewModelTest`'s
 * restricted-mode tests) so the test is deterministic regardless of when it runs,
 * without touching the already-covered pure `isRestrictedNow` logic.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26], qualifiers = "w360dp-h640dp")
class RestrictedModeIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `restricted mode blocks a non-allowlisted app but not an allowlisted one`() {
        val twitter = AppEntry("Twitter", "com.twitter.android")
        val phone = AppEntry("Phone", "com.android.dialer")
        var launched: String? = null

        val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
        val excludingNow = TimeWindow((nowMinute + 120) % 1440, (nowMinute + 180) % 1440)

        val vm = realHomeViewModel(
            usage = null,
            launcher = FakeLauncherAppsSource(installed = listOf(twitter, phone)),
            settings = FakeSettingsRepository(
                setupComplete = true,
                allowed = setOf(phone.packageName),
                restrictedModeEnabled = true,
                allowedTimeWindows = listOf(excludingNow),
            ),
        )

        composeRule.setContent { NoImpulseContent(vm = vm, onLaunchApp = { launched = it }) }
        composeRule.mainClock.autoAdvance = false
        composeRule.settle()

        composeRule.onNodeWithTag("openDrawer").performClick()
        composeRule.settle()

        composeRule.onNodeWithTag("drawerApp_${twitter.packageName}").performClick()
        composeRule.settle()
        assertTrue(composeRule.onAllNodesWithTag("restrictedTimeOk").fetchSemanticsNodes().isNotEmpty())
        assertNull(launched)
        composeRule.onNodeWithTag("restrictedTimeOk").performClick()
        composeRule.settle()

        composeRule.onNodeWithTag("drawerApp_${phone.packageName}").performClick()
        composeRule.settle()
        assertEquals(phone.packageName, launched)
    }
}
