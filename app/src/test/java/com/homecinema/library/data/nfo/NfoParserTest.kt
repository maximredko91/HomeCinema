package com.homecinema.library.data.nfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

private fun parse(xml: String): NfoData = NfoParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

class NfoParserTest {

    @Test
    fun `parses a typical movie nfo`() {
        val data = parse(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <movie>
                <title>Интерстеллар</title>
                <originaltitle>Interstellar</originaltitle>
                <year>2014</year>
                <genre>Sci-Fi</genre>
                <genre>Drama</genre>
                <plot>Освоение космоса ради выживания человечества.</plot>
                <rating>8.6</rating>
                <runtime>169</runtime>
                <country>USA</country>
                <director>Christopher Nolan</director>
            </movie>
            """.trimIndent()
        )

        assertEquals("movie", data.rootTag)
        assertEquals("Интерстеллар", data.title)
        assertEquals("Interstellar", data.originalTitle)
        assertEquals(2014, data.year)
        assertEquals(listOf("Sci-Fi", "Drama"), data.genres)
        assertEquals("Освоение космоса ради выживания человечества.", data.plot)
        assertEquals(8.6, data.rating!!, 0.0001)
        assertEquals(169, data.runtimeMinutes)
        assertEquals("USA", data.country)
        assertEquals(listOf("Christopher Nolan"), data.directors)
    }

    @Test
    fun `disambiguates set name from actor name via the tag stack`() {
        // This is the whole reason NfoParser tracks a tag *stack* instead of a single
        // "current tag": <set><name> and <actor><name> would otherwise collide.
        val data = parse(
            """
            <movie>
                <title>007: Казино Рояль</title>
                <set>
                    <name>James Bond Collection</name>
                </set>
                <actor>
                    <name>Daniel Craig</name>
                    <role>James Bond</role>
                </actor>
                <actor>
                    <name>Eva Green</name>
                    <role>Vesper Lynd</role>
                </actor>
            </movie>
            """.trimIndent()
        )

        assertEquals("James Bond Collection", data.collectionName)
        assertEquals(listOf("Daniel Craig", "Eva Green"), data.actors)
        // "role" isn't a name and must never leak into the actors list
        assertFalse(data.actors.contains("James Bond"))
    }

    @Test
    fun `blank set name is treated as no collection`() {
        val data = parse(
            """
            <movie>
                <title>Solo</title>
                <set><name></name></set>
            </movie>
            """.trimIndent()
        )
        assertNull(data.collectionName)
    }

    @Test
    fun `collects multiple directors in document order and dedupes`() {
        val data = parse(
            """
            <movie>
                <title>Матрица</title>
                <director>Lana Wachowski</director>
                <director>Lilly Wachowski</director>
                <director>Lana Wachowski</director>
            </movie>
            """.trimIndent()
        )
        assertEquals(listOf("Lana Wachowski", "Lilly Wachowski"), data.directors)
    }

    @Test
    fun `parses tvshow root and genre for animation detection`() {
        val data = parse(
            """
            <tvshow>
                <title>Улица Сезам</title>
                <genre>Animation</genre>
            </tvshow>
            """.trimIndent()
        )
        assertEquals("tvshow", data.rootTag)
        assertTrue(data.genres.looksAnimated())
    }

    @Test
    fun `parses episodedetails season and episode`() {
        val data = parse(
            """
            <episodedetails>
                <title>Пилот</title>
                <season>1</season>
                <episode>2</episode>
                <plot>Начало истории.</plot>
            </episodedetails>
            """.trimIndent()
        )
        assertEquals("episodedetails", data.rootTag)
        assertEquals(1, data.season)
        assertEquals(2, data.episode)
    }

    @Test
    fun `rating nested inside a value tag is still picked up`() {
        val data = parse(
            """
            <movie>
                <title>Nested rating</title>
                <ratings>
                    <rating name="imdb">
                        <value>7.4</value>
                    </rating>
                </ratings>
            </movie>
            """.trimIndent()
        )
        assertEquals(7.4, data.rating!!, 0.0001)
    }

    @Test
    fun `tolerates a URL prepended before the xml declaration by old scrapers`() {
        val data = parse(
            "http://example.com/scraper-leftover\n<movie><title>Legacy Scrape</title></movie>"
        )
        assertEquals("Legacy Scrape", data.title)
    }

    @Test
    fun `first title and plot win, later duplicates are ignored`() {
        val data = parse(
            """
            <movie>
                <title>First</title>
                <title>Second</title>
                <plot>First plot</plot>
                <plot>Second plot</plot>
            </movie>
            """.trimIndent()
        )
        assertEquals("First", data.title)
        assertEquals("First plot", data.plot)
    }

    @Test
    fun `looksAnimated matches Russian and English animation keywords`() {
        assertTrue(listOf("Мультфильм").looksAnimated())
        assertTrue(listOf("Action", "Animation").looksAnimated())
        assertTrue(listOf("Anime").looksAnimated())
        assertFalse(listOf("Drama", "Thriller").looksAnimated())
        assertFalse(emptyList<String>().looksAnimated())
    }
}
