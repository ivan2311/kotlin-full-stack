package com.predictor.server.plugins

import com.predictor.server.data.PredictionStore
import com.predictor.shared.api.ApiError
import com.predictor.shared.api.ApiRoutes
import com.predictor.shared.api.PredictionResponse
import com.predictor.shared.api.SubmitPredictionRequest
import com.predictor.server.data.Seed
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File

/**
 * Every HTTP route the server exposes. The paths come from [ApiRoutes] in the shared
 * module, so they cannot drift from what the client calls.
 */
fun Application.configureRouting(store: PredictionStore) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(cause.message ?: "Unexpected server error"),
            )
        }
    }

    routing {
        get("/api/health") { call.respond(mapOf("status" to "ok")) }

        get(ApiRoutes.USERS) { call.respond(store.allUsers()) }

        get(ApiRoutes.TOURNAMENTS) { call.respond(store.tournamentSummaries()) }

        get("${ApiRoutes.TOURNAMENTS}/{id}") {
            val id = call.parameters["id"]!!
            val tournament = store.tournament(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("No tournament '$id'"))
            call.respond(tournament)
        }

        get("${ApiRoutes.TOURNAMENTS}/{id}/matches") {
            val id = call.parameters["id"]!!
            val userId = call.request.queryParameters[ApiRoutes.USER_ID_PARAM] ?: Seed.DEFAULT_USER_ID
            val views = store.matchViews(id, userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("No tournament '$id'"))
            call.respond(views)
        }

        get("${ApiRoutes.TOURNAMENTS}/{id}/leaderboard") {
            val id = call.parameters["id"]!!
            val leaderboard = store.leaderboard(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("No tournament '$id'"))
            call.respond(leaderboard)
        }

        get(ApiRoutes.PREDICTIONS) {
            val userId = call.request.queryParameters[ApiRoutes.USER_ID_PARAM]
            val tournamentId = call.request.queryParameters[ApiRoutes.TOURNAMENT_ID_PARAM]
            if (userId == null || tournamentId == null) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Both '${ApiRoutes.USER_ID_PARAM}' and '${ApiRoutes.TOURNAMENT_ID_PARAM}' are required"),
                )
            }
            call.respond(store.predictionsFor(userId, tournamentId))
        }

        post(ApiRoutes.PREDICTIONS) {
            val request = call.receive<SubmitPredictionRequest>()
            when (val result = store.submitPrediction(request.userId, request.matchId, request.outcome)) {
                is PredictionStore.SubmitResult.Ok ->
                    call.respond(HttpStatusCode.Created, PredictionResponse(result.prediction))

                PredictionStore.SubmitResult.UnknownUser ->
                    call.respond(HttpStatusCode.NotFound, ApiError("Unknown user '${request.userId}'"))

                PredictionStore.SubmitResult.UnknownMatch ->
                    call.respond(HttpStatusCode.NotFound, ApiError("Unknown match '${request.matchId}'"))

                PredictionStore.SubmitResult.MatchClosed ->
                    call.respond(HttpStatusCode.Conflict, ApiError("This match no longer accepts predictions"))

                is PredictionStore.SubmitResult.DrawNotAllowed ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ApiError("${result.sport} matches cannot end in a draw"))
            }
        }

        // Serve the compiled Compose/Wasm frontend if it has been built. The same
        // server process then hosts both the API and the UI on one origin.
        val webDist = locateWebDist()
        if (webDist != null) {
            staticFiles("/", webDist) { default("index.html") }
        }
    }
}

/**
 * Finds the built web distribution regardless of where the process was launched from
 * (repo root when run via a jar, or the `server/` module dir when run via Gradle), by
 * walking up from the working directory looking for the dist folder.
 */
private fun locateWebDist(): File? {
    val relativeCandidates = listOf(
        "web/build/dist/wasmJs/productionExecutable",
        "web/build/dist/wasmJs/developmentExecutable",
    )
    var dir: File? = File(".").absoluteFile
    repeat(5) {
        val base = dir ?: return null
        for (rel in relativeCandidates) {
            val candidate = File(base, rel)
            if (candidate.isDirectory) return candidate
        }
        dir = base.parentFile
    }
    return null
}
