package com.homecinema.library.data.streaming

import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.media.subtitleMimeType
import com.homecinema.library.data.smb.SmbRandomAccessInputStream
import com.homecinema.library.data.smb.toSmbConfig
import fi.iki.elonen.NanoHTTPD
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.runBlocking

private const val STREAM_PATH_PREFIX = "/stream/"
private const val SUBTITLE_PATH_PREFIX = "/subtitle/"

/**
 * Loopback-only HTTP server that re-exposes a single library item's SMB file as
 * `http://127.0.0.1:<port>/stream/<itemId>`, with `Range` support for seeking. Exists because
 * most external video players (everything except VLC, which has native SMB support) can't open
 * an `smb://` URI passed via `Intent.ACTION_VIEW` - they need an ordinary HTTP(S) URL. Never
 * binds beyond 127.0.0.1 - this is a same-device bridge for the external-player intent, not a
 * media server for the network.
 *
 * Reuses the exact same jcifs-ng random-access read pattern as [com.homecinema.library.data.smb.SmbDataSource]
 * (ExoPlayer's internal-playback data source) and the download-resume logic in `DownloadWorker`.
 */
class LocalStreamingServer(port: Int = 0) : NanoHTTPD("127.0.0.1", port) {

    /** Called when a streaming response starts / when it (eventually) closes - lets
     * [StreamingService] know whether a connection is actually still live rather than guessing
     * from request cadence. A player can legitimately buffer ahead over the loopback connection
     * and then go quiet on it for a long stretch while it plays from its own buffer without ever
     * closing the connection - only the close is a real "done" signal. */
    var onStreamOpened: (() -> Unit)? = null
    var onStreamClosed: (() -> Unit)? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri.startsWith(STREAM_PATH_PREFIX) -> serveVideo(session, uri.removePrefix(STREAM_PATH_PREFIX))
            uri.startsWith(SUBTITLE_PATH_PREFIX) -> serveSubtitle(uri.removePrefix(SUBTITLE_PATH_PREFIX))
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun serveVideo(session: IHTTPSession, itemId: String): Response {
        if (itemId.isBlank()) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

        val resolved = runCatching { resolveSmbFile(itemId) }.getOrNull()
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

        return runCatching { serveFile(session, resolved.first, resolved.second) }
            .getOrElse { newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", it.message ?: "Error") }
    }

    /** Used by external players that don't discover a sidecar subtitle on their own (they
     * only ever see this single flat streaming URL, no folder to browse) - see
     * DetailScreen.playExternally for how the URL gets passed on. Small file, no Range
     * support needed unlike the video route. */
    private fun serveSubtitle(itemId: String): Response {
        if (itemId.isBlank()) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

        val resolved = runCatching { resolveSubtitleFile(itemId) }.getOrNull()
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        val (smbFile, extension) = resolved

        return runCatching {
            newFixedLengthResponse(Response.Status.OK, subtitleMimeType(extension), smbFile.inputStream, smbFile.length())
        }.getOrElse { newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", it.message ?: "Error") }
    }

    /** Suspend calls (Room + SmbSourceResolver) bridged to blocking - NanoHTTPD dispatches each
     * request on its own worker thread, so blocking here doesn't stall anything else. */
    private fun resolveSmbFile(itemId: String): Pair<SmbFile, String>? = runBlocking {
        val app = HomeCinemaApp.instance
        val item = app.database.libraryDao().getById(itemId) ?: return@runBlocking null
        val source = app.sourceResolver.resolve(item.sourceId) ?: return@runBlocking null
        val smbFile = app.smbManager.openFile(source.id, source.toSmbConfig(), item.videoFilePath)
        smbFile to item.videoFilePath
    }

    private fun resolveSubtitleFile(itemId: String): Pair<SmbFile, String>? = runBlocking {
        val app = HomeCinemaApp.instance
        val item = app.database.libraryDao().getById(itemId) ?: return@runBlocking null
        val source = app.sourceResolver.resolve(item.sourceId) ?: return@runBlocking null
        val config = source.toSmbConfig()
        val subtitlePath = app.smbManager.findSiblingSubtitle(source.id, config, item.videoFilePath) ?: return@runBlocking null
        val smbFile = app.smbManager.openFile(source.id, config, subtitlePath)
        smbFile to subtitlePath.substringAfterLast('.', "")
    }

    private fun serveFile(session: IHTTPSession, smbFile: SmbFile, path: String): Response {
        val totalLength = runCatching { smbFile.length() }.getOrDefault(-1L)
        val mimeType = mimeTypeForExtension(path.substringAfterLast('.', ""))
        val rangeHeader = session.headers["range"]

        val (start, end, isPartial) = resolveRange(rangeHeader, totalLength)
        val raf = SmbRandomAccessFile(smbFile, "r").apply { seek(start) }
        val contentLength = if (totalLength > 0) end - start + 1 else -1L

        onStreamOpened?.invoke()
        val response = newFixedLengthResponse(
            if (isPartial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
            mimeType,
            SmbRandomAccessInputStream(raf) { onStreamClosed?.invoke() },
            contentLength
        )
        response.addHeader("Accept-Ranges", "bytes")
        if (isPartial && totalLength > 0) {
            response.addHeader("Content-Range", "bytes $start-$end/$totalLength")
        }
        return response
    }
}

/** (start, end, isPartial) for a Range header like "bytes=1000-" or "bytes=1000-2000" against a
 * file of [totalLength] bytes. Falls back to the whole file (non-partial) if there's no Range
 * header or the length is unknown. */
internal fun resolveRange(rangeHeader: String?, totalLength: Long): Triple<Long, Long, Boolean> {
    if (rangeHeader == null || !rangeHeader.startsWith("bytes=") || totalLength <= 0) {
        return Triple(0L, (totalLength - 1).coerceAtLeast(0L), false)
    }
    val spec = rangeHeader.removePrefix("bytes=")
    val parts = spec.split("-")
    val start = parts.getOrNull(0)?.toLongOrNull()?.coerceIn(0, totalLength - 1) ?: 0L
    val requestedEnd = parts.getOrNull(1)?.toLongOrNull()
    val end = requestedEnd?.coerceIn(start, totalLength - 1) ?: (totalLength - 1)
    return Triple(start, end, true)
}

internal fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "avi" -> "video/x-msvideo"
    "mov" -> "video/quicktime"
    "webm" -> "video/webm"
    "ts" -> "video/mp2t"
    "wmv" -> "video/x-ms-wmv"
    else -> "video/*"
}
