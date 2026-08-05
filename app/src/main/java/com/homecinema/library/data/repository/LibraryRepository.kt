package com.homecinema.library.data.repository

import android.content.Context
import com.homecinema.library.data.db.CustomListEntity
import com.homecinema.library.data.db.LibraryDao
import com.homecinema.library.data.db.ListCountRow
import com.homecinema.library.data.db.ListDao
import com.homecinema.library.data.db.ListItemCrossRef
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
    private val context: Context,
    private val listDao: ListDao
) {
    private val sourceResolver = SmbSourceResolver(sourceDao, credentialStore)
    private val scanner = LibraryScanner(smbManager, dao, sourceDao, sourceResolver, context)

    fun observeLibrary(): Flow<List<MediaItemEntity>> = dao.observeLibrary()

    // --- Favorites ---

    fun observeFavorites(): Flow<List<MediaItemEntity>> = dao.observeFavorites()

    suspend fun setFavorite(id: String, isFavorite: Boolean) {
        dao.setFavorite(id, isFavorite)
    }

    // --- User-created custom lists ---

    fun observeLists(): Flow<List<CustomListEntity>> = listDao.observeLists()

    fun observeListCounts(): Flow<List<ListCountRow>> = listDao.observeListCounts()

    suspend fun createList(name: String): String {
        val id = UUID.randomUUID().toString()
        listDao.insertList(CustomListEntity(id = id, name = name, createdAt = System.currentTimeMillis()))
        return id
    }

    suspend fun renameList(id: String, name: String) = listDao.renameList(id, name)

    suspend fun deleteList(id: String) = listDao.deleteList(id)

    suspend fun addItemToList(listId: String, itemId: String) =
        listDao.addItemToList(ListItemCrossRef(listId, itemId))

    suspend fun removeItemFromList(listId: String, itemId: String) = listDao.removeItemFromList(listId, itemId)

    fun observeItemsInList(listId: String): Flow<List<MediaItemEntity>> = listDao.observeItemsInList(listId)

    fun observeListIdsForItem(itemId: String): Flow<List<String>> = listDao.observeListIdsForItem(itemId)

    fun observeEpisodesForShow(showId: String): Flow<List<MediaItemEntity>> = dao.observeEpisodesForShow(showId)

    fun observeDownloaded(): Flow<List<MediaItemEntity>> = dao.observeDownloaded()

    fun observeContinueWatching(): Flow<List<MediaItemEntity>> = dao.observeContinueWatching()

    fun observeWatchHistory(): Flow<List<MediaItemEntity>> = dao.observeWatchHistory()

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

    /** The password normally goes to the encrypted CredentialStore rather than Room, so a
     * copy of the (unencrypted) app database doesn't carry it. But CredentialStore touches
     * Android Keystore/Tink, which can fail on a given device or build for reasons outside
     * our control - if it does, the source must still get saved (with the password sitting
     * in Room as a fallback) rather than silently vanishing, which previously showed up as
     * "0 titles found, no error" with no clue why. */
    suspend fun saveSource(source: SmbSourceEntity) {
        val encrypted = runCatching { credentialStore.setPassword(source.id, source.password) }.isSuccess
        sourceDao.upsert(source.copy(password = if (encrypted) "" else source.password))
        smbManager.invalidateCache(source.id)
    }

    suspend fun deleteSource(id: String) {
        sourceDao.delete(id)
        runCatching { credentialStore.deletePassword(id) }
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
        val encrypted = runCatching { credentialStore.setPassword(id, legacy.password) }.isSuccess
        sourceDao.upsert(
            SmbSourceEntity(
                id = id,
                name = "Основной",
                host = legacy.host,
                share = legacy.share,
                rootPath = legacy.rootPath,
                domain = legacy.domain,
                username = legacy.username,
                password = if (encrypted) "" else legacy.password,
                guest = legacy.guest
            )
        )
    }
}
