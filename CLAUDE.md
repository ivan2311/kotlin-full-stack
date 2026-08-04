# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A full-stack Kotlin tournament-prediction game (pick scores, earn points, climb the leaderboard). Its defining trait: **backend, frontend, and the business logic they share are all Kotlin in one Gradle build**, with the domain model, scoring rules, and REST contract written exactly once in `:shared` and compiled to both JVM (server) and WebAssembly (browser).

## Modules

| Module    | Target      | Role                                                                     |
|-----------|-------------|--------------------------------------------------------------------------|
| `shared`  | JVM + Wasm  | Domain model, scoring engine, leaderboard calculator, REST contract      |
| `server`  | JVM         | Ktor (Netty) backend — REST API + in-memory store; also serves the built web app |
| `web`     | Wasm        | Compose Multiplatform UI compiled to WebAssembly (no hand-written HTML/JS) |

Package root is `com.predictor.*` across all modules. Requires JDK 17+ (built with JDK 21). The Gradle wrapper (`./gradlew`) provides the Kotlin, Compose, Wasm, and Node toolchains — do not install these separately.

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
- `data/PredictionStore.kt` is the **entire data layer**, in-memory and deliberately swappable — every route talks to it through plain methods so it could be replaced with Exposed + a DB without rippling outward. Reads are lock-free (`ConcurrentHashMap`); prediction writes return a typed `SubmitResult` sealed interface that `configureRouting` maps to HTTP status codes (rather than throwing). Preserve this result-type-to-status pattern when adding mutations.
- `configureRouting` also serves the compiled Wasm frontend via `staticFiles` when `locateWebDist()` finds it (walking up from the working dir). This is why `:server:run` sets `workingDir = rootProject.projectDir` — so it locates `web/build/dist/...`.

## Web specifics

- Compose Multiplatform targets `wasmJs`; entry point is `Main.kt`. `ApiClient.kt` holds **no DTO definitions** — it calls `ApiRoutes` paths and deserializes the shared model types directly. `baseUrl` defaults to `window.location.origin` (same origin as the API in production).
- Dependency versions are centralized in `gradle/libs.versions.toml`; the root `build.gradle.kts` only declares plugin versions (`apply false`) so subprojects apply them without repeating a version.
