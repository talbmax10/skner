package com.talbmax.skner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00A99D)
private val Navy = Color(0xFF0B2B3A)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = Navy,
    background = Color(0xFFF4F7F7),
    surface = Color.White
)
private val DarkColors = darkColorScheme(
    primary = Teal,
    secondary = Color(0xFF7FDCD4),
    background = Color(0xFF102A33),
    surface = Color(0xFF163642)
)

@Composable
fun SkenrTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
