package com.homecinema.library.data.download

import android.content.Context
import com.homecinema.library.data.db.DownloadState
import com.homecinema.library.data.db.LibraryDao
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.smb.SmbManager
import com.homecinema.library.data.smb.SmbSourceResolver
import com.homecinema.library.data.smb.toSmbConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads a movie/cartoon/episode from the SMB share to app-private local storage
 * so it can be watched offline afterwards. Runs on an application-scoped coroutine
 * scope so a download keeps going if the user navigates away from the screen that
 * started it (it does NOT survive the whole app process being killed - there's no
 * foreground Service backing this, see README for that limitation).
 */
class DownloadManager(
    private val smbManager: SmbManager,
    private val dao: LibraryDao,
    private val sourceResolver: SmbSourceResolver,
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _liveProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** itemId -> percent, only contains entries for downloads currently in progress. */
    val liveProgress: StateFlow<Map<String, Int>> = _liveProgress.asStateFlow()

    private val downloadsDir: File by lazy {
        File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
    }

    fun isDownloading(itemId: String): Boolean = activeJobs.containsKey(itemId)

    fun start(item: MediaItemEntity) {
        if (item.videoFilePath.isBlank() || activeJobs.containsKey(item.id)) return

        val job = scope.launch(Dispatchers.IO) {
            try {
                setProgress(item.id, 0)
                dao.updateDownloadProgress(item.id, DownloadState.DOWNLOADING, 0)

                val source = sourceResolver.resolve(item.sourceId) ?: error("SMB source not found for ${item.title}")
                val smbFile = smbManager.openFile(source.id, source.toSmbConfig(), item.videoFilePath)
                val totalBytes = runCatching { smbFile.length() }.getOrDefault(-1L)
                val extension = item.videoFilePath.substringAfterLast('.', "mp4")
                val destFile = File(downloadsDir, "${item.id}.$extension")

                smbFile.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var copiedBytes = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            if (totalBytes > 0) {
                                val percent = ((copiedBytes * 100) / totalBytes).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    setProgress(item.id, percent)
                                    dao.updateDownloadProgress(item.id, DownloadState.DOWNLOADING, percent)
                                }
                            }
                        }
                    }
                }

                dao.updateDownloadResult(item.id, DownloadState.COMPLETED, 100, destFile.absolutePath)
                clearProgress(item.id)
            } catch (e: CancellationException) {
                destFileFor(item)?.delete()
                dao.updateDownloadResult(item.id, DownloadState.NONE, 0, null)
                clearProgress(item.id)
            } catch (e: Exception) {
                destFileFor(item)?.delete()
                dao.updateDownloadResult(item.id, DownloadState.FAILED, 0, null)
                clearProgress(item.id)
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    fun cancel(itemId: String) {
        activeJobs[itemId]?.cancel()
    }

    suspend fun deleteDownload(item: MediaItemEntity) {
        item.localFilePath?.let { File(it).delete() }
        dao.updateDownloadResult(item.id, DownloadState.NONE, 0, null)
    }

    private fun destFileFor(item: MediaItemEntity): File? {
        val extension = item.videoFilePath.substringAfterLast('.', "mp4")
        return File(downloadsDir, "${item.id}.$extension")
    }

    private fun setProgress(id: String, percent: Int) {
        _liveProgress.update { it + (id to percent) }
    }

    private fun clearProgress(id: String) {
        _liveProgress.update { it - id }
    }
}
