package com.homecinema.library.data.smb

import com.homecinema.library.data.settings.SettingsStore
import com.homecinema.library.data.settings.SmbConfig
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/**
 * Thin wrapper around jcifs-ng. Builds an authenticated CIFSContext from the
 * user's saved settings and exposes helpers to list/open files on the share.
 *
 * Keenetic Titan exposes the attached SSD as a standard SMB2/3 share, so this
 * talks to it exactly like it would to any Windows/NAS share.
 */
class SmbManager(private val settingsStore: SettingsStore) {

    @Volatile private var cachedContext: CIFSContext? = null
    @Volatile private var cachedConfig: SmbConfig? = null

    private fun buildContext(config: SmbConfig): CIFSContext {
        val props = Properties().apply {
            // Force SMB2/3 dialects - modern routers/NAS (incl. Keenetic) support them,
            // and jcifs-ng's default negotiation is more reliable when pinned.
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.resolveOrder", "DNS")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.connTimeout", "15000")
        }
        val base = BaseContext(PropertyConfiguration(props))
        return if (config.guest || config.username.isBlank()) {
            base.withGuestCrendentials()
        } else {
            base.withCredentials(
                NtlmPasswordAuthenticator(config.domain, config.username, config.password)
            )
        }
    }

    private suspend fun contextFor(config: SmbConfig): CIFSContext {
        val cached = cachedContext
        if (cached != null && cachedConfig == config) return cached
        return withContext(Dispatchers.IO) {
            buildContext(config).also {
                cachedContext = it
                cachedConfig = config
            }
        }
    }

    /** Builds the smb:// URL for the configured share root (or a sub-path within it). */
    fun rootUrl(config: SmbConfig, subPath: String = ""): String {
        val share = config.share.trim('/')
        val root = config.rootPath.trim('/')
        val extra = subPath.trim('/')
        val parts = listOf(root, extra).filter { it.isNotBlank() }
        val joined = parts.joinToString("/")
        return "smb://${config.host}/$share/" + if (joined.isNotBlank()) "$joined/" else ""
    }

    /** Returns the authenticated CIFSContext built from the saved settings, for use by the player. */
    suspend fun authenticatedContext(): CIFSContext = withContext(Dispatchers.IO) {
        contextFor(settingsStore.currentConfig())
    }

    /** Opens an SmbFile for a full smb:// path using the current credentials. */
    suspend fun openFile(path: String): SmbFile = withContext(Dispatchers.IO) {
        val config = settingsStore.currentConfig()
        val ctx = contextFor(config)
        SmbFile(path, ctx)
    }

    /** Verifies the share is reachable with the given config. Throws on failure. */
    suspend fun testConnection(config: SmbConfig): Boolean = withContext(Dispatchers.IO) {
        val ctx = buildContext(config)
        val f = SmbFile(rootUrl(config), ctx)
        f.exists() && f.isDirectory
    }

    /** Recursively lists every file under the configured root (used by the scanner). */
    suspend fun listAllFilesRecursive(config: SmbConfig): List<SmbFile> = withContext(Dispatchers.IO) {
        val ctx = contextFor(config)
        val root = SmbFile(rootUrl(config), ctx)
        val out = mutableListOf<SmbFile>()
        walk(root, out)
        out
    }

    private fun walk(dir: SmbFile, out: MutableList<SmbFile>) {
        val children = try { dir.listFiles() } catch (e: Exception) { return }
        for (child in children ?: emptyArray()) {
            if (child.isDirectory) {
                walk(child, out)
            } else {
                out.add(child)
            }
        }
    }

    fun invalidateCache() {
        cachedContext = null
        cachedConfig = null
    }
}
