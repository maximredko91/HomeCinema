package com.homecinema.library.data.smb

import com.homecinema.library.data.db.SmbSourceDao
import com.homecinema.library.data.db.SmbSourceEntity
import com.homecinema.library.data.security.CredentialStore

/**
 * The single place that turns a stored source into one with its real password attached.
 * SmbSourceEntity.password is never populated with a real value in Room going forward -
 * the actual password lives in CredentialStore instead - so every caller that needs to
 * actually connect (scanner, downloads, player) must go through here rather than reading
 * SmbSourceDao directly. Also carries a one-time migration: sources saved before this
 * split still have their real password sitting in the Room column, so the first time one
 * of those is resolved, it gets moved into CredentialStore and wiped from Room.
 */
class SmbSourceResolver(
    private val sourceDao: SmbSourceDao,
    private val credentialStore: CredentialStore
) {
    suspend fun resolve(sourceId: String): SmbSourceEntity? =
        sourceDao.getById(sourceId)?.let { withRealPassword(it) }

    suspend fun withRealPassword(source: SmbSourceEntity): SmbSourceEntity {
        val stored = credentialStore.getPassword(source.id)
        if (stored.isNotEmpty()) return source.copy(password = stored)

        if (source.password.isNotEmpty()) {
            credentialStore.setPassword(source.id, source.password)
            sourceDao.upsert(source.copy(password = ""))
        }
        return source
    }
}
