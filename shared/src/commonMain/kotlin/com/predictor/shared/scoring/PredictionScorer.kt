package com.predictor.shared.scoring

import com.predictor.shared.model.MatchOutcome
import kotlinx.serialization.Serializable

/** The tier a prediction fell into, from best to worst. */
@Serializable
enum class ScoreCategory(val label: String) {
    EXACT("Exact score"),
    RESULT_AND_MARGIN("Right result & margin"),
    RESULT("Right result"),
    MISS("Missed"),
}

/** The outcome of scoring one prediction: how many points and why. */
@Serializable
data class ScoreBreakdown(
    val points: Int,
    val category: ScoreCategory,
)

/**
 * The single source of truth for turning a prediction plus an actual result into
 * points. Because it lives in the shared module it runs unchanged in two places:
 *
 *  - on the **server**, as the authority that computes the real leaderboard, and
 *  - in the **browser**, so the UI can show "you'd earn 3 points" live as the user
 *    types, with zero risk of the two disagreeing.
 *
 * That "write the rule once, run it everywhere" property is the whole point of the
 * shared-codebase approach.
 */
object PredictionScorer {

    fun score(prediction: MatchOutcome, actual: MatchOutcome, rules: ScoringRules): ScoreBreakdown =
        when {
            prediction == actual ->
                ScoreBreakdown(rules.exactScore, ScoreCategory.EXACT)

            prediction.result == actual.result && prediction.margin == actual.margin ->
                ScoreBreakdown(rules.correctResultAndMargin, ScoreCategory.RESULT_AND_MARGIN)

            prediction.result == actual.result ->
                ScoreBreakdown(rules.correctResult, ScoreCategory.RESULT)

            else ->
                ScoreBreakdown(0, ScoreCategory.MISS)
        }
}
