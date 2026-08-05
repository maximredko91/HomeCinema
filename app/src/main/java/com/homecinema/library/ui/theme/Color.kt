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

/** "Стекло" theme background presets - a deep, slightly cool base so blurred-glass chrome
 * panels (top bars, sheets, cards) read clearly against it. [surface] is the tint color handed
 * to Haze's real backdrop blur (see GlassStyle.kt) at a user-adjustable opacity rather than a
 * fixed one - see SettingsStore.glassOpacityFlow. [swatch] is a separate, more saturated color
 * used only for the picker circle in Settings - the actual [background]/[surface] tones are
 * deliberately dark and muted (that's the point of a glass theme), which made them all but
 * indistinguishable as small picker swatches against a dark screen. */
enum class GlassBackgroundColor(
    val label: String,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val swatch: Color
) {
    INDIGO("Индиго", Color(0xFF121022), Color(0xFF1C1930), Color(0xFF272240), Color(0xFF6C63FF)),
    CHARCOAL("Графит", Color(0xFF16171B), Color(0xFF202126), Color(0xFF2A2C33), Color(0xFFA6ACB4)),
    MIDNIGHT("Ночная синь", Color(0xFF0A1020), Color(0xFF142038), Color(0xFF1D2C4A), Color(0xFF3E7BEF)),
    EMERALD("Изумруд", Color(0xFF0B1A15), Color(0xFF15281F), Color(0xFF1E362A), Color(0xFF2ECC71)),
    WINE("Бордо", Color(0xFF1D0F14), Color(0xFF2A1620), Color(0xFF381E2C), Color(0xFFD6456F)),
    TRUE_BLACK("Чёрный", Color(0xFF000000), Color(0xFF0B0B0B), Color(0xFF161616), Color(0xFF7A7A7A));

    companion object {
        fun fromName(name: String): GlassBackgroundColor = entries.firstOrNull { it.name == name } ?: INDIGO
    }
}

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
