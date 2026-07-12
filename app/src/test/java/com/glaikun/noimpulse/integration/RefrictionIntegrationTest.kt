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
import com.glaikun.noimpulse.ui.exceededDailyLimit
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
 * whether re-friction fires at all, plus the `inSession` guard on
 * [com.glaikun.noimpulse.ui.exceededDailyLimit] that stops a DAILY_LAUNCHES verdict from
 * being re-raised against a session that already paid for its own open.
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

    @Test
    fun `hitting the launches cap does not block the rest of that same session, but the next session is blocked`() {
        // Twitter just used its one permitted open today, and the user is currently
        // still inside it (ledger has it in session) — the exact state a passed gate
        // leaves behind. This is also exactly the moment the bug reproduced on-device:
        // FrictionWatchService.evaluateLimits re-runs on the app's own transition into
        // foreground (and again on any internal navigation after that), and without an
        // inSession guard it found the count already at the cap — the same increment
        // this very open just made — and immediately bounced the user back out.
        val twitter = AppEntry("Twitter", "com.twitter.android")
        val rules = listOf(FrictionRule(FrictionType.DAILY_LAUNCHES, 1))
        var launched: String? = null
        val ledger = FrictionSessionLedger().apply { markPassed(twitter.packageName) }
        val vm = realHomeViewModel(
            usage = null,
            launcher = FakeLauncherAppsSource(installed = listOf(twitter)),
            settings = FakeSettingsRepository(
                setupComplete = true,
                appFriction = mapOf(twitter.packageName to rules),
                appLaunches = mapOf(twitter.packageName to 1),
            ),
            ledger = ledger,
        )

        composeRule.setContent { NoImpulseContent(vm = vm, onLaunchApp = { launched = it }) }
        composeRule.mainClock.autoAdvance = false
        composeRule.settle()

        // FrictionWatchService.evaluateLimits would compute this exact verdict for the
        // app's own foreground transition, or any internal navigation, or a Recents
        // round-trip — all while still the same in-session pass.
        val midSessionVerdict = exceededDailyLimit(
            rules = rules,
            launchesToday = vm.state.value.appLaunchesToday[twitter.packageName] ?: 0,
            minutesToday = 0,
            inSession = ledger.isInSession(twitter.packageName),
        )
        // No verdict, so evaluateLimits never calls startRefriction — nothing reaches the UI.
        assertNull(midSessionVerdict)
        assertEquals(AppScreen.Home, vm.screen.value)

        // Sleeping the phone (ACTION_SCREEN_OFF) clears the ledger — the next pickup is
        // a new session, and the cap applies again.
        ledger.clearAll()
        val nextSessionVerdict = exceededDailyLimit(
            rules = rules,
            launchesToday = vm.state.value.appLaunchesToday[twitter.packageName] ?: 0,
            minutesToday = 0,
            inSession = ledger.isInSession(twitter.packageName),
        )
        assertEquals(FrictionRule(FrictionType.DAILY_LAUNCHES, 1), nextSessionVerdict)

        vm.requestRefriction(twitter.packageName, nextSessionVerdict)
        composeRule.settle()

        assertTrue(composeRule.onAllNodesWithTag("dailyLimitNotice").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag("challengeInput").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("dailyLimitOk").performClick()
        composeRule.settle()
        assertNull(launched)
    }
}
