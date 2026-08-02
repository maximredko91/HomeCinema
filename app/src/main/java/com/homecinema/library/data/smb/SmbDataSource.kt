package com.homecinema.library.data.smb

import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.common.util.UnstableApi
import jcifs.CIFSContext
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException

/**
 * Streams a single SMB file for ExoPlayer, supporting seeking via
 * jcifs-ng's SmbRandomAccessFile (needed for scrubbing playback position).
 */
@UnstableApi
class SmbDataSource(private val cifsContext: CIFSContext) : BaseDataSource(true) {

    private var raf: SmbRandomAccessFile? = null
    private var uri: android.net.Uri? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        val smbFile = SmbFile(dataSpec.uri.toString(), cifsContext)
        val handle = SmbRandomAccessFile(smbFile, "r")
        handle.seek(dataSpec.position)
        raf = handle

        val fileLength = smbFile.length()
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            fileLength - dataSpec.position
        }
        if (bytesRemaining < 0) throw IOException("Requested position is beyond file length")

        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length
            else minOf(length.toLong(), bytesRemaining).toInt()

        val read = raf?.read(buffer, offset, toRead) ?: -1
        if (read == -1) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0) {
                throw IOException("Unexpected end of SMB stream")
            }
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): android.net.Uri? = uri

    override fun close() {
        try {
            raf?.close()
        } catch (e: IOException) {
            // ignore on close
        } finally {
            raf = null
            transferEnded()
        }
    }

    @UnstableApi
    class Factory(private val cifsContext: CIFSContext) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(cifsContext)
    }
}
