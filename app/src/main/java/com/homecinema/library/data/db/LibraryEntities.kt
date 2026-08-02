package com.homecinema.library.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType { MOVIE, CARTOON, TV_SHOW, CARTOON_SERIES, EPISODE }

enum class DownloadState { NONE, DOWNLOADING, COMPLETED, FAILED }

/**
 * One row per playable/browsable node in the library:
 * - MOVIE / CARTOON: a standalone playable title (folderPath points at its own folder).
 * - TV_SHOW / CARTOON_SERIES: a series "shell" - not directly playable, opens a screen
 *   listing its EPISODE children (see parentShowId below).
 * - EPISODE: a single episode belonging to a TV_SHOW/CARTOON_SERIES via parentShowId.
 */
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String, // stable hash of the folder/file's smb path
    val title: String,
    val year: Int?,
    val genres: String, // comma-separated for simplicity
    val plot: String,
    val rating: Double?,
    val mediaType: MediaType,
    val folderPath: String,    // smb:// path to the containing folder
    val videoFilePath: String, // smb:// path to the playable video file (empty for show shells)
    val posterLocalPath: String?, // cached poster file on local storage, null if none found
    val lastScanned: Long,

    // Episode hierarchy - only meaningful when mediaType == EPISODE
    val parentShowId: String? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,

    // Offline download tracking - meaningful for MOVIE / CARTOON / EPISODE
    val localFilePath: String? = null,
    val downloadState: DownloadState = DownloadState.NONE,
    val downloadProgress: Int = 0 // 0-100
)
