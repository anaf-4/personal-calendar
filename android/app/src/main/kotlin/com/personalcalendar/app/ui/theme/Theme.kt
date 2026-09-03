package com.personalcalendar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AccentBlue = Color(0xFF5B8DEF)
val DangerRed = Color(0xFFE0596B)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = AccentBlue,
    background = Color(0xFF1E1F24),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF26272E),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF2D2F38),
    onSurfaceVariant = Color(0xFF9A9CA6),
    outline = Color(0xFF3A3C46),
    error = DangerRed
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D6FD6),
    onPrimary = Color.White,
    secondary = Color(0xFF3D6FD6),
    background = Color(0xFFF4F5F8),
    onBackground = Color(0xFF202227),
    surface = Color.White,
    onSurface = Color(0xFF202227),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF6C6F7A),
    outline = Color(0xFFDDE0E7),
    error = Color(0xFFD0384F)
)

/** [themeMode] is "dark" | "light" | "system". */
@Composable
fun PersonalCalendarTheme(themeMode: String, content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content
    )
}
