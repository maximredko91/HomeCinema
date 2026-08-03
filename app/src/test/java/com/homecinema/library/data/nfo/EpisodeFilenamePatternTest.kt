package com.homecinema.library.data.nfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeFilenamePatternTest {

    @Test
    fun `extracts SxxExx pattern`() {
        assertEquals(1 to 2, EpisodeFilenamePattern.extract("Show.S01E02.1080p.mkv"))
        assertEquals(12 to 145, EpisodeFilenamePattern.extract("Show S12E145.mkv"))
    }

    @Test
    fun `is case insensitive for SxxExx`() {
        assertEquals(1 to 2, EpisodeFilenamePattern.extract("show.s01e02.mkv"))
    }

    @Test
    fun `extracts NxN pattern when SxxExx is absent`() {
        assertEquals(1 to 2, EpisodeFilenamePattern.extract("Show 1x02.mkv"))
    }

    @Test
    fun `prefers SxxExx over NxN when both could match`() {
        // "01x02" would also match NxN, but the SxxExx-style token should win if present.
        assertEquals(3 to 4, EpisodeFilenamePattern.extract("Show.S03E04.1x99.mkv"))
    }

    @Test
    fun `returns null when no pattern is found`() {
        assertNull(EpisodeFilenamePattern.extract("Show - Special Feature.mkv"))
        assertNull(EpisodeFilenamePattern.extract("random_file_name.mkv"))
    }
}
