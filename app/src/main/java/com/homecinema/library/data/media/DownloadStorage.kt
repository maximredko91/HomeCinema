package com.homecinema.library.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.homecinema.library.data.db.MediaItemEntity
import java.io.IOException
import java.io.OutputStream

private val DEFAULT_RELATIVE_BASE = "${Environment.DIRECTORY_DOWNLOADS}/HomeCinema"

/** A downloaded item's `localFilePath` used to always be a plain filesystem path into the app's
 * private external-files directory - cleared by Android on some devices/situations, which is
 * exactly the data loss this whole file exists to avoid. Downloads now go either into the real,
 * public Download/HomeCinema collection (the default, via MediaStore) or into a folder the user
 * explicitly picked themselves in Settings (via Storage Access Framework, "download folder URI"
 * - any location, including an SD card) - both survive the app's data being cleared, and both
 * are visible in a normal file manager. Either way `localFilePath` ends up holding a `content://`
 * URI string; [isContentUri] tells that apart from a download that already existed before this
 * change and keeps working from its old plain path without needing any migration step.
 *
 * The two backends need different creation/listing/deletion calls (MediaStore.Downloads vs
 * DocumentFile against a SAF tree), but once a `content://` URI exists, reading/writing/
 * resuming bytes through it is identical either way - a plain ContentResolver stream/file
 * descriptor works the same regardless of which provider is actually behind the URI. Only the
 * "does this URI belong to the SAF tree or to MediaStore" question needs deciding per call,
 * via [DocumentsContract.isDocumentUri]. */
object DownloadStorage {

    /** One folder per title (sanitized, not a hash) so it reads sensibly if the user ever
     * browses the destination directly - the video, its subtitle, and its .nfo all end up side
     * by side there. */
    private fun sanitizedTitleFolder(item: MediaItemEntity): String =
        item.title
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(120)
            .ifBlank { item.id }

    fun relativePathFor(item: MediaItemEntity): String = "$DEFAULT_RELATIVE_BASE/${sanitizedTitleFolder(item)}/"

    /** Finds an already-created (possibly still mid-download, from an interrupted attempt)
     * entry for this exact file, or creates a fresh one. Reusing an existing row/document
     * rather than always inserting a new one is what makes resuming a partial download
     * actually resume instead of silently starting a second copy next to the first.
     * [customTreeUri] is the user's own picked folder (from Settings), or null for the default
     * Download/HomeCinema location. */
    fun getOrCreateEntry(
        context: Context,
        item: MediaItemEntity,
        fileName: String,
        mimeType: String,
        customTreeUri: Uri?
    ): Uri {
        if (customTreeUri != null) {
            val folder = findOrCreateSafFolder(context, customTreeUri, item)
                ?: throw IOException("Не удалось открыть выбранную папку для загрузок")
            folder.findFile(fileName)?.let { return it.uri }
            return folder.createFile(mimeType, fileName)?.uri
                ?: throw IOException("Не удалось создать файл в выбранной папке")
        }

        val relativePath = relativePathFor(item)
        findMediaStoreEntry(context, relativePath, fileName)?.let { return it }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Не удалось создать файл в Download/HomeCinema")
    }

    private fun findOrCreateSafFolder(context: Context, treeUri: Uri, item: MediaItemEntity): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val folderName = sanitizedTitleFolder(item)
        val existing = root.findFile(folderName)
        if (existing != null && existing.isDirectory) return existing
        return root.createDirectory(folderName)
    }

    private fun findMediaStoreEntry(context: Context, relativePath: String, fileName: String): Uri? =
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(relativePath, fileName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            } else null
        }

    /** Current size of a (possibly still-pending/partial) entry, for resuming a partial
     * download - 0 if unreadable/missing/genuinely empty. Backend-agnostic: a plain file
     * descriptor stat works the same for a MediaStore or a SAF document URI. */
    fun currentLength(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        }.getOrDefault(0L)

    /** "wa" (write-append) rather than "w" so resuming a partial download continues from where
     * it left off instead of the ContentResolver truncating the file back to empty first. */
    fun openOutputStream(context: Context, uri: Uri, append: Boolean): OutputStream =
        context.contentResolver.openOutputStream(uri, if (append) "wa" else "w")
            ?: throw IOException("Не удалось открыть файл для записи")

    /** Clears IS_PENDING once writing is finished - required before any other app (a file
     * manager, an external player) can see or read a MediaStore-backed download at all. A SAF
     * document has no such concept - it's visible to other apps as soon as it exists, so this
     * is a no-op for one made in a user-picked folder. */
    fun markComplete(context: Context, uri: Uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) return
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }

    /** A subtitle previously downloaded alongside [item]'s video, wherever its video also
     * lives - the MediaStore or SAF equivalent of [findLocalSiblingSubtitle], which only knows
     * how to look next to a plain java.io.File and can't see into a content:// location.
     * Returns the file's extension alongside its URI since that's needed to pick the right
     * subtitle MIME type at playback time, and isn't otherwise recoverable from a content://
     * URI alone. */
    fun findDownloadedSubtitle(context: Context, item: MediaItemEntity, videoUri: Uri, customTreeUri: Uri?): Pair<Uri, String>? {
        val isSaf = DocumentsContract.isDocumentUri(context, videoUri)
        if (isSaf) {
            // customTreeUri is the *current* setting, only a best-effort hint here - if the
            // video was downloaded to a folder the user has since moved away from, this can
            // miss its subtitle. Not data loss, just a subtitle that won't show up until the
            // folder setting points at the right place again.
            if (customTreeUri == null) return null
            val root = DocumentFile.fromTreeUri(context, customTreeUri) ?: return null
            val folder = root.findFile(sanitizedTitleFolder(item))?.takeIf { it.isDirectory } ?: return null
            for (child in folder.listFiles()) {
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val base = name.substringBeforeLast('.')
                if (base.equals(item.id, ignoreCase = true) && ext in SUBTITLE_EXTENSIONS) return child.uri to ext
            }
            return null
        }
        val relativePath = relativePathFor(item)
        return context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(relativePath),
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)
                val ext = name.substringAfterLast('.', "").lowercase()
                val base = name.substringBeforeLast('.')
                if (base.equals(item.id, ignoreCase = true) && ext in SUBTITLE_EXTENSIONS) {
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idCol))
                    return uri to ext
                }
            }
            null
        }
    }

    /** Deletes every file belonging to this item (video, subtitle, .nfo together), wherever it
     * lives - used when removing a download entirely rather than looking up/deleting each
     * companion file individually.
     *
     * The video itself is always deleted directly via its own stored URI ([videoUri]) -
     * unambiguous and correct no matter what the *current* download-folder setting is, which
     * matters because the setting can change after a download was made (delete must still find
     * a title that was downloaded to a folder the user has since moved away from). Companion
     * cleanup (subtitle/.nfo) is filtered by this item's own id prefix, not just "everything in
     * the folder" - two different titles that happen to be named identically would otherwise
     * share a folder, and a blanket folder wipe would take the other title's files down with
     * it. [customTreeUri] is only a best-effort hint for locating SAF companions if the video
     * itself lived in a user-picked folder - if the setting changed since that download, an
     * orphaned subtitle/.nfo may be left behind, but the video (the part that matters) is still
     * always removed correctly. */
    fun deleteAll(context: Context, item: MediaItemEntity, videoUri: Uri?, customTreeUri: Uri?) {
        val isSaf = videoUri != null && DocumentsContract.isDocumentUri(context, videoUri)
        if (videoUri != null) {
            runCatching {
                if (isSaf) DocumentsContract.deleteDocument(context.contentResolver, videoUri)
                else context.contentResolver.delete(videoUri, null, null)
            }
        }
        if (isSaf) {
            if (customTreeUri == null) return
            runCatching {
                val root = DocumentFile.fromTreeUri(context, customTreeUri) ?: return@runCatching
                val folder = root.findFile(sanitizedTitleFolder(item))?.takeIf { it.isDirectory } ?: return@runCatching
                folder.listFiles().forEach { child ->
                    if (child.name?.startsWith("${item.id}.") == true) child.delete()
                }
            }
        } else {
            val relativePath = relativePathFor(item)
            runCatching {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf(relativePath, "${item.id}.%")
                )
            }
        }
    }
}

/** True if [path] is a `content://` URI (a download made after the move away from the app's
 * private cache, whether to the default Download/HomeCinema or a user-picked folder) rather
 * than a plain filesystem path (an older download, from before that change, still sitting in
 * its original app-private location - left as-is, not migrated, see the class doc above). */
fun isContentUri(path: String): Boolean = path.startsWith("content://")
