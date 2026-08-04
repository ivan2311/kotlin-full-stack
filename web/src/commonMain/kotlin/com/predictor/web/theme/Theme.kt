package com.predictor.web.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PitchGreen = Color(0xFF2E7D32)
private val PitchGreenDark = Color(0xFF1B5E20)
private val Amber = Color(0xFFF9A825)

private val colors = lightColorScheme(
    primary = PitchGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F6CA),
    onPrimaryContainer = PitchGreenDark,
    secondary = Amber,
    onSecondary = Color(0xFF3E2723),
    background = Color(0xFFF6F7F5),
    surface = Color.White,
    onSurface = Color(0xFF1B1C1A),
)

@Composable
fun PredictorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
