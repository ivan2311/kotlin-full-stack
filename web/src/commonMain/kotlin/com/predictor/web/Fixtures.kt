package com.predictor.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.predictor.shared.api.MatchView
import com.predictor.shared.model.MatchOutcome
import com.predictor.shared.model.MatchStatus
import com.predictor.shared.model.Sport
import com.predictor.shared.scoring.PredictionScorer
import com.predictor.shared.scoring.ScoreBreakdown
import com.predictor.shared.scoring.ScoreCategory

@Composable
fun FixturesList(vm: AppViewModel, sport: Sport, compact: Boolean = false) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(vm.matchViews) { view ->
            MatchRow(
                view = view,
                sport = sport,
                compact = compact,
                onSave = { outcome -> vm.savePrediction(view.match.id, outcome) },
            )
        }
    }
}

@Composable
private fun MatchRow(view: MatchView, sport: Sport, compact: Boolean, onSave: (MatchOutcome) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(view.match.stage, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                StatusChip(view.match.status)
            }
            Spacer(Modifier.size(12.dp))

            // On phones the teams and the prediction controls each need the full width,
            // so we stack them vertically instead of side by side.
            if (compact) {
                Column(Modifier.fillMaxWidth()) {
                    Text(view.home.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(view.away.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.size(12.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        MatchControls(view, sport, onSave)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(view.home.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(view.away.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    MatchControls(view, sport, onSave)
                }
            }
        }
    }
}

@Composable
private fun MatchControls(view: MatchView, sport: Sport, onSave: (MatchOutcome) -> Unit) {
    when (view.match.status) {
        MatchStatus.FINISHED -> FinishedPanel(view, sport)
        MatchStatus.LIVE -> Text("In progress…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        MatchStatus.SCHEDULED -> PredictionEditor(view, sport, onSave)
    }
}

/** The panel shown once a match is over: the real result plus the points earned. */
@Composable
private fun FinishedPanel(view: MatchView, sport: Sport) {
    val actual = view.match.actualOutcome!!
    // Scored right here in the browser using the SAME engine the server uses.
    val earned: ScoreBreakdown? = view.prediction?.let {
        PredictionScorer.score(it, actual, sport.scoringRules)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("${actual.home} – ${actual.away}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("final", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(Modifier.size(6.dp))
        if (view.prediction != null && earned != null) {
            PointsPill(earned)
            Text(
                "you predicted ${view.prediction!!.home}–${view.prediction!!.away}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        } else {
            Text("no prediction", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun PointsPill(earned: ScoreBreakdown) {
    val color = when (earned.category) {
        ScoreCategory.EXACT -> Color(0xFF2E7D32)
        ScoreCategory.RESULT_AND_MARGIN -> Color(0xFF558B2F)
        ScoreCategory.RESULT -> Color(0xFFF9A825)
        ScoreCategory.MISS -> Color(0xFF9E9E9E)
    }
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text("+${earned.points} · ${earned.category.label}", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** The editable predictor for an upcoming match. */
@Composable
private fun PredictionEditor(view: MatchView, sport: Sport, onSave: (MatchOutcome) -> Unit) {
    var home by remember(view.prediction) { mutableStateOf(view.prediction?.home ?: 0) }
    var away by remember(view.prediction) { mutableStateOf(view.prediction?.away ?: 0) }

    val isDraw = home == away
    val drawBlocked = isDraw && !sport.allowsDraw
    val changed = view.prediction?.let { it.home != home || it.away != away } ?: true

    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(home) { home = it }
            Text(" : ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Stepper(away) { away = it }
        }
        Spacer(Modifier.size(6.dp))
        if (drawBlocked) {
            Text("${sport.displayName} can't end level", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        } else {
            Button(
                onClick = { onSave(MatchOutcome(home, away)) },
                enabled = changed,
            ) {
                Text(if (view.prediction == null) "Predict" else "Update")
            }
        }
        if (view.prediction != null && !changed) {
            Text("saved", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { if (value > 0) onChange(value - 1) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(32.dp),
        ) { Text("−", fontSize = 16.sp) }
        Text(
            value.toString(),
            modifier = Modifier.width(28.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        OutlinedButton(
            onClick = { onChange(value + 1) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(32.dp),
        ) { Text("+", fontSize = 16.sp) }
    }
}

@Composable
private fun StatusChip(status: MatchStatus) {
    val (label, color) = when (status) {
        MatchStatus.SCHEDULED -> "Upcoming" to Color(0xFF1565C0)
        MatchStatus.LIVE -> "Live" to Color(0xFFD32F2F)
        MatchStatus.FINISHED -> "Finished" to Color(0xFF616161)
    }
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
