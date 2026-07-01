# NoImpulse

NoImpulse is a free, open-source Android app that helps you break impulsive phone habits. It is also a **custom home-screen launcher**: you set it as your default launcher, and the only frictionless path to an app is one you've explicitly allowlisted. Everything else sits behind deliberate friction. All data stays on your device — the app has no network access at all.

## Table of contents

- [App goals](#app-goals)
- [Privacy](#privacy)
- [Design](#design)
- [Architecture](#architecture)
  - [Build environment](#build-environment)
  - [UI layer](#ui-layer)
  - [Data layer](#data-layer)
  - [Cross-cutting concerns](#cross-cutting-concerns)
- [Project structure](#project-structure)
- [Ideas](#ideas)
- [Release process](#release-process)
- [License](#license)

## App goals

- Restrict app usage, or add enough inconvenience, to break impulsive habits.
- Control access to distracting websites (social media, games, and the like).
- Allowlists, never blocklists — you opt in to what you want, rather than chasing an endless list of things to ban.

## Privacy

NoImpulse keeps everything on your device. Concretely:

- **No `INTERNET` permission.** The `<uses-permission>` list in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) does not declare it, so the OS would refuse any network attempt. This is independently verifiable by anyone inspecting the APK.
- **No analytics, no remote config, no cloud sync.** The allowlist, friction rules, and counters live in on-device DataStore.
- **The accessibility service is scoped to the package name of whatever app comes to the foreground.** It uses `canRetrieveWindowContent="false"` (see [`res/xml/accessibility_service_config.xml`](app/src/main/res/xml/accessibility_service_config.xml)), which means the OS itself withholds window content — we couldn't read messages, passwords, or anything you type even if we wanted to. The service does one thing: re-show the friction screen when you return to an app you've added friction to.
- **Source is the documentation.** Anything claimed here can be verified in this repo.

## Design

The whole interface is intentionally dull — visual calm is part of the point.

- **Home screen.** Black background, white text, everything centred. It shows the clock, battery charge, today's pickup count and screen-on time, and a grid of your allowlisted ("fav") apps. Nothing else competes for attention.
- **Greyscale icons.** App icons are rendered desaturated, so no app gets to pull your eye with bright branding.
- **App drawer.** A swipe-up drawer lists *every* installed app — the escape hatch for apps you didn't allowlist. Opening one from here means passing friction first.
- **Friction.** Launching a non-allowlisted app runs a sequence of small obstacles: a baseline "type these tokens" challenge that escalates with how much you've already drifted today, plus any extra friction you've chosen to stack on an app (a timed wait, a math problem, or reflection questions).
- **Re-friction.** Returning to a friction-tracked app via Recents re-shows the gate, so the drawer isn't a one-time toll.
- **Onboarding.** A short intro explains the launcher model, the deliberate friction, and the privacy posture, followed by a setup screen for permissions and the initial allowlist.
- **Always-allowed core.** Phone, settings, messages, camera, and maps (the device defaults) stay allowlisted at all times — they can't be removed from the allowlist, can't have friction added, and are never blocked by Restricted Mode.
- **Settings.** Reached from the gear in the app drawer. Re-grant permissions, re-prompt to make NoImpulse the default launcher (behind the same UUID gate as allowlisting), configure Restricted Mode, and choose the colour theme and text size — all after onboarding.
- **Restricted Mode.** An optional schedule of "allowed times" of day. While it's on and the clock is outside every allowed window, the phone is in *restricted time*: apps you haven't allowlisted won't open and returning to one bounces you back to the launcher with a "Currently in restricted time" notice. Your allowlisted apps (and the always-allowed core) keep working. Inside an allowed window everything behaves normally. Turning the mode off while a schedule exists is gated behind the UUID challenge, so loosening it takes the same deliberate effort as removing friction.
- **Appearance & accessibility.** A light/dark/follow-system theme choice, and a text-size setting (Default / Large / Largest) that scales every text style for easier reading.

## Architecture

NoImpulse follows the [official Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations). The short version: split the app into a **UI layer** and a **data layer**, keep data flowing in one direction, and never read the same thing from two places.

### Build environment

| | Version |
| --- | --- |
| Kotlin | 2.0.21 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.7.3 |
| Hilt | 2.52 (+ KSP `2.0.21-1.0.28`) |
| `minSdk` | 26 (Android 8.0) |
| `compileSdk` / `targetSdk` | 36 |
| JVM target | 11 |

Build a debug APK with `./gradlew assembleDebug`, or open the project in a recent Android Studio.

`minSdk = 26` is the floor needed for the modern `UsageStatsManager` and `AccessibilityService` APIs the app uses to watch the foreground app and intercept navigation.

Pinned to the AGP 8.x line for now: the Hilt Gradle plugin's bytecode transform doesn't work on AGP 9.0 yet ([google/dagger#5083](https://github.com/google/dagger/issues/5083)).

### UI layer

Built with **Jetpack Compose** (the modern replacement for XML layouts) and **Material 3**.

- A single **`ViewModel`** holds the screen's current state and survives screen rotations.
- The state is a plain immutable Kotlin `data class` exposed as a `StateFlow` — an observable value the UI subscribes to. The UI redraws whenever the state changes.
- Button clicks and user input call methods on the `ViewModel`, which updates the state. Data only flows one way: state down to the UI, events up to the `ViewModel`.
- Composables are grouped by role: full-screen destinations in [`ui/screens/`](app/src/main/java/com/glaikun/noimpulse/ui/screens), the app drawer in [`ui/drawer/`](app/src/main/java/com/glaikun/noimpulse/ui/drawer), and the shared `ViewModel`, navigation, icon, and friction Composables in the `ui` root.

### Data layer

This layer owns the data and is the single source of truth — the UI never reads files or databases directly.

- **Repositories and sources** are plain Kotlin classes the rest of the app talks to. A `ViewModel` asks `SettingsRepository` for the allowed apps; it doesn't know or care where they're stored. Each interface lives beside its implementation in [`data/`](app/src/main/java/com/glaikun/noimpulse/data), and the plain data types they exchange live in [`model/`](app/src/main/java/com/glaikun/noimpulse/model).
- **Jetpack DataStore (Preferences)** — replaces `SharedPreferences`. Holds the `introSeen`/`setupComplete` flags, the allowlist (a `Set<String>` of package names), per-app friction rules, the date-keyed drawer-launch counter, the Restricted Mode toggle plus its allowed time-of-day windows, and the theme/text-size preferences.
- **Room** — planned for anything that needs structured rows or history (usage roll-ups, domain rules with timestamps). Not wired yet.
- **System APIs** — `UsageStatsManager` (today's pickups + screen time), `PackageManager` (list installed apps, resolve the device's default Settings/Phone/Messages/Maps/Clock/Camera/Gallery for the first-run seed), `RoleManager` (the default-home prompt), and `AccessibilityService` (re-friction on foreground return).

### Cross-cutting concerns

- **Async work** — Kotlin **coroutines** (`suspend` functions) and `Flow`, never threads or `AsyncTask`. Long or deferrable jobs will run on **WorkManager** so Android can batch them and survive reboots (planned).
- **Dependency injection** — **Hilt** wires repositories into `ViewModel`s and services. Same idea as Dagger/Guice in Java, with less boilerplate.
- **Friction enforcement** — a foreground `AccessibilityService` ([`FrictionWatchService`](app/src/main/java/com/glaikun/noimpulse/services/FrictionWatchService.kt)) re-triggers friction when a tracked app returns to the foreground, backed by an in-memory session ledger that clears on screen-off.
- **Privacy** — everything stays on-device. See the [Privacy](#privacy) section above for the verifiable specifics.
- **Testing** — repositories and pure-Kotlin logic get JUnit tests; Compose screens use `androidx.compose.ui.test`. Interfaces are swapped for hand-written fakes rather than a mocking framework.

## Project structure

Annotated tree of the production source:

```
app/src/main/java/com/glaikun/noimpulse/
├── MainActivity.kt              — single-activity launcher; routes via the AppScreen state machine
├── NoImpulseApp.kt              — @HiltAndroidApp application class
├── model/                       — plain data types shared across layers
│   ├── AppEntry.kt              — (packageName, label) for an installed app
│   ├── DailyUsage.kt            — pickup count + screen-on minutes for today
│   ├── StatusSnapshot.kt        — system-status read (usage access, default-home, accessibility, usage)
│   ├── SettingsSnapshot.kt      — persisted settings snapshot (intro flag, allowlist, friction, restricted mode)
│   ├── Friction.kt              — FrictionType enum + FrictionRule data class
│   ├── TimeWindow.kt            — a time-of-day window (minutes since midnight) for Restricted Mode
│   ├── ThemeMode.kt             — colour-scheme choice (system / light / dark)
│   └── TextSize.kt              — accessibility text-size choice + its scale factor
├── data/                        — repositories, system data sources, and the interfaces they implement
│   ├── SettingsRepository.kt            — settings contract (allowlist, friction, counters)
│   ├── DataStoreSettingsRepository.kt   — DataStore-backed implementation
│   ├── LauncherAppsSource.kt            — installed-apps contract
│   ├── SystemLauncherAppsSource.kt      — PackageManager-backed implementation
│   ├── UsageStatsSource.kt              — usage-stats contract
│   ├── SystemUsageStatsSource.kt        — UsageStatsManager-backed implementation
│   ├── AccessibilityStatusSource.kt     — "is our service enabled" contract
│   ├── SystemAccessibilityStatusSource.kt — reads Settings.Secure for the answer
│   └── FrictionSessionLedger.kt         — in-memory ledger of passed-friction apps (cleared on screen-off)
├── di/                          — Hilt wiring
│   ├── AppModule.kt             — binds interfaces to implementations
│   └── IoDispatcher.kt          — @Qualifier for the IO CoroutineDispatcher
├── services/
│   └── FrictionWatchService.kt  — AccessibilityService; re-friction trigger + screen-off ledger reset
└── ui/
    ├── AppScreen.kt             — sealed AppScreen + AppEvent + pure nextScreen() transition
    ├── HomeViewModel.kt         — single ViewModel; exposes navigation + UI state as StateFlow
    ├── AppIcon.kt               — renders a greyscale app icon from a PackageManager Drawable
    ├── FrictionGate.kt          — renders the friction sequence for an app (shared by drawer + re-friction)
    ├── FrictionDialogs.kt       — friction-dialog Composables (timed wait, math, reflection, UUID gate, restricted-time notice)
    ├── PermissionTiles.kt       — permission-request tiles shared by the Setup and Settings screens
    ├── screens/
    │   ├── HomeScreen.kt        — clock/date/battery/stats + allowlisted-app grid
    │   ├── IntroScreen.kt       — pre-setup explainer
    │   ├── SetupScreen.kt       — first-run permissions + allowlist seed
    │   └── SettingsScreen.kt    — post-onboarding settings (permissions, switch launcher, Restricted Mode, theme + text size)
    ├── drawer/
    │   └── AppDrawerScreen.kt   — swipe-up drawer; every installed app + per-app friction options
    └── theme/                   — Material 3 colour, typography, theme
```

## Ideas

The core app is built and usable: the minimalist launcher home screen, allowlist onboarding, an app drawer gated by escalating friction, re-friction when a tracked app returns to the foreground, a post-onboarding settings screen, and Restricted Mode (allowed time-of-day windows enforced across the launcher).

What follows is a loose backlog, not a commitment. Anything picked up should follow the [official Android architecture guidance](https://developer.android.com/topic/architecture) and the conventions already in this repo — the bar is readable, idiomatic, well-tested code over clever code.

- **Website blocking.** Extend `FrictionWatchService` to check browser URLs against a domain allowlist. This requires flipping `canRetrieveWindowContent` to `true` — a deliberate trust change that needs its own consent copy.
- **Per-app time-of-day rules.** Restricted Mode currently applies one global schedule to the whole launcher; a natural extension is per-app windows (e.g. work apps reachable only 9–5).
- **Temporary unlock.** An "open for N minutes" cooldown for genuinely deliberate access.
- **Usage history.** A trends screen built on `UsageStatsManager`.
- **Tighter re-friction.** An idle-timer fallback for the case where the screen never turns off (the session ledger only clears on screen-off today), plus an optional decay curve that softens the per-day drawer-friction escalation over idle time.

## Release process

Releases are cut by the **Release** GitHub Actions workflow (`.github/workflows/release.yml`), run
manually from the Actions tab. Development happens on `develop`; `master` only ever moves through
this workflow — direct pushes to `master` are blocked for everyone, and the workflow is the sole
actor allowed past the branch rulesets (it authenticates as the `release-bot` deploy key).

Cutting a release:

1. Confirm the current `versionName` on `develop` (in [app/build.gradle.kts](app/build.gradle.kts)) — the workflow bumps *from* it.
2. Dispatch **Release** and choose how to bump for this release (`major`/`minor`/`patch`).

The workflow runs the unit tests, bumps `develop`'s `versionName` and `versionCode` and commits
`Release X.Y.Z`, fast-forward-merges that commit into `master`, then tags and publishes the release
(with the APK attached to a GitHub Release). `master` and `develop` end at the same commit.

Prerequisites (one-time): the `RELEASE_SSH_KEY` secret (private half of the write-enabled
`release-bot` deploy key). For an installable, signed APK, also set the `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` secrets; without them the release APK is
published unsigned.

## License

NoImpulse is free software, released under the [GNU General Public License v3](LICENSE).
