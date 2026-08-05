package com.homecinema.library.ui.theme

import com.homecinema.library.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    @Test
    fun `LIGHT is never dark regardless of system setting`() {
        assertFalse(resolveDarkTheme(ThemeMode.LIGHT, systemInDarkTheme = true))
        assertFalse(resolveDarkTheme(ThemeMode.LIGHT, systemInDarkTheme = false))
    }

    @Test
    fun `DARK and OLED are always dark regardless of system setting`() {
        assertTrue(resolveDarkTheme(ThemeMode.DARK, systemInDarkTheme = false))
        assertTrue(resolveDarkTheme(ThemeMode.OLED, systemInDarkTheme = false))
        assertTrue(resolveDarkTheme(ThemeMode.DARK, systemInDarkTheme = true))
        assertTrue(resolveDarkTheme(ThemeMode.OLED, systemInDarkTheme = true))
    }

    @Test
    fun `SYSTEM follows the system setting`() {
        assertTrue(resolveDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = true))
        assertFalse(resolveDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun `background color matches the resolved mode`() {
        val glass = GlassBackgroundColor.INDIGO
        assertEquals(LightBackground, backgroundColorFor(ThemeMode.LIGHT, systemInDarkTheme = true, glassBackground = glass))
        assertEquals(CinemaBackground, backgroundColorFor(ThemeMode.DARK, systemInDarkTheme = false, glassBackground = glass))
        assertEquals(OledBackground, backgroundColorFor(ThemeMode.OLED, systemInDarkTheme = false, glassBackground = glass))
        assertEquals(CinemaBackground, backgroundColorFor(ThemeMode.SYSTEM, systemInDarkTheme = true, glassBackground = glass))
        assertEquals(LightBackground, backgroundColorFor(ThemeMode.SYSTEM, systemInDarkTheme = false, glassBackground = glass))
    }

    @Test
    fun `glass theme background color follows the selected preset`() {
        assertEquals(
            GlassBackgroundColor.EMERALD.background,
            backgroundColorFor(ThemeMode.GLASS, systemInDarkTheme = false, glassBackground = GlassBackgroundColor.EMERALD)
        )
    }

    @Test
    fun `accent color lookup falls back to gold for an unknown or blank name`() {
        assertEquals(AccentColor.GOLD, AccentColor.fromName("unknown"))
        assertEquals(AccentColor.GOLD, AccentColor.fromName(""))
        assertEquals(AccentColor.BLUE, AccentColor.fromName("BLUE"))
    }

    @Test
    fun `glass background color lookup falls back to indigo for an unknown or blank name`() {
        assertEquals(GlassBackgroundColor.INDIGO, GlassBackgroundColor.fromName("unknown"))
        assertEquals(GlassBackgroundColor.INDIGO, GlassBackgroundColor.fromName(""))
        assertEquals(GlassBackgroundColor.EMERALD, GlassBackgroundColor.fromName("EMERALD"))
    }
}
