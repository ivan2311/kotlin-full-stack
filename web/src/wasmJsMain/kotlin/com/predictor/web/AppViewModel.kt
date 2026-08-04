package com.predictor.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.predictor.shared.api.MatchView
import com.predictor.shared.api.SubmitPredictionRequest
import com.predictor.shared.api.TournamentSummary
import com.predictor.shared.model.Leaderboard
import com.predictor.shared.model.MatchOutcome
import com.predictor.shared.model.User
import com.predictor.web.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class DetailTab { FIXTURES, LEADERBOARD }

/**
 * All UI state for the app plus the actions that mutate it, kept out of the
 * composables. Compose's snapshot state (`mutableStateOf`) makes every property here
 * observable: assign to it and any composable that read it recomposes.
 */
class AppViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var users by mutableStateOf<List<User>>(emptyList())
        private set
    var currentUserId by mutableStateOf(DEFAULT_USER_ID)
        private set
    var tournaments by mutableStateOf<List<TournamentSummary>>(emptyList())
        private set
    var selectedTournamentId by mutableStateOf<String?>(null)
        private set
    var matchViews by mutableStateOf<List<MatchView>>(emptyList())
        private set
    var leaderboard by mutableStateOf<Leaderboard?>(null)
        private set
    var tab by mutableStateOf(DetailTab.FIXTURES)
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val selectedTournament: TournamentSummary?
        get() = tournaments.firstOrNull { it.id == selectedTournamentId }

    val currentUser: User?
        get() = users.firstOrNull { it.id == currentUserId }

    fun start() {
        scope.launch {
            guard {
                users = api.users()
                tournaments = api.tournaments()
                tournaments.firstOrNull()?.let { select(it.id) }
            }
        }
    }

    fun select(tournamentId: String) {
        selectedTournamentId = tournamentId
        tab = DetailTab.FIXTURES
        refreshDetail()
    }

    fun switchUser(userId: String) {
        currentUserId = userId
        refreshDetail()
    }

    fun savePrediction(matchId: String, outcome: MatchOutcome) {
        scope.launch {
            guard {
                api.submitPrediction(SubmitPredictionRequest(currentUserId, matchId, outcome))
                refreshDetailSuspending()
            }
        }
    }

    private fun refreshDetail() {
        scope.launch { guard { refreshDetailSuspending() } }
    }

    private suspend fun refreshDetailSuspending() {
        val id = selectedTournamentId ?: return
        matchViews = api.matchViews(id, currentUserId)
        leaderboard = api.leaderboard(id)
    }

    /** Runs [block], flipping the loading flag and capturing any failure as [error]. */
    private suspend fun guard(block: suspend () -> Unit) {
        loading = true
        error = null
        try {
            block()
        } catch (t: Throwable) {
            error = t.message ?: "Something went wrong talking to the server"
        } finally {
            loading = false
        }
    }

    companion object {
        const val DEFAULT_USER_ID = "u-you"
    }
}
