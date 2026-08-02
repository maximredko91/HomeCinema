package com.homecinema.library.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType
import com.homecinema.library.data.scanner.ScanProgress
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

class LibraryViewModel : ViewModel() {
    private val app: HomeCinemaApp get() = HomeCinemaApp.instance

    private val allItems: StateFlow<List<MediaItemEntity>> = app.repository.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter

    val items: StateFlow<List<MediaItemEntity>> = combine(allItems, _filter) { items, filter ->
        val types = filter.types
        if (types == null) items else items.filter { it.mediaType in types }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress

    val isConfigured: StateFlow<Boolean> = app.settingsStore.configFlow
        .map { it.isConfigured }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
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
}
