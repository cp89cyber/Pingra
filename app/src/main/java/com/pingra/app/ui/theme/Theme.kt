package com.pingra.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PingraColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF374151),
)

@Composable
fun PingraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PingraColors,
        content = content,
    )
}

