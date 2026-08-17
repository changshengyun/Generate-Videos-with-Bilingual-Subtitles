package com.example.lyriccaptioner.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFB7F36B),
    onPrimary = Color(0xFF162000),
    primaryContainer = Color(0xFF30491B),
    onPrimaryContainer = Color(0xFFD3FF9D),
    secondary = Color(0xFFB9CBA8),
    onSecondary = Color(0xFF253322),
    surface = Color(0xFF12151A),
    surfaceVariant = Color(0xFF1B1F25),
    onSurface = Color(0xFFF4F5F7),
    onSurfaceVariant = Color(0xFFBEC4CE),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFF4F5F7),
)

@Composable
fun LyricCaptionerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
