package com.homecinema.library

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.homecinema.library.data.db.AppDatabase
import com.homecinema.library.data.download.DownloadManager
import com.homecinema.library.data.repository.LibraryRepository
import com.homecinema.library.data.scanner.LibraryRescanWorker
import com.homecinema.library.data.security.CredentialStore
import com.homecinema.library.data.settings.SettingsStore
import com.homecinema.library.data.smb.SmbManager
import com.homecinema.library.data.smb.SmbSourceResolver
import com.homecinema.library.data.update.ReleaseInfo
import com.homecinema.library.data.update.UpdateChecker
import com.homecinema.library.data.update.isNewerVersion
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.concurrent.TimeUnit

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
    lateinit var sourceResolver: SmbSourceResolver
        private set
    lateinit var downloadManager: DownloadManager
        private set

    private val _availableUpdate = MutableStateFlow<ReleaseInfo?>(null)
    /** Non-null once a newer GitHub Release than the installed version is found and hasn't
     * been dismissed for that specific version yet - see checkForUpdate(). */
    val availableUpdate: StateFlow<ReleaseInfo?> = _availableUpdate.asStateFlow()

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
        sourceResolver = SmbSourceResolver(database.smbSourceDao(), credentialStore)
        repository = LibraryRepository(database.libraryDao(), database.smbSourceDao(), smbManager, credentialStore, this, database.listDao())
        downloadManager = DownloadManager(database.libraryDao(), this, applicationScope)

        createDownloadNotificationChannel()
        createStreamingNotificationChannel()
        scheduleAutoRescan()
        checkForUpdate()

        applicationScope.launch { repository.migrateLegacySourceIfNeeded(settingsStore) }
    }

    /** Distribution is manual (GitHub Releases, no Play Store), so this is the only way
     * anyone finds out a new version exists - a quiet, best-effort check at launch, never
     * blocking startup and silent on any failure (offline, GitHub down, whatever). */
    private fun checkForUpdate() {
        applicationScope.launch {
            val release = UpdateChecker.fetchLatestRelease() ?: return@launch
            val currentVersion = runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull() ?: return@launch
            if (!isNewerVersion(currentVersion, release.version)) return@launch
            if (settingsStore.dismissedUpdateVersionFlow.first() == release.version) return@launch
            _availableUpdate.value = release
        }
    }

    fun dismissUpdate(version: String) {
        applicationScope.launch { settingsStore.setDismissedUpdateVersion(version) }
        _availableUpdate.value = null
    }

    private fun createDownloadNotificationChannel() {
        val channel = NotificationChannel(
            DOWNLOAD_NOTIFICATION_CHANNEL_ID,
            getString(R.string.download_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private fun createStreamingNotificationChannel() {
        val channel = NotificationChannel(
            STREAMING_NOTIFICATION_CHANNEL_ID,
            getString(R.string.streaming_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /** Periodic background rescan, replacing the old app-launch-only trigger - survives
     * process death/reboots since WorkManager persists its own queue. LibraryRescanWorker
     * checks settingsStore.autoRescanEnabledFlow itself, so this can stay unconditionally
     * scheduled here. */
    private fun scheduleAutoRescan() {
        val request = PeriodicWorkRequestBuilder<LibraryRescanWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "library_auto_rescan",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        lateinit var instance: HomeCinemaApp
            private set

        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "downloads"
        const val STREAMING_NOTIFICATION_CHANNEL_ID = "streaming"
    }
}
