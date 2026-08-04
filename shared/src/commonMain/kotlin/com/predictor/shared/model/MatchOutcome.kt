package com.predictor.shared.model

import kotlinx.serialization.Serializable

/** Which side came out on top of a fixture. */
@Serializable
enum class MatchResult { HOME, AWAY, DRAW }

/**
 * A concrete result of a fixture, expressed as a pair of scores.
 *
 * The same type is used for both what actually happened and what a user predicts
 * would happen — comparing the two is the entire job of the scoring engine. The
 * meaning of the numbers depends on the sport ([Sport.scoreUnit]): goals for soccer
 * and handball, sets for tennis.
 */
@Serializable
data class MatchOutcome(val home: Int, val away: Int) {
    init {
        require(home >= 0 && away >= 0) { "Scores cannot be negative (got $home-$away)" }
    }

    /** Who won, derived purely from the scores. */
    val result: MatchResult
        get() = when {
            home > away -> MatchResult.HOME
            away > home -> MatchResult.AWAY
            else -> MatchResult.DRAW
        }

    /** Home score minus away score — the winning margin (negative if away won). */
    val margin: Int get() = home - away

    override fun toString(): String = "$home-$away"
}
