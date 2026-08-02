package com.homecinema.library.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

enum class ThemeMode { SYSTEM, LIGHT, DARK, OLED }

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
        val ALPHABET_INDEX_ENABLED = booleanPreferencesKey("alphabet_index_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val AUTO_RESCAN_ENABLED = booleanPreferencesKey("auto_rescan_enabled")
        val LAST_AUTO_SCAN_AT = longPreferencesKey("last_auto_scan_at")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
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

    val alphabetIndexEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ALPHABET_INDEX_ENABLED] ?: true
    }

    suspend fun setAlphabetIndexEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALPHABET_INDEX_ENABLED] = enabled
        }
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    /** Stored as a plain string key (not the Compose Color itself) to keep this data-layer
     * class free of UI dependencies - the ui.theme.AccentColor enum owns the actual colors. */
    val accentColorNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACCENT_COLOR] ?: "GOLD"
    }

    suspend fun saveAccentColorName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCENT_COLOR] = name
        }
    }

    val autoRescanEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_RESCAN_ENABLED] ?: true
    }

    suspend fun setAutoRescanEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_RESCAN_ENABLED] = enabled
        }
    }

    val lastAutoScanAtFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_AUTO_SCAN_AT] ?: 0L
    }

    suspend fun recordAutoScanNow() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_AUTO_SCAN_AT] = System.currentTimeMillis()
        }
    }

    /** Number of columns in the library/collections grid - 2 (bigger posters) or 3 (denser). */
    val gridColumnsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.GRID_COLUMNS] ?: 2).coerceIn(2, 3)
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GRID_COLUMNS] = columns.coerceIn(2, 3)
        }
    }
}
