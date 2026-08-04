package com.predictor.shared.scoring

import kotlinx.serialization.Serializable

/**
 * The points a correct prediction is worth, in decreasing order of precision.
 *
 * These weights are the only thing that differs between sports' scoring, which is
 * why they live in data rather than in code. A sport picks one of the presets (or
 * defines its own) and the [PredictionScorer] does the rest.
 */
@Serializable
data class ScoringRules(
    /** Nailed the exact scoreline, e.g. predicted 2-1 and it finished 2-1. */
    val exactScore: Int,
    /** Right winner (or draw) *and* right margin, but wrong scoreline, e.g. 2-1 vs 3-2. */
    val correctResultAndMargin: Int,
    /** Right winner (or draw) only, e.g. predicted a home win and the home side won. */
    val correctResult: Int,
) {
    init {
        require(exactScore >= correctResultAndMargin && correctResultAndMargin >= correctResult) {
            "More precise predictions must never be worth fewer points than looser ones"
        }
        require(correctResult >= 0) { "Points cannot be negative" }
    }

    companion object {
        /** Score-heavy sports (soccer, handball) where the exact scoreline is prized. */
        val SCORELINE = ScoringRules(exactScore = 5, correctResultAndMargin = 3, correctResult = 2)

        /**
         * Winner-heavy sports (tennis) where simply picking the right player matters
         * most and the exact set count is a smaller bonus.
         */
        val WINNER_FOCUSED = ScoringRules(exactScore = 4, correctResultAndMargin = 3, correctResult = 2)
    }
}
