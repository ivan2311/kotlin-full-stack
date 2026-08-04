package com.predictor.server.data

import com.predictor.shared.api.MatchView
import com.predictor.shared.api.TournamentSummary
import com.predictor.shared.model.Competitor
import com.predictor.shared.model.Leaderboard
import com.predictor.shared.model.Match
import com.predictor.shared.model.MatchOutcome
import com.predictor.shared.model.MatchStatus
import com.predictor.shared.model.Prediction
import com.predictor.shared.model.Tournament
import com.predictor.shared.model.User
import com.predictor.shared.scoring.LeaderboardCalculator
import com.predictor.shared.scoring.PredictionScorer
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

/**
 * The application's entire data layer, now backed by a real SQL database through
 * Exposed (see [Tables] and [DatabaseFactory]).
 *
 * It is deliberately small and swappable: every route talks to this class through the
 * same plain methods it always has, so moving off the old in-memory maps did not
 * ripple past this file. Each method runs in its own transaction; the "one prediction
 * per user per match" invariant is enforced by the primary key, so a re-submission is
 * a single atomic upsert rather than something we have to lock around by hand.
 *
 * On construction the schema is created if missing and, when the database is empty,
 * populated from [seed] — so a fresh checkout still starts with demo content, while a
 * database that already holds data (e.g. predictions from a previous run) is left as is.
 */
class PredictionStore(
    private val database: Database,
    seed: SeedData = Seed.build(),
) {

    init {
        transaction(database) {
            SchemaUtils.create(Users, Tournaments, Competitors, Matches, Predictions)
            if (Users.selectAll().empty()) seedInto(seed)
        }
    }

    private fun key(userId: String, matchId: String) = "$userId:$matchId"

    fun allUsers(): List<User> = transaction(database) {
        Users.selectAll().orderBy(Users.displayName).map { it.toUser() }
    }

    fun user(id: String): User? = transaction(database) {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()
    }

    fun tournamentSummaries(): List<TournamentSummary> = transaction(database) {
        Tournaments.selectAll().orderBy(Tournaments.name).map { row ->
            val id = row[Tournaments.id]
            TournamentSummary(
                id = id,
                sport = row[Tournaments.sport],
                name = row[Tournaments.name],
                season = row[Tournaments.season],
                competitorCount = Competitors.selectAll()
                    .where { Competitors.tournamentId eq id }.count().toInt(),
                matchCount = Matches.selectAll()
                    .where { Matches.tournamentId eq id }.count().toInt(),
            )
        }
    }

    fun tournament(id: String): Tournament? = transaction(database) { loadTournament(id) }

    /**
     * The fixture list for a tournament, enriched for [userId]: each row carries the
     * resolved competitors, that user's current prediction, and — once the match is
     * finished — the points it earned. All the scoring reuses the shared engine.
     */
    fun matchViews(tournamentId: String, userId: String): List<MatchView>? = transaction(database) {
        val tournament = loadTournament(tournamentId) ?: return@transaction null
        val rules = tournament.sport.scoringRules
        val matchIds = tournament.matches.map { it.id }
        val predictions = Predictions.selectAll()
            .where { (Predictions.userId eq userId) and (Predictions.matchId inList matchIds) }
            .associate { it[Predictions.matchId] to it.toOutcome() }

        tournament.matches.map { match ->
            val prediction = predictions[match.id]
            val earned = if (match.isScored && prediction != null) {
                PredictionScorer.score(prediction, match.actualOutcome!!, rules)
            } else {
                null
            }
            MatchView(
                match = match,
                home = tournament.competitor(match.homeId),
                away = tournament.competitor(match.awayId),
                prediction = prediction,
                earned = earned,
            )
        }
    }

    fun leaderboard(tournamentId: String): Leaderboard? = transaction(database) {
        val tournament = loadTournament(tournamentId) ?: return@transaction null
        val matchIds = tournament.matches.map { it.id }
        LeaderboardCalculator.build(
            tournament = tournament,
            users = Users.selectAll().map { it.toUser() },
            predictions = Predictions.selectAll()
                .where { Predictions.matchId inList matchIds }
                .map { it.toPrediction() },
        )
    }

    fun predictionsFor(userId: String, tournamentId: String): List<Prediction> = transaction(database) {
        val matchIds = Matches.selectAll()
            .where { Matches.tournamentId eq tournamentId }
            .mapTo(HashSet()) { it[Matches.id] }
        if (matchIds.isEmpty()) return@transaction emptyList()
        Predictions.selectAll()
            .where { (Predictions.userId eq userId) and (Predictions.matchId inList matchIds) }
            .map { it.toPrediction() }
    }

    /**
     * Places or replaces a prediction. Returns a typed [SubmitResult] rather than
     * throwing so the route can map failure cleanly to an HTTP status.
     */
    fun submitPrediction(userId: String, matchId: String, outcome: MatchOutcome): SubmitResult =
        transaction(database) {
            if (Users.selectAll().where { Users.id eq userId }.empty()) {
                return@transaction SubmitResult.UnknownUser
            }

            val matchRow = Matches.selectAll().where { Matches.id eq matchId }.singleOrNull()
                ?: return@transaction SubmitResult.UnknownMatch

            if (matchRow[Matches.status] != MatchStatus.SCHEDULED) {
                return@transaction SubmitResult.MatchClosed
            }

            val sport = Tournaments.selectAll()
                .where { Tournaments.id eq matchRow[Matches.tournamentId] }
                .single()[Tournaments.sport]
            if (!sport.allowsDraw && outcome.home == outcome.away) {
                return@transaction SubmitResult.DrawNotAllowed(sport.displayName)
            }

            val id = key(userId, matchId)
            val submittedAt = Clock.System.now()
            Predictions.upsert {
                it[Predictions.id] = id
                it[Predictions.userId] = userId
                it[Predictions.matchId] = matchId
                it[predictedHome] = outcome.home
                it[predictedAway] = outcome.away
                it[Predictions.submittedAt] = submittedAt
            }
            SubmitResult.Ok(Prediction(id, userId, matchId, outcome, submittedAt))
        }

    sealed interface SubmitResult {
        data class Ok(val prediction: Prediction) : SubmitResult
        data object UnknownUser : SubmitResult
        data object UnknownMatch : SubmitResult
        data object MatchClosed : SubmitResult
        data class DrawNotAllowed(val sport: String) : SubmitResult
    }

    // -- Loading & mapping -------------------------------------------------------

    /** Rebuilds a full [Tournament] (with its competitors and fixtures) from the DB. */
    private fun loadTournament(id: String): Tournament? {
        val row = Tournaments.selectAll().where { Tournaments.id eq id }.singleOrNull() ?: return null
        val competitors = Competitors.selectAll()
            .where { Competitors.tournamentId eq id }
            .map { it.toCompetitor() }
        val matches = Matches.selectAll()
            .where { Matches.tournamentId eq id }
            .orderBy(Matches.kickoff to SortOrder.ASC)
            .map { it.toMatch() }
        return Tournament(
            id = id,
            sport = row[Tournaments.sport],
            name = row[Tournaments.name],
            season = row[Tournaments.season],
            competitors = competitors,
            matches = matches,
        )
    }

    private fun ResultRow.toUser() = User(this[Users.id], this[Users.displayName])

    private fun ResultRow.toCompetitor() = Competitor(
        id = this[Competitors.competitorId],
        name = this[Competitors.name],
        shortCode = this[Competitors.shortCode],
        seed = this[Competitors.seed],
    )

    private fun ResultRow.toMatch(): Match {
        val home = this[Matches.actualHome]
        val away = this[Matches.actualAway]
        return Match(
            id = this[Matches.id],
            tournamentId = this[Matches.tournamentId],
            stage = this[Matches.stage],
            homeId = this[Matches.homeId],
            awayId = this[Matches.awayId],
            kickoff = this[Matches.kickoff],
            status = this[Matches.status],
            actualOutcome = if (home != null && away != null) MatchOutcome(home, away) else null,
        )
    }

    private fun ResultRow.toOutcome() =
        MatchOutcome(this[Predictions.predictedHome], this[Predictions.predictedAway])

    private fun ResultRow.toPrediction() = Prediction(
        id = this[Predictions.id],
        userId = this[Predictions.userId],
        matchId = this[Predictions.matchId],
        predicted = toOutcome(),
        submittedAt = this[Predictions.submittedAt],
    )

    // -- Seeding -----------------------------------------------------------------

    private fun seedInto(data: SeedData) {
        Users.batchInsert(data.users) { user ->
            this[Users.id] = user.id
            this[Users.displayName] = user.displayName
        }
        data.tournaments.forEach { tournament ->
            Tournaments.insert {
                it[id] = tournament.id
                it[sport] = tournament.sport
                it[name] = tournament.name
                it[season] = tournament.season
            }
            Competitors.batchInsert(tournament.competitors) { competitor ->
                this[Competitors.tournamentId] = tournament.id
                this[Competitors.competitorId] = competitor.id
                this[Competitors.name] = competitor.name
                this[Competitors.shortCode] = competitor.shortCode
                this[Competitors.seed] = competitor.seed
            }
            Matches.batchInsert(tournament.matches) { match ->
                this[Matches.id] = match.id
                this[Matches.tournamentId] = match.tournamentId
                this[Matches.stage] = match.stage
                this[Matches.homeId] = match.homeId
                this[Matches.awayId] = match.awayId
                this[Matches.kickoff] = match.kickoff
                this[Matches.status] = match.status
                this[Matches.actualHome] = match.actualOutcome?.home
                this[Matches.actualAway] = match.actualOutcome?.away
            }
        }
        Predictions.batchInsert(data.predictions) { prediction ->
            this[Predictions.id] = prediction.id
            this[Predictions.userId] = prediction.userId
            this[Predictions.matchId] = prediction.matchId
            this[Predictions.predictedHome] = prediction.predicted.home
            this[Predictions.predictedAway] = prediction.predicted.away
            this[Predictions.submittedAt] = prediction.submittedAt
        }
    }
}
