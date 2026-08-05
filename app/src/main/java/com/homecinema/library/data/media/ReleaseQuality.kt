package com.homecinema.library.data.media

private val RESOLUTION_REGEX = Regex("""\b(2160p|1080p|720p|480p|4K|8K)\b""", RegexOption.IGNORE_CASE)
private val SOURCE_REGEX = Regex(
    """\b(BDRemux|BDRip|BRRip|BluRay|Blu-Ray|WEB-?DL|WEBRip|HDRip|DVDRip|DVDScr|HDTV|CAMRip|TS|TC)\b""",
    RegexOption.IGNORE_CASE
)

/**
 * Best-effort "release quality" (e.g. "1080p BDRip") parsed out of a video file's name -
 * there's no standard .nfo tag for this (it's a filename convention from release/scene groups,
 * not part of the Kodi NFO spec), so scanning the name is the only real source. Returns null if
 * neither a resolution nor a source tag is recognized, rather than a confusing partial guess.
 */
fun extractReleaseQuality(fileName: String): String? {
    val resolution = RESOLUTION_REGEX.find(fileName)?.value
    val source = SOURCE_REGEX.find(fileName)?.value
    return listOfNotNull(resolution, source).joinToString(" ").ifBlank { null }
}
