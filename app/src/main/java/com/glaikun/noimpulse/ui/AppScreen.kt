package com.glaikun.noimpulse.ui

/**
 * Every routable screen in the app, modelled as a sealed hierarchy so the `when`
 * the Activity uses to render is checked for exhaustiveness by the compiler.
 *
 * Navigation is driven by [nextScreen], a pure function over (current state, event).
 * The VM holds the current value in a [kotlinx.coroutines.flow.MutableStateFlow] and
 * dispatches events; the screen-to-Composable mapping happens in `MainActivity`.
 */
sealed interface AppScreen {
    /** Settings have not been read from DataStore yet. */
    data object Loading : AppScreen

    /** The pre-setup intro/explainer is showing. */
    data object Intro : AppScreen

    /** First-run setup (permissions + allowlist) is showing. */
    data object Setup : AppScreen

    /** The launcher home is showing. */
    data object Home : AppScreen

    /** The app drawer is overlaying the home. */
    data object Drawer : AppScreen

    /** Re-friction is being run for [packageName], typically because the user
     *  returned to it via Recents after a screen-off cycle. */
    data class Refriction(val packageName: String) : AppScreen
}

/** All inputs that can drive a transition between [AppScreen]s. */
sealed interface AppEvent {
    /** First time DataStore emits the persisted flags — moves out of [AppScreen.Loading]. */
    data class SettingsLoaded(val introSeen: Boolean, val setupComplete: Boolean) : AppEvent

    /** User dismissed the intro. */
    data object IntroAcknowledged : AppEvent

    /** User tapped Finish in setup. */
    data object SetupFinished : AppEvent

    /** User swiped up / tapped the chevron on Home. */
    data object OpenDrawer : AppEvent

    /** User dismissed the drawer (back press, app launched, etc.). */
    data object CloseDrawer : AppEvent

    /** Accessibility service spotted a foreground change that needs friction. */
    data class RefrictionRequested(val packageName: String) : AppEvent

    /** Re-friction completed (passed or cancelled) — caller decides what to launch. */
    data object RefrictionResolved : AppEvent
}

/**
 * Pure transition function. No coroutines, no Compose, no Hilt — easy to unit-test.
 *
 * Unknown transitions are no-ops (return [current]) rather than throws: the FSM is
 * driven from multiple call sites (UI events, lifecycle callbacks, the accessibility
 * service) and a stray event during an in-flight transition shouldn't crash the app.
 */
fun nextScreen(current: AppScreen, event: AppEvent): AppScreen = when (event) {
    is AppEvent.SettingsLoaded -> when {
        !event.introSeen -> AppScreen.Intro
        !event.setupComplete -> AppScreen.Setup
        else -> AppScreen.Home
    }
    AppEvent.IntroAcknowledged -> AppScreen.Setup
    AppEvent.SetupFinished -> AppScreen.Home
    AppEvent.OpenDrawer -> if (current == AppScreen.Home) AppScreen.Drawer else current
    AppEvent.CloseDrawer -> if (current == AppScreen.Drawer) AppScreen.Home else current
    is AppEvent.RefrictionRequested -> AppScreen.Refriction(event.packageName)
    AppEvent.RefrictionResolved -> AppScreen.Home
}
