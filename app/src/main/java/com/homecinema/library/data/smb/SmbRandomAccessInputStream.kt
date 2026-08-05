package com.homecinema.library.data.smb

import jcifs.smb.SmbRandomAccessFile
import java.io.InputStream

/** Adapts jcifs-ng's [SmbRandomAccessFile] (which supports seeking) to a plain [InputStream]
 * so it can be handed to anything that reads streams - ExoPlayer's DataSource, download resume,
 * or the local HTTP proxy for external players. [onClose] fires once the stream is closed - the
 * local HTTP proxy uses it to know when a streaming connection has actually ended, rather than
 * guessing from read cadence (a player can legitimately go quiet for minutes while it plays from
 * its own buffer without ever closing the connection). */
class SmbRandomAccessInputStream(
    private val raf: SmbRandomAccessFile,
    private val onClose: (() -> Unit)? = null
) : InputStream() {
    override fun read(): Int = raf.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
    override fun close() {
        raf.close()
        onClose?.invoke()
    }
}
