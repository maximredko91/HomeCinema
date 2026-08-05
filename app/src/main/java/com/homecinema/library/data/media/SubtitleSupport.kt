package com.homecinema.library.data.media

import androidx.media3.common.MimeTypes
import java.io.File

/** Sidecar subtitle file extensions this app will look for next to a video - the common
 * formats real releases actually ship with. */
val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "sub", "vtt")

/** ExoPlayer MIME type for a sidecar subtitle file, by extension. */
fun subtitleMimeType(extension: String): String = when (extension.lowercase()) {
    "srt", "sub" -> MimeTypes.APPLICATION_SUBRIP
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    "vtt" -> MimeTypes.TEXT_VTT
    else -> MimeTypes.APPLICATION_SUBRIP
}

/** A subtitle file sitting next to [videoFile] in the same local folder with the same base
 * name (e.g. downloaded alongside the video by DownloadWorker - see destFileFor there),
 * or null if none of the common extensions matches anything in that folder. */
fun findLocalSiblingSubtitle(videoFile: File): File? {
    val dir = videoFile.parentFile ?: return null
    val baseName = videoFile.nameWithoutExtension
    return dir.listFiles()?.firstOrNull { child ->
        child.isFile &&
            child.nameWithoutExtension.equals(baseName, ignoreCase = true) &&
            child.extension.lowercase() in SUBTITLE_EXTENSIONS
    }
}
