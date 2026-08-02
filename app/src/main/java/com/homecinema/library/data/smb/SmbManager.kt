package com.homecinema.library.data.smb

import com.homecinema.library.data.db.SmbSourceEntity
import com.homecinema.library.data.settings.SmbConfig
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

fun SmbSourceEntity.toSmbConfig(): SmbConfig = SmbConfig(
    host = host,
    share = share,
    rootPath = rootPath,
    domain = domain,
    username = username,
    password = password,
    guest = guest
)

/**
 * Thin wrapper around jcifs-ng. Builds an authenticated CIFSContext per configured
 * source and exposes helpers to list/open files on the share.
 *
 * A Keenetic Titan (or any other router/NAS) exposes its attached SSD as a standard
 * SMB2/3 share, so this talks to it exactly like it would to any Windows/NAS share.
 * Since the library can span several sources at once, contexts are cached per source id
 * rather than as a single global connection.
 */
class SmbManager {

    private val contextCache = ConcurrentHashMap<String, CIFSContext>()

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

    private fun contextFor(sourceId: String, config: SmbConfig): CIFSContext =
        contextCache.getOrPut(sourceId) { buildContext(config) }

    /** Builds the smb:// URL for a source's share root (or a sub-path within it). */
    fun rootUrl(config: SmbConfig, subPath: String = ""): String {
        val share = config.share.trim('/')
        val root = config.rootPath.trim('/')
        val extra = subPath.trim('/')
        val parts = listOf(root, extra).filter { it.isNotBlank() }
        val joined = parts.joinToString("/")
        return "smb://${config.host}/$share/" + if (joined.isNotBlank()) "$joined/" else ""
    }

    /** Returns the authenticated CIFSContext for the given source, for use by the player. */
    suspend fun authenticatedContext(sourceId: String, config: SmbConfig): CIFSContext =
        withContext(Dispatchers.IO) { contextFor(sourceId, config) }

    /** Opens an SmbFile for a full smb:// path using the given source's credentials. */
    suspend fun openFile(sourceId: String, config: SmbConfig, path: String): SmbFile =
        withContext(Dispatchers.IO) { SmbFile(path, contextFor(sourceId, config)) }

    /** Verifies the share is reachable with the given config. Throws on failure. */
    suspend fun testConnection(config: SmbConfig): Boolean = withContext(Dispatchers.IO) {
        val ctx = buildContext(config)
        val f = SmbFile(rootUrl(config), ctx)
        f.exists() && f.isDirectory
    }

    /** Recursively lists every file under a source's root (used by the scanner). */
    suspend fun listAllFilesRecursive(sourceId: String, config: SmbConfig): List<SmbFile> = withContext(Dispatchers.IO) {
        val ctx = contextFor(sourceId, config)
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

    /** Drops cached connections so edited/removed sources reconnect with fresh credentials. */
    fun invalidateCache(sourceId: String? = null) {
        if (sourceId == null) contextCache.clear() else contextCache.remove(sourceId)
    }
}
