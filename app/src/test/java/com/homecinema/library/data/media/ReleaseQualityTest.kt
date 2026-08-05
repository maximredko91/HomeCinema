package com.homecinema.library.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseQualityTest {

    @Test
    fun `extracts resolution and source together`() {
        assertEquals("1080p BDRip", extractReleaseQuality("Movie.2023.1080p.BDRip.x264.mkv"))
    }

    @Test
    fun `extracts resolution only when no source tag present`() {
        assertEquals("720p", extractReleaseQuality("Movie.2023.720p.mkv"))
    }

    @Test
    fun `extracts source only when no resolution present`() {
        assertEquals("WEB-DL", extractReleaseQuality("Movie.2023.WEB-DL.mkv"))
    }

    @Test
    fun `is case-insensitive`() {
        assertEquals("1080p bluray", extractReleaseQuality("movie.1080p.bluray.mkv"))
    }

    @Test
    fun `returns null when nothing recognizable is found`() {
        assertNull(extractReleaseQuality("Movie Title (2023).mkv"))
    }
}
