package com.homecinema.library.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One configured SMB share to scan (host/share/credentials). Multiple sources let the
 * library span more than one NAS/router share at once - each scanned item records which
 * source it came from via MediaItemEntity.sourceId, so playback/downloads use the right
 * credentials for that specific share.
 */
@Entity(tableName = "smb_sources")
data class SmbSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val share: String,
    val rootPath: String,
    val domain: String,
    val username: String,
    val password: String,
    val guest: Boolean
)
