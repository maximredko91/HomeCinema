package com.homecinema.library.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryScreenHelpersTest {

    @Test
    fun `filmsWord uses singular for 1, 21, 101 - but not 11`() {
        assertEquals("фильм", filmsWord(1))
        assertEquals("фильм", filmsWord(21))
        assertEquals("фильм", filmsWord(101))
        assertEquals("фильмов", filmsWord(11))
    }

    @Test
    fun `filmsWord uses the few-form for 2, 3, 4, 22 - but not 12`() {
        assertEquals("фильма", filmsWord(2))
        assertEquals("фильма", filmsWord(3))
        assertEquals("фильма", filmsWord(4))
        assertEquals("фильма", filmsWord(22))
        assertEquals("фильмов", filmsWord(12))
    }

    @Test
    fun `filmsWord uses the many-form for 0, 5-20`() {
        assertEquals("фильмов", filmsWord(0))
        assertEquals("фильмов", filmsWord(5))
        assertEquals("фильмов", filmsWord(11))
        assertEquals("фильмов", filmsWord(14))
        assertEquals("фильмов", filmsWord(20))
        assertEquals("фильмов", filmsWord(100))
        assertEquals("фильмов", filmsWord(111))
    }
}
