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

New behaviour ships **with** tests: pure logic gets a JVM unit test (`app/src/test/…`), repositories are exercised through their interface with hand-written fakes, and Compose screens get an instrumentation test (`app/src/androidTest/…`) where it adds value. Never mark work complete on a red or skipped suite — fix the root cause, or state plainly what is failing and why.

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
