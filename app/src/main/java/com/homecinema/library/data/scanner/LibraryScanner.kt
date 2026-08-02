package com.homecinema.library.data.scanner

import android.content.Context
import com.homecinema.library.data.db.LibraryDao
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType
import com.homecinema.library.data.nfo.EpisodeFilenamePattern
import com.homecinema.library.data.nfo.NfoData
import com.homecinema.library.data.nfo.NfoParser
import com.homecinema.library.data.nfo.looksAnimated
import com.homecinema.library.data.settings.SettingsStore
import com.homecinema.library.data.smb.SmbManager
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

sealed class ScanProgress {
    data class Scanning(val filesFound: Int) : ScanProgress()
    data class Parsing(val current: Int, val total: Int, val currentTitle: String) : ScanProgress()
    data class Done(val itemsFound: Int) : ScanProgress()
    data class Error(val message: String) : ScanProgress()
}

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "m4v", "ts", "wmv", "webm")
private val POSTER_NAMES = setOf("poster.jpg", "poster.png", "folder.jpg", "folder.png", "cover.jpg", "cover.png")

class LibraryScanner(
    private val smbManager: SmbManager,
    private val dao: LibraryDao,
    private val settingsStore: SettingsStore,
    private val context: Context
) {

    private val posterCacheDir: File by lazy {
        File(context.cacheDir, "posters").apply { mkdirs() }
    }

    suspend fun scan(onProgress: (ScanProgress) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val config = settingsStore.currentConfig()
            val allFiles = smbManager.listAllFilesRecursive(config)
            onProgress(ScanProgress.Scanning(allFiles.size))

            val byFolder = allFiles.groupBy { parentPath(it) }

            // Pass 1: find every folder that is a TV/cartoon-series root (has a tvshow.nfo).
            val showRootPaths = byFolder.filterValues { files ->
                files.any { it.name.equals("tvshow.nfo", ignoreCase = true) }
            }.keys

            fun showRootFor(folderPath: String): String? =
                showRootPaths.firstOrNull { root -> folderPath == root || folderPath.startsWith(root) }

            val results = mutableListOf<MediaItemEntity>()

            // Pass 2: build one shell entity per show root, from its tvshow.nfo + poster.
            var processed = 0
            val totalFolders = byFolder.size
            for (showRoot in showRootPaths) {
                processed++
                val files = byFolder[showRoot].orEmpty()
                val nfoFile = files.first { it.name.equals("tvshow.nfo", ignoreCase = true) }
                val nfoData = runCatching { NfoParser.parse(nfoFile.inputStream) }.getOrNull()
                val posterFile = files.firstOrNull { it.name.lowercase() in POSTER_NAMES }

                val id = hashOf(showRoot)
                val title = nfoData?.title?.takeIf { it.isNotBlank() } ?: showRoot.trimEnd('/').substringAfterLast('/')
                val mediaType = if (nfoData?.genres.orEmpty().looksAnimated()) MediaType.CARTOON_SERIES else MediaType.TV_SHOW

                onProgress(ScanProgress.Parsing(processed, totalFolders, title))
                val posterLocalPath = posterFile?.let { cachePoster(it, id) }

                results.add(
                    MediaItemEntity(
                        id = id,
                        title = title,
                        year = nfoData?.year,
                        genres = nfoData?.genres?.joinToString(", ").orEmpty(),
                        plot = nfoData?.plot.orEmpty(),
                        rating = nfoData?.rating,
                        mediaType = mediaType,
                        folderPath = showRoot,
                        videoFilePath = "", // shells are not directly playable
                        posterLocalPath = posterLocalPath,
                        lastScanned = System.currentTimeMillis()
                    )
                )

                // Episodes: every video file anywhere under this show root.
                val episodeVideoFiles = allFiles.filter { f ->
                    val p = parentPath(f)
                    (p == showRoot || p.startsWith(showRoot)) && extensionOf(f.name) in VIDEO_EXTENSIONS
                }
                episodeVideoFiles.forEachIndexed { index, videoFile ->
                    val episodeFolder = byFolder[parentPath(videoFile)].orEmpty()
                    val matchingNfo = episodeFolder.firstOrNull {
                        it.name.equals(videoFile.name.substringBeforeLast('.') + ".nfo", ignoreCase = true)
                    } ?: episodeFolder.firstOrNull { it.name.endsWith(".nfo", ignoreCase = true) }

                    val epNfo: NfoData? = matchingNfo?.let { runCatching { NfoParser.parse(it.inputStream) }.getOrNull() }
                    val filenameGuess = EpisodeFilenamePattern.extract(videoFile.name)

                    val season = epNfo?.season ?: filenameGuess?.first ?: 1
                    val episodeNum = epNfo?.episode ?: filenameGuess?.second ?: (index + 1)
                    val epTitle = epNfo?.title?.takeIf { it.isNotBlank() }
                        ?: videoFile.name.substringBeforeLast('.')

                    val epId = hashOf(videoFile.path)
                    results.add(
                        MediaItemEntity(
                            id = epId,
                            title = epTitle,
                            year = null,
                            genres = "",
                            plot = epNfo?.plot.orEmpty(),
                            rating = epNfo?.rating,
                            mediaType = MediaType.EPISODE,
                            folderPath = parentPath(videoFile),
                            videoFilePath = videoFile.path,
                            posterLocalPath = null,
                            lastScanned = System.currentTimeMillis(),
                            parentShowId = id,
                            season = season,
                            episodeNumber = episodeNum
                        )
                    )
                }
            }

            // Pass 3: everything else - standalone movies/cartoons, one per remaining folder.
            val movieFolders = byFolder.keys.filter { showRootFor(it) == null }
            movieFolders.forEachIndexed { index, folderPath ->
                processed++
                val files = byFolder[folderPath].orEmpty()
                val videoFile = files
                    .filter { extensionOf(it.name) in VIDEO_EXTENSIONS }
                    .filterNot { it.name.contains("trailer", ignoreCase = true) }
                    .maxByOrNull { runCatching { it.length() }.getOrDefault(0L) }
                    ?: return@forEachIndexed // no playable video here, skip folder

                val nfoFile = files.firstOrNull { it.name.endsWith(".nfo", ignoreCase = true) }
                val posterFile = files.firstOrNull { it.name.lowercase() in POSTER_NAMES }
                val nfoData = nfoFile?.let { f -> runCatching { NfoParser.parse(f.inputStream) }.getOrNull() }

                val id = hashOf(folderPath)
                val title = nfoData?.title?.takeIf { it.isNotBlank() } ?: videoFile.name.substringBeforeLast('.')
                val mediaType = if (nfoData?.genres.orEmpty().looksAnimated()) MediaType.CARTOON else MediaType.MOVIE

                onProgress(ScanProgress.Parsing(processed, totalFolders, title))
                val posterLocalPath = posterFile?.let { cachePoster(it, id) }

                results.add(
                    MediaItemEntity(
                        id = id,
                        title = title,
                        year = nfoData?.year,
                        genres = nfoData?.genres?.joinToString(", ").orEmpty(),
                        plot = nfoData?.plot.orEmpty(),
                        rating = nfoData?.rating,
                        mediaType = mediaType,
                        folderPath = folderPath,
                        videoFilePath = videoFile.path,
                        posterLocalPath = posterLocalPath,
                        lastScanned = System.currentTimeMillis()
                    )
                )
            }

            // Preserve any existing download progress/state when re-scanning.
            val merged = results.map { fresh ->
                val existing = dao.getById(fresh.id)
                if (existing != null) {
                    fresh.copy(
                        localFilePath = existing.localFilePath,
                        downloadState = existing.downloadState,
                        downloadProgress = existing.downloadProgress
                    )
                } else fresh
            }

            dao.upsertAll(merged)
            dao.deleteMissingTopLevel(merged.filter { it.mediaType != MediaType.EPISODE }.map { it.folderPath })
            dao.deleteMissingEpisodes(merged.filter { it.mediaType == MediaType.EPISODE }.map { it.id })

            onProgress(ScanProgress.Done(merged.size))
        } catch (e: Exception) {
            onProgress(ScanProgress.Error(e.message ?: "Unknown error while scanning"))
        }
    }

    private fun cachePoster(smbFile: SmbFile, id: String): String? {
        return try {
            val ext = extensionOf(smbFile.name).ifBlank { "jpg" }
            val dest = File(posterCacheDir, "$id.$ext")
            smbFile.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun parentPath(file: SmbFile): String {
        val trimmed = file.path.trimEnd('/')
        return trimmed.substringBeforeLast('/') + "/"
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    private fun hashOf(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
