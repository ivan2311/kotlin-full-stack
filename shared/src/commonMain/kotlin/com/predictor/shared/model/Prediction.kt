package com.predictor.shared.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * One user's call on how a single fixture will end. A user can hold at most one
 * prediction per match; re-submitting overwrites the previous call.
 */
@Serializable
data class Prediction(
    val id: String,
    val userId: String,
    val matchId: String,
    val predicted: MatchOutcome,
    val submittedAt: Instant,
)
