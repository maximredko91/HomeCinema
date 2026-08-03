package com.homecinema.library.data.streaming

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalStreamingServerTest {

    @Test
    fun `no range header serves the whole file, non-partial`() {
        val (start, end, isPartial) = resolveRange(null, totalLength = 1000L)
        assertEquals(0L, start)
        assertEquals(999L, end)
        assertEquals(false, isPartial)
    }

    @Test
    fun `open-ended range serves from the requested offset to the end`() {
        val (start, end, isPartial) = resolveRange("bytes=500-", totalLength = 1000L)
        assertEquals(500L, start)
        assertEquals(999L, end)
        assertEquals(true, isPartial)
    }

    @Test
    fun `bounded range respects both endpoints`() {
        val (start, end, isPartial) = resolveRange("bytes=100-199", totalLength = 1000L)
        assertEquals(100L, start)
        assertEquals(199L, end)
        assertEquals(true, isPartial)
    }

    @Test
    fun `range end beyond the file length is clamped to the last byte`() {
        val (start, end, _) = resolveRange("bytes=900-5000", totalLength = 1000L)
        assertEquals(900L, start)
        assertEquals(999L, end)
    }

    @Test
    fun `unknown total length falls back to a non-partial response even with a range header`() {
        // Can't compute a valid Content-Range without knowing the file's length.
        val (start, end, isPartial) = resolveRange("bytes=100-", totalLength = -1L)
        assertEquals(0L, start)
        assertEquals(0L, end)
        assertEquals(false, isPartial)
    }

    @Test
    fun `maps common video extensions to their mime types`() {
        assertEquals("video/mp4", mimeTypeForExtension("mp4"))
        assertEquals("video/mp4", mimeTypeForExtension("MP4"))
        assertEquals("video/x-matroska", mimeTypeForExtension("mkv"))
        assertEquals("video/x-msvideo", mimeTypeForExtension("avi"))
    }

    @Test
    fun `unknown extension falls back to a generic video mime type`() {
        assertEquals("video/*", mimeTypeForExtension("xyz"))
        assertEquals("video/*", mimeTypeForExtension(""))
    }
}
