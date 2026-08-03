package com.homecinema.library.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android-Keystore-backed storage for SMB source passwords. Kept separate from Room
 * (which is a plain unencrypted SQLite file) so a lost/rooted/backed-up phone doesn't
 * leak router/NAS credentials in plain text - only the password moves here, everything
 * else about a source (host, share, username...) isn't sensitive enough to bother with.
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "smb_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getPassword(sourceId: String): String = prefs.getString(sourceId, "").orEmpty()

    fun setPassword(sourceId: String, password: String) {
        prefs.edit().putString(sourceId, password).apply()
    }

    fun deletePassword(sourceId: String) {
        prefs.edit().remove(sourceId).apply()
    }
}
