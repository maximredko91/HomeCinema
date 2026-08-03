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
        assertEquals(LightBackground, backgroundColorFor(ThemeMode.LIGHT, systemInDarkTheme = true))
        assertEquals(CinemaBackground, backgroundColorFor(ThemeMode.DARK, systemInDarkTheme = false))
        assertEquals(OledBackground, backgroundColorFor(ThemeMode.OLED, systemInDarkTheme = false))
        assertEquals(CinemaBackground, backgroundColorFor(ThemeMode.SYSTEM, systemInDarkTheme = true))
        assertEquals(LightBackground, backgroundColorFor(ThemeMode.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun `accent color lookup falls back to gold for an unknown or blank name`() {
        assertEquals(AccentColor.GOLD, AccentColor.fromName("unknown"))
        assertEquals(AccentColor.GOLD, AccentColor.fromName(""))
        assertEquals(AccentColor.BLUE, AccentColor.fromName("BLUE"))
    }
}
