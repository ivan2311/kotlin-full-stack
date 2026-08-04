# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A full-stack Kotlin tournament-prediction game (pick scores, earn points, climb the leaderboard). Its defining trait: **backend, frontend, and the business logic they share are all Kotlin in one Gradle build**, with the domain model, scoring rules, and REST contract written exactly once in `:shared` and compiled to the JVM (server), Android, iOS, and WebAssembly (the clients).

## Modules

| Module    | Targets                        | Role                                                                     |
|-----------|--------------------------------|--------------------------------------------------------------------------|
| `shared`  | JVM · Android · iOS · Wasm     | Domain model, scoring engine, leaderboard calculator, REST contract      |
| `server`  | JVM                            | Ktor (Netty) backend — REST API + in-memory store; also serves the built web app |
| `web`     | Android · iOS · Wasm           | Compose Multiplatform client. **UI lives in `commonMain`** and is shared by all three targets; each target adds only a launcher + Ktor engine + base URL. `iosApp/` is the Swift shell that hosts the iOS framework. |

Package root is `com.predictor.*` across all modules. Requires JDK 17+ (built with JDK 21). The Gradle wrapper (`./gradlew`) provides the Kotlin, Compose, Wasm, and Node toolchains — do not install these separately.

**Android/iOS build requirements.** The Android target is an explicit **opt-in**: pass `-Ppredictor.android=true` (or set `predictor.android=true` in `gradle.properties`) on a machine that has the Android SDK. See `androidEnabled` in the root build — when opted in it puts the Android Gradle plugin on the buildscript classpath and the modules apply it (`gradle/android-*.gradle.kts` hold the AGP-typed config). It is opt-in rather than SDK-auto-detected because CI runners often have an SDK on `PATH` yet only run the JVM/iOS/Wasm tasks; auto-detecting there would apply AGP for nothing and break those builds. With Android off (the default, including CI), AGP is never resolved. The iOS targets are always declared but compile only on **macOS with Xcode**; on other hosts their compile tasks are simply never invoked. So the default Linux/CI build covers JVM + Wasm exactly as before.

**Firebase App Distribution.** A second opt-in layered on Android ships the `:web` APK to testers: `-Ppredictor.firebase=true` (requires `-Ppredictor.android=true`). Same pattern — the `com.google.firebase.appdistribution` plugin classpath is added in the root build only when both flags are on, and its `firebaseAppDistribution { }` config lives in `gradle/android-firebase-app-distribution.gradle.kts`, reading every value (app id, credentials, groups) from Gradle properties/env so nothing secret is committed. A manual `.github/workflows/distribute-android.yml` builds + uploads from CI; the default `ci.yml` is untouched. Full setup in `docs/firebase-app-distribution.md`.

## Commands

```bash
# Tests
./gradlew test                 # all modules
./gradlew :shared:jvmTest      # scoring + leaderboard unit tests (shared logic)
./gradlew :server:test         # Ktor REST API integration tests
# Single test: append --tests, e.g.
./gradlew :shared:jvmTest --tests "com.predictor.shared.scoring.PredictionScorerTest"

# Run the app (two steps, one origin)
./gradlew :web:wasmJsBrowserDistribution   # 1. build the Wasm frontend
./gradlew :server:run                      # 2. server serves API + frontend at http://localhost:8080

# Frontend hot-reload during dev (webpack dev server instead of the Ktor host)
./gradlew :web:wasmJsBrowserDevelopmentRun --continuous
```

CI (`.github/workflows/ci.yml`) runs `:shared:jvmTest :server:test` then compiles the Wasm bundle via `:web:wasmJsBrowserDevelopmentExecutableDistribution` (the development distribution skips the slow production `wasm-opt` pass). Match this locally before pushing.

## Architecture: why `:shared` is the whole point

The reason to read across modules is the single-source-of-truth boundary. Three things live in `:shared` and are consumed identically by server and web:

- **`scoring/PredictionScorer.kt`** — the pure function turning (prediction, actual result, rules) into a `ScoreBreakdown`. It runs on the **server** as the leaderboard authority (`PredictionStore.matchViews`, `LeaderboardCalculator`) *and* in the **browser** to show points live as the user types. Never fork this logic into a module — that would defeat the demo and let the two tiers disagree.
- **`api/ApiContract.kt`** — `ApiRoutes` (path strings/builders) plus the `@Serializable` request/response DTOs. The server's `configureRouting` and the web's `ApiClient` both reference these exact paths and types, so the compiler is the contract test; there is no separate spec to drift. When changing an endpoint, edit `ApiRoutes` and the DTOs here first — both sides then fail to compile until updated.
- **`model/Sport.kt`** — sport behavior is **data, not branches**. Each `Sport` enum entry carries its vocabulary (`competitorNoun`, `scoreUnit`), `allowsDraw`, and a `ScoringRules` preset. The API, scoring engine, and Compose UI all read these properties, so there are intentionally no `when (sport)` blocks. Adding a sport = one enum entry + seed data in `server/.../data/SeedData.kt`; nothing else changes.

## Server specifics

- `Application.kt` wires one Ktor module from small `configure…` extensions (`configureSerialization`, `configureMonitoring`, `configureRouting`) in `plugins/`. Port comes from `PORT` env var, default 8080.
- `data/PredictionStore.kt` is the **entire data layer**, backed by a SQL database through **Exposed** (the schema is `data/Tables.kt`; the connection comes from `data/DatabaseFactory.kt`). Every route still talks to it through the same plain methods, so swapping the old in-memory maps for a real DB did not ripple outward — that swappability was the whole point of the boundary. Each method runs in its own `transaction`; the "one prediction per user per match" invariant is the `predictions` primary key, so a re-submission is a single atomic `upsert`. Prediction writes still return a typed `SubmitResult` sealed interface that `configureRouting` maps to HTTP status codes (rather than throwing) — preserve this result-type-to-status pattern when adding mutations. On startup the schema is created if missing and, only when the DB is empty, seeded from `SeedData` (`data/SeedData.kt`), so an already-populated database is never re-seeded.
- **Database config is entirely env-driven** (`DatabaseConfig.fromEnv`): `DATABASE_URL` / `DATABASE_DRIVER` / `DATABASE_USER` / `DATABASE_PASSWORD` / `DATABASE_MAX_POOL_SIZE`. The default is an embedded, file-backed **H2** database at `./data/predictor` (relative to the run's working dir, i.e. the repo root — gitignored), so `:server:run` persists predictions across restarts with zero setup. Point `DATABASE_URL` at Postgres (add that JDBC driver) and the same server persists there instead. Tests use `DatabaseConfig.inMemory(name)` for a fresh, isolated in-memory DB per test.
- `configureRouting` also serves the compiled Wasm frontend via `staticFiles` when `locateWebDist()` finds it (walking up from the working dir). This is why `:server:run` sets `workingDir = rootProject.projectDir` — so it locates `web/build/dist/...`.

## Web specifics

- Compose Multiplatform targets `wasmJs`; entry point is `Main.kt`. `ApiClient.kt` holds **no DTO definitions** — it calls `ApiRoutes` paths and deserializes the shared model types directly. `baseUrl` defaults to `window.location.origin` (same origin as the API in production).
- Dependency versions are centralized in `gradle/libs.versions.toml`; the root `build.gradle.kts` only declares plugin versions (`apply false`) so subprojects apply them without repeating a version.
