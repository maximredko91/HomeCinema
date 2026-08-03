package com.homecinema.library.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType
import com.homecinema.library.data.scanner.ScanProgress
import com.homecinema.library.data.update.ReleaseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI filter for the library grid - "null" means "show everything". */
enum class LibraryFilter(val types: Set<MediaType>?) {
    ALL(null),
    MOVIES(setOf(MediaType.MOVIE)),
    TV_SHOWS(setOf(MediaType.TV_SHOW)),
    CARTOONS(setOf(MediaType.CARTOON)),
    CARTOON_SERIES(setOf(MediaType.CARTOON_SERIES))
}

enum class SortOrder(val label: String) {
    TITLE("По названию"),
    YEAR("По году"),
    GENRE("По жанру"),
    RATING("По рейтингу")
}

enum class LibraryTab { ALL, COLLECTIONS }

data class CollectionSummary(
    val name: String,
    val posterPath: String?,
    val count: Int
)

class LibraryViewModel : ViewModel() {
    private val app: HomeCinemaApp get() = HomeCinemaApp.instance

    private val allItems: StateFlow<List<MediaItemEntity>> = app.repository.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    private val _libraryTab = MutableStateFlow(LibraryTab.ALL)
    val libraryTab: StateFlow<LibraryTab> = _libraryTab

    private val _selectedCollection = MutableStateFlow<String?>(null)
    val selectedCollection: StateFlow<String?> = _selectedCollection

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre

    private val _yearRange = MutableStateFlow<IntRange?>(null)
    val yearRange: StateFlow<IntRange?> = _yearRange

    /** All distinct genres in the library, for the genre filter picker. */
    val availableGenres: StateFlow<List<String>> = allItems.map { items ->
        items.flatMap { it.genres.split(",").map { g -> g.trim() } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Min/max release year across the library, as bounds for the year-range picker. */
    val availableYearBounds: StateFlow<IntRange?> = allItems.map { items ->
        val years = items.mapNotNull { it.year }
        if (years.isEmpty()) null else years.min()..years.max()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Movies/cartoons grouped by their Kodi "movie set", for the Коллекции tab. */
    val collections: StateFlow<List<CollectionSummary>> = allItems.map { items ->
        items.filter { !it.collectionName.isNullOrBlank() }
            .groupBy { it.collectionName!! }
            .map { (name, group) ->
                CollectionSummary(
                    name = name,
                    posterPath = group.firstOrNull { it.posterLocalPath != null }?.posterLocalPath,
                    count = group.size
                )
            }
            .sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Item count per category, shown next to each option in the category filter chips. */
    val categoryCounts: StateFlow<Map<LibraryFilter, Int>> = allItems.map { items ->
        LibraryFilter.entries.associateWith { option ->
            val types = option.types
            if (types == null) items.size else items.count { it.mediaType in types }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private data class BaseFilterState(
        val items: List<MediaItemEntity>,
        val filter: LibraryFilter,
        val query: String,
        val sortOrder: SortOrder,
        val collection: String?
    )

    private val baseFilterState = combine(
        allItems, _filter, _query, _sortOrder, _selectedCollection
    ) { items, filter, query, sort, collection -> BaseFilterState(items, filter, query, sort, collection) }

    val items: StateFlow<List<MediaItemEntity>> = combine(
        baseFilterState, _selectedGenre, _yearRange
    ) { base, genre, years ->
        applyLibraryFilters(base.items, base.filter, base.query, base.sortOrder, base.collection, genre, years)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress

    val isConfigured: StateFlow<Boolean> = app.repository.observeSources()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val alphabetIndexEnabled: StateFlow<Boolean> = app.settingsStore.alphabetIndexEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val gridColumns: StateFlow<Int> = app.settingsStore.gridColumnsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    /** In-progress movies/cartoons/episodes, most recently played first. */
    val continueWatching: StateFlow<List<MediaItemEntity>> = app.repository.observeContinueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableUpdate: StateFlow<ReleaseInfo?> = app.availableUpdate

    fun dismissUpdate(version: String) = app.dismissUpdate(version)

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setLibraryTab(tab: LibraryTab) {
        _libraryTab.value = tab
        if (tab == LibraryTab.ALL) _selectedCollection.value = null
    }

    fun selectCollection(name: String) {
        _selectedCollection.value = name
    }

    fun clearCollectionSelection() {
        _selectedCollection.value = null
    }

    fun setGenre(genre: String?) {
        _selectedGenre.value = genre
    }

    fun setYearRange(range: IntRange?) {
        _yearRange.value = range
    }

    fun rescan() {
        viewModelScope.launch {
            _scanProgress.value = ScanProgress.Scanning(0)
            app.repository.rescan { progress -> _scanProgress.value = progress }
        }
    }

    fun dismissProgress() {
        _scanProgress.value = null
    }

    /** Removes an item from "Продолжить просмотр" by clearing its saved position - the
     * dao query that backs [continueWatching] requires playbackPositionMs > 5000, so
     * zeroing it out is enough to drop the item without a dedicated "hidden" flag. */
    fun dismissContinueWatching(id: String) {
        viewModelScope.launch {
            app.repository.updatePlaybackProgress(id, 0, 0)
        }
    }
}
