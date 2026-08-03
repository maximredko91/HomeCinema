package com.homecinema.library.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer minor version is detected`() {
        assertTrue(isNewerVersion(current = "1.0", latest = "1.1"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(isNewerVersion(current = "1.1", latest = "1.1"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(isNewerVersion(current = "1.1", latest = "1.0"))
    }

    @Test
    fun `compares numerically, not lexicographically`() {
        assertTrue(isNewerVersion(current = "1.9", latest = "1.10"))
        assertFalse(isNewerVersion(current = "1.10", latest = "1.9"))
    }

    @Test
    fun `newer major version wins regardless of minor`() {
        assertTrue(isNewerVersion(current = "1.9", latest = "2.0"))
    }

    @Test
    fun `handles a patch component present only on one side`() {
        assertTrue(isNewerVersion(current = "1.1", latest = "1.1.1"))
        assertFalse(isNewerVersion(current = "1.1.0", latest = "1.1"))
    }

    @Test
    fun `non-numeric components are treated as zero rather than crashing`() {
        assertFalse(isNewerVersion(current = "1.1", latest = "1.1-beta"))
    }
}
