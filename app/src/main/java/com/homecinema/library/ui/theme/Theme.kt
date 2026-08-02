package com.homecinema.library.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CinemaColorScheme = darkColorScheme(
    background = CinemaBackground,
    surface = CinemaSurface,
    surfaceVariant = CinemaSurfaceVariant,
    primary = CinemaAccent,
    secondary = CinemaAccentSoft,
    onBackground = CinemaTextPrimary,
    onSurface = CinemaTextPrimary,
    onPrimary = CinemaBackground,
    error = CinemaError
)

@Composable
fun HomeCinemaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CinemaColorScheme,
        typography = CinemaTypography,
        content = content
    )
}
