package com.predictor.shared.model

import com.predictor.shared.scoring.ScoringRules
import kotlinx.serialization.Serializable

/**
 * The set of sports the predictor understands.
 *
 * Adding a new sport is deliberately a one-line change: append an entry here with
 * the right vocabulary and scoring weights. Everything downstream — the API, the
 * scoring engine, and the Compose UI — is driven off these properties rather than
 * off hard-coded sport checks, so no `when (sport)` branches need to be touched.
 */
@Serializable
enum class Sport(
    val displayName: String,
    /** What a single side of a match is called, e.g. "Team" for soccer, "Player" for tennis. */
    val competitorNoun: String,
    /** The unit a score is counted in, e.g. "Goals" for soccer, "Sets" for tennis. */
    val scoreUnit: String,
    /** Whether a fixture can legitimately end level. Tennis cannot; soccer group games can. */
    val allowsDraw: Boolean,
    /** How predictions for this sport are rewarded. */
    val scoringRules: ScoringRules,
) {
    SOCCER(
        displayName = "Soccer",
        competitorNoun = "Team",
        scoreUnit = "Goals",
        allowsDraw = true,
        scoringRules = ScoringRules.SCORELINE,
    ),
    HANDBALL(
        displayName = "Handball",
        competitorNoun = "Team",
        scoreUnit = "Goals",
        allowsDraw = true,
        scoringRules = ScoringRules.SCORELINE,
    ),
    TENNIS(
        displayName = "Tennis",
        competitorNoun = "Player",
        scoreUnit = "Sets",
        allowsDraw = false,
        scoringRules = ScoringRules.WINNER_FOCUSED,
    ),
}
