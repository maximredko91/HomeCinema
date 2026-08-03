package com.homecinema.library.ui.viewmodel

import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

private fun item(
    id: String,
    title: String,
    mediaType: MediaType = MediaType.MOVIE,
    year: Int? = null,
    genres: String = "",
    plot: String = "",
    actors: String? = null,
    director: String? = null,
    collectionName: String? = null,
    rating: Double? = null,
    tags: String = ""
): MediaItemEntity = MediaItemEntity(
    id = id,
    title = title,
    year = year,
    genres = genres,
    plot = plot,
    rating = rating,
    mediaType = mediaType,
    folderPath = "smb://host/share/$id/",
    videoFilePath = "smb://host/share/$id/video.mkv",
    posterLocalPath = null,
    lastScanned = 0L,
    actors = actors,
    director = director,
    collectionName = collectionName,
    tags = tags
)

class LibraryFilteringTest {

    private val bond2006 = item(
        "1", "007: Казино Рояль", year = 2006, genres = "Боевик, Триллер",
        actors = "Daniel Craig, Eva Green", director = "Martin Campbell", collectionName = "007",
        rating = 8.0, tags = "шпионы, покер"
    )
    private val bond2008 = item(
        "2", "007: Квант милосердия", year = 2008, genres = "Боевик",
        director = "Marc Forster", collectionName = "007", rating = 6.6
    )
    private val interstellar = item(
        "3", "Интерстеллар", year = 2014, genres = "Фантастика, Драма",
        plot = "Освоение космоса ради выживания человечества.", director = "Christopher Nolan",
        rating = 8.6
    )
    private val sesame = item("4", "Улица Сезам", mediaType = MediaType.TV_SHOW, genres = "Мультсериал")
    private val allItems = listOf(bond2006, bond2008, interstellar, sesame)

    // Separate from allItems (used by unrelated tests with exact expected orderings/counts
    // already spelled out below) so adding it doesn't force reworking those.
    private val pirates = item(
        "5", "Пираты Карибского моря", year = 2003, genres = "Приключения",
        tags = "пираты, сокровища"
    )
    private val itemsWithPirates = allItems + pirates

    @Test
    fun `filters by media type`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.TV_SHOWS, "", SortOrder.TITLE)
        assertEquals(listOf(sesame), result)
    }

    @Test
    fun `ALL filter keeps everything`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.TITLE)
        assertEquals(4, result.size)
    }

    @Test
    fun `search matches title case-insensitively`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "интерстел", SortOrder.TITLE)
        assertEquals(listOf(interstellar), result)
    }

    @Test
    fun `search also matches plot, actors and director`() {
        assertEquals(listOf(interstellar), applyLibraryFilters(allItems, LibraryFilter.ALL, "выживания", SortOrder.TITLE))
        assertEquals(listOf(bond2006), applyLibraryFilters(allItems, LibraryFilter.ALL, "eva green", SortOrder.TITLE))
        assertEquals(listOf(interstellar), applyLibraryFilters(allItems, LibraryFilter.ALL, "nolan", SortOrder.TITLE))
    }

    @Test
    fun `search with no matches returns empty list`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "no such title", SortOrder.TITLE)
        assertEquals(emptyList<MediaItemEntity>(), result)
    }

    @Test
    fun `genre filter matches any of the item's comma-separated genres`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.TITLE, genre = "Драма")
        assertEquals(listOf(interstellar), result)
    }

    @Test
    fun `genre filter is case-insensitive`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.TITLE, genre = "боевик")
        assertEquals(setOf(bond2006, bond2008), result.toSet())
    }

    @Test
    fun `year range filter is inclusive on both ends`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.TITLE, yearRange = 2006..2008)
        assertEquals(setOf(bond2006, bond2008), result.toSet())
    }

    @Test
    fun `year range filter excludes items with no year`() {
        // sesame has no year set - must not show up in any year-bounded query
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.TITLE, yearRange = 1900..2100)
        assertEquals(false, result.contains(sesame))
    }

    @Test
    fun `collection filter restricts to that collection only`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.TITLE, collection = "007")
        assertEquals(setOf(bond2006, bond2008), result.toSet())
    }

    @Test
    fun `filters combine with AND semantics`() {
        val result = applyLibraryFilters(
            allItems, LibraryFilter.ALL, "", SortOrder.TITLE,
            collection = "007", yearRange = 2008..2008
        )
        assertEquals(listOf(bond2008), result)
    }

    @Test
    fun `sorts by title alphabetically`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.TITLE)
        assertEquals(listOf(bond2006, bond2008, interstellar, sesame), result)
    }

    @Test
    fun `sorts by year descending, newest first`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.YEAR)
        // sesame has no year (treated as 0) and sorts last
        assertEquals(listOf(interstellar, bond2008, bond2006, sesame), result)
    }

    @Test
    fun `sorts by genre, using each item's first listed genre`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.GENRE)
        // Боевик < Мультсериал < Фантастика alphabetically (Cyrillic order)
        assertEquals(listOf(bond2006, bond2008, sesame, interstellar), result)
    }

    @Test
    fun `sorts by rating descending, highest first`() {
        val result = applyLibraryFilters(allItems, LibraryFilter.ALL, "", SortOrder.RATING)
        // sesame has no rating (treated as 0.0) and sorts last
        assertEquals(listOf(interstellar, bond2006, bond2008, sesame), result)
    }

    @Test
    fun `hashtag query matches only the tags field, not title or plot`() {
        val result = applyLibraryFilters(itemsWithPirates, LibraryFilter.ALL, "#пираты", SortOrder.TITLE)
        assertEquals(listOf(pirates), result)
    }

    @Test
    fun `hashtag search is case-insensitive and ignores surrounding whitespace`() {
        val result = applyLibraryFilters(itemsWithPirates, LibraryFilter.ALL, "#ПИРАТЫ", SortOrder.TITLE)
        assertEquals(listOf(pirates), result)
    }

    @Test
    fun `hashtag query with no matching tag returns empty, even if the word is in the title`() {
        // "Рояль" is right there in bond2006's title, but it's not one of its tags - a
        // #-prefixed query must not fall back to matching title/plot/actors/director.
        val result = applyLibraryFilters(itemsWithPirates, LibraryFilter.ALL, "#рояль", SortOrder.TITLE)
        assertEquals(emptyList<MediaItemEntity>(), result)
    }

    @Test
    fun `ordinary search also matches tags, not just title-plot-actors-director`() {
        val result = applyLibraryFilters(itemsWithPirates, LibraryFilter.ALL, "сокровища", SortOrder.TITLE)
        assertEquals(listOf(pirates), result)
    }
}
