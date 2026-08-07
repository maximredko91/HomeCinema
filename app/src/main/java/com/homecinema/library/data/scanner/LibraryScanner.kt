package com.homecinema.library.data.scanner

import android.content.Context
import com.homecinema.library.R
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
import com.homecinema.library.data.smb.toSmbUserMessage
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

sealed class ScanProgress {
    data class Scanning(val filesFound: Int) : ScanProgress()
    data class Parsing(val current: Int, val total: Int, val currentTitle: String) : ScanProgress()
    data class Done(val itemsFound: Int) : ScanProgress()
    data class Error(val message: String) : ScanProgress()
}

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "m4v", "ts", "wmv", "webm")
private const val SCANNING_PROGRESS_STEP = 5
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
// How many NFO/image reads can be in flight over SMB at once. Each one is a blocking
// network round-trip; running them one at a time made a several-thousand-title library
// take many minutes, almost all of it spent waiting rather than actually transferring
// bytes. Kept modest since this is still one shared session/router, not a datacenter.
private const val MAX_CONCURRENT_IO = 6
// How many titles to write to the database per batch while scanning. The library screen
// observes this table, so writing incrementally (instead of once at the very end) is what
// makes titles appear as they're found rather than only once the whole scan is done.
private const val FLUSH_BATCH_SIZE = 30

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
    private val ioSemaphore = Semaphore(MAX_CONCURRENT_IO)

    suspend fun scan(onProgress: (ScanProgress) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val sources = sourceDao.observeAll().first()
            if (sources.isEmpty()) {
                onProgress(ScanProgress.Done(0))
                return@withContext
            }

            // A single unreachable source (e.g. a NAS that's asleep) shouldn't block scanning
            // the rest - that resilience is the whole point of supporting several sources.
            // Errors are collected rather than reported immediately: onProgress(Error(...))
            // here used to get silently overwritten by the Done(...) at the end of this
            // function, so a failing source looked exactly like "0 titles found" with no
            // indication anything had gone wrong.
            val allResults = mutableListOf<MediaItemEntity>()
            val errors = mutableListOf<String>()
            for (source in sources) {
                try {
                    allResults += scanSource(source, onProgress)
                } catch (e: Exception) {
                    errors += "«${source.name}»: ${e.toSmbUserMessage(context)}"
                }
            }

            // Cleanup needs the complete picture across every source, so it stays a final
            // step - deleting per-source-as-you-go would make everything from source B look
            // "missing" while source A was still being scanned.
            dao.deleteMissingTopLevel(allResults.filter { it.mediaType != MediaType.EPISODE }.map { it.folderPath })
            dao.deleteMissingEpisodes(allResults.filter { it.mediaType == MediaType.EPISODE }.map { it.id })

            // allResults.size counts every episode individually too, alongside movies/shows -
            // accurate for the cleanup calls above, but a very different number from what the
            // user actually sees as "titles" in the library grid (a show is one card, however
            // many episodes it has). Reported count excludes episodes for the same reason
            // effectiveColumns/totalFolders exclude season subfolders elsewhere in this file -
            // it should mean "how many cards did this add", not "how many rows got written".
            val topLevelCount = allResults.count { it.mediaType != MediaType.EPISODE }
            if (errors.isNotEmpty()) {
                onProgress(ScanProgress.Error(context.getString(R.string.scan_error_summary, topLevelCount, errors.joinToString("; "))))
            } else {
                onProgress(ScanProgress.Done(topLevelCount))
            }
        } catch (e: Exception) {
            onProgress(ScanProgress.Error(e.toSmbUserMessage(context)))
        }
    }

    /** Scans a single configured source and returns its raw results (not yet merged/saved
     * for cleanup bookkeeping in [scan] - the actual DB writes happen incrementally via
     * [flush] as each show/batch of movies finishes, not from this return value). */
    private suspend fun scanSource(source: SmbSourceEntity, onProgress: (ScanProgress) -> Unit): List<MediaItemEntity> {
        val config = sourceResolver.withRealPassword(source).toSmbConfig()
        // Only video files count toward the "Обход папок… найдено: N" progress line - the
        // raw file count (every .nfo/.srt/poster/fanart alongside each video) used to show
        // there instead, which read as noise unrelated to "how much media is there".
        val videoCount = AtomicInteger(0)
        val allFiles = smbManager.listAllFilesRecursive(source.id, config) { file ->
            if (extensionOf(file.name) in VIDEO_EXTENSIONS) {
                val count = videoCount.incrementAndGet()
                if (count % SCANNING_PROGRESS_STEP == 0) onProgress(ScanProgress.Scanning(count))
            }
        }
        onProgress(ScanProgress.Scanning(videoCount.get()))

        val byFolder = allFiles.groupBy { parentPath(it) }

        // Pass 1: find every folder that is a TV/cartoon-series root (has a tvshow.nfo).
        val showRootPaths = byFolder.filterValues { files ->
            files.any { it.name.equals("tvshow.nfo", ignoreCase = true) }
        }.keys

        fun showRootFor(folderPath: String): String? =
            showRootPaths.firstOrNull { root -> folderPath == root || folderPath.startsWith(root) }

        val results = mutableListOf<MediaItemEntity>()
        // Folders that will actually get their own "Разбор X/Y" progress tick - one per show
        // root (not one per season subfolder underneath it, those episodes get swept in without
        // their own tick) plus one per standalone movie folder. byFolder.size (every folder
        // found, including season subfolders and any folder with no video at all) is always
        // bigger than this whenever a show has season subfolders - using it as the denominator
        // meant the counter could never reach 100% of its own total even once the scan had
        // genuinely finished everything there was to process.
        val movieFolders = byFolder.keys.filter { showRootFor(it) == null }
        val totalFolders = showRootPaths.size + movieFolders.size
        val processed = AtomicInteger(0)

        // Pass 2: one shell entity per show root, from its tvshow.nfo + poster, plus every
        // episode underneath it. Episodes within a show are scanned concurrently (bounded
        // by ioSemaphore) since each needs its own NFO + thumbnail round-trip over SMB.
        for (showRoot in showRootPaths) {
            val files = byFolder[showRoot].orEmpty()
            val nfoFile = files.first { it.name.equals("tvshow.nfo", ignoreCase = true) }
            val nfoData = runCatching { NfoParser.parse(nfoFile.inputStream) }.getOrNull()
            val posterFile = files.firstOrNull { it.name.lowercase() in POSTER_NAMES }
            val fanartFile = files.firstOrNull { it.name.lowercase() in FANART_NAMES }

            val id = hashOf(showRoot)
            val title = nfoData?.title?.takeIf { it.isNotBlank() } ?: showRoot.trimEnd('/').substringAfterLast('/')
            val mediaType = if (nfoData?.genres.orEmpty().looksAnimated()) MediaType.CARTOON_SERIES else MediaType.TV_SHOW

            onProgress(ScanProgress.Parsing(processed.incrementAndGet(), totalFolders, title))
            val posterLocalPath = posterFile?.let { cacheImage(it, id, "") }
            val fanartLocalPath = fanartFile?.let { cacheImage(it, id, "-fanart") }

            val showEntity = MediaItemEntity(
                id = id,
                title = title,
                originalTitle = nfoData?.originalTitle?.takeIf { it.isNotBlank() && it != title },
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
                collectionName = nfoData?.collectionName,
                tags = nfoData?.tags?.joinToString(", ").orEmpty(),
                mpaa = nfoData?.mpaa,
                studio = nfoData?.studios?.joinToString(", ")?.ifBlank { null },
                tagline = nfoData?.tagline
            )

            val episodeVideoFiles = allFiles.filter { f ->
                val p = parentPath(f)
                (p == showRoot || p.startsWith(showRoot)) && extensionOf(f.name) in VIDEO_EXTENSIONS
            }
            val episodeEntities = coroutineScope {
                episodeVideoFiles.mapIndexed { index, videoFile ->
                    async {
                        ioSemaphore.withPermit {
                            buildEpisodeEntity(source.id, id, index, videoFile, byFolder[parentPath(videoFile)].orEmpty())
                        }
                    }
                }.awaitAll()
            }

            results.add(showEntity)
            results.addAll(episodeEntities)
            flush(listOf(showEntity) + episodeEntities)
        }

        // Pass 3: everything else - standalone movies/cartoons, one per remaining folder.
        // Same concurrency treatment, batched so the library fills in as it goes rather
        // than staying empty until all several thousand folders are done. (movieFolders
        // computed above, alongside showRootPaths, for the totalFolders progress denominator.)
        for (chunk in movieFolders.chunked(FLUSH_BATCH_SIZE)) {
            val entities = coroutineScope {
                chunk.map { folderPath ->
                    async {
                        ioSemaphore.withPermit {
                            buildMovieEntity(source.id, folderPath, byFolder[folderPath].orEmpty(), totalFolders, processed, onProgress)
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            results.addAll(entities)
            flush(entities)
        }

        return results
    }

    private fun buildEpisodeEntity(
        sourceId: String,
        showId: String,
        index: Int,
        videoFile: SmbFile,
        episodeFolder: List<SmbFile>
    ): MediaItemEntity {
        val matchingNfo = episodeFolder.firstOrNull {
            it.name.equals(videoFile.name.substringBeforeLast('.') + ".nfo", ignoreCase = true)
        } ?: episodeFolder.firstOrNull { it.name.endsWith(".nfo", ignoreCase = true) }

        val epNfo: NfoData? = matchingNfo?.let { runCatching { NfoParser.parse(it.inputStream) }.getOrNull() }
        val filenameGuess = EpisodeFilenamePattern.extract(videoFile.name)

        val season = epNfo?.season ?: filenameGuess?.first ?: 1
        val episodeNum = epNfo?.episode ?: filenameGuess?.second ?: (index + 1)
        val epTitle = epNfo?.title?.takeIf { it.isNotBlank() } ?: videoFile.name.substringBeforeLast('.')

        val epId = hashOf(videoFile.path)
        // Kodi/scrapers usually drop a "<episode>-thumb.jpg" next to the video - shown in
        // the episode list instead of just the parent show's poster.
        val thumbFile = findImageFile(episodeFolder, videoFile.name, emptySet(), EPISODE_THUMB_SUFFIXES)
        val thumbLocalPath = thumbFile?.let { cacheImage(it, epId, "") }

        return MediaItemEntity(
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
            sourceId = sourceId,
            parentShowId = showId,
            season = season,
            episodeNumber = episodeNum
        )
    }

    private fun buildMovieEntity(
        sourceId: String,
        folderPath: String,
        files: List<SmbFile>,
        totalFolders: Int,
        processed: AtomicInteger,
        onProgress: (ScanProgress) -> Unit
    ): MediaItemEntity? {
        val videoFile = files
            .filter { extensionOf(it.name) in VIDEO_EXTENSIONS }
            .filterNot { it.name.contains("trailer", ignoreCase = true) }
            .maxByOrNull { runCatching { it.length() }.getOrDefault(0L) }
            ?: return null // no playable video here, skip folder

        val nfoFile = files.firstOrNull { it.name.endsWith(".nfo", ignoreCase = true) }
        val posterFile = findImageFile(files, videoFile.name, POSTER_NAMES, MOVIE_POSTER_SUFFIXES)
        val fanartFile = findImageFile(files, videoFile.name, FANART_NAMES, MOVIE_FANART_SUFFIXES)
        val nfoData = nfoFile?.let { f -> runCatching { NfoParser.parse(f.inputStream) }.getOrNull() }

        val id = hashOf(folderPath)
        val title = nfoData?.title?.takeIf { it.isNotBlank() } ?: videoFile.name.substringBeforeLast('.')
        val mediaType = if (nfoData?.genres.orEmpty().looksAnimated()) MediaType.CARTOON else MediaType.MOVIE

        onProgress(ScanProgress.Parsing(processed.incrementAndGet(), totalFolders, title))
        val posterLocalPath = posterFile?.let { cacheImage(it, id, "") }
        val fanartLocalPath = fanartFile?.let { cacheImage(it, id, "-fanart") }

        return MediaItemEntity(
            id = id,
            title = title,
            originalTitle = nfoData?.originalTitle?.takeIf { it.isNotBlank() && it != title },
            year = nfoData?.year,
            genres = nfoData?.genres?.joinToString(", ").orEmpty(),
            plot = nfoData?.plot.orEmpty(),
            rating = nfoData?.rating,
            mediaType = mediaType,
            folderPath = folderPath,
            videoFilePath = videoFile.path,
            posterLocalPath = posterLocalPath,
            lastScanned = System.currentTimeMillis(),
            sourceId = sourceId,
            fanartLocalPath = fanartLocalPath,
            runtimeMinutes = nfoData?.runtimeMinutes,
            country = nfoData?.country,
            director = nfoData?.directors.orEmpty().joinToString(", ").ifBlank { null },
            actors = nfoData?.actors.orEmpty().take(MAX_ACTORS).joinToString(", ").ifBlank { null },
            collectionName = nfoData?.collectionName,
            tags = nfoData?.tags?.joinToString(", ").orEmpty(),
            mpaa = nfoData?.mpaa,
            studio = nfoData?.studios?.joinToString(", ")?.ifBlank { null },
            tagline = nfoData?.tagline
        )
    }

    /** Merges fresh results with whatever's already saved for those ids (so a rescan
     * doesn't clobber download/playback progress) and writes them immediately - called
     * repeatedly as the scan progresses, which is what makes titles show up in the library
     * live instead of only once the entire scan (all NFOs, all images) has finished. */
    private suspend fun flush(batch: List<MediaItemEntity>) {
        if (batch.isEmpty()) return
        val existingById = dao.getByIds(batch.map { it.id }).associateBy { it.id }
        val merged = batch.map { fresh ->
            existingById[fresh.id]?.let { existing ->
                fresh.copy(
                    localFilePath = existing.localFilePath,
                    downloadState = existing.downloadState,
                    downloadProgress = existing.downloadProgress,
                    playbackPositionMs = existing.playbackPositionMs,
                    durationMs = existing.durationMs,
                    lastPlayedAt = existing.lastPlayedAt,
                    // isFavorite is pure user state, never derived from .nfo - missing from
                    // this list meant every rescan (including the nightly automatic one)
                    // silently reset every title's favorite flag back to false. Real data
                    // loss, not just a cosmetic gap: confirmed by a user losing everything
                    // they'd favorited across earlier versions.
                    isFavorite = existing.isFavorite,
                    // Same reasoning for a manually-corrected movie/cartoon or
                    // show/cartoon-series classification - once the user has overridden it,
                    // a rescan must not silently recompute it back from the .nfo genre
                    // keywords (the whole point of the override is that heuristic was wrong).
                    mediaType = if (existing.mediaTypeOverridden) existing.mediaType else fresh.mediaType,
                    mediaTypeOverridden = existing.mediaTypeOverridden
                )
            } ?: fresh
        }
        dao.upsertAll(merged)
    }

    private fun cacheImage(smbFile: SmbFile, id: String, suffix: String): String? {
        return try {
            val ext = extensionOf(smbFile.name).ifBlank { "jpg" }
            val dest = File(posterCacheDir, "$id$suffix.$ext")
            // Posters/fanarts/thumbnails essentially never change once scraped - on a
            // rescan, re-downloading an image that's already sitting on disk was pure
            // wasted network time, multiplied by every image in the whole library, every
            // single rescan (including the automatic one on every app launch).
            if (dest.exists() && dest.length() > 0) return dest.absolutePath
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
