package com.predictor.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.predictor.shared.model.LeaderboardEntry

// Shared column widths so the header and every row line up.
private const val RANK_W = 44
private const val NUM_W = 76

@Composable
fun LeaderboardTable(vm: AppViewModel, compact: Boolean = false) {
    val board = vm.leaderboard
    if (board == null || board.entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No standings yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The fixed-width numeric columns don't fit on a phone, so compact mode
        // drops the header and folds the extra stats under the player's name.
        if (!compact) item { HeaderRow() }
        items(board.entries) { entry ->
            LeaderboardRow(entry, highlighted = entry.userId == vm.currentUserId, compact = compact)
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FixedHeader("#", RANK_W)
        Text(
            "Player",
            Modifier.weight(1f).padding(start = 4.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        FixedHeader("Exact", NUM_W, alignEnd = true)
        FixedHeader("Scored", NUM_W, alignEnd = true)
        FixedHeader("Points", NUM_W, alignEnd = true)
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, highlighted: Boolean, compact: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RANK_W.dp), contentAlignment = Alignment.Center) { RankMedal(entry.rank) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                entry.displayName,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
            )
            // On phones the Exact/Scored columns are gone, so surface those numbers here.
            if (compact) {
                Text(
                    "${entry.exactHits} exact · ${entry.predictionsScored} scored",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        if (!compact) {
            FixedValue(entry.exactHits.toString())
            FixedValue(entry.predictionsScored.toString())
        }
        Box(
            modifier = if (compact) Modifier else Modifier.width(NUM_W.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                "${entry.totalPoints}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RankMedal(rank: Int) {
    val medalColor = when (rank) {
        1 -> Color(0xFFD4AF37) // gold
        2 -> Color(0xFF9E9E9E) // silver
        3 -> Color(0xFFB87333) // bronze
        else -> null
    }
    if (medalColor != null) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(medalColor.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$rank", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = medalColor)
        }
    } else {
        Text("$rank", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun RowScope.FixedValue(text: String) {
    Box(Modifier.width(NUM_W.dp), contentAlignment = Alignment.CenterEnd) {
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
    }
}

@Composable
private fun RowScope.FixedHeader(text: String, widthDp: Int, alignEnd: Boolean = false) {
    Box(
        Modifier.width(widthDp.dp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}
