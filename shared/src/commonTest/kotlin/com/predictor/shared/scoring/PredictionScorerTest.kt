package com.predictor.shared.scoring

import com.predictor.shared.model.MatchOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class PredictionScorerTest {

    private val rules = ScoringRules.SCORELINE // exact=5, result+margin=3, result=2

    @Test
    fun exactScoreEarnsTopPoints() {
        val breakdown = PredictionScorer.score(
            prediction = MatchOutcome(2, 1),
            actual = MatchOutcome(2, 1),
            rules = rules,
        )
        assertEquals(5, breakdown.points)
        assertEquals(ScoreCategory.EXACT, breakdown.category)
    }

    @Test
    fun rightResultAndMarginButWrongScore() {
        // Predicted a one-goal home win, got a one-goal home win, different scoreline.
        val breakdown = PredictionScorer.score(MatchOutcome(2, 1), MatchOutcome(3, 2), rules)
        assertEquals(3, breakdown.points)
        assertEquals(ScoreCategory.RESULT_AND_MARGIN, breakdown.category)
    }

    @Test
    fun rightResultWrongMargin() {
        // Predicted a home win by one, got a home win by three.
        val breakdown = PredictionScorer.score(MatchOutcome(2, 1), MatchOutcome(3, 0), rules)
        assertEquals(2, breakdown.points)
        assertEquals(ScoreCategory.RESULT, breakdown.category)
    }

    @Test
    fun wrongResultEarnsNothing() {
        val breakdown = PredictionScorer.score(MatchOutcome(2, 1), MatchOutcome(0, 1), rules)
        assertEquals(0, breakdown.points)
        assertEquals(ScoreCategory.MISS, breakdown.category)
    }

    @Test
    fun predictedDrawWithDifferentScoreIsResultAndMargin() {
        // Both are draws (margin 0) but not the same scoreline -> result & margin tier.
        val breakdown = PredictionScorer.score(MatchOutcome(1, 1), MatchOutcome(2, 2), rules)
        assertEquals(3, breakdown.points)
        assertEquals(ScoreCategory.RESULT_AND_MARGIN, breakdown.category)
    }

    @Test
    fun winnerFocusedRulesRewardResultOverExactScore() {
        // Tennis-style rules: exact set score is worth less, but the tiers still hold.
        val tennis = ScoringRules.WINNER_FOCUSED // exact=4, result+margin=3, result=2
        assertEquals(4, PredictionScorer.score(MatchOutcome(2, 0), MatchOutcome(2, 0), tennis).points)
        assertEquals(2, PredictionScorer.score(MatchOutcome(2, 0), MatchOutcome(2, 1), tennis).points)
    }
}
