package com.homecinema.library.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.homecinema.library.R

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
    @StringRes val labelRes: Int,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val swatch: Color
) {
    INDIGO(R.string.glass_bg_indigo, Color(0xFF121022), Color(0xFF1C1930), Color(0xFF272240), Color(0xFF6C63FF)),
    CHARCOAL(R.string.glass_bg_charcoal, Color(0xFF16171B), Color(0xFF202126), Color(0xFF2A2C33), Color(0xFFA6ACB4)),
    MIDNIGHT(R.string.glass_bg_midnight, Color(0xFF0A1020), Color(0xFF142038), Color(0xFF1D2C4A), Color(0xFF3E7BEF)),
    EMERALD(R.string.glass_bg_emerald, Color(0xFF0B1A15), Color(0xFF15281F), Color(0xFF1E362A), Color(0xFF2ECC71)),
    WINE(R.string.glass_bg_wine, Color(0xFF1D0F14), Color(0xFF2A1620), Color(0xFF381E2C), Color(0xFFD6456F)),
    TRUE_BLACK(R.string.glass_bg_true_black, Color(0xFF000000), Color(0xFF0B0B0B), Color(0xFF161616), Color(0xFF7A7A7A));

    companion object {
        fun fromName(name: String): GlassBackgroundColor = entries.firstOrNull { it.name == name } ?: INDIGO
    }
}

enum class AccentColor(@StringRes val labelRes: Int, val color: Color) {
    GOLD(R.string.accent_gold, Color(0xFFE6B450)),
    BLUE(R.string.accent_blue, Color(0xFF4C8DFF)),
    GREEN(R.string.accent_green, Color(0xFF4CAF7D)),
    RED(R.string.accent_red, Color(0xFFE05A5A)),
    PURPLE(R.string.accent_purple, Color(0xFF9C6ADE)),
    TEAL(R.string.accent_teal, Color(0xFF3FBFB0)),
    PINK(R.string.accent_pink, Color(0xFFE667A6));

    companion object {
        fun fromName(name: String): AccentColor = entries.firstOrNull { it.name == name } ?: GOLD
    }
}
