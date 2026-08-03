package com.homecinema.library

import android.app.Application
import com.homecinema.library.data.db.AppDatabase
import com.homecinema.library.data.download.DownloadManager
import com.homecinema.library.data.repository.LibraryRepository
import com.homecinema.library.data.security.CredentialStore
import com.homecinema.library.data.settings.SettingsStore
import com.homecinema.library.data.smb.SmbManager
import com.homecinema.library.data.smb.SmbSourceResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

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

        // jcifs-ng needs MD4 (for NTLM auth) from Bouncy Castle, which it depends on as a
        // real library - but Android ships its own stripped-down provider already
        // registered under the same name "BC", missing MD4 and some others. Unless the
        // real implementation is explicitly inserted ahead of it, "BC" resolves to
        // Android's incomplete one and every SMB connection fails with
        // NoSuchAlgorithmException, even though nothing looks wrong at compile time.
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        settingsStore = SettingsStore(this)
        smbManager = SmbManager()
        database = AppDatabase.build(this)
        val credentialStore = CredentialStore(this)
        val sourceResolver = SmbSourceResolver(database.smbSourceDao(), credentialStore)
        repository = LibraryRepository(database.libraryDao(), database.smbSourceDao(), smbManager, credentialStore, this)
        downloadManager = DownloadManager(smbManager, database.libraryDao(), sourceResolver, this, applicationScope)

        applicationScope.launch { repository.migrateLegacySourceIfNeeded(settingsStore) }
    }

    companion object {
        lateinit var instance: HomeCinemaApp
            private set
    }
}
