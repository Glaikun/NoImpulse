package com.glaikun.noimpulse.integration

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import com.glaikun.noimpulse.model.TimeWindow
import com.glaikun.noimpulse.data.FrictionSessionLedger
import com.glaikun.noimpulse.services.RefrictionDecision
import com.glaikun.noimpulse.services.refrictionDecision
import com.glaikun.noimpulse.testing.FakeLauncherAppsSource
import com.glaikun.noimpulse.testing.FakeSettingsRepository
import com.glaikun.noimpulse.testing.allTextsMatching
import com.glaikun.noimpulse.testing.realHomeViewModel
import com.glaikun.noimpulse.testing.settle
import com.glaikun.noimpulse.ui.AppScreen
import com.glaikun.noimpulse.ui.NoImpulseContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime

private val HEX_TOKEN_REGEX = Regex("^[0-9A-F]{6}$")
private const val OWN_PACKAGE = "com.glaikun.noimpulse"

/**
 * Simulates `FrictionWatchService` re-triggering the gate for a package already in the
 * foreground — the same entry point `MainActivity` uses via its re-friction intent
 * extras — by calling [com.glaikun.noimpulse.ui.HomeViewModel.requestRefriction]
 * directly, and confirms [NoImpulseContent] renders the right overlay for each of its
 * three outcomes, plus the session lifecycle across a screen-off/on cycle that decides
 * whether re-friction fires at all.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26], qualifiers = "w360dp-h640dp")
class RefrictionIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `plain re-friction shows the gate and completing it launches then returns home`() {
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

        vm.requestRefriction(twitter.packageName)
        composeRule.settle()

        val token = composeRule.allTextsMatching(HEX_TOKEN_REGEX).single()
        composeRule.onNodeWithTag("challengeInput").performTextInput(token)
        composeRule.settle()
        composeRule.onNodeWithTag("challengeLaunch").performClick()
        composeRule.settle()

        assertEquals(twitter.packageName, launched)
        assertEquals(AppScreen.Home, vm.screen.value)
        // The pass counts toward the daily-launches cap — a Recents round-trip must
        // not be a free open.
        assertEquals(mapOf(twitter.packageName to 1), vm.state.value.appLaunchesToday)
    }

    @Test
    fun `re-friction with an over-limit verdict shows the block notice, not the gate`() {
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

        vm.requestRefriction(twitter.packageName, FrictionRule(FrictionType.DAILY_LAUNCHES, 1))
        composeRule.settle()

        assertTrue(composeRule.onAllNodesWithTag("dailyLimitNotice").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("dailyLimitOk").performClick()
        composeRule.settle()

        assertNull(launched)
        assertEquals(AppScreen.Home, vm.screen.value)
    }

    @Test
    fun `re-friction during restricted time shows the restricted notice, not the gate`() {
        val twitter = AppEntry("Twitter", "com.twitter.android")
        var launched: String? = null
        val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
        val excludingNow = TimeWindow((nowMinute + 120) % 1440, (nowMinute + 180) % 1440)
        val vm = realHomeViewModel(
            usage = null,
            launcher = FakeLauncherAppsSource(installed = listOf(twitter)),
            settings = FakeSettingsRepository(
                setupComplete = true,
                restrictedModeEnabled = true,
                allowedTimeWindows = listOf(excludingNow),
            ),
        )

        composeRule.setContent { NoImpulseContent(vm = vm, onLaunchApp = { launched = it }) }
        composeRule.mainClock.autoAdvance = false
        composeRule.settle()

        vm.requestRefriction(twitter.packageName)
        composeRule.settle()

        assertTrue(composeRule.onAllNodesWithTag("restrictedTimeOk").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("restrictedTimeOk").performClick()
        composeRule.settle()

        assertNull(launched)
        assertEquals(AppScreen.Home, vm.screen.value)
    }

    @Test
    fun `sleeping the phone clears the session so reopening a passed app re-triggers friction`() {
        val twitter = AppEntry("Twitter", "com.twitter.android")
        var launched: String? = null
        val ledger = FrictionSessionLedger()
        val vm = realHomeViewModel(
            usage = null,
            launcher = FakeLauncherAppsSource(installed = listOf(twitter)),
            settings = FakeSettingsRepository(setupComplete = true),
            ledger = ledger,
        )

        composeRule.setContent { NoImpulseContent(vm = vm, onLaunchApp = { launched = it }) }
        composeRule.mainClock.autoAdvance = false
        composeRule.settle()

        // First open: drawer -> friction gate -> pass it, exactly like a real first launch.
        composeRule.onNodeWithTag("openDrawer").performClick()
        composeRule.settle()
        composeRule.onNodeWithTag("drawerApp_${twitter.packageName}").performClick()
        composeRule.settle()
        val firstToken = composeRule.allTextsMatching(HEX_TOKEN_REGEX).single()
        composeRule.onNodeWithTag("challengeInput").performTextInput(firstToken)
        composeRule.settle()
        composeRule.onNodeWithTag("challengeLaunch").performClick()
        composeRule.settle()
        assertEquals(twitter.packageName, launched)
        assertTrue(ledger.isInSession(twitter.packageName))

        // While the app is in session, FrictionWatchService would skip it if the user
        // just switched back to it (e.g. via Recents) — no phone lock happened yet.
        assertEquals(
            RefrictionDecision.SKIP,
            refrictionDecision(
                packageName = twitter.packageName,
                ownPackageName = OWN_PACKAGE,
                allowedPackages = emptySet(),
                launchablePackages = setOf(twitter.packageName),
                hasLimitRules = { false },
                isInSession = ledger::isInSession,
                restrictedNow = false,
            ),
        )

        // Sleeping the phone fires FrictionWatchService's ACTION_SCREEN_OFF receiver,
        // which clears the ledger.
        ledger.clearAll()

        // Reopening the app now recomputes to TRIGGER — the same decision
        // FrictionWatchService.onAccessibilityEvent would reach.
        val decision = refrictionDecision(
            packageName = twitter.packageName,
            ownPackageName = OWN_PACKAGE,
            allowedPackages = emptySet(),
            launchablePackages = setOf(twitter.packageName),
            hasLimitRules = { false },
            isInSession = ledger::isInSession,
            restrictedNow = false,
        )
        assertEquals(RefrictionDecision.TRIGGER, decision)

        // What MainActivity does on receiving FrictionWatchService's re-friction intent.
        launched = null
        vm.requestRefriction(twitter.packageName)
        composeRule.settle()

        val secondToken = composeRule.allTextsMatching(HEX_TOKEN_REGEX).single()
        composeRule.onNodeWithTag("challengeInput").performTextInput(secondToken)
        composeRule.settle()
        composeRule.onNodeWithTag("challengeLaunch").performClick()
        composeRule.settle()

        assertEquals(twitter.packageName, launched)
        assertEquals(AppScreen.Home, vm.screen.value)
    }
}
