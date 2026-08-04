package com.predictor.shared.scoring

import com.predictor.shared.model.Competitor
import com.predictor.shared.model.Match
import com.predictor.shared.model.MatchOutcome
import com.predictor.shared.model.MatchStatus
import com.predictor.shared.model.Prediction
import com.predictor.shared.model.Sport
import com.predictor.shared.model.Tournament
import com.predictor.shared.model.User
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class LeaderboardCalculatorTest {

    private val t0 = Instant.parse("2026-06-11T18:00:00Z")

    private fun finishedMatch(id: String, outcome: MatchOutcome) = Match(
        id = id,
        tournamentId = "wc",
        stage = "Group A",
        homeId = "a",
        awayId = "b",
        kickoff = t0,
        status = MatchStatus.FINISHED,
        actualOutcome = outcome,
    )

    private val tournament = Tournament(
        id = "wc",
        sport = Sport.SOCCER,
        name = "Test Cup",
        season = "2026",
        competitors = listOf(Competitor("a", "Alpha", "ALP"), Competitor("b", "Beta", "BET")),
        matches = listOf(
            finishedMatch("m1", MatchOutcome(2, 1)),
            finishedMatch("m2", MatchOutcome(0, 0)),
        ),
    )

    private fun prediction(user: String, match: String, outcome: MatchOutcome) =
        Prediction("$user-$match", user, match, outcome, t0)

    @Test
    fun ranksUsersByPointsThenExactHits() {
        val ada = User("ada", "Ada")
        val bob = User("bob", "Bob")

        val predictions = listOf(
            // Ada: exact on m1 (5) + exact on m2 (5) = 10, two exact hits.
            prediction("ada", "m1", MatchOutcome(2, 1)),
            prediction("ada", "m2", MatchOutcome(0, 0)),
            // Bob: result+margin on m1 (3) + result on m2 draw is exact here... use a miss.
            prediction("bob", "m1", MatchOutcome(3, 2)), // right result & margin -> 3
            prediction("bob", "m2", MatchOutcome(1, 0)), // predicted home win, was draw -> 0
        )

        val board = LeaderboardCalculator.build(tournament, listOf(bob, ada), predictions)

        assertEquals(2, board.entries.size)
        assertEquals("ada", board.entries[0].userId)
        assertEquals(1, board.entries[0].rank)
        assertEquals(10, board.entries[0].totalPoints)
        assertEquals(2, board.entries[0].exactHits)

        assertEquals("bob", board.entries[1].userId)
        assertEquals(2, board.entries[1].rank)
        assertEquals(3, board.entries[1].totalPoints)
    }

    @Test
    fun usersWithoutPredictionsScoreZero() {
        val ghost = User("ghost", "Ghost")
        val board = LeaderboardCalculator.build(tournament, listOf(ghost), emptyList())
        assertEquals(0, board.entries.single().totalPoints)
        assertEquals(0, board.entries.single().predictionsScored)
    }
}
