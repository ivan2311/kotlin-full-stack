package com.predictor.shared.model

import kotlinx.serialization.Serializable

/** One player's standing in a tournament's prediction contest. */
@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val totalPoints: Int,
    val exactHits: Int,
    val predictionsScored: Int,
)

/** A fully ranked table of players for a single tournament. */
@Serializable
data class Leaderboard(
    val tournamentId: String,
    val entries: List<LeaderboardEntry>,
)
