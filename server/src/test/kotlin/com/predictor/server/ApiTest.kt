package com.predictor.server

import com.predictor.server.plugins.configureMonitoring
import com.predictor.server.plugins.configureRouting
import com.predictor.server.plugins.configureSerialization
import com.predictor.server.data.DatabaseConfig
import com.predictor.server.data.DatabaseFactory
import com.predictor.server.data.PredictionStore
import com.predictor.shared.api.ApiRoutes
import com.predictor.shared.api.MatchView
import com.predictor.shared.api.PredictionResponse
import com.predictor.shared.api.SubmitPredictionRequest
import com.predictor.shared.api.TournamentSummary
import com.predictor.shared.model.Leaderboard
import com.predictor.shared.model.MatchOutcome
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest {

    /** Each test gets its own throwaway in-memory database, freshly seeded and isolated. */
    private var dbCounter = 0

    private fun testApp(block: suspend (client: io.ktor.client.HttpClient) -> Unit) = testApplication {
        val database = DatabaseFactory.connect(DatabaseConfig.inMemory("api-test-${dbCounter++}"))
        application {
            val store = PredictionStore(database)
            configureSerialization()
            configureMonitoring()
            configureRouting(store)
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        block(client)
    }

    @Test
    fun listsSeededTournaments() = testApp { client ->
        val tournaments: List<TournamentSummary> = client.get(ApiRoutes.TOURNAMENTS).body()
        assertEquals(3, tournaments.size)
        assertTrue(tournaments.any { it.name == "World Cup" })
    }

    @Test
    fun matchViewsCarryScoredPoints() = testApp { client ->
        val views: List<MatchView> = client.get(ApiRoutes.matches("wc-2026")) {
            url { parameters.append(ApiRoutes.USER_ID_PARAM, "u-ada") }
        }.body()
        // Ada predicted sc-1 exactly (2-0), so that row should show earned points.
        val sc1 = views.first { it.match.id == "sc-1" }
        assertEquals(5, sc1.earned?.points)
    }

    @Test
    fun leaderboardIsRanked() = testApp { client ->
        val board: Leaderboard = client.get(ApiRoutes.leaderboard("wc-2026")).body()
        assertTrue(board.entries.isNotEmpty())
        assertEquals(1, board.entries.first().rank)
        // Entries must be sorted by descending points.
        val points = board.entries.map { it.totalPoints }
        assertEquals(points.sortedDescending(), points)
    }

    @Test
    fun submittingAPredictionPersistsIt() = testApp { client ->
        val response = client.post(ApiRoutes.PREDICTIONS) {
            contentType(ContentType.Application.Json)
            setBody(SubmitPredictionRequest("u-you", "sc-3", MatchOutcome(2, 1)))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val saved: PredictionResponse = response.body()
        assertEquals(MatchOutcome(2, 1), saved.prediction.predicted)
    }

    @Test
    fun tennisRejectsDraws() = testApp { client ->
        val response = client.post(ApiRoutes.PREDICTIONS) {
            contentType(ContentType.Application.Json)
            setBody(SubmitPredictionRequest("u-you", "tn-3", MatchOutcome(1, 1)))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }
}
