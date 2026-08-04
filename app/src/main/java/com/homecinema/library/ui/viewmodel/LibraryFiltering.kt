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
    yearRange: IntRange? = null,
    listMemberIds: Set<String>? = null
): List<MediaItemEntity> {
    val types = filter.types
    var result = if (types == null) items else items.filter { it.mediaType in types }

    if (query.isNotBlank()) {
        if (query.startsWith("#")) {
            // "#пираты" - explicit tag search, matches only against .nfo <tag> keywords
            // (a separate, free-form field from <genre>) rather than the usual text fields.
            val tagQuery = query.removePrefix("#").trim()
            result = result.filter { item ->
                item.tags.split(",").map { it.trim() }.any { it.contains(tagQuery, ignoreCase = true) }
            }
        } else {
            result = result.filter { item ->
                item.title.contains(query, ignoreCase = true) ||
                    item.plot.contains(query, ignoreCase = true) ||
                    item.actors?.contains(query, ignoreCase = true) == true ||
                    item.director?.contains(query, ignoreCase = true) == true ||
                    item.tags.contains(query, ignoreCase = true)
            }
        }
    }

    if (collection != null) {
        result = result.filter { it.collectionName == collection }
    }

    if (listMemberIds != null) {
        result = result.filter { it.id in listMemberIds }
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
        SortOrder.RATING -> result.sortedWith(compareByDescending<MediaItemEntity> { it.rating ?: 0.0 }.thenBy { it.title })
    }
}
