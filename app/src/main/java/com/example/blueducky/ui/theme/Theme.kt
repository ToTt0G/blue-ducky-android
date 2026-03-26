package com.example.blueducky.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- Brand Palette ---
val DuckYellow     = Color(0xFFFFC107)
val DuckYellowDark = Color(0xFFFFA000)
val DuckGreen      = Color(0xFF00E676)
val DuckBackground = Color(0xFF0D0D0D)
val DuckSurface    = Color(0xFF1A1A2E)
val DuckSurface2   = Color(0xFF16213E)
val DuckOnSurface  = Color(0xFFE0E0E0)
val DuckError      = Color(0xFFCF6679)

private val DarkColorScheme = darkColorScheme(
    primary          = DuckYellow,
    onPrimary        = Color(0xFF1A1A00),
    primaryContainer = DuckYellowDark,
    secondary        = DuckGreen,
    onSecondary      = Color(0xFF003314),
    background       = DuckBackground,
    surface          = DuckSurface,
    onSurface        = DuckOnSurface,
    surfaceVariant   = DuckSurface2,
    error            = DuckError,
    onError          = Color.White,
)

@Composable
fun BlueDuckyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
