package com.glaikun.noimpulse.ui

import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-function tests for the routing FSM. No Compose, no coroutines, no Hilt — just
 * `nextScreen(state, event)`.
 */
class AppScreenTest {

    // ── SettingsLoaded routes out of Loading into the right starting screen ──

    @Test
    fun `loaded fresh install lands on Intro`() {
        val result = nextScreen(
            AppScreen.Loading,
            AppEvent.SettingsLoaded(introSeen = false, setupComplete = false),
        )
        assertEquals(AppScreen.Intro, result)
    }

    @Test
    fun `loaded after intro but before setup lands on Setup`() {
        val result = nextScreen(
            AppScreen.Loading,
            AppEvent.SettingsLoaded(introSeen = true, setupComplete = false),
        )
        assertEquals(AppScreen.Setup, result)
    }

    @Test
    fun `loaded with everything done lands on Home`() {
        val result = nextScreen(
            AppScreen.Loading,
            AppEvent.SettingsLoaded(introSeen = true, setupComplete = true),
        )
        assertEquals(AppScreen.Home, result)
    }

    // ── User-driven setup progression ────────────────────────────────────────

    @Test
    fun `IntroAcknowledged moves Intro to Setup`() {
        assertEquals(AppScreen.Setup, nextScreen(AppScreen.Intro, AppEvent.IntroAcknowledged))
    }

    @Test
    fun `SetupFinished moves Setup to Home`() {
        assertEquals(AppScreen.Home, nextScreen(AppScreen.Setup, AppEvent.SetupFinished))
    }

    // ── Drawer transitions are idempotent at the edges ───────────────────────

    @Test
    fun `OpenDrawer from Home reaches Drawer`() {
        assertEquals(AppScreen.Drawer, nextScreen(AppScreen.Home, AppEvent.OpenDrawer))
    }

    @Test
    fun `OpenDrawer from any non-Home state is a no-op`() {
        assertEquals(AppScreen.Setup, nextScreen(AppScreen.Setup, AppEvent.OpenDrawer))
        assertEquals(AppScreen.Drawer, nextScreen(AppScreen.Drawer, AppEvent.OpenDrawer))
    }

    @Test
    fun `CloseDrawer from Drawer returns to Home`() {
        assertEquals(AppScreen.Home, nextScreen(AppScreen.Drawer, AppEvent.CloseDrawer))
    }

    @Test
    fun `CloseDrawer from non-Drawer is a no-op`() {
        assertEquals(AppScreen.Home, nextScreen(AppScreen.Home, AppEvent.CloseDrawer))
        assertEquals(AppScreen.Setup, nextScreen(AppScreen.Setup, AppEvent.CloseDrawer))
    }

    // ── Settings opens from the drawer and closes back to Home ───────────────

    @Test
    fun `OpenSettings reaches Settings`() {
        assertEquals(AppScreen.Settings, nextScreen(AppScreen.Drawer, AppEvent.OpenSettings))
    }

    @Test
    fun `CloseSettings returns to Home`() {
        assertEquals(AppScreen.Home, nextScreen(AppScreen.Settings, AppEvent.CloseSettings))
    }

    // ── Re-friction can be entered from anywhere (service triggers it) ───────

    @Test
    fun `RefrictionRequested from Home moves to Refriction with the package`() {
        val result = nextScreen(
            AppScreen.Home,
            AppEvent.RefrictionRequested("com.twitter.android"),
        )
        assertEquals(AppScreen.Refriction("com.twitter.android"), result)
    }

    @Test
    fun `RefrictionRequested from Drawer also moves to Refriction`() {
        val result = nextScreen(
            AppScreen.Drawer,
            AppEvent.RefrictionRequested("com.twitter.android"),
        )
        assertEquals(AppScreen.Refriction("com.twitter.android"), result)
    }

    @Test
    fun `RefrictionRequested without a verdict carries a null overLimit`() {
        val result = nextScreen(
            AppScreen.Home,
            AppEvent.RefrictionRequested("com.twitter.android"),
        )
        assertNull((result as AppScreen.Refriction).overLimit)
    }

    @Test
    fun `RefrictionRequested carries the exceeded daily limit into Refriction`() {
        val limit = FrictionRule(FrictionType.DAILY_MINUTES, 30)
        val result = nextScreen(
            AppScreen.Home,
            AppEvent.RefrictionRequested("com.twitter.android", overLimit = limit),
        )
        assertEquals(AppScreen.Refriction("com.twitter.android", overLimit = limit), result)
    }

    @Test
    fun `RefrictionResolved from Refriction returns to Home`() {
        val result = nextScreen(
            AppScreen.Refriction("com.twitter.android"),
            AppEvent.RefrictionResolved,
        )
        assertEquals(AppScreen.Home, result)
    }
}
