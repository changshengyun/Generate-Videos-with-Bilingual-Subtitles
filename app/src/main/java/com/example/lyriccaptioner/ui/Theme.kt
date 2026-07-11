package com.example.lyriccaptioner.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E6B5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EFE9),
    onPrimaryContainer = Color(0xFF08231D),
    secondary = Color(0xFF5C5B1E),
    onSecondary = Color.White,
    surface = Color(0xFFFBFCFA),
    surfaceVariant = Color(0xFFE7ECE7),
    onSurface = Color(0xFF1B1D1B),
    onSurfaceVariant = Color(0xFF4A514C),
)

@Composable
fun LyricCaptionerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
