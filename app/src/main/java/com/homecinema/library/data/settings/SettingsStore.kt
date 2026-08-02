package com.homecinema.library.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "home_cinema_settings")

data class SmbConfig(
    val host: String = "",
    val share: String = "",
    // path inside the share, e.g. "Movies" or "Media/Films" (empty = share root)
    val rootPath: String = "",
    val domain: String = "",
    val username: String = "",
    val password: String = "",
    // when true, an anonymous/guest connection is attempted instead of user/pass
    val guest: Boolean = false
) {
    val isConfigured: Boolean get() = host.isNotBlank() && share.isNotBlank()
}

enum class PlaybackMode { INTERNAL, EXTERNAL, ASK }

class SettingsStore(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("smb_host")
        val SHARE = stringPreferencesKey("smb_share")
        val ROOT_PATH = stringPreferencesKey("smb_root_path")
        val DOMAIN = stringPreferencesKey("smb_domain")
        val USERNAME = stringPreferencesKey("smb_username")
        val PASSWORD = stringPreferencesKey("smb_password")
        val GUEST = stringPreferencesKey("smb_guest")
        val PLAYBACK_MODE = stringPreferencesKey("playback_mode")
    }

    val configFlow: Flow<SmbConfig> = context.dataStore.data.map { prefs ->
        SmbConfig(
            host = prefs[Keys.HOST] ?: "",
            share = prefs[Keys.SHARE] ?: "",
            rootPath = prefs[Keys.ROOT_PATH] ?: "",
            domain = prefs[Keys.DOMAIN] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            password = prefs[Keys.PASSWORD] ?: "",
            guest = (prefs[Keys.GUEST] ?: "false").toBoolean()
        )
    }

    val playbackModeFlow: Flow<PlaybackMode> = context.dataStore.data.map { prefs ->
        runCatching { PlaybackMode.valueOf(prefs[Keys.PLAYBACK_MODE] ?: "ASK") }
            .getOrDefault(PlaybackMode.ASK)
    }

    suspend fun currentConfig(): SmbConfig = configFlow.first()

    suspend fun saveConfig(config: SmbConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = config.host
            prefs[Keys.SHARE] = config.share
            prefs[Keys.ROOT_PATH] = config.rootPath
            prefs[Keys.DOMAIN] = config.domain
            prefs[Keys.USERNAME] = config.username
            prefs[Keys.PASSWORD] = config.password
            prefs[Keys.GUEST] = config.guest.toString()
        }
    }

    suspend fun savePlaybackMode(mode: PlaybackMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAYBACK_MODE] = mode.name
        }
    }
}
