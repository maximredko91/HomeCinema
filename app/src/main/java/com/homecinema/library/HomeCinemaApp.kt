package com.homecinema.library

import android.app.Application
import com.homecinema.library.data.db.AppDatabase
import com.homecinema.library.data.download.DownloadManager
import com.homecinema.library.data.repository.LibraryRepository
import com.homecinema.library.data.settings.SettingsStore
import com.homecinema.library.data.smb.SmbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Simple hand-rolled service locator (no Hilt/Dagger to keep the project
 * easy to read and modify). Access shared singletons via HomeCinemaApp.instance
 */
class HomeCinemaApp : Application() {

    /** Outlives individual screens so an in-progress download isn't cancelled on navigation. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settingsStore: SettingsStore
        private set
    lateinit var smbManager: SmbManager
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var repository: LibraryRepository
        private set
    lateinit var downloadManager: DownloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        settingsStore = SettingsStore(this)
        smbManager = SmbManager()
        database = AppDatabase.build(this)
        repository = LibraryRepository(database.libraryDao(), database.smbSourceDao(), smbManager, this)
        downloadManager = DownloadManager(smbManager, database.libraryDao(), database.smbSourceDao(), this, applicationScope)

        applicationScope.launch { repository.migrateLegacySourceIfNeeded(settingsStore) }
    }

    companion object {
        lateinit var instance: HomeCinemaApp
            private set
    }
}
