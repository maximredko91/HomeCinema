package com.homecinema.library.ui.theme

import androidx.compose.ui.graphics.Color

val CinemaBackground = Color(0xFF0E0E12)
val CinemaSurface = Color(0xFF1A1A20)
val CinemaSurfaceVariant = Color(0xFF26262E)
val CinemaAccent = Color(0xFFE6B450) // warm gold, feels like a cinema marquee
val CinemaAccentSoft = Color(0xFFF2CD7C)
val CinemaTextPrimary = Color(0xFFF5F5F7)
val CinemaTextSecondary = Color(0xFFA0A0AC)
val CinemaError = Color(0xFFE0605A)

// True-black surfaces for the OLED theme (saves power on OLED panels, deeper contrast).
val OledBackground = Color(0xFF000000)
val OledSurface = Color(0xFF0A0A0A)
val OledSurfaceVariant = Color(0xFF161616)

enum class AccentColor(val label: String, val color: Color) {
    GOLD("Золотой", Color(0xFFE6B450)),
    BLUE("Синий", Color(0xFF4C8DFF)),
    GREEN("Зелёный", Color(0xFF4CAF7D)),
    RED("Красный", Color(0xFFE05A5A)),
    PURPLE("Фиолетовый", Color(0xFF9C6ADE)),
    TEAL("Бирюзовый", Color(0xFF3FBFB0)),
    PINK("Розовый", Color(0xFFE667A6));

    companion object {
        fun fromName(name: String): AccentColor = entries.firstOrNull { it.name == name } ?: GOLD
    }
}
