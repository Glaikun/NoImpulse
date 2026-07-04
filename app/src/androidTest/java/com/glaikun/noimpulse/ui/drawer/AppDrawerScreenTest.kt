package com.glaikun.noimpulse.ui.drawer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDrawerScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun settingsButtonFiresOnOpenSettings() {
        var opened = false
        rule.setContent {
            AppDrawerScreen(
                installedApps = emptyList(),
                allowedPackages = emptySet(),
                drawerLaunchesToday = 0,
                onOpenSettings = { opened = true },
            )
        }

        rule.onNodeWithTag("openSettings").performClick()
        assertTrue(opened)
    }

    @Test
    fun tappingANonAllowedAppDuringRestrictedTimeShowsTheNoticeInsteadOfLaunching() {
        var restrictedTapped = false
        var launched = false
        val app = AppEntry("Twitter", "com.twitter")
        rule.setContent {
            AppDrawerScreen(
                installedApps = listOf(app),
                allowedPackages = emptySet(),               // not allowed → blocked off-hours
                drawerLaunchesToday = 0,
                isRestrictedNow = true,
                onLaunchApp = { launched = true },
                onRestrictedTap = { restrictedTapped = true },
            )
        }

        rule.onNodeWithTag("drawerApp_${app.packageName}").performClick()
        assertTrue(restrictedTapped)
        assertFalse(launched)
    }

    @Test
    fun tappingAnAllowlistedAppDuringRestrictedTimeStillLaunches() {
        var restrictedTapped = false
        var launchedPkg: String? = null
        val app = AppEntry("Phone", "com.phone")
        rule.setContent {
            AppDrawerScreen(
                installedApps = listOf(app),
                allowedPackages = setOf(app.packageName),   // allowlisted → reachable off-hours
                drawerLaunchesToday = 0,
                isRestrictedNow = true,
                onLaunchApp = { launchedPkg = it },
                onRestrictedTap = { restrictedTapped = true },
            )
        }

        rule.onNodeWithTag("drawerApp_${app.packageName}").performClick()
        assertEquals(app.packageName, launchedPkg)
        assertFalse(restrictedTapped)
    }

    @Test
    fun shorteningTheTimedWaitRequiresTheUuidGate() {
        val app = AppEntry("Twitter", "com.twitter")
        rule.setContent {
            AppDrawerScreen(
                installedApps = listOf(app),
                allowedPackages = emptySet(),
                drawerLaunchesToday = 0,
                appFriction = mapOf(
                    app.packageName to listOf(FrictionRule(FrictionType.TIMED_WAIT, 60)),
                ),
            )
        }

        rule.onNodeWithTag("drawerApp_${app.packageName}").performTouchInput { longClick() }
        rule.onNodeWithTag("timedWait_10").performClick()

        // Weakening friction gets the UUID gate, not the light "apply?" prompt.
        rule.onNodeWithTag("uuidInput").assertIsDisplayed()
        rule.onAllNodesWithTag("confirmAddFriction").assertCountEquals(0)
    }

    @Test
    fun lengtheningTheTimedWaitOnlyNeedsTheLightConfirm() {
        val app = AppEntry("Twitter", "com.twitter")
        rule.setContent {
            AppDrawerScreen(
                installedApps = listOf(app),
                allowedPackages = emptySet(),
                drawerLaunchesToday = 0,
                appFriction = mapOf(
                    app.packageName to listOf(FrictionRule(FrictionType.TIMED_WAIT, 10)),
                ),
            )
        }

        rule.onNodeWithTag("drawerApp_${app.packageName}").performTouchInput { longClick() }
        rule.onNodeWithTag("timedWait_60").performClick()

        rule.onNodeWithTag("confirmAddFriction").assertIsDisplayed()
        rule.onAllNodesWithTag("uuidInput").assertCountEquals(0)
    }

    @Test
    fun lockedAppOptionsSheetHidesRemoveAndFriction() {
        val app = AppEntry("Phone", "com.phone")
        rule.setContent {
            AppDrawerScreen(
                installedApps = listOf(app),
                allowedPackages = setOf(app.packageName),
                lockedPackages = setOf(app.packageName),
                drawerLaunchesToday = 0,
            )
        }

        rule.onNodeWithTag("drawerApp_${app.packageName}").performTouchInput { longClick() }

        // The locked note shows; the remove-from-allowlist control does not.
        rule.onNodeWithTag("lockedAppNote").assertIsDisplayed()
        rule.onAllNodesWithTag("removeFromAllowlist").assertCountEquals(0)
    }
}
