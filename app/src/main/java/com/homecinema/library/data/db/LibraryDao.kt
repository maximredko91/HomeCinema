package com.homecinema.library.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    // Top-level browsable cards: movies, cartoons, and series shells (not raw episodes)
    @Query("SELECT * FROM media_items WHERE mediaType != 'EPISODE' ORDER BY title ASC")
    fun observeLibrary(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE mediaType = :type ORDER BY title ASC")
    fun observeByType(type: MediaType): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MediaItemEntity?

    // Bulk lookup used by the scanner to preserve download/playback progress when merging
    // a freshly-scanned batch - one query per batch instead of one per item.
    @Query("SELECT * FROM media_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE parentShowId = :showId ORDER BY season ASC, episodeNumber ASC")
    fun observeEpisodesForShow(showId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE downloadState = 'COMPLETED' ORDER BY title ASC")
    fun observeDownloaded(): Flow<List<MediaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity)

    @Query("DELETE FROM media_items")
    suspend fun clearAll()

    @Query("DELETE FROM media_items WHERE folderPath NOT IN (:keepFolderPaths) AND mediaType != 'EPISODE'")
    suspend fun deleteMissingTopLevel(keepFolderPaths: List<String>)

    @Query("DELETE FROM media_items WHERE mediaType = 'EPISODE' AND id NOT IN (:keepIds)")
    suspend fun deleteMissingEpisodes(keepIds: List<String>)

    @Query("UPDATE media_items SET downloadState = :state, downloadProgress = :progress WHERE id = :id")
    suspend fun updateDownloadProgress(id: String, state: DownloadState, progress: Int)

    @Query("UPDATE media_items SET downloadState = :state, downloadProgress = :progress, localFilePath = :localPath WHERE id = :id")
    suspend fun updateDownloadResult(id: String, state: DownloadState, progress: Int, localPath: String?)

    @Query(
        "UPDATE media_items SET playbackPositionMs = :positionMs, durationMs = :durationMs, " +
            "lastPlayedAt = :playedAt WHERE id = :id"
    )
    suspend fun updatePlaybackProgress(id: String, positionMs: Long, durationMs: Long, playedAt: Long)

    // Movies/cartoons/episodes started but not finished, most recently played first - for
    // the "Продолжить просмотр" row. (durationMs > 0 excludes rows that never got a real
    // checkpoint; the 0.95 cutoff matches PlayerScreen's own "basically finished" threshold.)
    @Query(
        "SELECT * FROM media_items WHERE mediaType != 'TV_SHOW' AND mediaType != 'CARTOON_SERIES' " +
            "AND durationMs > 0 AND playbackPositionMs > 5000 AND playbackPositionMs < durationMs * 0.95 " +
            "ORDER BY lastPlayedAt DESC LIMIT 20"
    )
    fun observeContinueWatching(): Flow<List<MediaItemEntity>>

    // Everything ever played, most recent first - unlike observeContinueWatching this
    // includes finished titles too (no durationMs/position filtering), for a dedicated
    // "История просмотров" screen rather than the library's "continue watching" row.
    @Query(
        "SELECT * FROM media_items WHERE mediaType != 'TV_SHOW' AND mediaType != 'CARTOON_SERIES' " +
            "AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 200"
    )
    fun observeWatchHistory(): Flow<List<MediaItemEntity>>

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE media_items SET mediaType = :mediaType, mediaTypeOverridden = :overridden WHERE id = :id")
    suspend fun setMediaType(id: String, mediaType: MediaType, overridden: Boolean)

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 ORDER BY title ASC")
    fun observeFavorites(): Flow<List<MediaItemEntity>>
}
