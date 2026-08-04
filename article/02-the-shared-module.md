# Full stack 'Android' dev: The shared module — write the rules once

*Part 2 of a series building a multi-sport tournament predictor entirely in Kotlin. This
part is the heart of it: the module that server, Android, iOS, and web all compile into
themselves.*

> **Full stack 'Android' dev** — a series:
> 1. [The idea — one language, every screen](01-the-idea.md)
> 2. **The shared module — write the rules once** *(you are here)*
> 3. [The Ktor backend — a JVM server that speaks your model](03-the-ktor-backend.md)
> 4. [One Compose UI — Android, iOS, and the web](04-the-frontend.md)

---

In [Part 1](01-the-idea.md) we made a promise: write the domain model, the business rules,
and the API contract *once*, and compile that same source to every platform. This part is
where the promise gets paid. Everything here lives in `:shared/src/commonMain` and is
compiled — unchanged — to JVM bytecode for the server and to Android, iOS, and Wasm for
the clients.

---

## One `build.gradle.kts`, every target

What makes a module shareable is that it declares more than one compilation target:

```kotlin
kotlin {
    jvm()                                    // consumed by the Ktor server (and Android is JVM-family too)
    androidTarget()                          // consumed by the Android app
    iosArm64(); iosSimulatorArm64()          // consumed by the iOS app
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }                     // consumed by the web app

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
```

Everything in `commonMain` is compiled *once per target* from the same source.
`kotlinx.serialization`, `datetime`, and `coroutines` all publish for every one of these
targets, so the shared code has real libraries to lean on — not a lowest-common-denominator
subset.

> The reference repo wires up all of these: `jvm()` for the server, `iosArm64()` /
> `iosSimulatorArm64()` for iOS, `androidTarget()` for Android, and `wasmJs { browser() }`
> for the web. The Android target is switched on only when an Android SDK is present, and
> the iOS targets compile only on macOS — so the JVM/Wasm build still runs anywhere,
> including CI. Crucially, **none of the domain code changed** to gain those targets,
> because none of it assumed a platform in the first place. That's the point of keeping it
> in `commonMain`.

---

## The model: a normal data class that happens to run everywhere

Here's the outcome of a fixture. The only unusual thing about it is *where it runs*: this
exact class, this exact `@Serializable` annotation, becomes the JSON the server sends and
the object every client receives and works with.

```kotlin
@Serializable
data class MatchOutcome(val home: Int, val away: Int) {
    init {
        require(home >= 0 && away >= 0) { "Scores cannot be negative (got $home-$away)" }
    }

    /** Who won, derived purely from the scores. */
    val result: MatchResult
        get() = when {
            home > away -> MatchResult.HOME
            away > home -> MatchResult.AWAY
            else -> MatchResult.DRAW
        }

    /** Home score minus away score — the winning margin. */
    val margin: Int get() = home - away
}
```

The same `MatchOutcome` represents *what actually happened* **and** *what a user predicts
will happen*. Comparing those two values is the entire job of the game — and that job is
the next file.

---

## The scoring engine: the most important 15 lines in the repo

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

Nailed the exact scoreline? Top points. Right winner and right margin but the wrong score?
Fewer. Right winner only? Fewer still. Wrong? Nothing.

Now the part that pays for the whole architecture. This function runs in **two kinds of
place**:

1. On the **server**, as the source of truth that computes the official leaderboard.
2. On **every client** — Android, iOS, web — so the moment a match finishes the UI shows
   *"+3 · Right result & margin"* next to your prediction, computed locally, instantly, and
   **guaranteed** to match what the server will say, because it is literally the same code.

No API round-trip to score a result you already have. No risk of the client's idea of "3
points" drifting from the server's. You cannot have two implementations, because there is
only one.

It returns a small, serializable breakdown rather than a bare `Int`, so the UI can render
*why* you scored what you did:

```kotlin
@Serializable
enum class ScoreCategory(val label: String) {
    EXACT("Exact score"),
    RESULT_AND_MARGIN("Right result & margin"),
    RESULT("Right result"),
    MISS("Missed"),
}

@Serializable
data class ScoreBreakdown(val points: Int, val category: ScoreCategory)
```

---

## Multi-sport without a single `when (sport)`

A predictor for soccer is easy. A predictor that *also* does tennis is where naïve designs
fall apart, because tennis has no draws and its scores mean something different (sets, not
goals). The temptation is a scattering of `if (sport == TENNIS)` checks across the server,
the scorer, and every UI. We avoid that entirely by pushing the differences into **data**.

First, the points weights become a value, not code:

```kotlin
@Serializable
data class ScoringRules(
    val exactScore: Int,
    val correctResultAndMargin: Int,
    val correctResult: Int,
) {
    init {
        require(exactScore >= correctResultAndMargin && correctResultAndMargin >= correctResult) {
            "More precise predictions must never be worth fewer points than looser ones"
        }
    }

    companion object {
        val SCORELINE = ScoringRules(exactScore = 5, correctResultAndMargin = 3, correctResult = 2)
        val WINNER_FOCUSED = ScoringRules(exactScore = 4, correctResultAndMargin = 3, correctResult = 2)
    }
}
```

Then each `Sport` *carries* its own vocabulary and rules:

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

Now look at how the rest of the system reads it:

- The **scoring engine** takes a `ScoringRules` — it never asks *which* sport it's scoring.
- The **UI** labels a column "Goals" or "Sets" by reading `sport.scoreUnit`, and calls a
  side a "Team" or a "Player" via `sport.competitorNoun`.
- The **server** rejects a 1–1 tennis prediction by reading `sport.allowsDraw`.

There is not one `when (sport)` branch in the business logic. Because the behavior *is* the
data, **adding a sport is one enum entry plus some seed data** — nothing has to be hunted
down and edited:

```kotlin
CRICKET("Cricket", "Team", "Runs", allowsDraw = true, ScoringRules.SCORELINE)
```

Add that line (and a seed tournament, covered in Part 3) and the picker shows cricket, the
fixtures screen labels scores "Runs", the scorer scores it, and the leaderboard ranks it —
on the server, on Android, on iOS, and on the web at once.

---

## The leaderboard: more shared logic, for free

Rolling scored predictions up into a ranked table is *also* pure shared logic that reuses
the scorer:

```kotlin
object LeaderboardCalculator {
    fun build(tournament: Tournament, users: List<User>, predictions: List<Prediction>): Leaderboard {
        val rules = tournament.sport.scoringRules
        val matchesById = tournament.matches.associateBy { it.id }

        val entries = users.map { user ->
            var points = 0; var exactHits = 0; var scored = 0
            for (p in predictions.filter { it.userId == user.id }) {
                val actual = matchesById[p.matchId]?.actualOutcome ?: continue
                val breakdown = PredictionScorer.score(p.predicted, actual, rules)   // same scorer again
                points += breakdown.points; scored++
                if (breakdown.category == ScoreCategory.EXACT) exactHits++
            }
            LeaderboardEntry(rank = 0, userId = user.id, displayName = user.displayName,
                             totalPoints = points, exactHits = exactHits, predictionsScored = scored)
        }

        return Leaderboard(
            tournamentId = tournament.id,
            entries = entries
                .sortedWith(compareByDescending<LeaderboardEntry> { it.totalPoints }
                    .thenByDescending { it.exactHits }.thenBy { it.displayName })
                .mapIndexed { i, e -> e.copy(rank = i + 1) },
        )
    }
}
```

Today the server calls it. But because it's in `:shared` and dependency-free, a client
could call it verbatim to render an optimistic, offline-first standings view — no new code,
no second ranking rule to keep in sync.

---

## The API contract is shared too

The routes and the request/response types live in `:shared`, so the client and server are
type-checked against **one** definition:

```kotlin
object ApiRoutes {
    const val TOURNAMENTS = "/api/tournaments"
    fun leaderboard(tournamentId: String) = "$TOURNAMENTS/$tournamentId/leaderboard"
    const val PREDICTIONS = "/api/predictions"
    const val USER_ID_PARAM = "userId"
    // ...
}

@Serializable
data class SubmitPredictionRequest(val userId: String, val matchId: String, val outcome: MatchOutcome)

@Serializable
data class MatchView(               // a fixture enriched with everything a UI row needs
    val match: Match,
    val home: Competitor,
    val away: Competitor,
    val prediction: MatchOutcome? = null,
    val earned: ScoreBreakdown? = null,
)
```

The server *implements* `ApiRoutes.leaderboard(id)`; every client *calls*
`ApiRoutes.leaderboard(id)`. If someone renames the path, both sides move together — or it
doesn't compile. There is no separate, hand-copied API spec that can drift. **The compiler
is the contract test.**

---

## Testing the core once covers every platform

Because the rules are platform-agnostic, their tests are too — they live in
`commonTest` and validate the exact code every target ships:

```kotlin
@Test
fun exactScore_beatsResultOnly() {
    val rules = ScoringRules.SCORELINE
    val exact = PredictionScorer.score(MatchOutcome(2, 1), MatchOutcome(2, 1), rules)
    val resultOnly = PredictionScorer.score(MatchOutcome(3, 0), MatchOutcome(2, 1), rules)
    assertEquals(ScoreCategory.EXACT, exact.category)
    assertTrue(exact.points > resultOnly.points)
}
```

Run `./gradlew :shared:jvmTest` and you've verified the scoring the Android app, the iOS
app, and the web app all use. One test suite, every screen.

---

## Next: giving the model a server

The shared module is a library — it computes, but it doesn't *serve*. In
[Part 3](03-the-ktor-backend.md) we wrap it in a small Ktor backend that serializes these
exact types to JSON, maps typed results to HTTP status codes, and even hosts the compiled
web client — all in a few small functions, because consuming the shared module takes
nothing exotic.

---

> **Full stack 'Android' dev** — a series:
> [1. The idea](01-the-idea.md) ·
> **2. The shared module** ·
> [3. The Ktor backend](03-the-ktor-backend.md) ·
> [4. One Compose UI](04-the-frontend.md)
