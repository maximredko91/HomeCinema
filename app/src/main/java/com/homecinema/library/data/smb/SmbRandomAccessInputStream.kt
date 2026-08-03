package com.homecinema.library.data.smb

import jcifs.smb.SmbRandomAccessFile
import java.io.InputStream

/** Adapts jcifs-ng's [SmbRandomAccessFile] (which supports seeking) to a plain [InputStream]
 * so it can be handed to anything that reads streams - ExoPlayer's DataSource, download resume,
 * or the local HTTP proxy for external players. */
class SmbRandomAccessInputStream(private val raf: SmbRandomAccessFile) : InputStream() {
    override fun read(): Int = raf.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
    override fun close() = raf.close()
}
