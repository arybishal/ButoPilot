package com.autopilot.android.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared palette: white surfaces, black text, yellow actions. */
object AppColors {
    val Primary = Color(0xFFFFC107)
    val OnPrimary = Color.Black
    val Background = Color.White
    val Surface = Color.White
    val OnSurface = Color.Black
    val Muted = Color(0xFF64748B)
    val Border = Color(0xFFE2E8F0)
}

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    primaryContainer = AppColors.Primary,
    onPrimaryContainer = AppColors.OnPrimary,
    secondary = AppColors.Primary,
    onSecondary = AppColors.OnPrimary,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    primaryContainer = Color(0xFFFFF3C4),
    onPrimaryContainer = AppColors.OnSurface,
    secondary = AppColors.Primary,
    onSecondary = AppColors.OnPrimary,
    background = AppColors.Background,
    surface = AppColors.Surface,
    onSurface = AppColors.OnSurface,
    error = Color(0xFFB00020)
)

@Composable
fun AutoPilotTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
