package com.example.proxypotps.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF7DB2FF),
    secondary = Color(0xFF7FE7C4),
    background = Color(0xFF0E1116),
    surface = Color(0xFF161A22),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE6E6E6),
    onSurface = Color(0xFFE6E6E6)
)

@Composable
fun ProxyPotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
