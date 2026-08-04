package com.predictor.shared.api

import com.predictor.shared.model.Competitor
import com.predictor.shared.model.Match
import com.predictor.shared.model.MatchOutcome
import com.predictor.shared.model.Prediction
import com.predictor.shared.model.Sport
import com.predictor.shared.scoring.ScoreBreakdown
import kotlinx.serialization.Serializable

/**
 * The REST surface, defined once and shared. The server implements exactly these
 * paths; the client calls exactly these paths. There is no separate, hand-copied
 * API spec that can drift out of sync — the compiler is the contract test.
 */
object ApiRoutes {
    const val TOURNAMENTS = "/api/tournaments"
    fun tournament(id: String) = "$TOURNAMENTS/$id"
    fun matches(tournamentId: String) = "$TOURNAMENTS/$tournamentId/matches"
    fun leaderboard(tournamentId: String) = "$TOURNAMENTS/$tournamentId/leaderboard"

    const val PREDICTIONS = "/api/predictions"
    /** All of one user's predictions in a tournament: /api/predictions?userId=..&tournamentId=.. */
    const val USER_ID_PARAM = "userId"
    const val TOURNAMENT_ID_PARAM = "tournamentId"

    const val USERS = "/api/users"
}

/** Lightweight tournament descriptor for the picker screen. */
@Serializable
data class TournamentSummary(
    val id: String,
    val sport: Sport,
    val name: String,
    val season: String,
    val competitorCount: Int,
    val matchCount: Int,
)

/**
 * A match enriched with everything the UI needs to render one fixture row: the two
 * resolved competitors, the current user's prediction (if any) and the points it has
 * earned (once the match is scored).
 */
@Serializable
data class MatchView(
    val match: Match,
    val home: Competitor,
    val away: Competitor,
    val prediction: MatchOutcome? = null,
    val earned: ScoreBreakdown? = null,
)

/** Request body for placing or updating a prediction. */
@Serializable
data class SubmitPredictionRequest(
    val userId: String,
    val matchId: String,
    val outcome: MatchOutcome,
)

/** Standard response after a prediction is accepted. */
@Serializable
data class PredictionResponse(
    val prediction: Prediction,
)

/** Uniform error envelope so the client can show a message instead of a raw 500. */
@Serializable
data class ApiError(
    val message: String,
)
