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

// Explicit (rather than relying on Material3's baseline default) so MainActivity can
// paint the system status/navigation bars with this exact same color.
val LightBackground = Color(0xFFFFFBFE)

// "Стекло" theme: a deep, slightly cool base so blurred-glass chrome panels (top bars,
// sheets, cards) read clearly against it. GlassSurface is the tint color handed to Haze's
// real backdrop blur (see GlassStyle.kt) rather than a hand-picked translucency alpha -
// Haze's material presets work out their own appropriate opacity on top of it.
val GlassBackground = Color(0xFF121022)
val GlassSurface = Color(0xFF1C1930)
val GlassSurfaceVariant = Color(0xFF272240)

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
