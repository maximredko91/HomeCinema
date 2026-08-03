package com.homecinema.library.ui.viewmodel

import com.homecinema.library.data.db.MediaItemEntity

/**
 * Pure filter/search/sort pipeline for the library grid - extracted out of
 * LibraryViewModel's combine{} block so it can be unit tested without touching
 * StateFlow/ViewModel/HomeCinemaApp plumbing.
 */
fun applyLibraryFilters(
    items: List<MediaItemEntity>,
    filter: LibraryFilter,
    query: String,
    sortOrder: SortOrder,
    collection: String? = null,
    genre: String? = null,
    yearRange: IntRange? = null
): List<MediaItemEntity> {
    val types = filter.types
    var result = if (types == null) items else items.filter { it.mediaType in types }

    if (query.isNotBlank()) {
        result = result.filter { item ->
            item.title.contains(query, ignoreCase = true) ||
                item.plot.contains(query, ignoreCase = true) ||
                item.actors?.contains(query, ignoreCase = true) == true ||
                item.director?.contains(query, ignoreCase = true) == true
        }
    }

    if (collection != null) {
        result = result.filter { it.collectionName == collection }
    }

    if (genre != null) {
        result = result.filter { item ->
            item.genres.split(",").map { it.trim() }.any { it.equals(genre, ignoreCase = true) }
        }
    }

    if (yearRange != null) {
        result = result.filter { item -> item.year != null && item.year in yearRange }
    }

    return when (sortOrder) {
        SortOrder.TITLE -> result.sortedBy { it.title }
        SortOrder.YEAR -> result.sortedWith(compareByDescending<MediaItemEntity> { it.year ?: 0 }.thenBy { it.title })
        SortOrder.GENRE -> result.sortedWith(
            compareBy<MediaItemEntity> { it.genres.substringBefore(",").trim().ifBlank { "￿" } }.thenBy { it.title }
        )
    }
}
