package com.homecinema.library.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.DownloadState
import com.homecinema.library.data.db.LibraryDao
import com.homecinema.library.data.db.MediaItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Downloads a movie/cartoon/episode from the SMB share to app-private local storage so it can
 * be watched offline afterwards. A thin facade over WorkManager/[DownloadWorker] - the actual
 * transfer is a persistent WorkManager job that survives process death and resumes an
 * interrupted download from where it left off, rather than a scope-bound coroutine that would
 * vanish if the app process got killed mid-download.
 */
class DownloadManager(
    private val dao: LibraryDao,
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** itemId -> percent, only contains entries for downloads currently in progress. */
    val liveProgress: StateFlow<Map<String, Int>> = workManager
        .getWorkInfosByTagFlow(DOWNLOAD_WORK_TAG)
        .map { infos -> infos.toProgressMap() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun isDownloading(itemId: String): Boolean = liveProgress.value.containsKey(itemId)

    fun start(item: MediaItemEntity) {
        if (item.videoFilePath.isBlank()) return

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("itemId" to item.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(DOWNLOAD_WORK_TAG)
            // Expedited (falling back to regular work once the quota's used up) so a
            // WorkManager-initiated resume - not just the user tapping "Скачать" - is still
            // allowed to call setForeground() and show the progress notification; a plain
            // background job hits Android 12+'s background-foreground-service-start
            // restriction and the notification silently fails to appear (download itself
            // still completes either way, setForeground() is wrapped in runCatching).
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(downloadWorkName(item.id), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(itemId: String) {
        // Reset the DB row and delete the partial file *before* cancelling the work, so
        // DownloadWorker's cancellation path (which deliberately touches neither) can tell an
        // explicit cancel apart from a system-triggered stop just by looking at the DB state
        // once cancellation lands.
        scope.launch(Dispatchers.IO) {
            val item = dao.getById(itemId) ?: return@launch
            destFileFor(context as HomeCinemaApp, item).delete()
            dao.updateDownloadResult(itemId, DownloadState.NONE, 0, null)
        }
        workManager.cancelUniqueWork(downloadWorkName(itemId))
    }

    suspend fun deleteDownload(item: MediaItemEntity) {
        item.localFilePath?.let { File(it).delete() }
        dao.updateDownloadResult(item.id, DownloadState.NONE, 0, null)
    }
}

private fun List<WorkInfo>.toProgressMap(): Map<String, Int> = this
    .filter { it.state == WorkInfo.State.RUNNING }
    .mapNotNull { info ->
        val id = info.progress.getString("itemId") ?: return@mapNotNull null
        val percent = info.progress.getInt("progress", -1)
        if (percent < 0) null else id to percent
    }
    .toMap()
