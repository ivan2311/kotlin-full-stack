package com.predictor.server.data

import com.predictor.shared.api.MatchView
import com.predictor.shared.api.TournamentSummary
import com.predictor.shared.model.Leaderboard
import com.predictor.shared.model.MatchOutcome
import com.predictor.shared.model.Prediction
import com.predictor.shared.model.Tournament
import com.predictor.shared.model.User
import com.predictor.shared.scoring.LeaderboardCalculator
import com.predictor.shared.scoring.PredictionScorer
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * The application's entire data layer, kept in memory.
 *
 * It is deliberately small and swappable: every route talks to this class through
 * plain methods, so replacing the maps with Exposed + a real database later would
 * not ripple past this file. Reads are lock-free; the only mutation (placing a
 * prediction) is synchronised to keep the "one prediction per user per match"
 * invariant honest under concurrent requests.
 */
class PredictionStore(seed: SeedData = Seed.build()) {

    private val tournaments: Map<String, Tournament> = seed.tournaments.associateBy { it.id }
    private val users: Map<String, User> = seed.users.associateBy { it.id }

    /** Key: "userId:matchId" -> prediction. */
    private val predictions = ConcurrentHashMap<String, Prediction>().apply {
        seed.predictions.forEach { put(key(it.userId, it.matchId), it) }
    }

    private fun key(userId: String, matchId: String) = "$userId:$matchId"

    fun allUsers(): List<User> = users.values.sortedBy { it.displayName }

    fun user(id: String): User? = users[id]

    fun tournamentSummaries(): List<TournamentSummary> =
        tournaments.values
            .sortedBy { it.name }
            .map {
                TournamentSummary(
                    id = it.id,
                    sport = it.sport,
                    name = it.name,
                    season = it.season,
                    competitorCount = it.competitors.size,
                    matchCount = it.matches.size,
                )
            }

    fun tournament(id: String): Tournament? = tournaments[id]

    /**
     * The fixture list for a tournament, enriched for [userId]: each row carries the
     * resolved competitors, that user's current prediction, and — once the match is
     * finished — the points it earned. All the scoring reuses the shared engine.
     */
    fun matchViews(tournamentId: String, userId: String): List<MatchView>? {
        val tournament = tournaments[tournamentId] ?: return null
        val rules = tournament.sport.scoringRules
        return tournament.matches.map { match ->
            val prediction = predictions[key(userId, match.id)]?.predicted
            val earned = if (match.isScored && prediction != null) {
                PredictionScorer.score(prediction, match.actualOutcome!!, rules)
            } else {
                null
            }
            MatchView(
                match = match,
                home = tournament.competitor(match.homeId),
                away = tournament.competitor(match.awayId),
                prediction = prediction,
                earned = earned,
            )
        }
    }

    fun leaderboard(tournamentId: String): Leaderboard? {
        val tournament = tournaments[tournamentId] ?: return null
        return LeaderboardCalculator.build(
            tournament = tournament,
            users = users.values.toList(),
            predictions = predictions.values.toList(),
        )
    }

    fun predictionsFor(userId: String, tournamentId: String): List<Prediction> {
        val matchIds = tournaments[tournamentId]?.matches?.mapTo(HashSet()) { it.id } ?: return emptyList()
        return predictions.values.filter { it.userId == userId && it.matchId in matchIds }
    }

    /**
     * Places or replaces a prediction. Returns a typed [SubmitResult] rather than
     * throwing so the route can map failure cleanly to an HTTP status.
     */
    fun submitPrediction(userId: String, matchId: String, outcome: MatchOutcome): SubmitResult {
        if (users[userId] == null) return SubmitResult.UnknownUser

        val tournament = tournaments.values.firstOrNull { t -> t.matches.any { it.id == matchId } }
            ?: return SubmitResult.UnknownMatch
        val match = tournament.match(matchId) ?: return SubmitResult.UnknownMatch

        if (!match.acceptsPredictions) return SubmitResult.MatchClosed
        if (!tournament.sport.allowsDraw && outcome.home == outcome.away) {
            return SubmitResult.DrawNotAllowed(tournament.sport.displayName)
        }

        val prediction = Prediction(
            id = key(userId, matchId),
            userId = userId,
            matchId = matchId,
            predicted = outcome,
            submittedAt = Clock.System.now(),
        )
        predictions[key(userId, matchId)] = prediction
        return SubmitResult.Ok(prediction)
    }

    sealed interface SubmitResult {
        data class Ok(val prediction: Prediction) : SubmitResult
        data object UnknownUser : SubmitResult
        data object UnknownMatch : SubmitResult
        data object MatchClosed : SubmitResult
        data class DrawNotAllowed(val sport: String) : SubmitResult
    }
}
