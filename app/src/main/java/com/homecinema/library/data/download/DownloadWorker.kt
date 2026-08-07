package com.homecinema.library.data.download

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.DownloadState
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.SmbSourceEntity
import com.homecinema.library.data.media.DownloadStorage
import com.homecinema.library.data.media.SUBTITLE_EXTENSIONS
import com.homecinema.library.data.smb.SmbRandomAccessInputStream
import com.homecinema.library.data.smb.toSmbConfig
import com.homecinema.library.data.streaming.mimeTypeForExtension
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

const val DOWNLOAD_WORK_TAG = "download"
private const val KEY_ITEM_ID = "itemId"
private const val KEY_PROGRESS = "progress"
private const val BUFFER_SIZE = 256 * 1024

/** Retry budget for transient failures (network hiccups, share briefly unreachable) before
 * giving up and surfacing FAILED - without a cap a broken source would retry forever. */
private const val MAX_TRANSIENT_ATTEMPTS = 8

/** Thrown for failures that won't fix themselves on retry (missing source, auth rejected) -
 * caught separately in [DownloadWorker.doWork] to fail immediately instead of burning through
 * the retry budget. */
private class PermanentDownloadException(message: String) : Exception(message)

fun downloadWorkName(itemId: String) = "download_$itemId"

/**
 * Downloads one item from its SMB share to local storage, persisted via WorkManager so it
 * survives process death and retries automatically. Resumes an interrupted transfer from the
 * partial file already on disk rather than restarting - see [resumeOffset].
 */
class DownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HomeCinemaApp
        val dao = app.database.libraryDao()
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val item = dao.getById(itemId) ?: return Result.failure()

        runCatching { setForeground(foregroundInfo(itemId, item.title, item.downloadProgress)) }

        return try {
            downloadItem(app, item)
            Result.success()
        } catch (e: CancellationException) {
            // An explicit user cancel already reset the DB row to NONE and deleted the
            // partial file in DownloadManager.cancel() *before* cancelling this work, so
            // nothing to clean up here. A system-triggered stop (constraints lost, app
            // backgrounded further) leaves the DB at DOWNLOADING with the partial file
            // intact, so the next automatic run resumes it from where it left off.
            throw e
        } catch (e: PermanentDownloadException) {
            dao.updateDownloadResult(itemId, DownloadState.FAILED, 0, null)
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_TRANSIENT_ATTEMPTS) {
                Result.retry()
            } else {
                dao.updateDownloadResult(itemId, DownloadState.FAILED, 0, null)
                Result.failure()
            }
        } finally {
            runCatching {
                applicationContext.getSystemService(NotificationManager::class.java)
                    ?.cancel(notificationId(itemId))
            }
        }
    }

    private suspend fun downloadItem(app: HomeCinemaApp, item: MediaItemEntity) {
        val dao = app.database.libraryDao()

        val source = app.sourceResolver.resolve(item.sourceId)
            ?: throw PermanentDownloadException("SMB source not found for ${item.title}")

        val smbFile = try {
            app.smbManager.openFile(source.id, source.toSmbConfig(), item.videoFilePath)
        } catch (e: SmbException) {
            throw PermanentDownloadException("Cannot open ${item.videoFilePath}: ${e.message}")
        }

        val totalBytes = runCatching { smbFile.length() }.getOrDefault(-1L)
        val extension = item.videoFilePath.substringAfterLast('.', "mp4")
        val customTreeUri = app.settingsStore.downloadFolderUriFlow.first().takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        val destUri = DownloadStorage.getOrCreateEntry(
            applicationContext, item, "${item.id}.$extension", mimeTypeForExtension(extension), customTreeUri
        )
        val offset = resumeOffset(DownloadStorage.currentLength(applicationContext, destUri), totalBytes)

        dao.updateDownloadProgress(item.id, DownloadState.DOWNLOADING, item.downloadProgress)

        openResumableInput(smbFile, offset).use { input ->
            DownloadStorage.openOutputStream(applicationContext, destUri, append = offset > 0).use { output ->
                copyWithProgress(input, output, offset, totalBytes) { percent ->
                    dao.updateDownloadProgress(item.id, DownloadState.DOWNLOADING, percent)
                    setProgress(workDataOf(KEY_ITEM_ID to item.id, KEY_PROGRESS to percent))
                    runCatching { updateNotification(item.id, item.title, percent) }
                }
            }
        }
        DownloadStorage.markComplete(applicationContext, destUri)

        dao.updateDownloadResult(item.id, DownloadState.COMPLETED, 100, destUri.toString())

        downloadCompanionFiles(app, item, source, customTreeUri)
    }

    /** Best-effort: the subtitle and .nfo are small and secondary to the video itself, so any
     * failure here (not found, share hiccup) is swallowed rather than failing the whole
     * download or burning through the retry budget. Named to match the video's own filename
     * (itemId, not the original filename) so findDownloadedSubtitle can find it later. */
    private suspend fun downloadCompanionFiles(app: HomeCinemaApp, item: MediaItemEntity, source: SmbSourceEntity, customTreeUri: Uri?) {
        val config = source.toSmbConfig()
        runCatching {
            val subtitlePath = app.smbManager.findSiblingSubtitle(source.id, config, item.videoFilePath)
            if (subtitlePath != null) {
                val extension = subtitlePath.substringAfterLast('.', "").lowercase()
                if (extension in SUBTITLE_EXTENSIONS) {
                    copySmbFileToDownloads(app, item, source, "${item.id}.$extension", "text/plain", subtitlePath, customTreeUri)
                }
            }
        }
        runCatching {
            val nfoPath = app.smbManager.findSiblingNfo(source.id, config, item.videoFilePath)
            if (nfoPath != null) {
                copySmbFileToDownloads(app, item, source, "${item.id}.nfo", "application/xml", nfoPath, customTreeUri)
            }
        }
    }

    private suspend fun copySmbFileToDownloads(
        app: HomeCinemaApp,
        item: MediaItemEntity,
        source: SmbSourceEntity,
        fileName: String,
        mimeType: String,
        smbPath: String,
        customTreeUri: Uri?
    ) {
        val uri = DownloadStorage.getOrCreateEntry(applicationContext, item, fileName, mimeType, customTreeUri)
        val smbCompanionFile = app.smbManager.openFile(source.id, source.toSmbConfig(), smbPath)
        smbCompanionFile.inputStream.use { input ->
            DownloadStorage.openOutputStream(applicationContext, uri, append = false).use { output ->
                input.copyTo(output)
            }
        }
        DownloadStorage.markComplete(applicationContext, uri)
    }

    private fun openResumableInput(smbFile: SmbFile, offset: Long): InputStream =
        if (offset > 0) {
            SmbRandomAccessInputStream(SmbRandomAccessFile(smbFile, "r").apply { seek(offset) })
        } else {
            smbFile.inputStream
        }

    private fun foregroundInfo(itemId: String, title: String, percent: Int): ForegroundInfo {
        val notification = buildNotification(itemId, title, percent)
        return ForegroundInfo(notificationId(itemId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun updateNotification(itemId: String, title: String, percent: Int) {
        val notification = buildNotification(itemId, title, percent)
        applicationContext.getSystemService(NotificationManager::class.java)
            ?.notify(notificationId(itemId), notification)
    }

    private fun buildNotification(itemId: String, title: String, percent: Int) =
        NotificationCompat.Builder(applicationContext, HomeCinemaApp.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Загрузка… $percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun notificationId(itemId: String) = itemId.hashCode()
}

/**
 * Byte offset to resume a partial download from. Only resumes when the local partial file is
 * smaller than the remote file's current length - if it's equal or bigger (source file
 * changed, or a rare crash-right-after-the-last-byte edge case) or the remote length couldn't
 * be determined, the safe fallback is to delete and restart from 0 rather than risk writing a
 * corrupt file.
 */
internal fun resumeOffset(localLength: Long, remoteLength: Long): Long =
    if (localLength > 0 && remoteLength > 0 && localLength < remoteLength) localLength else 0L

/**
 * Copies [input] to [output] in [BUFFER_SIZE] chunks, calling [onProgress] once per percent
 * change (not per chunk) so callers can persist progress via a real suspend function - that
 * suspend call is also what makes worker cancellation actually take effect promptly, since the
 * blocking read/write loop itself has no other suspension point.
 */
internal suspend fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    startBytes: Long,
    totalBytes: Long,
    onProgress: suspend (percent: Int) -> Unit
) {
    val buffer = ByteArray(BUFFER_SIZE)
    var copiedBytes = startBytes
    var lastPercent = if (totalBytes > 0) ((copiedBytes * 100) / totalBytes).toInt() else -1
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
        copiedBytes += read
        if (totalBytes > 0) {
            val percent = ((copiedBytes * 100) / totalBytes).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(percent)
            }
        }
    }
}
