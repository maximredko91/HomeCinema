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
    val episode: Int? = null
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

        // <episodedetails> nfo files can contain a <season>/<episode> tag directly,
        // or nest them inside <uniqueid type="..."> variants depending on the scraper.
        var currentTag: String? = null
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (rootTag == null) rootTag = parser.name
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text?.trim().orEmpty()
                    if (value.isNotEmpty()) {
                        when (currentTag) {
                            "title" -> if (title == null) title = value
                            "originaltitle" -> if (originalTitle == null) originalTitle = value
                            "year" -> year = value.toIntOrNull() ?: year
                            "genre" -> genres.add(value)
                            "plot" -> if (plot == null) plot = value
                            "rating", "value" -> rating = value.toDoubleOrNull() ?: rating
                            "season" -> season = value.toIntOrNull() ?: season
                            "episode" -> episode = value.toIntOrNull() ?: episode
                        }
                    }
                }
                XmlPullParser.END_TAG -> currentTag = null
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
            episode = episode
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
