# NoImpulse
NoImpulse is an app that is free, no data sharing, and open source that helps users control impulses that they may
have created when using their phone.

## App Goals
- Restrict App Usage or Inconvenience Enough to Avoid Impulses
- Control Website - No Porn, Social Media, or Games
- White Lists Rather Than Black Lists

## Design

### Home Screen Design
##### Theme
- Black Background & White Font
- Everything Centered
##### Content
- Clock
- Phone Charge
- Days Usage
- Fav Apps

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

`minSdk = 26` is the floor needed for the modern `UsageStatsManager` and `AccessibilityService` APIs the app uses to watch the foreground app and intercept navigation.

Pinned to the AGP 8.x line for now: the Hilt Gradle plugin's bytecode transform doesn't work on AGP 9.0 yet ([google/dagger#5083](https://github.com/google/dagger/issues/5083)).

---
### UI layer

Built with **Jetpack Compose** (the modern replacement for XML layouts) and **Material 3**.

- A **`ViewModel`** holds the screen's current state and survives screen rotations.
- The state is a plain immutable Kotlin `data class` (think Java `record`) called something like `HomeUiState`.
- The `ViewModel` exposes it as a `StateFlow` — basically an observable value the UI subscribes to. The UI redraws whenever the state changes.
- Button clicks and user input call methods on the `ViewModel`, which updates the state. Data only flows one way: state down to the UI, events up to the `ViewModel`.
---
### Data layer

This layer owns the data and is the single source of truth — the UI never reads files or databases directly.

- **Repositories** are plain Kotlin classes that the rest of the app talks to. A `ViewModel` asks `SettingsRepository` for the allowed apps; it doesn't know or care where they're stored.
- **Jetpack DataStore (Preferences)** — replaces `SharedPreferences`. Today it holds both the `setupComplete` flag and the allowlist itself (a `Set<String>` of package names). Good enough while the allowlist is a flat unordered set.
- **Room** — planned for anything that needs structured rows or history (usage roll-ups, domain rules with timestamps). Not wired yet.
- **System APIs** — `UsageStatsManager` (today's pickups + screen time), `PackageManager` (list installed apps, resolve the device's default Settings/Phone/Messages/Maps/Clock for the first-run seed), `RoleManager` (the default-home prompt). `AccessibilityService` is reserved for Phase 7 (website blocking).
---
### Cross-cutting concerns

- **Async work** — Kotlin **coroutines** (`suspend` functions) instead of threads or `AsyncTask`. Long or deferrable jobs run on **WorkManager** so Android can batch them and survive reboots.
- **Dependency injection** — **Hilt** wires repositories into `ViewModel`s and services. Same idea as Dagger/Guice in Java, just with less boilerplate.
- **Blocking enforcement** — A foreground `AccessibilityService` does the real-time blocking; periodic chores (cleanup, roll-ups) are WorkManager workers.
- **Privacy** — Everything stays on-device. No analytics, no remote config, no network calls for user data.
- **Testing** — Repositories and pure-Kotlin classes get JUnit tests. Compose screens are tested with `androidx.compose.ui.test`. Repository interfaces are swapped out for hand-written fakes in tests rather than mocking frameworks.
---
## Plan

A feature roadmap, ordered so each step builds on the previous one and teaches a chunk of Android along the way. Treat this as a learning path — not a fixed spec.

**Status at a glance:** Phases 1–4 are live. Phase 5 (discouraging launcher swaps) is partially in place — `isDefaultHome` is re-checked on every `ON_RESUME`. Phases 6–7 are not started.

### Start here (before Phase 1)

Worth a skim before you write a line of code:

- [Kotlin for Java developers](https://kotlinlang.org/docs/comparison-to-java.html) — the differences that matter day-to-day (null safety, `val`/`var`, data classes, scope functions).
- [Android Kotlin learning hub](https://developer.android.com/kotlin/learn) — Google's curated Kotlin path for Android.
- [App architecture guide](https://developer.android.com/topic/architecture) — source material for the Architecture section above. Read once; refer back often.
- [**Now in Android**](https://github.com/android/nowinandroid) — Google's full reference app. When you wonder "how should I structure this?", grep this repo first.
- [Architecture samples](https://github.com/android/architecture-samples) — smaller, single-concept samples (good when Now in Android feels too big).

---
### Phase 1 — A plain home screen (no launcher behaviour yet)

**Status: delivered** — [`HomeScreen.kt`](app/src/main/java/com/glaikun/noimpulse/ui/HomeScreen.kt) renders time/date, battery, today's pickup count + screen-on minutes, and a grid of the user's "fav" (allowlisted) apps. Battery is read via a sticky `ACTION_BATTERY_CHANGED` broadcast; usage stats come from `UsageStatsManager` once the user grants access.

Build a regular Compose screen that *looks* like the eventual home screen. No intent-filter changes, no onboarding — just one `Activity`, one screen, one `ViewModel`.

- time/date
- Battery percentage
- How many times users picked up phone & how many hours they've had
- A hard-coded list of app names

**You'll learn:** Compose layout (`Column`, `Row`, `Text`), `ViewModel` + `StateFlow`, `BatteryManager`, and how the project's UI layer fits together. Get comfortable here before adding anything else.

**Docs:**
- [Compose pathway](https://developer.android.com/courses/pathways/compose) — codelabs that take you from zero to a working Compose app.
- [Thinking in Compose](https://developer.android.com/jetpack/compose/mental-model) — the mental shift from XML/Views to Compose.
- [State and Jetpack Compose](https://developer.android.com/jetpack/compose/state) — how `StateFlow` plugs into Compose via `collectAsStateWithLifecycle()`.
- [`ViewModel` overview](https://developer.android.com/topic/libraries/architecture/viewmodel).
- [`BatteryManager` reference](https://developer.android.com/reference/android/os/BatteryManager) for the charge percentage.
- Example screen: [`ForYouScreen.kt`](https://github.com/android/nowinandroid/blob/main/feature/foryou/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/ForYouScreen.kt) in Now in Android — a real-world Compose screen + `ViewModel` + `StateFlow`.

---
### Phase 2 — Allowlisting apps

**Status: delivered** — The picker lives in [`SetupScreen.kt`](app/src/main/java/com/glaikun/noimpulse/ui/SetupScreen.kt) and lists every launchable app (`PackageManager` `MAIN` + `LAUNCHER` query, ourselves filtered out), with a filter `TextField` and a per-row `Switch`. Persistence is via [`DataStoreSettingsRepository`](app/src/main/java/com/glaikun/noimpulse/data/DataStoreSettingsRepository.kt) — a `Set<String>` of allowed package names, not Room. Sort order: device essentials (default Settings/Phone/SMS/Maps/Clock, resolved via `Intent` queries) → most-recently-used → alphabetical. On a truly fresh install (`setupComplete == false` AND allowlist empty), [`HomeViewModel.seedEssentialsIfFresh()`](app/src/main/java/com/glaikun/noimpulse/ui/HomeViewModel.kt) pre-allows those essentials so the home screen isn't empty.

Replace the hard-coded list with one the user picks themselves.

- An "Allowed apps" screen that lists every installed app (via `PackageManager`) with a checkbox.
- Persist the user's selection in Room.
- The home screen reads the allowlist through an `AllowlistRepository`.

**You'll learn:** Room (entities, DAO, database), Hilt for wiring the repository, querying `PackageManager`, and how a `Flow` from the database keeps the UI in sync automatically.

**Docs:**
- [Room codelab (Kotlin)](https://developer.android.com/codelabs/android-room-with-a-view-kotlin) — step-by-step build of a Room-backed app.
- [Save data in a local database with Room](https://developer.android.com/training/data-storage/room) — the official guide.
- [Hilt for Android](https://developer.android.com/training/dependency-injection/hilt-android) — DI setup, modules, and `@HiltViewModel`.
- [Package visibility](https://developer.android.com/training/package-visibility) — required on API 30+ to query installed apps with `PackageManager`. Easy to miss; will silently return an empty list otherwise.
- Example: the `core/database` and `core/data` modules in [Now in Android](https://github.com/android/nowinandroid/tree/main/core) show Room + Hilt + repository in production shape.

---
### Phase 3 — Become a launcher

**Status: delivered** — `MainActivity` declares the `HOME` + `DEFAULT` intent filter and runs in `singleTask`. Tapping an app icon on the home grid calls `PackageManager.getLaunchIntentForPackage`. The `LAUNCHER` intent filter is still on the activity during development so the icon is reachable from a normal app drawer before the user makes us the default home.

Now turn the app into an actual home-screen replacement.

- Add the launcher intent filter (`android.intent.category.HOME` + `DEFAULT`) to `MainActivity` in the manifest.
- Make the home screen launch the chosen apps when tapped (`PackageManager.getLaunchIntentForPackage`).
- Handle the back button so it doesn't exit the launcher.

**You'll learn:** intent filters, the launcher role, Android's app-lifecycle quirks when you *are* the home screen.

**Docs:**
- [Intents and intent filters](https://developer.android.com/guide/components/intents-filters) — the mechanism the `HOME` filter plugs into.
- [`<intent-filter>` manifest element](https://developer.android.com/guide/topics/manifest/intent-filter-element) — the exact attributes you'll add to `AndroidManifest.xml`.
- [`PackageManager.getLaunchIntentForPackage`](https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentForPackage(java.lang.String)) — used to actually launch the apps the user tapped.
- [Predictive back handling in Compose](https://developer.android.com/guide/navigation/custom-back) — useful for keeping the back button from exiting the launcher.
- Heads-up: Google has no official "build a custom launcher" guide. The combination is intent filter + `singleTask` launch mode + handling `onNewIntent`. Search GitHub for `category="android.intent.category.HOME"` to see how others have done it (e.g. [Olauncher](https://github.com/tanujnotes/Olauncher) is a small open-source minimalist launcher worth reading).

---
### Phase 4 — First-run onboarding

**Status: delivered** — When `setupComplete` is `false`, `MainActivity` shows `SetupScreen` instead of `HomeScreen`. The screen has two permission steps (usage access, default-home role) plus the app picker from Phase 2, and a Finish button that flips `setupComplete = true` in DataStore. Default-home uses `RoleManager.createRequestRoleIntent(ROLE_HOME)` on API 29+ and falls back to `Settings.ACTION_HOME_SETTINGS` below that. No Navigation Compose graph yet — there's only the two screens, so a `when (state.setupComplete)` branch in `MainActivity` is enough.

Now that the destination screens exist, build the flow that gets the user there.

- A welcome screen explaining what NoImpulse does.
- An app-picker screen (reuses the allowlist UI from Phase 2).
- A prompt to set NoImpulse as the default launcher (the system dialog, via `RoleManager` on API 29+, or `Intent.ACTION_MAIN` + `CATEGORY_HOME` on older devices).
- A `DataStore` flag so the flow only runs once.

**You'll learn:** Navigation Compose for multi-screen flows, `DataStore` for simple key-value settings, `RoleManager`, and the right way to ask for permissions.

**Docs:**
- [Navigation in Compose](https://developer.android.com/jetpack/compose/navigation) — multi-screen flows with a typed nav graph.
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) — the modern replacement for `SharedPreferences`. Use the Preferences flavour first.
- [`RoleManager` reference](https://developer.android.com/reference/android/app/role/RoleManager) — request the `HOME` role via `createRequestRoleIntent(RoleManager.ROLE_HOME)`.
- [Request runtime permissions](https://developer.android.com/training/permissions/requesting) — the modern launcher-based API. (Compose has `rememberLauncherForActivityResult` for this.)

---
### Phase 5 — Discouraging launcher swaps

**Status: partial** — `MainActivity` re-checks `RoleManager.isRoleHeld(ROLE_HOME)` on every `ON_RESUME` via `LifecycleEventEffect`, so the `SetupScreen`'s "Set as default home" step un-greys itself the moment the user comes back from the system picker. No "set me back" prompt yet, and no device-owner work.

Worth flagging upfront: Android intentionally lets the user change launchers from system settings, so you **cannot fully block this**. You can only add friction.

Options, easiest to hardest:
- Detect when the app is no longer the default launcher (on resume, check `RoleManager.isRoleHeld`) and show a "set me back" prompt.
- Hide the path to the system launcher settings — there's no public API for this, but you can avoid surfacing shortcuts.
- (Advanced) Provision the app as a **device owner** via ADB, which lets you call `DevicePolicyManager.addPersistentPreferredActivity` to lock the launcher role. Powerful but requires a factory-reset device for setup — niche, but worth knowing exists.

**You'll learn:** `RoleManager`, lifecycle callbacks (`DefaultLifecycleObserver`), and the realistic limits of what an Android app is allowed to enforce.

**Docs:**
- [Handle Lifecycle events](https://developer.android.com/topic/libraries/architecture/lifecycle) — how to react to the app coming back to the foreground.
- [`DefaultLifecycleObserver`](https://developer.android.com/reference/androidx/lifecycle/DefaultLifecycleObserver) — implement `onResume` here to recheck the launcher role.
- [`DevicePolicyManager`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager) — the device-policy API surface.
- [Dedicated devices (device-owner) overview](https://developer.android.com/work/dpc/dedicated-devices) — what's possible if you go the device-owner route, and the setup cost.

---
### Phase 6 — App drawer with escalating UUID friction

A second screen, reachable by a **swipe-up gesture from the home surface**, that lists **every** installed app — not just the allowlist. The allowlist gives a frictionless path to the apps the user genuinely wants quick access to; the drawer is the escape hatch. Friction lives between "I tapped an app" and "the app actually opens": the user has to retype a short randomly-generated UUID before the launch happens. The deeper they've already drifted today (more drawer launches, more minutes in non-allowlisted apps), the more UUIDs they have to type.

The whole point is "make impulsive paths inconvenient, not impossible." A single distracted tap shouldn't open Twitter; a deliberate "yes I want this" should still work.

- **Swipe-up trigger.** Compose's `Modifier.pointerInput { detectVerticalDragGestures(...) }` on the home `Surface`, or a `BottomSheetScaffold` whose sheet is the drawer. Caveat noted in the user request: when NoImpulse *is* the system home, Android still owns the gesture-nav swipe-up (it goes to recents/home). The drawer's swipe is therefore an in-app gesture from anywhere on the home surface, not a system gesture — and on a 3-button-nav device you'll need an on-screen affordance too.
- **The drawer itself.** A `LazyVerticalGrid` of every launchable app from `LauncherAppsSource.installedLaunchableApps()` (we already have this method). Same filter `TextField` pattern as `SetupScreen`.
- **UUID challenge.** Tapping an app opens a dialog showing N short tokens (e.g. the first 6 hex chars of `UUID.randomUUID()`). A `TextField` is enabled only while the typed text matches; the launch button only enables on the last token. Full 36-char UUIDs would tip from "friction" to "frustration".
- **Escalation signal.** Count drawer-launches in DataStore, keyed by date (e.g. `drawer_launches_2026_06_21`). Map count → token count via a small step function (1 token for the first launch of the day, +1 every ~3 launches, cap at 5). The exact curve is a product call — start hardcoded, make it configurable later if it matters.
- **Reset / decay.** Simplest first cut: counter resets at local midnight. A time-based decay (e.g. -1 token per hour of no drawer use) is a nice second pass but not needed to ship.

**You'll learn:** gesture detection in Compose (`pointerInput`, `detectVerticalDragGestures`), modal sheets vs. full screens, generating + comparing one-shot tokens, and storing per-day counters in DataStore preferences with date-keyed keys. Bigger picture: this is where the "friction over restriction" philosophy moves from a slogan to a concrete UX you can dial in.

**Open questions to settle before / during implementation:**
- Reset on midnight vs. decay vs. reset-on-allowlist-toggle?
- Does the counter include drawer-launches of allowlisted apps (probably no — penalising the user for using "approved" apps undercuts the point)?
- Should typing fail audibly / haptically, or just silently not enable the button?

**Docs:**
- [Compose pointer input](https://developer.android.com/jetpack/compose/gestures) and [`detectVerticalDragGestures`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/gestures/package-summary#detectVerticalDragGestures(androidx.compose.ui.input.pointer.PointerInputScope,kotlin.Function1,kotlin.Function0,kotlin.Function0,kotlin.Function2)) — the gesture primitives.
- [`BottomSheetScaffold`](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#BottomSheetScaffold(kotlin.Function1,androidx.compose.ui.Modifier,androidx.compose.material3.BottomSheetScaffoldState,androidx.compose.ui.unit.Dp,androidx.compose.ui.graphics.Shape,androidx.compose.ui.graphics.Color,androidx.compose.ui.graphics.Color,androidx.compose.ui.unit.Dp,androidx.compose.ui.graphics.Color,kotlin.Function0,kotlin.Boolean,kotlin.Function1)) — the easiest path to a swipe-up sheet.
- [Android gestural navigation](https://developer.android.com/training/gestures/edge-to-edge#system-gestures) — explains which gestures the system reserves; useful for understanding why we can't intercept the OS-level swipe-up.
- [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore#preferences-datastore) — for the per-day counter.
- [`java.util.UUID`](https://developer.android.com/reference/java/util/UUID) — `UUID.randomUUID()` is fine for friction tokens; we don't need cryptographic randomness here.

---

### Phase 7 — Website blocking

The biggest piece, deliberately last because `AccessibilityService` is fiddly.

- Implement an `AccessibilityService` that watches browser events.
- Check the destination URL against a domain allowlist (a second Room table or a DataStore set).
- If blocked, redirect the browser to a "blocked" page or back to the launcher.

**You'll learn:** `AccessibilityService`, foreground services, declaring services in the manifest, and how Android's privacy prompts shape the install/setup experience.

**Docs:**
- [Build an accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service) — the official end-to-end guide.
- [`AccessibilityService` reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) and [`AccessibilityEvent`](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent) — the event types you'll filter on.
- [Foreground services](https://developer.android.com/develop/background-work/services/fgs) and [foreground service types](https://developer.android.com/about/versions/14/changes/fgs-types-required) — required on API 34+; you must declare a type.
- Example open-source apps doing the same trick (read for inspiration, not copy-paste): [BlockerX](https://github.com/CzechFan/BlockerX-Android) and the older [DNS66](https://github.com/julian-klode/dns66) (which solves the same problem with a local VPN rather than accessibility — a useful alternative to know about).

---
### Beyond

Likely follow-ups once the core works: a settings screen for editing the allowlist after onboarding, an "unlock for N minutes" cooldown when the user wants temporary access, time-of-day rules (e.g. work apps only 9–5), and a usage-history screen built on `UsageStatsManager`.

**Docs to bookmark for these:**
- [`UsageStatsManager`](https://developer.android.com/reference/android/app/usage/UsageStatsManager) — read screen time, last-used time, foreground events. Requires the `PACKAGE_USAGE_STATS` special permission.
- [WorkManager guide](https://developer.android.com/topic/libraries/architecture/workmanager) — for daily roll-ups, scheduled allowlist syncs, etc.
- [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule) — if you need time-of-day rules with second-level precision (WorkManager isn't exact enough).

##