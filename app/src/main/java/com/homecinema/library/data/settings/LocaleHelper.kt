package com.homecinema.library.data.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.LocaleList
import com.homecinema.library.MainActivity
import java.util.Locale

/**
 * Manual per-app language, independent of the device's system locale (the user picks it in
 * Settings, not "follow system"). No AppCompat dependency needed - just wraps the base
 * Context each Application/Activity gets with a Configuration forced to the chosen Locale.
 *
 * attachBaseContext() runs before Application.onCreate() / before the Application object is
 * fully attached, so the normal SettingsStore(DataStore) flow can't be read here: DataStore's
 * preferencesDataStore delegate needs Context.applicationContext, which is genuinely still
 * null at this exact point (confirmed live - reading it here crashed every launch with a NPE
 * deep inside DataStore's file-lock setup). A plain SharedPreferences file doesn't have that
 * dependency and reads synchronously, so the chosen language is mirrored into one here
 * whenever [SettingsStore.saveAppLanguage] is called, specifically for this early read.
 */
object LocaleHelper {
    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun wrap(base: Context): Context {
        val raw = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        val language = runCatching { AppLanguage.valueOf(raw ?: "RUSSIAN") }.getOrDefault(AppLanguage.RUSSIAN)
        val locale = Locale(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** [restartApp] kills this process right after this returns (see below) - apply() only
     * guarantees the write reaches disk "eventually", which can lose a race against that kill
     * (confirmed live: the new process then read the stale value back). commit() blocks until
     * the write is actually on disk, which is what correctness here needs. */
    fun saveSync(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, language.name)
            .commit()
    }

    /** Changing [AppLanguage] mid-session isn't enough to fix text baked into places that
     * only ever read the Application's one-time attachBaseContext() locale - background
     * WorkManager workers (DownloadWorker, LibraryRescanWorker) and services (StreamingService,
     * PlaybackNotificationService) build notification text off `applicationContext`, which
     * Activity.recreate() alone never re-wraps. A full process restart is the reliable fix:
     * launch a fresh MainActivity task, then kill this process so Application re-attaches
     * with the new locale from scratch on the next launch. */
    fun restartApp(context: Context) {
        val restartIntent = Intent.makeRestartActivityTask(Intent(context, MainActivity::class.java).component)
        context.startActivity(restartIntent)
        (context as? Activity)?.finish()
        Runtime.getRuntime().exit(0)
    }
}
