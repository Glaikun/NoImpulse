# NoImpulse

A free, open-source, on-device Android app that helps users avoid impulsive phone use. It is also a **custom home-screen launcher**: the user sets NoImpulse as their default launcher and can only reach apps they've explicitly allowlisted; everything else sits behind deliberate friction. The app is public and GPL-3.0 licensed.

## How to work in this codebase

- **Readability is the top priority.** Code is read far more than it is written, and this is a learning project as much as a product. Prefer the clear, obvious solution over the clever one. Match the naming, comment density, and idioms of the surrounding code. A comment should explain *why*, not restate *what*. If a choice needs justification, leave a short note rather than assuming the next reader will reconstruct it.
- **Keep the README's file structure current.** Whenever you add a new file, move/rename one, or otherwise change the directory layout, update the annotated tree in the `## Project structure` section of [README.md](README.md) in the same change so it never drifts from reality.

## Acceptance criteria (must pass before a change is done)

A change is **not complete** until it builds and its tests pass. Before marking any task done — and always before opening a PR — run these and get a green result:

- `./gradlew :app:testDebugUnitTest` — JVM unit tests (Robolectric + hand-written fakes).
- `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` — main **and** instrumentation sources compile.
- `./gradlew :app:assembleDebug` — the debug APK assembles.

Never mark work complete on a red or skipped suite — fix the root cause, or state plainly what is failing and why.

### Test tiers

New behaviour ships **with** a test, at the cheapest tier that can actually see the behavior — this is a testing pyramid, so tier 1 should have far more tests than tier 2, tier 2 more than tier 3, and tier 4 stays to a handful of smoke-level scenarios:

1. **Unit tests** — `app/src/test/…` (flat, e.g. `HomeViewModelTest.kt`). Robolectric + hand-written fakes (`testing/Fakes.kt`), no UI rendering. Pure logic and single components (a ViewModel, a repository) tested directly. This is where most new logic should land.
2. **Screen instrumentation tests** — `app/src/androidTest/…` under `ui/`/`drawer/`/`services/` (e.g. `AppDrawerScreenTest.kt`). Real device/emulator, real Compose rendering, but each screen tested in isolation with hand-fed state and lambdas — not the real `HomeViewModel`, not real screen-to-screen navigation.
3. **Integration tests** — `app/src/test/java/com/glaikun/noimpulse/integration/`. Full-scenario tests that render the real `NoImpulseContent` composition root against a real `HomeViewModel` (`testing/Fakes.kt`'s `realHomeViewModel()`), driven under Robolectric via `androidx.compose.ui.test`. Real UI + real cross-screen navigation together, but every system-facing source (`LauncherAppsSource`, `UsageStatsSource`, …) is still a hand-written fake — no installed APK, no device, no real `PackageManager`/`AccessibilityService`. Runs as part of `testDebugUnitTest`, unlike the two device-based tiers. Robolectric-hosted Compose has sharp edges undocumented anywhere upstream — a missing `@Config(qualifiers = ...)` silently zeroes `LazyColumn` viewports, and any dialog/bottom-sheet or focused text field makes `waitForIdle()` spin until Espresso's `AppNotIdleException`. Use `testing/ComposeSemantics.kt`'s `settle()` (its doc explains why) instead of `waitForIdle()` in any integration test that opens a dialog.
4. **Real e2e** — flat under `app/src/androidTest/java/com/glaikun/noimpulse/` (e.g. `OnboardingAndFrictionGateE2eTest.kt`). Launches the actual `MainActivity` with its actual Hilt graph: `HiltTestRunner` swaps in `HiltTestApplication`, and `TestAppModule` `@TestInstallIn`-replaces production `AppModule` with the same style of fakes as tier 3, via its own small `testing/Fakes.kt` (`test` and `androidTest` are separate compilation units, so this is deliberate duplication, not drift). Needs a connected device or emulator (`./gradlew :app:connectedDebugAndroidTest`) and isn't part of the standard build — this tier exists to catch what only a real installed app can prove (broken Hilt wiring, an Activity that doesn't actually come up), not to re-prove tier-3 coverage just because it's now possible.

## Product constraints (non-negotiable)

- **Allowlists only, never blocklists.** Apps and websites the user wants access to must be opted in.
- **All data stays on-device.** No analytics, no remote config, no network calls for user state. The app declares no `INTERNET` permission — keep it that way.
- **Friction over restriction.** Android won't let an app fully prevent the user from changing the default launcher; the app's job is to make impulsive paths inconvenient, not impossible.

## Architecture (per [Google's recommendations](https://developer.android.com/topic/architecture/recommendations))

Two layers, unidirectional data flow:

- **UI** — Jetpack Compose + Material 3. A single `ViewModel` exposes immutable state as `StateFlow`; UI collects via `collectAsStateWithLifecycle()`. Composables are grouped by role: screen destinations in `ui/screens/`, the app drawer in `ui/drawer/`, and the shared `ViewModel`/navigation/friction Composables in the `ui` root.
- **Data** — Repositories and sources are the single source of truth. Each interface lives beside its implementation in `data/`; the plain data types they exchange live in `model/`. Room for allowlists/history (planned), Jetpack DataStore for settings (not `SharedPreferences`).
- **Cross-cutting** — Hilt for DI, Kotlin coroutines + `Flow` for async (no `LiveData`, no RxJava, no `AsyncTask`), WorkManager for deferrable work (planned), `AccessibilityService` (`FrictionWatchService`) for re-friction when a tracked app returns to the foreground.

Single `:app` module today. Tests use hand-written fakes, not mocking frameworks.

## Build environment

- Kotlin 2.0.21, Gradle 8.11.1, AGP 8.7.3, JVM 11
- `minSdk = 26` (required by `UsageStatsManager` and modern `AccessibilityService` APIs — do not lower)
- `compileSdk` / `targetSdk` = 36
- DI: Hilt 2.52 (+ KSP `2.0.21-1.0.28`). **Stay on the AGP 8.x line:** the Hilt Gradle plugin's bytecode transform does not work on AGP 9.0 yet ([google/dagger#5083](https://github.com/google/dagger/issues/5083)). Revisit AGP 9 once Hilt ships AGP-9 support.
- Android Lint and detekt run in CI (`.github/workflows/code-quality.yml`) as report-only checks — they surface findings but aren't required status checks, so they can't block a merge. Known flake: running `./gradlew lintDebug` a second time right after `./gradlew clean` can fail (AGP/KSP incremental-cache quirk); it works fine as part of a normal, non-clean build. This isn't a real regression and isn't worth chasing — CI is unaffected, since every run starts from a fresh checkout. If you hit this locally, just re-run lint without a preceding clean.

## Things to avoid suggesting

- XML layouts — Compose only.
- `SharedPreferences`, `LiveData`, RxJava, `AsyncTask` — superseded by the choices above.
- Mockito or other mocking frameworks for repository tests — hand-written fakes instead.
- Network calls, analytics, or third-party SDKs that touch user data — the app has no `INTERNET` permission.
- Blocklists — allowlists only (see Product constraints).
- Packages organised by language construct (e.g. an `interfaces/` package). Group by layer or feature, and keep each interface next to its implementation in `data/`.
- Clever code at the expense of clarity — readability wins.

## Ideas / roadmap

See the `## Ideas` section of [README.md](README.md) for the current status and the backlog of future work.
