package com.eka.pulsesync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TankaCyan,
    secondary = TankaEmerald,
    background = TankaBg,
    surface = TankaCard,
    surfaceVariant = TankaCardVariant,
    onBackground = TankaTextMain,
    onSurface = TankaTextMain,
    outline = TankaBorder
)

@Composable
fun PulseSyncTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
