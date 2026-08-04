# Full-Stack With Only Kotlin: One Language, Backend to Browser

*Building a multi-sport tournament predictor with Kotlin Multiplatform, Ktor, and Compose
for WebAssembly — and writing the business logic exactly once.*

> One language from the database to the DOM. The scoring rule of the game written
> once and executed, unmodified, on both the server and in the browser.

<!-- Medium import note: the H1 above becomes the title and the italic line the subtitle.
     Paste this file into a Medium story via Import, or use the markdown directly. -->

Most "full-stack" jobs are really two jobs in a trench coat. You write your domain
model in Kotlin or Java or Go on the backend, and then you write it *again* in
TypeScript on the frontend. Two definitions of a `Match`. Two ideas of what a valid
prediction is. Two implementations of the scoring formula that inevitably disagree the
first time someone edits one and forgets the other.

This article builds a small but complete application that refuses that duplication. It's
a **tournament prediction game** — pick the scores of upcoming fixtures, earn points
when you're right, climb a leaderboard — and every line of it is Kotlin: the backend,
the frontend, and, crucially, the code they *share*.

We'll cover:

- Structuring a **Kotlin Multiplatform** project so one module compiles to both the JVM
  and WebAssembly.
- A **Ktor** backend that serves a JSON API *and* the compiled web app.
- A **Compose Multiplatform** frontend that runs in the browser as WebAssembly.
- The payoff: a domain model, an API contract, and a **scoring engine written once** and
  shared by both tiers.

And we'll design it so that supporting a new sport — soccer today, tennis and handball
too, something else tomorrow — is a one-line change rather than a rewrite.

---

## The shape of the thing

Three Gradle modules:

```
:shared   →  domain model, scoring engine, leaderboard, API contract
             compiles to BOTH  →ᴶᵛᴹ  and  →ᵂᵃˢᵐ
:server   →  Ktor (JVM), REST + in-memory store, also hosts the frontend
:web      →  Compose Multiplatform UI, compiled to WebAssembly
```

`:server` and `:web` both depend on `:shared`. That single arrow — two consumers, one
definition — is the entire thesis of the project. Everything else is plumbing.

---

## The shared module: write it once

Here's a normal-looking Kotlin data class. The only unusual thing about it is *where it
runs*: this exact class, this exact `@Serializable` annotation, becomes the JSON the
server sends and the object the browser receives.

```kotlin
@Serializable
data class MatchOutcome(val home: Int, val away: Int) {
    val result: MatchResult
        get() = when {
            home > away -> MatchResult.HOME
            away > home -> MatchResult.AWAY
            else -> MatchResult.DRAW
        }
    val margin: Int get() = home - away
}
```

The same `MatchOutcome` is used for *what actually happened* and for *what a user
predicts will happen* — and comparing those two values is the whole job of the game.
That job is the scoring engine, and it is the most important 15 lines in the repository:

```kotlin
object PredictionScorer {
    fun score(prediction: MatchOutcome, actual: MatchOutcome, rules: ScoringRules): ScoreBreakdown =
        when {
            prediction == actual ->
                ScoreBreakdown(rules.exactScore, ScoreCategory.EXACT)

            prediction.result == actual.result && prediction.margin == actual.margin ->
                ScoreBreakdown(rules.correctResultAndMargin, ScoreCategory.RESULT_AND_MARGIN)

            prediction.result == actual.result ->
                ScoreBreakdown(rules.correctResult, ScoreCategory.RESULT)

            else ->
                ScoreBreakdown(0, ScoreCategory.MISS)
        }
}
```

Nailed the exact scoreline? Top points. Right winner and right margin but the wrong
score? Fewer. Right winner only? Fewer still. Wrong? Nothing.

Now the part that pays for the whole architecture. This function runs in **two places**:

1. On the **server**, as the source of truth that computes the official leaderboard.
2. In the **browser**, so the moment a match finishes the UI can show *"+3 — right
   result & margin"* next to your prediction — computed locally, instantly, and
   **guaranteed** to match what the server will say, because it is literally the same
   code.

No API round-trip to score a result you already have. No risk of the client's idea of
"3 points" drifting from the server's. You physically cannot have two implementations,
because there is only one.

### Making it multi-sport without `when (sport)`

A predictor for soccer is easy. A predictor that also does tennis is where naïve designs
fall apart, because tennis has no draws and its scores mean something different (sets,
not goals). The temptation is a scattering of `if (sport == TENNIS)` checks. We avoid
that entirely by pushing the differences into *data*:

```kotlin
@Serializable
enum class Sport(
    val displayName: String,
    val competitorNoun: String,   // "Team" vs "Player"
    val scoreUnit: String,        // "Goals" vs "Sets"
    val allowsDraw: Boolean,      // tennis: false
    val scoringRules: ScoringRules,
) {
    SOCCER  ("Soccer",   "Team",   "Goals", allowsDraw = true,  ScoringRules.SCORELINE),
    HANDBALL("Handball", "Team",   "Goals", allowsDraw = true,  ScoringRules.SCORELINE),
    TENNIS  ("Tennis",   "Player", "Sets",  allowsDraw = false, ScoringRules.WINNER_FOCUSED),
}
```

The scoring engine takes a `ScoringRules` — it never asks *which sport* it's scoring. The
UI labels a column "Goals" or "Sets" by reading `sport.scoreUnit`. The server rejects a
1–1 tennis prediction by reading `sport.allowsDraw`. **Adding a sport is one enum entry
plus some seed data. Nothing branches on the sport, so nothing has to be found and
edited.**

### The API contract is shared too

The routes and the request/response types live in `:shared`:

```kotlin
object ApiRoutes {
    const val TOURNAMENTS = "/api/tournaments"
    fun leaderboard(id: String) = "$TOURNAMENTS/$id/leaderboard"
    // ...
}

@Serializable
data class SubmitPredictionRequest(val userId: String, val matchId: String, val outcome: MatchOutcome)
```

The server *implements* `ApiRoutes.leaderboard(id)`; the client *calls*
`ApiRoutes.leaderboard(id)`. If someone renames the path, both sides move together and
the build stays green — or it doesn't compile. The compiler is the contract test.

### One `build.gradle.kts`, two targets

What makes `:shared` reusable is that it declares two compilation targets:

```kotlin
kotlin {
    jvm()                                    // consumed by the Ktor server
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }                     // consumed by the Compose frontend

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
```

Everything in `commonMain` is compiled *twice* — once to JVM bytecode, once to
WebAssembly — from the same source. `kotlinx.serialization`, `datetime`, and
`coroutines` all publish for both targets, so the shared code has real libraries to lean
on, not a lowest-common-denominator subset.

---

## The backend: Ktor in a few small functions

The server is deliberately unremarkable Kotlin, which is the point — nothing exotic is
needed to consume the shared module.

```kotlin
fun Application.module() {
    val store = PredictionStore()   // in-memory, seeded with three tournaments
    configureSerialization()        // kotlinx.serialization JSON
    configureMonitoring()           // logging + CORS
    configureRouting(store)         // the API + static hosting of the web app
}
```

Content negotiation serialises the shared types directly — the server never hand-writes
JSON:

```kotlin
install(ContentNegotiation) {
    json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
}
```

A route enriches each fixture with everything the UI needs — the resolved competitors,
your current prediction, and (once played) the points earned, scored by the shared
engine:

```kotlin
fun matchViews(tournamentId: String, userId: String): List<MatchView>? {
    val tournament = tournaments[tournamentId] ?: return null
    val rules = tournament.sport.scoringRules
    return tournament.matches.map { match ->
        val prediction = predictions[key(userId, match.id)]?.predicted
        val earned = if (match.isScored && prediction != null)
            PredictionScorer.score(prediction, match.actualOutcome!!, rules) else null
        MatchView(match, tournament.competitor(match.homeId),
                  tournament.competitor(match.awayId), prediction, earned)
    }
}
```

Storage is an in-memory `PredictionStore` seeded with a two-group World Cup, a tennis
knockout and a handball championship. Because every route talks to it through plain
methods, swapping those maps for a real database (JetBrains Exposed, say) later is a
change to one file — the API and the frontend never notice.

One neat trick: the same server process hosts the compiled frontend. After you build the
Wasm bundle, Ktor serves it at `/` and the API at `/api`, so the whole app is one origin,
one command:

```kotlin
staticFiles("/", webDist) { default("index.html") }
```

---

## The frontend: Compose Multiplatform, in the browser, as WebAssembly

This is the part that still makes people blink: the UI is written in **Jetpack
Compose** — `@Composable` functions, the same declarative model Android developers use —
and compiled to **WebAssembly** to run in the browser. No React, no TypeScript, no JSX.
Kotlin.

![The fixtures screen: a Compose UI running as WebAssembly, with points computed in the browser by the shared scorer](../docs/screenshots/fixtures.png)

```kotlin
@Composable
fun App() {
    val vm = remember { AppViewModel(ApiClient(), scope) }
    LaunchedEffect(Unit) { vm.start() }

    PredictorTheme {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(vm)
            Row(Modifier.fillMaxSize()) {
                TournamentRail(vm, Modifier.width(280.dp))
                DetailPane(vm, Modifier.weight(1f))
            }
        }
    }
}
```

State lives in a plain `AppViewModel` using Compose's snapshot state — assign to a
property and anything that read it recomposes. The HTTP client is Ktor's, and notice
what its methods return:

```kotlin
suspend fun leaderboard(tournamentId: String): Leaderboard =
    http.get(url(ApiRoutes.leaderboard(tournamentId))).body()
```

`Leaderboard`. The shared type. Not a hand-written interface that mirrors the server's
JSON — the actual class the server serialised. `.body()` deserialises straight into it.

And here is the shared scorer, now running **in the browser**, to render the points pill
the instant a result is shown:

```kotlin
@Composable
private fun FinishedPanel(view: MatchView, sport: Sport) {
    val actual = view.match.actualOutcome!!
    val earned = view.prediction?.let {
        // The SAME PredictionScorer the backend uses — executing as WebAssembly.
        PredictionScorer.score(it, actual, sport.scoringRules)
    }
    // ...render "+5 · Exact score"
}
```

That's the whole thesis made visible: one function, `PredictionScorer.score`, compiled
once to JVM bytecode for the server and once to WebAssembly for the browser, producing
identical results by construction.

---

## What it costs (an honest section)

It isn't free, and pretending otherwise helps no one:

- **Compose for Web (Wasm) is young.** It's excellent for app-like, canvas-rendered UIs
  (dashboards, tools, games like this). It is *not* how you'd build a
  content/SEO-driven marketing site — the UI paints onto a canvas, not semantic HTML.
- **The toolchain is heavier than `npm`.** The first Gradle build downloads the Kotlin
  Wasm compiler, Compose, Skia (via Skiko), and a Node/webpack layer. Subsequent builds
  are cached and quick, but the cold start is real.
- **Smaller ecosystem than JS.** You're mostly living in Kotlin/JetBrains libraries. For
  this domain that's plenty; for a niche widget you might miss an npm package.

The upside is the reason to pay it: **no duplicated domain logic, no client/server drift,
one language to hire for and reason about, and refactors that cross the network boundary
in a single compile.** For a team that's already strong in Kotlin, that trade is often
very good.

---

## Adding a sport, start to finish

To prove the extensibility claim, here's the entire diff to add, say, cricket:

1. One line in the `Sport` enum:
   ```kotlin
   CRICKET("Cricket", "Team", "Runs", allowsDraw = true, ScoringRules.SCORELINE)
   ```
2. A seed tournament in `SeedData.kt`.

Done. The picker shows it, the fixtures screen labels scores "Runs", the scorer scores
it, the leaderboard ranks it, and the tennis-style draw rule is applied or not based on
the flag — all without touching the API, the engine, or the UI, because none of them
branch on the sport.

---

## Try it

```bash
./gradlew :web:wasmJsBrowserDistribution   # build the Wasm frontend
./gradlew :server:run                       # serve API + frontend on :8080
```

Open <http://localhost:8080>, switch between players, predict the upcoming World Cup and
tennis fixtures, and watch the leaderboard react.

The full source is in the repository — start with `shared/scoring/PredictionScorer.kt`
and follow it outward. That one file runs everywhere, and that's the whole idea.

---

*Built with Kotlin Multiplatform, Ktor, and Compose Multiplatform. One language, from the
database to the DOM.*
