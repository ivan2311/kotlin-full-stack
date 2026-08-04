package com.predictor.shared.model

import kotlinx.serialization.Serializable

/**
 * One side of a fixture. Depending on the sport this is a national team (soccer,
 * handball) or an individual player (tennis) — the model is intentionally the same
 * so the rest of the system never has to care which it is looking at.
 */
@Serializable
data class Competitor(
    val id: String,
    val name: String,
    /** Short display code, e.g. "ARG", "FRA", or a player's surname. */
    val shortCode: String,
    /** Optional tournament seeding / world ranking, lower is stronger. */
    val seed: Int? = null,
)
