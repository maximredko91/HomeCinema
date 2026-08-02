package com.homecinema.library.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.homecinema.library.data.settings.ThemeMode

/** Whether [mode] resolves to a dark base palette, given the current system setting. */
fun resolveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.OLED -> true
    ThemeMode.SYSTEM -> systemInDarkTheme
}

/** The exact background color HomeCinemaTheme resolves to for [mode], for painting the
 * system status/navigation bars to match instead of leaving them their OS default (often
 * a stark white that clashes with a dark theme). */
fun backgroundColorFor(mode: ThemeMode, systemInDarkTheme: Boolean): Color = when {
    !resolveDarkTheme(mode, systemInDarkTheme) -> LightBackground
    mode == ThemeMode.OLED -> OledBackground
    else -> CinemaBackground
}

private fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

@Composable
fun HomeCinemaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.GOLD,
    content: @Composable () -> Unit
) {
    val isDark = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val onAccent = onColorFor(accent.color)

    val colorScheme = if (isDark) {
        val oled = themeMode == ThemeMode.OLED
        darkColorScheme(
            background = if (oled) OledBackground else CinemaBackground,
            surface = if (oled) OledSurface else CinemaSurface,
            surfaceVariant = if (oled) OledSurfaceVariant else CinemaSurfaceVariant,
            primary = accent.color,
            secondary = accent.color,
            onBackground = CinemaTextPrimary,
            onSurface = CinemaTextPrimary,
            onPrimary = onAccent,
            onSecondary = onAccent,
            error = CinemaError
        )
    } else {
        lightColorScheme(
            background = LightBackground,
            surface = LightBackground,
            primary = accent.color,
            secondary = accent.color,
            onPrimary = onAccent,
            onSecondary = onAccent,
            error = CinemaError
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CinemaTypography,
        content = content
    )
}
