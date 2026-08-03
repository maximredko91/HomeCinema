package com.homecinema.library.data.scanner

import android.content.Context
import com.homecinema.library.data.db.LibraryDao
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType
import com.homecinema.library.data.db.SmbSourceDao
import com.homecinema.library.data.db.SmbSourceEntity
import com.homecinema.library.data.nfo.EpisodeFilenamePattern
import com.homecinema.library.data.nfo.NfoData
import com.homecinema.library.data.nfo.NfoParser
import com.homecinema.library.data.nfo.looksAnimated
import com.homecinema.library.data.smb.SmbManager
import com.homecinema.library.data.smb.SmbSourceResolver
import com.homecinema.library.data.smb.toSmbConfig
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
private val FANART_NAMES = setOf("fanart.jpg", "fanart.png", "backdrop.jpg", "backdrop.png", "landscape.jpg", "landscape.png")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
// Movie folders (Kodi/Radarr convention) usually name artwork after the video file
// itself, e.g. "Movie (2020)-poster.jpg" or "Movie (2020).jpg", rather than the fixed
// "poster.jpg" used at TV show roots - so movies need a broader match than POSTER_NAMES.
private val MOVIE_POSTER_SUFFIXES = listOf("-poster", "-folder", "-cover", "")
private val MOVIE_FANART_SUFFIXES = listOf("-fanart", "-backdrop", "-landscape")
// Kodi scrapers name episode thumbnails either "<episode>-thumb.jpg" or, just as often,
// with the exact same basename as the video ("S01E01.mkv" + "S01E01.jpg") - the "" suffix
// covers that second, very common case.
private val EPISODE_THUMB_SUFFIXES = listOf("-thumb", "")
private const val MAX_ACTORS = 8

class LibraryScanner(
    private val smbManager: SmbManager,
    private val dao: LibraryDao,
    private val sourceDao: SmbSourceDao,
    private val sourceResolver: SmbSourceResolver,
    private val context: Context
) {

    private val posterCacheDir: File by lazy {
        File(context.cacheDir, "posters").apply { mkdirs() }
    }

    suspend fun scan(onProgress: (ScanProgress) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val sources = sourceDao.observeAll().first()
            if (sources.isEmpty()) {
                onProgress(ScanProgress.Done(0))
                return@withContext
            }

            // A single unreachable source (e.g. a NAS that's asleep) shouldn't block scanning
            // the rest - that resilience is the whole point of supporting several sources.
            val allResults = mutableListOf<MediaItemEntity>()
            for (source in sources) {
                try {
                    allResults += scanSource(source, onProgress)
                } catch (e: Exception) {
                    onProgress(ScanProgress.Error("«${source.name}»: ${e.message ?: "не удалось просканировать"}"))
                }
            }

            // Preserve any existing download/playback progress when re-scanning.
            val merged = allResults.map { fresh ->
                val existing = dao.getById(fresh.id)
                if (existing != null) {
                    fresh.copy(
                        localFilePath = existing.localFilePath,
                        downloadState = existing.downloadState,
                        downloadProgress = existing.downloadProgress,
                        playbackPositionMs = existing.playbackPositionMs,
                        durationMs = existing.durationMs,
                        lastPlayedAt = existing.lastPlayedAt
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

    /** Scans a single configured source and returns its raw results (not yet merged/saved). */
    private suspend fun scanSource(source: SmbSourceEntity, onProgress: (ScanProgress) -> Unit): List<MediaItemEntity> {
            val config = sourceResolver.withRealPassword(source).toSmbConfig()
            val allFiles = smbManager.listAllFilesRecursive(source.id, config)
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
                val fanartFile = files.firstOrNull { it.name.lowercase() in FANART_NAMES }

                val id = hashOf(showRoot)
                val title = nfoData?.title?.takeIf { it.isNotBlank() } ?: showRoot.trimEnd('/').substringAfterLast('/')
                val mediaType = if (nfoData?.genres.orEmpty().looksAnimated()) MediaType.CARTOON_SERIES else MediaType.TV_SHOW

                onProgress(ScanProgress.Parsing(processed, totalFolders, title))
                val posterLocalPath = posterFile?.let { cacheImage(it, id, "") }
                val fanartLocalPath = fanartFile?.let { cacheImage(it, id, "-fanart") }

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
                        lastScanned = System.currentTimeMillis(),
                        sourceId = source.id,
                        fanartLocalPath = fanartLocalPath,
                        runtimeMinutes = nfoData?.runtimeMinutes,
                        country = nfoData?.country,
                        director = nfoData?.directors.orEmpty().joinToString(", ").ifBlank { null },
                        actors = nfoData?.actors.orEmpty().take(MAX_ACTORS).joinToString(", ").ifBlank { null },
                        collectionName = nfoData?.collectionName
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
                    // Kodi/scrapers usually drop a "<episode>-thumb.jpg" next to the video -
                    // shown in the episode list instead of just the parent show's poster.
                    val thumbFile = findImageFile(episodeFolder, videoFile.name, emptySet(), EPISODE_THUMB_SUFFIXES)
                    val thumbLocalPath = thumbFile?.let { cacheImage(it, epId, "") }
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
                            posterLocalPath = thumbLocalPath,
                            lastScanned = System.currentTimeMillis(),
                            sourceId = source.id,
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
                val posterFile = findImageFile(files, videoFile.name, POSTER_NAMES, MOVIE_POSTER_SUFFIXES)
                val fanartFile = findImageFile(files, videoFile.name, FANART_NAMES, MOVIE_FANART_SUFFIXES)
                val nfoData = nfoFile?.let { f -> runCatching { NfoParser.parse(f.inputStream) }.getOrNull() }

                val id = hashOf(folderPath)
                val title = nfoData?.title?.takeIf { it.isNotBlank() } ?: videoFile.name.substringBeforeLast('.')
                val mediaType = if (nfoData?.genres.orEmpty().looksAnimated()) MediaType.CARTOON else MediaType.MOVIE

                onProgress(ScanProgress.Parsing(processed, totalFolders, title))
                val posterLocalPath = posterFile?.let { cacheImage(it, id, "") }
                val fanartLocalPath = fanartFile?.let { cacheImage(it, id, "-fanart") }

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
                        lastScanned = System.currentTimeMillis(),
                        sourceId = source.id,
                        fanartLocalPath = fanartLocalPath,
                        runtimeMinutes = nfoData?.runtimeMinutes,
                        country = nfoData?.country,
                        director = nfoData?.directors.orEmpty().joinToString(", ").ifBlank { null },
                        actors = nfoData?.actors.orEmpty().take(MAX_ACTORS).joinToString(", ").ifBlank { null },
                        collectionName = nfoData?.collectionName
                    )
                )
            }

            return results
    }

    private fun cacheImage(smbFile: SmbFile, id: String, suffix: String): String? {
        return try {
            val ext = extensionOf(smbFile.name).ifBlank { "jpg" }
            val dest = File(posterCacheDir, "$id$suffix.$ext")
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

    /**
     * Finds a poster/fanart image for a standalone movie or cartoon folder. Tries the
     * fixed TV-show-style names first, then falls back to names derived from the video
     * file's own basename, which is how movie artwork is conventionally named.
     */
    private fun findImageFile(
        files: List<SmbFile>,
        videoFileName: String,
        fixedNames: Set<String>,
        suffixes: List<String>
    ): SmbFile? {
        files.firstOrNull { it.name.lowercase() in fixedNames }?.let { return it }

        val base = videoFileName.substringBeforeLast('.').lowercase()
        return files.firstOrNull { f ->
            val ext = extensionOf(f.name)
            if (ext !in IMAGE_EXTENSIONS) return@firstOrNull false
            val nameWithoutExt = f.name.lowercase().removeSuffix(".$ext")
            suffixes.any { suffix -> nameWithoutExt == base + suffix }
        }
    }

    private fun hashOf(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
