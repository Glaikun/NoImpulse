# NoImpulse

A free, open-source, on-device Android app that helps users avoid impulsive phone use. It is also a **custom home-screen launcher**: the user sets NoImpulse as their default launcher and can only reach apps they've explicitly allowlisted.

## Product constraints (non-negotiable)

- **Allowlists only, never blocklists.** Apps and websites the user wants access to must be opted in.
- **All data stays on-device.** No analytics, no remote config, no network calls for user state.
- **Friction over restriction.** Android won't let an app fully prevent the user from changing the default launcher; the app's job is to make impulsive paths inconvenient, not impossible.

## Architecture (per [Google's recommendations](https://developer.android.com/topic/architecture/recommendations))

Two layers, unidirectional data flow:

- **UI** — Jetpack Compose + Material 3. `ViewModel` exposes immutable state as `StateFlow`; UI collects via `collectAsStateWithLifecycle()`.
- **Data** — Repositories are the single source of truth. Room for allowlists/history, Jetpack DataStore for settings (not `SharedPreferences`).
- **Cross-cutting** — Hilt for DI, Kotlin coroutines + `Flow` for async (no `LiveData`, no RxJava, no `AsyncTask`), WorkManager for deferrable work, `AccessibilityService` for real-time blocking.

Single `:app` module today. Tests use hand-written fakes, not mocking frameworks.

## Build environment

- Kotlin 2.0.21, Gradle 9.1.0, AGP 9.0.1, JVM 11
- `minSdk = 26` (required by `UsageStatsManager` and modern `AccessibilityService` APIs — do not lower)
- `compileSdk` / `targetSdk` = 36

## Things to avoid suggesting

- XML layouts — Compose only.
- `SharedPreferences`, `LiveData`, RxJava, `AsyncTask` — superseded by the choices above.
- Mockito or other mocking frameworks for repository tests — fakes instead.
- Network calls or third-party SDKs that touch user data.

## Roadmap

See the `## Plan` section of [README.md](README.md) for the phased feature roadmap and per-phase doc links.
