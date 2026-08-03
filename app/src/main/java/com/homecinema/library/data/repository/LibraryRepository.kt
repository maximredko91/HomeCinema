package com.homecinema.library.data.repository

import android.content.Context
import com.homecinema.library.data.db.LibraryDao
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.SmbSourceDao
import com.homecinema.library.data.db.SmbSourceEntity
import com.homecinema.library.data.scanner.LibraryScanner
import com.homecinema.library.data.scanner.ScanProgress
import com.homecinema.library.data.security.CredentialStore
import com.homecinema.library.data.settings.SettingsStore
import com.homecinema.library.data.smb.SmbManager
import com.homecinema.library.data.smb.SmbSourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LibraryRepository(
    private val dao: LibraryDao,
    private val sourceDao: SmbSourceDao,
    private val smbManager: SmbManager,
    private val credentialStore: CredentialStore,
    private val context: Context
) {
    private val sourceResolver = SmbSourceResolver(sourceDao, credentialStore)
    private val scanner = LibraryScanner(smbManager, dao, sourceDao, sourceResolver, context)

    fun observeLibrary(): Flow<List<MediaItemEntity>> = dao.observeLibrary()

    fun observeEpisodesForShow(showId: String): Flow<List<MediaItemEntity>> = dao.observeEpisodesForShow(showId)

    fun observeDownloaded(): Flow<List<MediaItemEntity>> = dao.observeDownloaded()

    fun observeContinueWatching(): Flow<List<MediaItemEntity>> = dao.observeContinueWatching()

    fun observeById(id: String): Flow<MediaItemEntity?> = dao.observeById(id)

    suspend fun getById(id: String): MediaItemEntity? = dao.getById(id)

    suspend fun rescan(onProgress: (ScanProgress) -> Unit) = scanner.scan(onProgress)

    suspend fun updatePlaybackProgress(id: String, positionMs: Long, durationMs: Long) =
        dao.updatePlaybackProgress(id, positionMs, durationMs, System.currentTimeMillis())

    /** Deletes cached poster images. Missing posters are re-downloaded on the next rescan. */
    suspend fun clearPosterCache() = withContext(Dispatchers.IO) {
        File(context.cacheDir, "posters").deleteRecursively()
    }

    // --- SMB sources (a library can span more than one share) ---

    /** For list/display UI only - password is always blank here, see [getSource]. */
    fun observeSources(): Flow<List<SmbSourceEntity>> = sourceDao.observeAll()

    /** Resolves the source with its real password attached (from CredentialStore), for
     * editing or for anything that actually needs to connect. */
    suspend fun getSource(id: String): SmbSourceEntity? = sourceResolver.resolve(id)

    /** The password never gets written into Room - it goes to the encrypted CredentialStore
     * instead, so a copy of the (unencrypted) app database never carries it. */
    suspend fun saveSource(source: SmbSourceEntity) {
        credentialStore.setPassword(source.id, source.password)
        sourceDao.upsert(source.copy(password = ""))
        smbManager.invalidateCache(source.id)
    }

    suspend fun deleteSource(id: String) {
        sourceDao.delete(id)
        credentialStore.deletePassword(id)
        smbManager.invalidateCache(id)
    }

    /**
     * Older versions of the app stored a single SMB connection in DataStore. On first run
     * after upgrading, that legacy config (if any, and if no sources exist yet) becomes the
     * user's first named source, so their existing setup keeps working without re-entering it.
     */
    suspend fun migrateLegacySourceIfNeeded(settingsStore: SettingsStore) {
        if (sourceDao.observeAll().first().isNotEmpty()) return
        val legacy = settingsStore.currentConfig()
        if (!legacy.isConfigured) return
        val id = UUID.randomUUID().toString()
        credentialStore.setPassword(id, legacy.password)
        sourceDao.upsert(
            SmbSourceEntity(
                id = id,
                name = "Основной",
                host = legacy.host,
                share = legacy.share,
                rootPath = legacy.rootPath,
                domain = legacy.domain,
                username = legacy.username,
                password = "",
                guest = legacy.guest
            )
        )
    }
}
