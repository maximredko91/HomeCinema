package com.homecinema.library.data.repository

import android.content.Context
import com.homecinema.library.data.db.LibraryDao
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.scanner.LibraryScanner
import com.homecinema.library.data.scanner.ScanProgress
import com.homecinema.library.data.settings.SettingsStore
import com.homecinema.library.data.smb.SmbManager
import kotlinx.coroutines.flow.Flow

class LibraryRepository(
    private val dao: LibraryDao,
    private val smbManager: SmbManager,
    context: Context
) {
    // SettingsStore is re-created here for the scanner's own use; the same
    // instance living on HomeCinemaApp is passed in from there in practice.
    private val settingsStore = SettingsStore(context)
    private val scanner = LibraryScanner(smbManager, dao, settingsStore, context)

    fun observeLibrary(): Flow<List<MediaItemEntity>> = dao.observeLibrary()

    fun observeEpisodesForShow(showId: String): Flow<List<MediaItemEntity>> = dao.observeEpisodesForShow(showId)

    fun observeDownloaded(): Flow<List<MediaItemEntity>> = dao.observeDownloaded()

    fun observeById(id: String): Flow<MediaItemEntity?> = dao.observeById(id)

    suspend fun getById(id: String): MediaItemEntity? = dao.getById(id)

    suspend fun rescan(onProgress: (ScanProgress) -> Unit) = scanner.scan(onProgress)
}
