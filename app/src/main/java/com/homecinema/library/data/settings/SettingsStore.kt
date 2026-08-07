package com.homecinema.library.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore(name = "home_cinema_settings")

private const val MAX_SEARCH_HISTORY = 15

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

enum class ThemeMode { SYSTEM, LIGHT, DARK, OLED, GLASS }

/** Which chrome LibraryScreen renders itself with - CLASSIC is the original top-bar-and-tabs
 * layout (unchanged), BOTTOM_NAV moves primary navigation to a bottom bar. Kept as a real user
 * choice (not a one-time migration) since the redesign is a big enough change in daily-use feel
 * that reverting should be one tap, not a rebuild. */
enum class LibraryLayout { CLASSIC, BOTTOM_NAV }

/** Manual in-app language choice, independent of the device's system locale - see
 * [com.homecinema.library.data.settings.LocaleHelper]. [tag] is the BCP-47 language tag used
 * to build the [java.util.Locale] the app's Context gets wrapped with. */
enum class AppLanguage(val tag: String) { RUSSIAN("ru"), ENGLISH("en") }

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
        val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
        val GLASS_BACKGROUND_COLOR = stringPreferencesKey("glass_background_color")
        val GLASS_OPACITY = intPreferencesKey("glass_opacity")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")
        val PREFERRED_EXTERNAL_PLAYER_PACKAGE = stringPreferencesKey("preferred_external_player_package")
        val LIBRARY_LAYOUT = stringPreferencesKey("library_layout")
        val DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
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

    val libraryLayoutFlow: Flow<LibraryLayout> = context.dataStore.data.map { prefs ->
        runCatching { LibraryLayout.valueOf(prefs[Keys.LIBRARY_LAYOUT] ?: "BOTTOM_NAV") }
            .getOrDefault(LibraryLayout.BOTTOM_NAV)
    }

    suspend fun saveLibraryLayout(layout: LibraryLayout) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_LAYOUT] = layout.name
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

    /** Version the user dismissed the "update available" banner for, so it doesn't nag them
     * again about the same release once closed - only a newer one after that. */
    val dismissedUpdateVersionFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DISMISSED_UPDATE_VERSION] ?: ""
    }

    suspend fun setDismissedUpdateVersion(version: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DISMISSED_UPDATE_VERSION] = version
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

    /** Stored as a plain string key, same reasoning as [accentColorNameFlow] -
     * ui.theme.GlassBackgroundColor owns the actual colors. */
    val glassBackgroundColorNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.GLASS_BACKGROUND_COLOR] ?: "INDIGO"
    }

    suspend fun saveGlassBackgroundColorName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GLASS_BACKGROUND_COLOR] = name
        }
    }

    /** How opaque the "Стекло" theme's blurred panels are, 40-95% - higher reads more clearly
     * (less of the blurred backdrop bleeding through text/icons) but looks less like glass. */
    val glassOpacityFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.GLASS_OPACITY] ?: 65).coerceIn(40, 95)
    }

    suspend fun setGlassOpacity(percent: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GLASS_OPACITY] = percent.coerceIn(40, 95)
        }
    }

    /** Recent search queries, most recent first - shown as suggestions when the search field
     * is tapped but still empty. Stored as a JSON array (order matters here, unlike the plain
     * comma-joined strings elsewhere in this file, so a Set wouldn't do). */
    val searchHistoryFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseSearchHistory(prefs[Keys.SEARCH_HISTORY])
    }

    /** Adds [query] to the front of the history, moving it there if already present
     * (case-insensitive) instead of duplicating it, capped at [MAX_SEARCH_HISTORY] entries. */
    suspend fun addSearchHistoryEntry(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = parseSearchHistory(prefs[Keys.SEARCH_HISTORY])
            val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(MAX_SEARCH_HISTORY)
            prefs[Keys.SEARCH_HISTORY] = JSONArray(updated).toString()
        }
    }

    suspend fun removeSearchHistoryEntry(query: String) {
        context.dataStore.edit { prefs ->
            val current = parseSearchHistory(prefs[Keys.SEARCH_HISTORY])
            prefs[Keys.SEARCH_HISTORY] = JSONArray(current.filterNot { it == query }).toString()
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs -> prefs.remove(Keys.SEARCH_HISTORY) }
    }

    /** The app's own versionCode as of the last launch that checked - lets HomeCinemaApp tell
     * "just updated" apart from "same version as last time", to offer a one-time rescan prompt
     * when a new version adds .nfo fields that older scans never populated. 0 means never
     * recorded (fresh install - no stale data to worry about, so no prompt is needed then). */
    val lastSeenVersionCodeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SEEN_VERSION_CODE] ?: 0
    }

    suspend fun setLastSeenVersionCode(code: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SEEN_VERSION_CODE] = code
        }
    }

    /** Package name of the app external playback should always launch, or "" to let Android
     * show its own chooser (or auto-pick a sole/previously-set default) every time - see
     * data.media.queryExternalPlayerApps for how the picker's option list is built. */
    val preferredExternalPlayerPackageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.PREFERRED_EXTERNAL_PLAYER_PACKAGE] ?: ""
    }

    suspend fun savePreferredExternalPlayerPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PREFERRED_EXTERNAL_PLAYER_PACKAGE] = packageName
        }
    }

    /** A user-picked folder (via Storage Access Framework - any location, including an SD
     * card) for new downloads to go into instead of the default Download/HomeCinema. Empty
     * means "use the default". Stores the persisted tree URI as a string - see
     * DownloadStorage for how this gets used to actually create files there. */
    val downloadFolderUriFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_FOLDER_URI] ?: ""
    }

    suspend fun saveDownloadFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_FOLDER_URI] = uri
        }
    }

    /** Defaults to Russian regardless of the device's system locale - the app had zero locale
     * awareness before this setting existed and always showed Russian, so defaulting to
     * anything else would silently change behavior for every existing install. */
    val appLanguageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        runCatching { AppLanguage.valueOf(prefs[Keys.APP_LANGUAGE] ?: "RUSSIAN") }
            .getOrDefault(AppLanguage.RUSSIAN)
    }

    suspend fun saveAppLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.name
        }
        // Also mirrored into a plain SharedPreferences - see LocaleHelper for why the DataStore
        // value above can't be read synchronously at Application.attachBaseContext() time.
        withContext(Dispatchers.IO) { LocaleHelper.saveSync(context, language) }
    }

    private fun parseSearchHistory(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }
}
