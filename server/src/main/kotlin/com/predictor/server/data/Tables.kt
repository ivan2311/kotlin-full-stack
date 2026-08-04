package com.predictor.server.data

import com.predictor.shared.model.MatchStatus
import com.predictor.shared.model.Sport
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * The relational schema behind [PredictionStore], expressed with Exposed's SQL DSL.
 *
 * The tables mirror the shared domain model one-to-one so the store can rebuild the
 * exact same `Tournament`/`Match`/`Prediction` objects the rest of the app already
 * speaks. Enums are stored by name (so a new [Sport] entry needs no migration), and a
 * [com.predictor.shared.model.MatchOutcome] is flattened into a pair of integer
 * columns — nullable for a match's actual result (unknown until it is played),
 * non-null for a prediction (a user always commits to concrete scores).
 */
object Users : Table("users") {
    val id = varchar("id", 64)
    val displayName = varchar("display_name", 128)
    override val primaryKey = PrimaryKey(id)
}

object Tournaments : Table("tournaments") {
    val id = varchar("id", 64)
    val sport = enumerationByName("sport", 32, Sport::class)
    val name = varchar("name", 128)
    val season = varchar("season", 32)
    override val primaryKey = PrimaryKey(id)
}

/** Competitor ids are only unique within their tournament, hence the composite key. */
object Competitors : Table("competitors") {
    val tournamentId = varchar("tournament_id", 64).references(Tournaments.id)
    val competitorId = varchar("competitor_id", 64)
    val name = varchar("name", 128)
    val shortCode = varchar("short_code", 16)
    val seed = integer("seed").nullable()
    override val primaryKey = PrimaryKey(tournamentId, competitorId)
}

object Matches : Table("matches") {
    val id = varchar("id", 64)
    val tournamentId = varchar("tournament_id", 64).references(Tournaments.id)
    val stage = varchar("stage", 64)
    val homeId = varchar("home_id", 64)
    val awayId = varchar("away_id", 64)
    val kickoff = timestamp("kickoff")
    val status = enumerationByName("status", 16, MatchStatus::class)
    val actualHome = integer("actual_home").nullable()
    val actualAway = integer("actual_away").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * At most one prediction per user per match. The natural id is "userId:matchId", so
 * the primary key already enforces that invariant — a re-submission upserts the row.
 */
object Predictions : Table("predictions") {
    val id = varchar("id", 160)
    val userId = varchar("user_id", 64).references(Users.id)
    val matchId = varchar("match_id", 64).references(Matches.id)
    val predictedHome = integer("predicted_home")
    val predictedAway = integer("predicted_away")
    val submittedAt = timestamp("submitted_at")
    override val primaryKey = PrimaryKey(id)
}
