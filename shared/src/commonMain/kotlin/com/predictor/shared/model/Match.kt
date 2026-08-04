package com.predictor.shared.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Lifecycle of a fixture. Predictions are only accepted while [SCHEDULED]. */
@Serializable
enum class MatchStatus { SCHEDULED, LIVE, FINISHED }

/**
 * A single fixture within a tournament.
 *
 * [homeId] / [awayId] reference [Competitor.id]s rather than embedding the whole
 * competitor, keeping the tournament graph normalised. [actualOutcome] is `null`
 * until the match is played, at which point predictions can be scored against it.
 */
@Serializable
data class Match(
    val id: String,
    val tournamentId: String,
    /** Human-readable phase, e.g. "Group C", "Quarter-final", "Final". */
    val stage: String,
    val homeId: String,
    val awayId: String,
    val kickoff: Instant,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val actualOutcome: MatchOutcome? = null,
) {
    /** True once the fixture has a final result to score predictions against. */
    val isScored: Boolean get() = status == MatchStatus.FINISHED && actualOutcome != null

    /** Whether new predictions may still be submitted for this fixture. */
    val acceptsPredictions: Boolean get() = status == MatchStatus.SCHEDULED
}
