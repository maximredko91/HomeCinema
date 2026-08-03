package com.homecinema.library.data.nfo

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/** Parsed subset of a Kodi nfo file we actually need for the library UI. */
data class NfoData(
    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val plot: String? = null,
    val rating: Double? = null,
    // "movie" / "tvshow" / "episodedetails" - the root tag tells us the nfo's kind
    val rootTag: String? = null,
    // only present in episodedetails.nfo
    val season: Int? = null,
    val episode: Int? = null,
    val runtimeMinutes: Int? = null,
    val country: String? = null,
    val directors: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    // from <set><name>...</name></set> - the Kodi "movie set" this title belongs to
    val collectionName: String? = null,
    // free-form theme keywords from <tag> - distinct from <genre>, e.g. "пираты", "based on a true story"
    val tags: List<String> = emptyList()
)

private val ANIMATION_KEYWORDS = listOf("animation", "аним", "мультф", "cartoon", "anime")

/** True if the given genre list looks like it describes an animated title. */
fun List<String>.looksAnimated(): Boolean =
    any { genre -> ANIMATION_KEYWORDS.any { genre.contains(it, ignoreCase = true) } }

/**
 * Parses standard Kodi nfo XML (root tags <movie>, <tvshow>, <episodedetails>, ...).
 * Kodi nfo files sometimes have a URL prepended before the XML declaration (from old
 * scrapers) - we skip forward to the first '<' to stay tolerant of that.
 */
object NfoParser {

    fun parse(input: InputStream): NfoData {
        val text = input.bufferedReader(Charsets.UTF_8).readText()
        val xmlStart = text.indexOf('<')
        val xml = if (xmlStart > 0) text.substring(xmlStart) else text

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        var rootTag: String? = null
        var title: String? = null
        var originalTitle: String? = null
        var year: Int? = null
        val genres = mutableListOf<String>()
        var plot: String? = null
        var rating: Double? = null
        var season: Int? = null
        var episode: Int? = null
        var runtimeMinutes: Int? = null
        var country: String? = null
        val directors = mutableListOf<String>()
        val actors = mutableListOf<String>()
        var collectionName: String? = null
        val tags = mutableListOf<String>()

        // A tag stack (rather than a single "current tag") is needed to tell apart
        // same-named leaf tags that mean different things depending on their parent,
        // e.g. <set><name> (collection name) vs <actor><name> (cast member name).
        val tagStack = ArrayDeque<String>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    tagStack.addLast(parser.name)
                    if (rootTag == null) rootTag = parser.name
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text?.trim().orEmpty()
                    if (value.isNotEmpty()) {
                        val currentTag = tagStack.lastOrNull()
                        val parentTag = if (tagStack.size >= 2) tagStack[tagStack.size - 2] else null
                        when (currentTag) {
                            "title" -> if (title == null) title = value
                            "originaltitle" -> if (originalTitle == null) originalTitle = value
                            "year" -> year = value.toIntOrNull() ?: year
                            "genre" -> genres.add(value)
                            "plot" -> if (plot == null) plot = value
                            "rating", "value" -> rating = value.toDoubleOrNull() ?: rating
                            "season" -> season = value.toIntOrNull() ?: season
                            "episode" -> episode = value.toIntOrNull() ?: episode
                            "runtime" -> runtimeMinutes = value.toIntOrNull() ?: runtimeMinutes
                            "country" -> if (country == null) country = value
                            "director" -> directors.add(value)
                            "tag" -> tags.add(value)
                            "name" -> when (parentTag) {
                                "set" -> collectionName = value
                                "actor" -> actors.add(value)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> tagStack.removeLastOrNull()
            }
            eventType = parser.next()
        }

        return NfoData(
            title = title,
            originalTitle = originalTitle,
            year = year,
            genres = genres.distinct(),
            plot = plot,
            rating = rating,
            rootTag = rootTag,
            season = season,
            episode = episode,
            runtimeMinutes = runtimeMinutes,
            country = country,
            directors = directors.distinct(),
            actors = actors.distinct(),
            collectionName = collectionName?.takeIf { it.isNotBlank() },
            tags = tags.distinct()
        )
    }
}

/**
 * Falls back to parsing "S01E02", "1x02", or "Season 1/Episode 2"-style patterns out
 * of a filename when a matching episodedetails.nfo is missing or lacks season/episode tags.
 */
object EpisodeFilenamePattern {
    private val SxxExx = Regex("[Ss](\\d{1,2})[Ee](\\d{1,3})")
    private val NxN = Regex("(\\d{1,2})x(\\d{1,3})")

    fun extract(name: String): Pair<Int, Int>? {
        SxxExx.find(name)?.let { m ->
            val (s, e) = m.destructured
            return s.toInt() to e.toInt()
        }
        NxN.find(name)?.let { m ->
            val (s, e) = m.destructured
            return s.toInt() to e.toInt()
        }
        return null
    }
}
