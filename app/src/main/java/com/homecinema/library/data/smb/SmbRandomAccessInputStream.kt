package com.homecinema.library.data.smb

import jcifs.smb.SmbRandomAccessFile
import java.io.InputStream

/** Adapts jcifs-ng's [SmbRandomAccessFile] (which supports seeking) to a plain [InputStream]
 * so it can be handed to anything that reads streams - ExoPlayer's DataSource, download resume,
 * or the local HTTP proxy for external players. [onRead] fires on every successful read - the
 * local HTTP proxy uses it to keep its idle-shutdown watchdog alive for the whole duration of a
 * single long-lived streaming connection, not just when it's first opened. */
class SmbRandomAccessInputStream(
    private val raf: SmbRandomAccessFile,
    private val onRead: (() -> Unit)? = null
) : InputStream() {
    override fun read(): Int = raf.read().also { if (it >= 0) onRead?.invoke() }
    override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len).also { if (it >= 0) onRead?.invoke() }
    override fun close() = raf.close()
}
