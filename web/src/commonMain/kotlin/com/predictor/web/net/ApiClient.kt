package com.predictor.web.net

import com.predictor.shared.api.ApiRoutes
import com.predictor.shared.api.MatchView
import com.predictor.shared.api.PredictionResponse
import com.predictor.shared.api.SubmitPredictionRequest
import com.predictor.shared.api.TournamentSummary
import com.predictor.shared.model.Leaderboard
import com.predictor.shared.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The client's view of the backend, shared by every platform. Note what it does
 * *not* contain: no DTO definitions and no JSON field names. It calls the paths from
 * [ApiRoutes] and receives the exact model types from the shared module, so the
 * client and server are type-checked against one contract by the same compiler.
 *
 * The only per-platform detail is the default [baseUrl] — the browser reads its own
 * origin, the mobile apps point at the API host — supplied by [defaultBaseUrl].
 */
class ApiClient(
    private val baseUrl: String = defaultBaseUrl(),
) {
    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun url(path: String) = "$baseUrl$path"

    suspend fun tournaments(): List<TournamentSummary> =
        http.get(url(ApiRoutes.TOURNAMENTS)).body()

    suspend fun users(): List<User> =
        http.get(url(ApiRoutes.USERS)).body()

    suspend fun matchViews(tournamentId: String, userId: String): List<MatchView> =
        http.get(url(ApiRoutes.matches(tournamentId))) {
            parameter(ApiRoutes.USER_ID_PARAM, userId)
        }.body()

    suspend fun leaderboard(tournamentId: String): Leaderboard =
        http.get(url(ApiRoutes.leaderboard(tournamentId))).body()

    suspend fun submitPrediction(request: SubmitPredictionRequest): PredictionResponse =
        http.post(url(ApiRoutes.PREDICTIONS)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
