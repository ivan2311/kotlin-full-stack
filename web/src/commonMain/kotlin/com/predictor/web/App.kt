package com.predictor.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.predictor.shared.api.TournamentSummary
import com.predictor.shared.model.Sport
import com.predictor.web.net.ApiClient
import com.predictor.web.theme.PredictorTheme

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val vm = remember { AppViewModel(ApiClient(), scope) }
    LaunchedEffect(Unit) { vm.start() }

    PredictorTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Below this width (phones, narrow windows) the side-by-side rail
                // doesn't fit, so we stack into a single column instead.
                val compact = maxWidth < 720.dp
                Column(Modifier.fillMaxSize()) {
                    HeaderBar(vm, compact)
                    vm.error?.let { ErrorBanner(it) }
                    if (compact) {
                        TournamentStrip(vm)
                        HorizontalDivider()
                        DetailPane(vm, Modifier.weight(1f), compact = true)
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            TournamentRail(vm, Modifier.width(280.dp))
                            VerticalRule()
                            DetailPane(vm, Modifier.weight(1f), compact = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(vm: AppViewModel, compact: Boolean) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("KP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Kotlin Predictor",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = if (compact) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                // The tagline crowds a phone header, so we only show it when there's room.
                if (!compact) {
                    Text(
                        "One Kotlin codebase — backend, frontend and scoring rules",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            UserPicker(vm, compact)
        }
    }
}

@Composable
private fun UserPicker(vm: AppViewModel, compact: Boolean) {
    var open by remember { mutableStateOf(false) }
    val name = vm.currentUser?.displayName ?: "…"
    Box {
        OutlinedButton(
            onClick = { open = true },
            contentPadding = if (compact) {
                androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            } else {
                androidx.compose.material3.ButtonDefaults.ContentPadding
            },
        ) {
            // The "Playing as:" prefix crowds a phone header, so compact shows just the name.
            Text(if (compact) name else "Playing as: $name", color = MaterialTheme.colorScheme.onPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            vm.users.forEach { user ->
                DropdownMenuItem(
                    text = { Text(user.displayName) },
                    onClick = {
                        open = false
                        vm.switchUser(user.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun TournamentRail(vm: AppViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "TOURNAMENTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        items(vm.tournaments) { summary ->
            TournamentCard(
                summary = summary,
                selected = summary.id == vm.selectedTournamentId,
                onClick = { vm.select(summary.id) },
            )
        }
    }
}

/** Compact-mode replacement for the side rail: a horizontally scrolling strip of tournaments. */
@Composable
private fun TournamentStrip(vm: AppViewModel) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(vm.tournaments) { summary ->
            CompactTournamentCard(
                summary = summary,
                selected = summary.id == vm.selectedTournamentId,
                onClick = { vm.select(summary.id) },
            )
        }
    }
}

@Composable
private fun CompactTournamentCard(summary: TournamentSummary, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.width(210.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SportBadge(summary.sport, size = 20)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${summary.name} ${summary.season}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                "${summary.competitorCount} ${summary.sport.competitorNoun.lowercase()}s · ${summary.matchCount} matches",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TournamentCard(summary: TournamentSummary, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SportBadge(summary.sport)
                Spacer(Modifier.width(8.dp))
                Text(summary.sport.displayName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.size(6.dp))
            Text("${summary.name} ${summary.season}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "${summary.competitorCount} ${summary.sport.competitorNoun.lowercase()}s · ${summary.matchCount} matches",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
fun SportBadge(sport: Sport, size: Int = 22) {
    val (letter, tint) = when (sport) {
        Sport.SOCCER -> "S" to Color(0xFF2E7D32)
        Sport.TENNIS -> "T" to Color(0xFFF9A825)
        Sport.HANDBALL -> "H" to Color(0xFF1565C0)
    }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, fontSize = (size - 10).sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun VerticalRule() {
    Box(Modifier.width(1.dp).fillMaxSize().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
}

@Composable
private fun DetailPane(vm: AppViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    val pad = if (compact) 16.dp else 24.dp
    val tournament = vm.selectedTournament
    if (tournament == null) {
        Box(modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            Text(if (vm.loading) "Loading…" else "Pick a tournament to start predicting")
        }
        return
    }

    Column(modifier.fillMaxSize().padding(pad)) {
        Text(
            "${tournament.name} ${tournament.season}",
            fontSize = if (compact) 20.sp else 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Predict every fixture. Exact scores earn the most; a correct result still scores.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.size(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton("Fixtures", vm.tab == DetailTab.FIXTURES) { vm.tab = DetailTab.FIXTURES }
            TabButton("Leaderboard", vm.tab == DetailTab.LEADERBOARD) { vm.tab = DetailTab.LEADERBOARD }
        }
        Spacer(Modifier.size(16.dp))
        HorizontalDivider()
        Spacer(Modifier.size(16.dp))

        when (vm.tab) {
            DetailTab.FIXTURES -> FixturesList(vm, tournament.sport, compact)
            DetailTab.LEADERBOARD -> LeaderboardTable(vm, compact)
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        androidx.compose.material3.Button(onClick = onClick) { Text(label) }
    } else {
        TextButton(onClick = onClick) { Text(label) }
    }
}
