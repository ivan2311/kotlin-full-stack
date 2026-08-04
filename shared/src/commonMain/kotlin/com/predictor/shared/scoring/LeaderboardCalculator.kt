package com.predictor.shared.scoring

import com.predictor.shared.model.Leaderboard
import com.predictor.shared.model.LeaderboardEntry
import com.predictor.shared.model.Prediction
import com.predictor.shared.model.Tournament
import com.predictor.shared.model.User

/**
 * Rolls every scored prediction up into a ranked leaderboard for a tournament.
 *
 * Like [PredictionScorer], this is pure, dependency-free logic in the shared module,
 * so the exact same ranking runs on the backend and could be reused verbatim in the
 * client if we ever wanted an optimistic, offline-first standings view.
 */
object LeaderboardCalculator {

    fun build(
        tournament: Tournament,
        users: List<User>,
        predictions: List<Prediction>,
    ): Leaderboard {
        val rules = tournament.sport.scoringRules
        val matchesById = tournament.matches.associateBy { it.id }
        val predictionsByUser = predictions.groupBy { it.userId }

        val rawEntries = users.map { user ->
            var points = 0
            var exactHits = 0
            var scored = 0

            for (prediction in predictionsByUser[user.id].orEmpty()) {
                val actual = matchesById[prediction.matchId]?.actualOutcome ?: continue
                val breakdown = PredictionScorer.score(prediction.predicted, actual, rules)
                points += breakdown.points
                scored++
                if (breakdown.category == ScoreCategory.EXACT) exactHits++
            }

            LeaderboardEntry(
                rank = 0, // assigned after sorting
                userId = user.id,
                displayName = user.displayName,
                totalPoints = points,
                exactHits = exactHits,
                predictionsScored = scored,
            )
        }

        // Sort by points, breaking ties with the number of exact hits, then name.
        val ranked = rawEntries
            .sortedWith(
                compareByDescending<LeaderboardEntry> { it.totalPoints }
                    .thenByDescending { it.exactHits }
                    .thenBy { it.displayName },
            )
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

        return Leaderboard(tournamentId = tournament.id, entries = ranked)
    }
}
