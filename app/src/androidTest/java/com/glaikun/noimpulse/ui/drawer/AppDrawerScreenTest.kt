package com.glaikun.noimpulse.ui.drawer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glaikun.noimpulse.model.AppEntry
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
    fun tappingAnAppDuringRestrictedTimeShowsTheNoticeInsteadOfLaunching() {
        var restrictedTapped = false
        var launched = false
        val app = AppEntry("Maps", "com.maps")
        rule.setContent {
            AppDrawerScreen(
                installedApps = listOf(app),
                allowedPackages = setOf(app.packageName),   // even an allowlisted app is blocked
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
}
