package com.predictor.shared.model

import kotlinx.serialization.Serializable

/**
 * A whole competition: its sport, its field of competitors and its fixture list.
 *
 * The same shape describes a 48-team soccer World Cup, an eight-player tennis
 * masters or a handball championship — the only thing that changes is [sport],
 * which in turn changes how the UI labels things and how predictions are scored.
 */
@Serializable
data class Tournament(
    val id: String,
    val sport: Sport,
    val name: String,
    val season: String,
    val competitors: List<Competitor>,
    val matches: List<Match>,
) {
    private val competitorsById: Map<String, Competitor> by lazy { competitors.associateBy { it.id } }

    fun competitor(id: String): Competitor =
        competitorsById[id] ?: error("Unknown competitor '$id' in tournament '$id'")

    fun match(id: String): Match? = matches.firstOrNull { it.id == id }

    /** Distinct stage names in fixture order, e.g. ["Group A", "Group B", "Final"]. */
    val stages: List<String> get() = matches.map { it.stage }.distinct()
}
