# Full stack 'Android' dev: The Ktor backend — a JVM server that speaks your model

*Part 3 of a series building a multi-sport tournament predictor entirely in Kotlin. The
shared module can compute; now we give it a server — in the same language, with the same
types.*

> **Full stack 'Android' dev** — a series:
> 1. [The idea — one language, every screen](01-the-idea.md)
> 2. [The shared module — write the rules once](02-the-shared-module.md)
> 3. **The Ktor backend — a JVM server that speaks your model** *(you are here)*
> 4. [One Compose UI — Android, iOS, and the web](04-the-frontend.md)

---

In [Part 2](02-the-shared-module.md) we built a shared module full of pure logic — the
model, the scorer, the leaderboard, the API contract. It's a library, though: it computes,
it doesn't *serve*. This part wraps it in a **Ktor** backend on the JVM.

The theme of this part is how little there is to it. An Android developer already knows
Kotlin and coroutines; Ktor asks for nothing more. The server is deliberately unremarkable,
which is the point — **consuming the shared module takes nothing exotic.**

---

## The whole server, wired in one module

Ktor apps are just functions. Here's the entire wiring:

```kotlin
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    val store = PredictionStore()   // in-memory, seeded with a few tournaments
    configureSerialization()        // kotlinx.serialization JSON
    configureMonitoring()           // logging + CORS
    configureRouting(store)         // the API + static hosting of the web app
}
```

Each `configure…` is a small extension function, so every concern can be read on its own.

---

## Serialization: the server never hand-writes JSON

Content negotiation is installed with the *same* `kotlinx.serialization` that the shared
module's `@Serializable` types already use:

```kotlin
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true })
    }
}
```

That's the entire JSON layer. When a route hands back a `List<MatchView>` or a
`Leaderboard`, Ktor serializes the shared types directly. There is no server-side DTO, no
mapping layer, no hand-written JSON — the bytes on the wire are the shared model, which is
*exactly* what every client deserializes back into.

---

## Routing: paths come from the shared contract

Every route path is drawn from `ApiRoutes` in the shared module, so it cannot drift from
what the clients call:

```kotlin
fun Application.configureRouting(store: PredictionStore) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ApiError(cause.message ?: "Unexpected error"))
        }
    }

    routing {
        get(ApiRoutes.TOURNAMENTS) { call.respond(store.tournamentSummaries()) }

        get("${ApiRoutes.TOURNAMENTS}/{id}/leaderboard") {
            val id = call.parameters["id"]!!
            val leaderboard = store.leaderboard(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("No tournament '$id'"))
            call.respond(leaderboard)   // a shared Leaderboard, serialized as-is
        }

        // ...predictions, matches, users
    }
}
```

`store.leaderboard(id)` returns the shared `Leaderboard` that `LeaderboardCalculator` built
in Part 2 — the server's job here is just to expose it over HTTP.

---

## Typed results map cleanly to HTTP status codes

Placing a prediction can fail in several *domain* ways — unknown match, closed match, an
illegal draw for a no-draw sport. Rather than throw, the store returns a **sealed
`SubmitResult`**, and the route turns each case into the right status code:

```kotlin
post(ApiRoutes.PREDICTIONS) {
    val request = call.receive<SubmitPredictionRequest>()   // a shared DTO
    when (val result = store.submitPrediction(request.userId, request.matchId, request.outcome)) {
        is PredictionStore.SubmitResult.Ok ->
            call.respond(HttpStatusCode.Created, PredictionResponse(result.prediction))
        PredictionStore.SubmitResult.UnknownMatch ->
            call.respond(HttpStatusCode.NotFound, ApiError("Unknown match '${request.matchId}'"))
        PredictionStore.SubmitResult.MatchClosed ->
            call.respond(HttpStatusCode.Conflict, ApiError("This match no longer accepts predictions"))
        is PredictionStore.SubmitResult.DrawNotAllowed ->
            call.respond(HttpStatusCode.UnprocessableEntity, ApiError("${result.sport} matches cannot end in a draw"))
        // ...
    }
}
```

The `when` is *exhaustive over the sealed type*, so if you add a new failure case later the
compiler forces you to handle it here. Notice the domain rule being enforced —
`DrawNotAllowed` — comes straight from `sport.allowsDraw` in the shared module. The server
doesn't re-decide what a legal tennis score is; it asks the shared model, exactly as the
clients do.

---

## The data layer is one swappable file

The "database" is an in-memory `PredictionStore` seeded with a couple of tournaments. Every
route talks to it through plain methods, and the scoring reuses the shared engine:

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

Reads are lock-free (`ConcurrentHashMap`); the one mutation returns the typed
`SubmitResult` above. Because everything goes through this class, swapping the maps for
JetBrains Exposed and a real database later is a change to *one file* — the routes, the
contract, and every client never notice.

Adding a sport (the `CRICKET` line from Part 2) is finished here, with seed data:

```kotlin
// SeedData.kt
tournament(Sport.CRICKET, name = "T20 Cup", season = "2026") { /* competitors + fixtures */ }
```

Nothing in routing or serialization changes, because nothing there branches on the sport.

---

## One process serves the API *and* the web client

A small bonus that makes the demo a single command: after the Wasm frontend is built, the
same Ktor process hosts it as static files, so the API and the UI share one origin.

```kotlin
val webDist = locateWebDist()          // walks up from the working dir to find the built bundle
if (webDist != null) {
    staticFiles("/", webDist) { default("index.html") }
}
```

```bash
./gradlew :web:wasmJsBrowserDistribution   # build the web client
./gradlew :server:run                       # serve API + client on :8080
```

The Android and iOS apps, of course, just point their `ApiClient` at this server's base
URL — same endpoints, same shared types over the wire.

---

## Next: the UI, once, for every screen

The backend now speaks fluent shared-model over HTTP. In [Part 4](04-the-frontend.md) we
build the client — a **single Compose Multiplatform UI** that runs natively on Android and
iOS and, as the bonus, in the browser — deserializing straight into the shared types and
running the *same* `PredictionScorer` on-device to show points the instant a match ends.

---

> **Full stack 'Android' dev** — a series:
> [1. The idea](01-the-idea.md) ·
> [2. The shared module](02-the-shared-module.md) ·
> **3. The Ktor backend** ·
> [4. One Compose UI](04-the-frontend.md)
