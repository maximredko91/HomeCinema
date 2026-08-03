package com.homecinema.library.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenHelpersTest {

    @Test
    fun `does not resume a fresh item with no saved position`() {
        assertFalse(shouldResume(positionMs = 0, durationMs = 6_000_000))
    }

    @Test
    fun `does not resume a position under the 5 second grace threshold`() {
        assertFalse(shouldResume(positionMs = 4_000, durationMs = 6_000_000))
    }

    @Test
    fun `resumes a position past the threshold and well before the end`() {
        assertTrue(shouldResume(positionMs = 60_000, durationMs = 6_000_000))
    }

    @Test
    fun `does not resume something that is essentially finished - starts over instead`() {
        // 96% through a 100-minute movie - should restart rather than resume 4 min from the end
        val duration = 6_000_000L
        assertFalse(shouldResume(positionMs = (duration * 0.96).toLong(), durationMs = duration))
    }

    @Test
    fun `resumes when duration is unknown as long as position clears the grace threshold`() {
        assertTrue(shouldResume(positionMs = 60_000, durationMs = 0))
        assertTrue(shouldResume(positionMs = 60_000, durationMs = -1))
    }
}
