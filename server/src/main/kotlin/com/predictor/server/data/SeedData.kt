package com.predictor.server.data

import com.predictor.shared.model.Competitor
import com.predictor.shared.model.Match
import com.predictor.shared.model.MatchOutcome
import com.predictor.shared.model.MatchStatus
import com.predictor.shared.model.Prediction
import com.predictor.shared.model.Sport
import com.predictor.shared.model.Tournament
import com.predictor.shared.model.User
import kotlinx.datetime.Instant

/**
 * All of the demo content the app starts up with. This is deliberately hand-written
 * rather than pulled from a live sports feed so the project runs offline with a
 * single command — swapping this out for a real data source is a later exercise.
 */
data class SeedData(
    val tournaments: List<Tournament>,
    val users: List<User>,
    val predictions: List<Prediction>,
)

object Seed {

    val users = listOf(
        User("u-ada", "Ada"),
        User("u-linus", "Linus"),
        User("u-grace", "Grace"),
        User("u-you", "You"),
    )

    /** The identity the frontend acts as by default (no auth in this iteration). */
    const val DEFAULT_USER_ID = "u-you"

    fun build(): SeedData {
        val soccer = soccerWorldCup()
        val tennis = tennisMasters()
        val handball = handballChampionship()
        return SeedData(
            tournaments = listOf(soccer, tennis, handball),
            users = users,
            predictions = seededPredictions(soccer, tennis),
        )
    }

    // ---------------------------------------------------------------------------
    // Soccer — a trimmed-down two-group World Cup with some results already in.
    // ---------------------------------------------------------------------------
    private fun soccerWorldCup(): Tournament {
        val teams = listOf(
            Competitor("arg", "Argentina", "ARG", seed = 1),
            Competitor("mex", "Mexico", "MEX", seed = 12),
            Competitor("pol", "Poland", "POL", seed = 21),
            Competitor("aus", "Australia", "AUS", seed = 27),
            Competitor("fra", "France", "FRA", seed = 2),
            Competitor("den", "Denmark", "DEN", seed = 14),
            Competitor("sen", "Senegal", "SEN", seed = 18),
            Competitor("jpn", "Japan", "JPN", seed = 20),
        )

        var kickoff = Instant.parse("2026-06-11T18:00:00Z")
        fun next(): Instant {
            val current = kickoff
            kickoff = current.plusHours(3)
            return current
        }

        val matches = listOf(
            // Group A — round 1 played, round 2 upcoming.
            soccerMatch("sc-1", "Group A", "arg", "aus", next(), MatchOutcome(2, 0)),
            soccerMatch("sc-2", "Group A", "mex", "pol", next(), MatchOutcome(1, 1)),
            soccerMatch("sc-3", "Group A", "arg", "mex", next(), null),
            soccerMatch("sc-4", "Group A", "pol", "aus", next(), null),
            // Group B — round 1 played, round 2 upcoming.
            soccerMatch("sc-5", "Group B", "fra", "jpn", next(), MatchOutcome(3, 1)),
            soccerMatch("sc-6", "Group B", "den", "sen", next(), MatchOutcome(0, 1)),
            soccerMatch("sc-7", "Group B", "fra", "den", next(), null),
            soccerMatch("sc-8", "Group B", "sen", "jpn", next(), null),
        )

        return Tournament(
            id = "wc-2026",
            sport = Sport.SOCCER,
            name = "World Cup",
            season = "2026",
            competitors = teams,
            matches = matches,
        )
    }

    private fun soccerMatch(
        id: String,
        stage: String,
        home: String,
        away: String,
        kickoff: Instant,
        result: MatchOutcome?,
    ) = Match(
        id = id,
        tournamentId = "wc-2026",
        stage = stage,
        homeId = home,
        awayId = away,
        kickoff = kickoff,
        status = if (result != null) MatchStatus.FINISHED else MatchStatus.SCHEDULED,
        actualOutcome = result,
    )

    // ---------------------------------------------------------------------------
    // Tennis — an eight-player knockout, scores expressed in sets. No draws.
    // ---------------------------------------------------------------------------
    private fun tennisMasters(): Tournament {
        val players = listOf(
            Competitor("alcaraz", "C. Alcaraz", "ALC", seed = 1),
            Competitor("sinner", "J. Sinner", "SIN", seed = 2),
            Competitor("djokovic", "N. Djokovic", "DJO", seed = 3),
            Competitor("zverev", "A. Zverev", "ZVE", seed = 4),
        )

        var kickoff = Instant.parse("2026-05-24T12:00:00Z")
        fun next(): Instant {
            val current = kickoff
            kickoff = current.plusHours(4)
            return current
        }

        val matches = listOf(
            tennisMatch("tn-1", "Semi-final", "alcaraz", "zverev", next(), MatchOutcome(2, 1)),
            tennisMatch("tn-2", "Semi-final", "sinner", "djokovic", next(), null),
            tennisMatch("tn-3", "Final", "alcaraz", "sinner", next(), null),
        )

        return Tournament(
            id = "atp-2026",
            sport = Sport.TENNIS,
            name = "Masters Finals",
            season = "2026",
            competitors = players,
            matches = matches,
        )
    }

    private fun tennisMatch(
        id: String,
        stage: String,
        home: String,
        away: String,
        kickoff: Instant,
        result: MatchOutcome?,
    ) = Match(
        id = id,
        tournamentId = "atp-2026",
        stage = stage,
        homeId = home,
        awayId = away,
        kickoff = kickoff,
        status = if (result != null) MatchStatus.FINISHED else MatchStatus.SCHEDULED,
        actualOutcome = result,
    )

    // ---------------------------------------------------------------------------
    // Handball — same shape as soccer, proving the model is sport-agnostic.
    // ---------------------------------------------------------------------------
    private fun handballChampionship(): Tournament {
        val teams = listOf(
            Competitor("den-h", "Denmark", "DEN", seed = 1),
            Competitor("fra-h", "France", "FRA", seed = 2),
            Competitor("swe-h", "Sweden", "SWE", seed = 3),
            Competitor("esp-h", "Spain", "ESP", seed = 4),
        )

        var kickoff = Instant.parse("2026-01-15T17:00:00Z")
        fun next(): Instant {
            val current = kickoff
            kickoff = current.plusHours(2)
            return current
        }

        val matches = listOf(
            handballMatch("hb-1", "Main Round", "den-h", "swe-h", next(), MatchOutcome(31, 28)),
            handballMatch("hb-2", "Main Round", "fra-h", "esp-h", next(), null),
            handballMatch("hb-3", "Main Round", "den-h", "fra-h", next(), null),
        )

        return Tournament(
            id = "eh-2026",
            sport = Sport.HANDBALL,
            name = "European Championship",
            season = "2026",
            competitors = teams,
            matches = matches,
        )
    }

    private fun handballMatch(
        id: String,
        stage: String,
        home: String,
        away: String,
        kickoff: Instant,
        result: MatchOutcome?,
    ) = Match(
        id = id,
        tournamentId = "eh-2026",
        stage = stage,
        homeId = home,
        awayId = away,
        kickoff = kickoff,
        status = if (result != null) MatchStatus.FINISHED else MatchStatus.SCHEDULED,
        actualOutcome = result,
    )

    // ---------------------------------------------------------------------------
    // A handful of predictions on already-played matches so the leaderboard is
    // populated the moment the app opens.
    // ---------------------------------------------------------------------------
    private fun seededPredictions(soccer: Tournament, tennis: Tournament): List<Prediction> {
        val at = Instant.parse("2026-06-10T09:00:00Z")
        fun p(user: String, match: String, home: Int, away: Int) =
            Prediction("$user:$match", user, match, MatchOutcome(home, away), at)

        return listOf(
            // Soccer sc-1 finished 2-0, sc-2 finished 1-1, sc-5 finished 3-1, sc-6 finished 0-1.
            p("u-ada", "sc-1", 2, 0),   // exact
            p("u-ada", "sc-2", 1, 1),   // exact
            p("u-ada", "sc-5", 2, 0),   // right result, wrong margin
            p("u-ada", "sc-6", 0, 1),   // exact

            p("u-linus", "sc-1", 1, 0), // right result & margin? 1-0 margin 1 vs 2-0 margin 2 -> result only
            p("u-linus", "sc-2", 0, 0), // draw predicted, draw happened, diff score -> result & margin
            p("u-linus", "sc-5", 3, 1), // exact
            p("u-linus", "sc-6", 2, 0), // wrong result -> miss

            p("u-grace", "sc-1", 3, 2), // right result & margin? margin 1 vs 2 -> result only
            p("u-grace", "sc-5", 4, 0), // right result, wrong margin
            p("u-grace", "sc-6", 1, 2), // right result (away win), margin -1 vs -1 -> result & margin

            // Tennis tn-1 finished 2-1.
            p("u-ada", "tn-1", 2, 1),   // exact
            p("u-linus", "tn-1", 2, 0), // right result, wrong set count
        )
    }
}

private fun Instant.plusHours(hours: Int): Instant =
    Instant.fromEpochSeconds(epochSeconds + hours * 3600L)
