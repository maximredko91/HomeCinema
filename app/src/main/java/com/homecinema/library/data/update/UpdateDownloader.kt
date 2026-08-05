package com.homecinema.library.data.update

import android.content.Context
import com.homecinema.library.data.download.copyWithProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the update APK straight into the app's own storage so installing a new build
 * doesn't require leaving the app for a browser download - reuses the same chunked
 * copy-with-progress loop [com.homecinema.library.data.download.DownloadWorker] uses for videos.
 */
object UpdateDownloader {
    suspend fun download(context: Context, url: String, version: String, onProgress: (Int) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 10000
                    readTimeout = 15000
                }
                val totalBytes = connection.contentLengthLong
                val updatesDir = File(context.getExternalFilesDir(null), "updates").apply {
                    mkdirs()
                    // Don't let every previous version's APK pile up here forever.
                    listFiles()?.forEach { it.delete() }
                }
                val destFile = File(updatesDir, "HomeCinema-$version.apk")
                connection.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        copyWithProgress(input, output, 0L, totalBytes) { percent -> onProgress(percent) }
                    }
                }
                destFile
            }
        }
}
