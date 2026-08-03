package com.homecinema.library.data.download

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DownloadWorkerTest {

    // --- resumeOffset ---

    @Test
    fun `no local file resumes from zero`() {
        assertEquals(0L, resumeOffset(localLength = 0L, remoteLength = 1000L))
    }

    @Test
    fun `local partial smaller than remote resumes from its length`() {
        assertEquals(400L, resumeOffset(localLength = 400L, remoteLength = 1000L))
    }

    @Test
    fun `local file equal to remote length restarts from zero`() {
        assertEquals(0L, resumeOffset(localLength = 1000L, remoteLength = 1000L))
    }

    @Test
    fun `local file bigger than remote (stale or corrupt) restarts from zero`() {
        assertEquals(0L, resumeOffset(localLength = 1500L, remoteLength = 1000L))
    }

    @Test
    fun `unknown remote length restarts from zero even with a local partial`() {
        assertEquals(0L, resumeOffset(localLength = 400L, remoteLength = -1L))
    }

    // --- copyWithProgress ---

    @Test
    fun `copies all bytes from start to finish`() = runTest {
        val data = ByteArray(10 * 1024) { (it % 256).toByte() }
        val input = ByteArrayInputStream(data)
        val output = ByteArrayOutputStream()

        copyWithProgress(input, output, startBytes = 0L, totalBytes = data.size.toLong()) { }

        assertEquals(data.toList(), output.toByteArray().toList())
    }

    @Test
    fun `reports progress only when the percent actually changes`() = runTest {
        val totalBytes = 1000L
        val data = ByteArray(totalBytes.toInt())
        val percents = mutableListOf<Int>()

        copyWithProgress(ByteArrayInputStream(data), ByteArrayOutputStream(), startBytes = 0L, totalBytes = totalBytes) { percent ->
            percents.add(percent)
        }

        assertEquals(percents, percents.distinct())
        assertEquals(100, percents.last())
    }

    @Test
    fun `resuming from an offset reports progress starting above zero`() = runTest {
        val totalBytes = 1000L
        val remaining = ByteArray(500)
        val percents = mutableListOf<Int>()

        copyWithProgress(ByteArrayInputStream(remaining), ByteArrayOutputStream(), startBytes = 500L, totalBytes = totalBytes) { percent ->
            percents.add(percent)
        }

        assertEquals(100, percents.last())
        assertEquals(true, percents.all { it >= 50 })
    }

    @Test
    fun `unknown total bytes never reports progress`() = runTest {
        val data = ByteArray(2048)
        val percents = mutableListOf<Int>()

        copyWithProgress(ByteArrayInputStream(data), ByteArrayOutputStream(), startBytes = 0L, totalBytes = -1L) { percent ->
            percents.add(percent)
        }

        assertEquals(emptyList<Int>(), percents)
    }
}
