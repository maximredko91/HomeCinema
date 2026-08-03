package com.homecinema.library.data.scanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.homecinema.library.HomeCinemaApp
import kotlinx.coroutines.flow.first

/**
 * Runs the same [LibraryScanner] the manual "rescan now" button uses, but on WorkManager's
 * own persistent 24h schedule (see HomeCinemaApp.scheduleAutoRescan) instead of only at app
 * launch. Silent by design - no notification, the Library screen already picks up new rows
 * reactively via Room Flow whenever it's next opened.
 */
class LibraryRescanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HomeCinemaApp

        val autoEnabled = app.settingsStore.autoRescanEnabledFlow.first()
        val configured = app.repository.observeSources().first().isNotEmpty()
        if (!autoEnabled || !configured) return Result.success()

        return try {
            app.repository.rescan { }
            app.settingsStore.recordAutoScanNow()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
