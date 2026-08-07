package com.homecinema.library.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.homecinema.library.R
import com.homecinema.library.data.db.CustomListEntity
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.scanner.ScanProgress
import com.homecinema.library.data.settings.LibraryLayout
import com.homecinema.library.data.settings.PlaybackMode
import com.homecinema.library.data.update.ReleaseInfo
import com.homecinema.library.data.update.UpdateDownloader
import com.homecinema.library.ui.components.MediaPosterCard
import com.homecinema.library.ui.viewmodel.CollectionSummary
import com.homecinema.library.ui.viewmodel.FAVORITES_LIST_ID
import com.homecinema.library.ui.viewmodel.LibraryFilter
import com.homecinema.library.ui.viewmodel.LibraryTab
import com.homecinema.library.ui.viewmodel.LibraryViewModel
import com.homecinema.library.ui.viewmodel.SortOrder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import com.homecinema.library.ui.theme.LocalIsGlassTheme
import com.homecinema.library.ui.theme.floatingChrome
import com.homecinema.library.ui.theme.glassBackdrop
import com.homecinema.library.ui.theme.glassEffect
import com.homecinema.library.ui.theme.glassSheetContainerColor
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors
import com.homecinema.library.ui.theme.ProvideGlassHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.File

/** The content width (screen width minus the grid's own horizontal padding) "Число колонок" in
 * Settings is implicitly calibrated against - a normal portrait phone. See the effectiveColumns
 * comment in LibraryGrid for why this matters. */
private const val REFERENCE_GRID_WIDTH_DP = 360f

/** "Число колонок" in Settings is calibrated against a normal portrait phone width - reused
 * verbatim in landscape (or on a tablet) it left every card roughly 2-3x wider, and since
 * posters keep a fixed aspect ratio, 2-3x taller too - tall enough to not fit on screen at all.
 * Scales the column count up with the extra width first, so each card stays close to the size
 * the user actually picked regardless of orientation - but width alone isn't enough: rotating
 * to landscape shrinks the available HEIGHT by far more than it grows the width, so a card
 * merely "sized like portrait" can still be taller than the entire screen (posters keep a tall
 * 2:3 aspect ratio). The second pass keeps adding columns - shrinking every card a bit more -
 * until at least ~1.4 rows actually fit vertically. Shared by the Библиотека/Коллекции/Списки
 * grids - each has its own LazyVerticalGrid, and all three had this same problem. */
/** Claims the left/right screen-edge strips so Android's own edge-swipe-for-back/home gesture
 * doesn't compete with this pager's own left/right swipe (switching Библиотека/Коллекции/
 * Списки) - without this, a swipe started too close to either edge got stolen by the system
 * gesture and the whole app closed/backgrounded instead of just changing tabs. Clears the
 * exclusion on dispose so it doesn't linger and affect other screens (Detail/Player) where the
 * normal system back gesture should work as usual. */
private fun Modifier.excludeFromSystemBackGesture(): Modifier = composed {
    val view = LocalView.current
    val density = LocalDensity.current
    DisposableEffect(view) {
        onDispose { view.systemGestureExclusionRects = emptyList() }
    }
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val edgeWidthPx = with(density) { 32.dp.toPx() }
        view.systemGestureExclusionRects = listOf(
            android.graphics.Rect(
                bounds.left.toInt(), bounds.top.toInt(),
                (bounds.left + edgeWidthPx).toInt(), bounds.bottom.toInt()
            ),
            android.graphics.Rect(
                (bounds.right - edgeWidthPx).toInt(), bounds.top.toInt(),
                bounds.right.toInt(), bounds.bottom.toInt()
            )
        )
    }
}

@Composable
private fun rememberEffectiveColumns(
    gridColumns: Int,
    availableWidth: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp
): Int =
    remember(gridColumns, availableWidth, availableHeight) {
        val widthScale = availableWidth.value / REFERENCE_GRID_WIDTH_DP
        var columns = (gridColumns * widthScale).roundToInt().coerceAtLeast(gridColumns)

        val horizontalPadding = 32f // 16dp start + 16dp end content padding
        val spacing = 12f // horizontalArrangement spacing between columns
        val posterAspect = 2f / 3f // width / height
        val titleAreaHeight = 56f // title (up to 2 lines) + subtitle + spacer below the poster
        val minVisibleRows = 1.4f
        while (columns < gridColumns + 8) {
            val cardWidth = (availableWidth.value - horizontalPadding - spacing * (columns - 1)) / columns
            val cardHeight = cardWidth / posterAspect + titleAreaHeight
            if (cardHeight * minVisibleRows <= availableHeight.value) break
            columns++
        }
        columns
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onPlayItem: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val items by viewModel.items.collectAsState()
    val progress by viewModel.scanProgress.collectAsState()
    val configured by viewModel.isConfigured.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.query.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val libraryTab by viewModel.libraryTab.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val selectedCollection by viewModel.selectedCollection.collectAsState()
    val alphabetIndexEnabled by viewModel.alphabetIndexEnabled.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()
    val yearRange by viewModel.yearRange.collectAsState()
    val availableYearBounds by viewModel.availableYearBounds.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val rescanSuggested by viewModel.rescanSuggested.collectAsState()
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val customLists by viewModel.customLists.collectAsState()
    val listCounts by viewModel.listCounts.collectAsState()
    val favoritesCount by viewModel.favoritesCount.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val libraryLayout by viewModel.libraryLayout.collectAsState()
    val playbackMode by viewModel.playbackMode.collectAsState()
    val context = LocalContext.current
    val playScope = rememberCoroutineScope()
    // Continue Watching used to always jump into the internal player regardless of the user's
    // playback-mode setting - if you'd started something in an external player (MX Player,
    // etc.), tapping its Continue Watching card yanked you into the internal one instead, with
    // no memory of where you actually were. Routes through the same setting as the detail
    // screen's own "Смотреть" button. ASK still defaults to internal here (no natural place for
    // a second button on a Continue Watching card the way DetailScreen has room for one).
    fun handleContinueWatchingClick(item: MediaItemEntity) {
        if (playbackMode == PlaybackMode.EXTERNAL) {
            playScope.launch { playExternally(context, item) }
        } else {
            onPlayItem(item.id)
        }
    }

    var searchActive by remember { mutableStateOf(false) }
    var filtersSheetOpen by remember { mutableStateOf(false) }
    var mediaTypeMenuOpen by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    // Whole header (top bar + banners) slides away on scroll-down, back on scroll-up - shared
    // between both layout branches below since only one is ever actually composed at a time.
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Hoisted here (rather than inside CollectionsGrid/ListsGrid) because LibraryScreen itself
    // stays mounted while browsing into and out of a collection/list - a state remembered
    // inside those composables would reset every time they unmount/remount. Separate instances
    // (not shared) because with swipeable tabs both grids can be composed simultaneously mid-
    // drag, and a single LazyGridState can only be attached to one LazyVerticalGrid at a time.
    val collectionsGridState = rememberLazyGridState()
    val listsGridState = rememberLazyGridState()
    val pagerState = rememberPagerState(initialPage = libraryTab.ordinal) { LibraryTab.entries.size }

    val selectedListName = when (selectedListId) {
        null -> null
        FAVORITES_LIST_ID -> "Избранное"
        else -> customLists.firstOrNull { it.id == selectedListId }?.name
    }

    // Two-way sync between the pager (swipe) and the ViewModel's tab state (tap on TabRow).
    // Switching from currentPage to settledPage (previous fix) stopped a multi-page tap (e.g.
    // Библиотека -> Списки, animating through Коллекции) from reporting the page it's merely
    // passing through as a real stop - but settledPage still isn't guaranteed to hold at the
    // ORIGIN page for the animation's entire duration on every Pager version/timing, and even
    // one spurious mid-flight report is enough: it calls setLibraryTab with the wrong tab,
    // which re-keys the second effect below and cancels the in-flight animateScrollToPage
    // before it ever reaches the real target - the net effect being a tap that visibly does
    // nothing (settles back at the origin) rather than landing on the wrong page. A tab tap
    // already knows its destination before the pager moves at all, so syncing the pager's
    // position back to the tab state while that same tap's animation is still running is
    // never needed - this flag suppresses it structurally instead of trusting the exact
    // instant settledPage flips.
    var animatingFromTabTap by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.settledPage) {
        if (animatingFromTabTap) return@LaunchedEffect
        val tab = LibraryTab.entries[pagerState.settledPage]
        if (tab != libraryTab) viewModel.setLibraryTab(tab)
    }
    LaunchedEffect(libraryTab) {
        if (pagerState.currentPage != libraryTab.ordinal) {
            animatingFromTabTap = true
            pagerState.animateScrollToPage(libraryTab.ordinal)
            animatingFromTabTap = false
        }
    }

    // Records whatever the user was searching for (if anything) before actually closing search -
    // capturing it here rather than after clearing the query, since setQuery("") below would
    // otherwise wipe out the value being recorded.
    fun closeSearch() {
        if (query.isNotBlank()) viewModel.recordSearchHistoryEntry(query)
        searchActive = false
        viewModel.setQuery("")
    }

    // Closing search takes priority over leaving a collection/list when both are active -
    // it's the more local, more recently opened piece of state.
    BackHandler(enabled = selectedCollection != null) { viewModel.clearCollectionSelection() }
    BackHandler(enabled = selectedListId != null) { viewModel.clearListSelection() }
    BackHandler(enabled = searchActive) { closeSearch() }

    // At the root of any of the three tabs (nothing drilled into, no search) - back used to
    // exit the app immediately, which is technically correct (there's nowhere else in the app
    // to go back to) but easy to trigger by accident with an edge-swipe. One more tap/swipe
    // within 2s actually exits; the first one just warns instead.
    var backPressedOnce by remember { mutableStateOf(false) }
    BackHandler(enabled = selectedCollection == null && selectedListId == null && !searchActive) {
        if (backPressedOnce) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "Нажмите «назад» ещё раз, чтобы выйти", Toast.LENGTH_SHORT).show()
            playScope.launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }

    val showSearchAndFilters = libraryTab == LibraryTab.ALL || selectedCollection != null || selectedListId != null
    // Same branch this drives in the `when` below - the update/continue-watching banners
    // only ever showed inside LibraryGrid, never above CollectionsGrid/ListsGrid.
    val showingLibraryGrid = !(libraryTab == LibraryTab.COLLECTIONS && selectedCollection == null) &&
        !(libraryTab == LibraryTab.LISTS && selectedListId == null)
    val filtersActive = filter != LibraryFilter.ALL || selectedGenre != null || yearRange != null

    ProvideGlassHazeState {
    val bodyContent: @Composable (PaddingValues) -> Unit = { padding ->
        Box(Modifier.fillMaxSize().glassBackdrop()) {
            if (searchActive && query.isBlank()) {
                SearchHistoryPanel(
                    history = searchHistory,
                    topContentPadding = padding.calculateTopPadding(),
                    onSelect = viewModel::setQuery,
                    onRemove = viewModel::removeSearchHistoryEntry,
                    onClearAll = viewModel::clearSearchHistory
                )
            } else if (selectedCollection == null && selectedListId == null) {
                // Swiping between tabs only makes sense at each tab's top level - once drilled
                // into a specific collection or list, that replaces the pager with a single
                // filtered view instead (same as before swipe support existed).
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().excludeFromSystemBackGesture()
                ) { page ->
                    when (LibraryTab.entries[page]) {
                        LibraryTab.ALL -> LibraryGrid(
                            configured = configured,
                            items = items,
                            progress = progress,
                            filter = filter,
                            query = query,
                            sortOrder = sortOrder,
                            // Only show the alphabet bar once settled on this page - during a
                            // swipe HorizontalPager keeps neighboring pages composed so the
                            // transition can be followed, which otherwise let the bar's edge
                            // show through over the Collections/Lists page mid-drag. Gated on
                            // the pager's own horizontal offset rather than isScrollInProgress -
                            // that flag also flips during an ordinary vertical fling inside the
                            // nested grid (nested-scroll handshake with the pager), which made
                            // the bar pop in/out and the grid reflow width on every scroll -
                            // the "card jitter" bug. currentPageOffsetFraction only moves when
                            // there's an actual horizontal drag/transition.
                            alphabetIndexEnabled = alphabetIndexEnabled &&
                                kotlin.math.abs(pagerState.currentPageOffsetFraction) < 0.01f,
                            gridColumns = gridColumns,
                            onOpenDetail = onOpenDetail,
                            onOpenSettings = onOpenSettings,
                            onSetQuery = viewModel::setQuery,
                            onRescan = viewModel::rescan,
                            onDismissProgress = viewModel::dismissProgress,
                            topContentPadding = padding.calculateTopPadding(),
                            bottomContentPadding = padding.calculateBottomPadding()
                        )
                        LibraryTab.COLLECTIONS -> CollectionsGrid(
                            configured = configured,
                            collections = collections,
                            gridColumns = gridColumns,
                            gridState = collectionsGridState,
                            onOpenSettings = onOpenSettings,
                            onSelectCollection = viewModel::selectCollection,
                            topContentPadding = padding.calculateTopPadding(),
                            bottomContentPadding = padding.calculateBottomPadding()
                        )
                        LibraryTab.LISTS -> ListsGrid(
                            customLists = customLists,
                            listCounts = listCounts,
                            favoritesCount = favoritesCount,
                            gridColumns = gridColumns,
                            gridState = listsGridState,
                            onSelectList = viewModel::selectList,
                            onCreateList = viewModel::createList,
                            onRenameList = viewModel::renameList,
                            onDeleteList = viewModel::deleteList,
                            topContentPadding = padding.calculateTopPadding(),
                            bottomContentPadding = padding.calculateBottomPadding()
                        )
                    }
                }
            } else {
                // Drilled into one specific collection or list - an empty result here means
                // "this collection/list has nothing in it", not "the library needs scanning".
                // The generic empty state (used on the main Библиотека tab) suggested running
                // a library scan even for an empty Избранное, which made no sense - there's
                // nothing to scan for, the user just hasn't favorited anything yet.
                val emptyOverride = if (items.isEmpty() && progress == null) {
                    when {
                        selectedListId == FAVORITES_LIST_ID -> LibraryEmptyOverride(
                            title = "Пока ничего нет в избранном",
                            subtitle = "Отмечайте фильмы и сериалы сердечком на странице описания, чтобы они появились здесь."
                        )
                        selectedListId != null -> LibraryEmptyOverride(
                            title = "Список пуст",
                            subtitle = "Добавьте фильмы или сериалы в этот список со страницы описания."
                        )
                        selectedCollection != null -> LibraryEmptyOverride(
                            title = "В коллекции ничего нет",
                            subtitle = "Похоже, все тайтлы из этой коллекции пропали из библиотеки."
                        )
                        else -> null
                    }
                } else null
                LibraryGrid(
                    configured = configured,
                    items = items,
                    progress = progress,
                    filter = filter,
                    query = query,
                    sortOrder = sortOrder,
                    alphabetIndexEnabled = alphabetIndexEnabled,
                    gridColumns = gridColumns,
                    onOpenDetail = onOpenDetail,
                    onOpenSettings = onOpenSettings,
                    onSetQuery = viewModel::setQuery,
                    onRescan = viewModel::rescan,
                    onDismissProgress = viewModel::dismissProgress,
                    topContentPadding = padding.calculateTopPadding(),
                    bottomContentPadding = padding.calculateBottomPadding(),
                    emptyOverride = emptyOverride
                )
            }
        }
    }

    if (libraryLayout == LibraryLayout.CLASSIC) {
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            Column(
                Modifier
                    .offset { IntOffset(0, topBarScrollBehavior.state.heightOffset.roundToInt()) }
                    .floatingChrome()
            ) {
            TopAppBar(
                scrollBehavior = topBarScrollBehavior,
                title = {
                    if (searchActive) {
                        TextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            placeholder = { Text("Название, актёры, описание или #тег") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                    } else if (libraryTab == LibraryTab.COLLECTIONS && selectedCollection != null) {
                        Text(
                            text = selectedCollection!!,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (libraryTab == LibraryTab.LISTS && selectedListName != null) {
                        Text(
                            text = selectedListName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "Home Cinema",
                            modifier = Modifier.size(32.dp).clickable {
                                closeSearch()
                                viewModel.setLibraryTab(LibraryTab.ALL)
                                viewModel.clearCollectionSelection()
                                viewModel.setFilter(LibraryFilter.ALL)
                                viewModel.setGenre(null)
                                viewModel.setYearRange(null)
                            }
                        )
                    }
                },
                navigationIcon = {
                    if (searchActive) {
                        IconButton(onClick = { closeSearch() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Закрыть поиск")
                        }
                    } else if (libraryTab == LibraryTab.COLLECTIONS && selectedCollection != null) {
                        IconButton(onClick = { viewModel.clearCollectionSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К списку коллекций")
                        }
                    } else if (libraryTab == LibraryTab.LISTS && selectedListId != null) {
                        IconButton(onClick = { viewModel.clearListSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К списку списков")
                        }
                    }
                },
                actions = {
                    if (searchActive) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    } else {
                        if (showSearchAndFilters) {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Поиск")
                            }
                            BadgedBox(badge = { if (filtersActive) Badge() }) {
                                IconButton(onClick = { filtersSheetOpen = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Фильтры и сортировка")
                                }
                            }
                        }
                        IconButton(onClick = onOpenDownloads) {
                            Icon(Icons.Default.Download, contentDescription = "Загрузки")
                        }
                        IconButton(onClick = onOpenHistory) {
                            Icon(Icons.Default.History, contentDescription = "История просмотров")
                        }
                        IconButton(onClick = { viewModel.rescan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Сканировать библиотеку")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                },
                colors = homeCinemaTopAppBarColors()
            )
            if (searchActive && filtersActive) {
                ActiveFilterDuringSearchBanner(
                    filterLabel = describeActiveFilters(filter, selectedGenre, yearRange),
                    onClearFilter = {
                        viewModel.setFilter(LibraryFilter.ALL)
                        viewModel.setGenre(null)
                        viewModel.setYearRange(null)
                    }
                )
            }
            if (configured) {
                PrimaryTabRow(selectedTabIndex = libraryTab.ordinal) {
                    Tab(
                        selected = libraryTab == LibraryTab.ALL,
                        onClick = {
                            if (libraryTab == LibraryTab.ALL) mediaTypeMenuOpen = true
                            else viewModel.setLibraryTab(LibraryTab.ALL)
                        },
                        text = { Text("Библиотека") }
                    )
                    MediaTypeFilterMenu(
                        expanded = mediaTypeMenuOpen,
                        onDismissRequest = { mediaTypeMenuOpen = false },
                        categoryCounts = categoryCounts,
                        onSelect = viewModel::setFilter
                    )
                    Tab(
                        selected = libraryTab == LibraryTab.COLLECTIONS,
                        onClick = { viewModel.setLibraryTab(LibraryTab.COLLECTIONS) },
                        text = { Text("Коллекции") }
                    )
                    Tab(
                        selected = libraryTab == LibraryTab.LISTS,
                        onClick = { viewModel.setLibraryTab(LibraryTab.LISTS) },
                        text = { Text("Списки") }
                    )
                }
            }
            // Hoisted up from LibraryGrid so they're part of the same fixed/pinned header
            // as the top bar and tab row (rather than a normal Column sibling above the
            // grid) - that's what lets the grid's own bounds extend behind this whole
            // header for the blur to sample real, moving content instead of a static tint.
            if (showingLibraryGrid) {
                availableUpdate?.let { release ->
                    UpdateAvailableBanner(
                        release = release,
                        onDismiss = { viewModel.dismissUpdate(release.version) }
                    )
                }
                if (rescanSuggested) {
                    RescanSuggestedBanner(
                        onRescan = { viewModel.dismissRescanSuggestion(); viewModel.rescan() },
                        onDismiss = viewModel::dismissRescanSuggestion
                    )
                }
                if (selectedCollection == null && continueWatching.isNotEmpty()) {
                    ContinueWatchingRow(items = continueWatching, onClick = ::handleContinueWatchingClick, onDismiss = viewModel::dismissContinueWatching)
                }
            }
            }
        }
    ) { padding -> bodyContent(padding) }
    } else {
    // BOTTOM_NAV layout - primary navigation (Библиотека/Коллекции/Списки/Загрузки) lives in a
    // bottom NavigationBar instead of a top TabRow, so it's within thumb reach. The top bar
    // shrinks to just the logo/breadcrumb, search and settings - filters moved to live next to
    // the search field (search doubles as "search and/or filter" instead of two separate
    // buttons), downloads/history/scan moved out entirely (downloads is now a bottom-nav
    // destination, history lives in Settings, scan is pull-to-refresh on the grid itself, same
    // gesture in both layouts).
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            Column(
                Modifier
                    .offset { IntOffset(0, topBarScrollBehavior.state.heightOffset.roundToInt()) }
                    .floatingChrome()
            ) {
            TopAppBar(
                scrollBehavior = topBarScrollBehavior,
                title = {
                    if (searchActive) {
                        TextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            placeholder = { Text("Название, актёры, описание или #тег") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                    } else if (libraryTab == LibraryTab.COLLECTIONS && selectedCollection != null) {
                        Text(
                            text = selectedCollection!!,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (libraryTab == LibraryTab.LISTS && selectedListName != null) {
                        Text(
                            text = selectedListName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "Home Cinema",
                            modifier = Modifier.size(32.dp).clickable {
                                closeSearch()
                                viewModel.setLibraryTab(LibraryTab.ALL)
                                viewModel.clearCollectionSelection()
                                viewModel.setFilter(LibraryFilter.ALL)
                                viewModel.setGenre(null)
                                viewModel.setYearRange(null)
                            }
                        )
                    }
                },
                navigationIcon = {
                    if (searchActive) {
                        IconButton(onClick = { closeSearch() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Закрыть поиск")
                        }
                    } else if (libraryTab == LibraryTab.COLLECTIONS && selectedCollection != null) {
                        IconButton(onClick = { viewModel.clearCollectionSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К списку коллекций")
                        }
                    } else if (libraryTab == LibraryTab.LISTS && selectedListId != null) {
                        IconButton(onClick = { viewModel.clearListSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К списку списков")
                        }
                    }
                },
                actions = {
                    if (searchActive) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    } else {
                        if (showSearchAndFilters) {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Поиск")
                            }
                            BadgedBox(badge = { if (filtersActive) Badge() }) {
                                IconButton(onClick = { filtersSheetOpen = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Фильтры и сортировка")
                                }
                            }
                        }
                        IconButton(onClick = onOpenDownloads) {
                            Icon(Icons.Default.Download, contentDescription = "Загрузки")
                        }
                        IconButton(onClick = { viewModel.rescan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Сканировать библиотеку")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                },
                colors = homeCinemaTopAppBarColors()
            )
            if (searchActive && filtersActive) {
                ActiveFilterDuringSearchBanner(
                    filterLabel = describeActiveFilters(filter, selectedGenre, yearRange),
                    onClearFilter = {
                        viewModel.setFilter(LibraryFilter.ALL)
                        viewModel.setGenre(null)
                        viewModel.setYearRange(null)
                    }
                )
            }
            if (showingLibraryGrid) {
                availableUpdate?.let { release ->
                    UpdateAvailableBanner(
                        release = release,
                        onDismiss = { viewModel.dismissUpdate(release.version) }
                    )
                }
                if (rescanSuggested) {
                    RescanSuggestedBanner(
                        onRescan = { viewModel.dismissRescanSuggestion(); viewModel.rescan() },
                        onDismiss = viewModel::dismissRescanSuggestion
                    )
                }
                if (selectedCollection == null && continueWatching.isNotEmpty()) {
                    ContinueWatchingRow(items = continueWatching, onClick = ::handleContinueWatchingClick, onDismiss = viewModel::dismissContinueWatching)
                }
            }
            }
        },
        bottomBar = {
            // Same treatment as the top bar (homeCinemaTopAppBarColors): transparent container
            // in Glass theme so glassEffect's real blur shows through instead of a flat color
            // sitting on top of it un-blurred; normal opaque Material3 default otherwise.
            val isGlass = LocalIsGlassTheme.current
            // Inset from the true screen edges, not full-bleed - on phones with strongly
            // rounded/curved screen corners the OS masks off the actual corner pixels, and
            // the outermost items (Библиотека on the far left, Загрузки on the far right) sat
            // close enough to the edge to get visually clipped by that mask. A few dp of
            // margin keeps every item's touch target and label inside the safe, unmasked area
            // regardless of how aggressive the curve is on a given device.
            NavigationBar(
                modifier = Modifier.floatingChrome(),
                containerColor = if (isGlass) Color.Transparent else NavigationBarDefaults.containerColor
            ) {
                NavigationBarItem(
                    selected = libraryTab == LibraryTab.ALL,
                    onClick = {
                        closeSearch()
                        // Tapping the already-active tab again offers to narrow down by media
                        // type instead of doing nothing - useful once "Библиотека" mixes
                        // movies, cartoons, shows and cartoon series together.
                        if (libraryTab == LibraryTab.ALL) mediaTypeMenuOpen = true
                        else viewModel.setLibraryTab(LibraryTab.ALL)
                    },
                    icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                    label = { Text("Библиотека") }
                )
                MediaTypeFilterMenu(
                    expanded = mediaTypeMenuOpen,
                    onDismissRequest = { mediaTypeMenuOpen = false },
                    categoryCounts = categoryCounts,
                    onSelect = viewModel::setFilter
                )
                NavigationBarItem(
                    selected = libraryTab == LibraryTab.COLLECTIONS,
                    onClick = { closeSearch(); viewModel.setLibraryTab(LibraryTab.COLLECTIONS) },
                    icon = { Icon(Icons.Default.Collections, contentDescription = null) },
                    label = { Text("Коллекции") }
                )
                NavigationBarItem(
                    selected = libraryTab == LibraryTab.LISTS,
                    onClick = { closeSearch(); viewModel.setLibraryTab(LibraryTab.LISTS) },
                    icon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                    label = { Text("Списки") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { closeSearch(); onOpenHistory() },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("История") }
                )
            }
        }
    ) { padding -> bodyContent(padding) }
    }

    if (filtersSheetOpen) {
        FiltersSheet(
            filter = filter,
            sortOrder = sortOrder,
            selectedGenre = selectedGenre,
            availableGenres = availableGenres,
            yearRange = yearRange,
            availableYearBounds = availableYearBounds,
            categoryCounts = categoryCounts,
            onSetFilter = viewModel::setFilter,
            onSetSortOrder = viewModel::setSortOrder,
            onSetGenre = viewModel::setGenre,
            onSetYearRange = viewModel::setYearRange,
            onDismiss = { filtersSheetOpen = false }
        )
    }
    }
}

/** Overrides the "nothing here" message for a drilled-into collection/list - see the call site
 * in LibraryScreen for why the generic "scan your library" empty state didn't fit there. */
private data class LibraryEmptyOverride(val title: String, val subtitle: String)

@Composable
private fun LibraryGrid(
    configured: Boolean,
    items: List<MediaItemEntity>,
    progress: ScanProgress?,
    filter: LibraryFilter,
    query: String,
    sortOrder: SortOrder,
    alphabetIndexEnabled: Boolean,
    gridColumns: Int,
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSetQuery: (String) -> Unit,
    onRescan: () -> Unit,
    onDismissProgress: () -> Unit,
    topContentPadding: androidx.compose.ui.unit.Dp,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    emptyOverride: LibraryEmptyOverride? = null
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    val letterIndex = remember(items, sortOrder) {
        if (sortOrder != SortOrder.TITLE) emptyMap()
        else buildMap {
            items.forEachIndexed { index, item ->
                val letter = item.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: return@forEachIndexed
                if (letter !in this) put(letter, index)
            }
        }
    }
    val showAlphabetBar = alphabetIndexEnabled && letterIndex.size > 1

    // Sorted by first-item-index so we can find "which letter section is the grid currently
    // scrolled to" - the last entry whose starting index is still <= the first visible item.
    val sortedLetterEntries = remember(letterIndex) { letterIndex.entries.sortedBy { it.value } }
    val currentScrollLetter by remember(sortedLetterEntries) {
        derivedStateOf {
            // Bail out before reading firstVisibleItemIndex while actively scrolling (drag or
            // fling) - that index changes on essentially every frame during a fling, and since
            // derivedStateOf only re-subscribes to whatever it actually reads, skipping the
            // read here means this block stops recomputing on every one of those frames,
            // rather than doing (and mostly discarding) that work continuously through the
            // whole scroll gesture.
            if (gridState.isScrollInProgress) return@derivedStateOf null
            val visibleIndex = gridState.firstVisibleItemIndex
            sortedLetterEntries.lastOrNull { it.value <= visibleIndex }?.key
        }
    }

    // Separate from currentScrollLetter on purpose - that one deliberately skips reading
    // firstVisibleItemIndex mid-scroll to avoid recomposing the (expensive, width-affecting)
    // alphabet bar UI every frame, which is exactly what caused the earlier card-jitter bug in
    // this screen. This one DOES need to see every frame (it's what a scroll-through-the-
    // alphabet tick is for), but it only ever feeds a LaunchedEffect key comparison - a cheap
    // string equality check, not a recomposition of any visible UI - so it doesn't reintroduce
    // that problem.
    if (showAlphabetBar) {
        val haptic = LocalHapticFeedback.current
        val hapticLetter by remember(sortedLetterEntries) {
            derivedStateOf {
                val visibleIndex = gridState.firstVisibleItemIndex
                sortedLetterEntries.lastOrNull { it.value <= visibleIndex }?.key
            }
        }
        LaunchedEffect(hapticLetter) {
            if (hapticLetter != null && gridState.isScrollInProgress) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f)) {
                // "Число колонок" in Settings is chosen against a normal portrait phone width -
                // reused as-is in landscape (or on a tablet), it left every card roughly 2-3x
                // wider (and, since posters keep a fixed aspect ratio, 2-3x taller too) than
                // intended, tall enough to not fit on screen at all. Scales the column count up
                // with the extra width instead, so each card stays close to the size the user
                // actually picked regardless of orientation - never scales below their setting.
                val effectiveColumns = rememberEffectiveColumns(gridColumns, maxWidth, maxHeight)
                when {
                    !configured -> Box(Modifier.fillMaxSize().padding(top = topContentPadding)) {
                        EmptyState(
                            title = "Подключение не настроено",
                            subtitle = "Добавьте источник SMB-шары в настройках, чтобы начать сканирование библиотеки.",
                            actionLabel = "Открыть настройки",
                            onAction = onOpenSettings
                        )
                    }
                    items.isEmpty() && progress == null && query.isNotBlank() -> Box(Modifier.fillMaxSize().padding(top = topContentPadding)) {
                        EmptyState(
                            title = "Ничего не найдено",
                            subtitle = "По запросу «$query» ничего не нашлось. Попробуйте изменить фильтр или поисковый запрос.",
                            actionLabel = "Очистить поиск",
                            onAction = { onSetQuery("") }
                        )
                    }
                    items.isEmpty() && progress == null && emptyOverride != null -> Box(Modifier.fillMaxSize().padding(top = topContentPadding)) {
                        EmptyState(
                            title = emptyOverride.title,
                            subtitle = emptyOverride.subtitle,
                            actionLabel = null,
                            onAction = {}
                        )
                    }
                    items.isEmpty() && progress == null -> Box(Modifier.fillMaxSize().padding(top = topContentPadding)) {
                        EmptyState(
                            title = if (filter == LibraryFilter.ALL) "Библиотека пуста" else "Ничего не найдено в этой категории",
                            subtitle = "Нажмите на значок обновления вверху, чтобы просканировать диск и найти фильмы, сериалы и мультфильмы по .nfo файлам.",
                            actionLabel = "Сканировать сейчас",
                            onAction = onRescan
                        )
                    }
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(effectiveColumns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomContentPadding + 16.dp, top = topContentPadding + 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            MediaPosterCard(item = item, onClick = { onOpenDetail(item.id) })
                        }
                    }
                }

                progress?.let { p ->
                    ScanProgressBanner(
                        progress = p,
                        onDismiss = onDismissProgress,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomContentPadding)
                    )
                }

                // The scan banner floats at the same bottom edge and would otherwise sit
                // right under these buttons - push them up above it while it's visible. Plus
                // bottomContentPadding so neither sits behind the bottom nav bar (BOTTOM_NAV
                // layout only - 0dp in CLASSIC, which has no bottom bar to clear).
                ScrollJumpButtons(
                    gridState = gridState,
                    itemCount = items.size,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = bottomContentPadding + if (progress != null) 88.dp else 16.dp)
                )
            }

            if (showAlphabetBar) {
                AlphabetIndexBar(
                    letters = letterIndex.keys.toList(),
                    activeScrollLetter = currentScrollLetter,
                    topInset = topContentPadding,
                    onLetterSelected = { letter ->
                        letterIndex[letter]?.let { index -> scope.launch { gridState.scrollToItem(index) } }
                    }
                )
            }
    }
}

@Composable
private fun ContinueWatchingRow(
    items: List<MediaItemEntity>,
    onClick: (MediaItemEntity) -> Unit,
    onDismiss: (String) -> Unit
) {
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Text(
            "Продолжить просмотр",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onClick(item) },
                    onDismiss = { onDismiss(item.id) }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(item: MediaItemEntity, onClick: () -> Unit, onDismiss: () -> Unit) {
    val progressFraction = if (item.durationMs > 0) {
        (item.playbackPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else 0f
    val thumb = item.fanartLocalPath ?: item.posterLocalPath

    Column(Modifier.width(180.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Убрать из «Продолжить просмотр»",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.4f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CollectionsGrid(
    configured: Boolean,
    collections: List<CollectionSummary>,
    gridColumns: Int,
    gridState: LazyGridState,
    onOpenSettings: () -> Unit,
    onSelectCollection: (String) -> Unit,
    topContentPadding: androidx.compose.ui.unit.Dp,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    when {
        !configured -> Box(Modifier.fillMaxSize().padding(top = topContentPadding)) {
            EmptyState(
                title = "Подключение не настроено",
                subtitle = "Добавьте источник SMB-шары в настройках, чтобы начать сканирование библиотеки.",
                actionLabel = "Открыть настройки",
                onAction = onOpenSettings
            )
        }
        collections.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = topContentPadding)) {
            EmptyState(
                title = "Коллекций не найдено",
                subtitle = "Коллекции определяются по тегу <set> в .nfo файлах (например, наборы фильмов из Kodi/Radarr).",
                actionLabel = "Открыть настройки",
                onAction = onOpenSettings
            )
        }
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val effectiveColumns = rememberEffectiveColumns(gridColumns, maxWidth, maxHeight)
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(effectiveColumns),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomContentPadding + 16.dp, top = topContentPadding + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(collections, key = { it.name }) { collection ->
                    CollectionCard(collection = collection, onClick = { onSelectCollection(collection.name) })
                }
            }

            ScrollJumpButtons(
                gridState = gridState,
                itemCount = collections.size,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomContentPadding + 16.dp)
            )
        }
    }
}

/** User-created lists (like playlists) plus the pinned "Избранное" entry - separate from
 * the NFO-derived, read-only Коллекции above. */
@Composable
private fun ListsGrid(
    customLists: List<CustomListEntity>,
    listCounts: Map<String, Int>,
    favoritesCount: Int,
    gridColumns: Int,
    gridState: LazyGridState,
    onSelectList: (String) -> Unit,
    onCreateList: (String) -> Unit,
    onRenameList: (String, String) -> Unit,
    onDeleteList: (String) -> Unit,
    topContentPadding: androidx.compose.ui.unit.Dp,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var listToRename by remember { mutableStateOf<CustomListEntity?>(null) }
    var listToDelete by remember { mutableStateOf<CustomListEntity?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val effectiveColumns = rememberEffectiveColumns(gridColumns, maxWidth, maxHeight)
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(effectiveColumns),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomContentPadding + 16.dp, top = topContentPadding + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = FAVORITES_LIST_ID) {
                ListCard(
                    title = "Избранное",
                    count = favoritesCount,
                    icon = Icons.Default.Favorite,
                    onClick = { onSelectList(FAVORITES_LIST_ID) }
                )
            }
            items(customLists, key = { it.id }) { list ->
                ListCard(
                    title = list.name,
                    count = listCounts[list.id] ?: 0,
                    icon = Icons.Default.Bookmark,
                    onClick = { onSelectList(list.id) },
                    onRename = { listToRename = list },
                    onDelete = { listToDelete = list }
                )
            }
            item(key = "__create_list__") {
                CreateListCard(onClick = { showCreateDialog = true })
            }
        }

        ScrollJumpButtons(
            gridState = gridState,
            itemCount = customLists.size + 2,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomContentPadding + 16.dp)
        )
    }

    if (showCreateDialog) {
        ListNameDialog(
            title = "Новый список",
            initialName = "",
            confirmLabel = "Создать",
            onConfirm = { name -> onCreateList(name) },
            onDismiss = { showCreateDialog = false }
        )
    }

    listToRename?.let { list ->
        ListNameDialog(
            title = "Переименовать список",
            initialName = list.name,
            confirmLabel = "Сохранить",
            onConfirm = { name -> onRenameList(list.id, name) },
            onDismiss = { listToRename = null }
        )
    }

    listToDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Удалить список «${list.name}»?") },
            text = { Text("Фильмы из библиотеки не пропадут, удалится только сам список.") },
            confirmButton = {
                TextButton(onClick = { onDeleteList(list.id); listToDelete = null }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ListCard(
    title: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            if (onRename != null || onDelete != null) {
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Действия со списком", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        onRename?.let { rename ->
                            DropdownMenuItem(
                                text = { Text("Переименовать") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; rename() }
                            )
                        }
                        onDelete?.let { delete ->
                            DropdownMenuItem(
                                text = { Text("Удалить") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; delete() }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "$count ${filmsWord(count)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun CreateListCard(onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Создать список",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Создать список",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ListNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()); onDismiss() }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun CollectionCard(collection: CollectionSummary, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (collection.posterPath != null) {
                AsyncImage(
                    model = collection.posterPath,
                    contentDescription = collection.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = collection.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "${collection.count} ${filmsWord(collection.count)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
    }
}

/** Small floating buttons to jump straight to the top or bottom of a long grid.
 *
 * Deliberately its own fixed dark/opaque look, not participating in the Glass theme's blur or
 * any theme-dependent color branching - two rounds of chasing a reported visual glitch here
 * (a stray square rendering alongside the arrow) through the glass-blur/shape-matching/content-
 * color machinery never actually confirmed a cause, since it couldn't be reproduced or seen up
 * close from here. Removing that machinery entirely removes whatever was producing it, and a
 * plain neutral floating control over a poster grid is a normal enough pattern on its own -
 * it doesn't need to look like the glass panels to read as "belonging" to the screen. */
@Composable
private fun ScrollJumpButtons(
    gridState: LazyGridState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val canScrollUp = gridState.canScrollBackward
    val canScrollDown = gridState.canScrollForward
    if (!canScrollUp && !canScrollDown) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canScrollUp) {
            ScrollJumpButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "В начало списка",
                onClick = { scope.launch { gridState.animateScrollToItem(0) } }
            )
        }
        if (canScrollDown) {
            ScrollJumpButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "В конец списка",
                onClick = { scope.launch { gridState.animateScrollToItem((itemCount - 1).coerceAtLeast(0)) } }
            )
        }
    }
}

@Composable
private fun ScrollJumpButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    // No shadowElevation - a drop shadow behind a CircleShape needs Android to compute an
    // outline to cast it from, and on some devices/GPU drivers that outline comes out as a
    // low-vertex polygon (reported: an octagon) instead of a true circle. Skipping the shadow
    // avoids that code path entirely rather than chasing which devices mis-render it.
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

internal fun filmsWord(count: Int): String {
    val rem100 = count % 100
    val rem10 = count % 10
    return when {
        rem100 in 11..14 -> "фильмов"
        rem10 == 1 -> "фильм"
        rem10 in 2..4 -> "фильма"
        else -> "фильмов"
    }
}

/**
 * Fast-scroll A-Z rail along the grid's edge. A single pointerInput gesture handles both
 * a plain tap (jump once) and a drag (scrub through letters continuously), since
 * detectDragGestures alone never fires for a stationary tap-up.
 */
@Composable
private fun AlphabetIndexBar(
    letters: List<String>,
    activeScrollLetter: String?,
    topInset: androidx.compose.ui.unit.Dp,
    onLetterSelected: (String) -> Unit
) {
    var heightPx by remember { mutableStateOf(0f) }
    var dragLetter by remember { mutableStateOf<String?>(null) }
    // While dragging the index bar itself, that takes priority; otherwise highlight
    // whichever letter section the grid is currently scrolled to.
    val highlightedLetter = dragLetter ?: activeScrollLetter

    fun selectAt(y: Float) {
        if (heightPx <= 0f || letters.isEmpty()) return
        val index = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter != dragLetter) {
            dragLetter = letter
            onLetterSelected(letter)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = topInset)
            .width(22.dp)
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    selectAt(down.position.y)
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        selectAt(change.position.y)
                        change.consume()
                    }
                    dragLetter = null
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { letter ->
            Text(
                letter,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (letter == highlightedLetter) FontWeight.Bold else FontWeight.Normal,
                color = if (letter == highlightedLetter) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/** Shown in place of the grid while search is active but the field is still empty - tapping an
 * entry re-runs that search, the per-row × removes just that one, "Очистить всё" wipes the
 * whole history. A query only lands here once search is actually closed (see closeSearch() in
 * LibraryScreen) - recording on every keystroke would just fill this with typing noise. */
@Composable
private fun SearchHistoryPanel(
    history: List<String>,
    topContentPadding: androidx.compose.ui.unit.Dp,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = topContentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        if (history.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "История поиска пуста",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "История поиска",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearAll) { Text("Очистить всё") }
            }
            history.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(entry, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemove(entry) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Удалить «$entry» из истории")
                    }
                }
            }
        }
    }
}

private data class FilterOption(val filter: LibraryFilter, val label: String)

private val FILTER_OPTIONS = listOf(
    FilterOption(LibraryFilter.ALL, "Всё"),
    FilterOption(LibraryFilter.MOVIES, "Фильмы"),
    FilterOption(LibraryFilter.TV_SHOWS, "Сериалы"),
    FilterOption(LibraryFilter.CARTOONS, "Мультфильмы"),
    FilterOption(LibraryFilter.CARTOON_SERIES, "Мультсериалы")
)

/**
 * One place for every way to narrow/order the library - category, genre, year, sort -
 * opened on demand from a single toolbar icon instead of permanently occupying screen
 * space above the grid (which used to fight with scroll gestures for room).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(
    filter: LibraryFilter,
    sortOrder: SortOrder,
    selectedGenre: String?,
    availableGenres: List<String>,
    yearRange: IntRange?,
    availableYearBounds: IntRange?,
    categoryCounts: Map<LibraryFilter, Int>,
    onSetFilter: (LibraryFilter) -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onSetGenre: (String?) -> Unit,
    onSetYearRange: (IntRange?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var yearSliderValue by remember(yearRange, availableYearBounds) {
        mutableStateOf(
            (yearRange?.first ?: availableYearBounds?.first ?: 0).toFloat()..
                (yearRange?.last ?: availableYearBounds?.last ?: 0).toFloat()
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassSheetContainerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Фильтры и сортировка", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text("Категория", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FILTER_OPTIONS.forEach { option ->
                    val count = categoryCounts[option.filter]
                    FilterChip(
                        selected = filter == option.filter,
                        onClick = { onSetFilter(option.filter) },
                        label = { Text(if (count != null) "${option.label} ($count)" else option.label) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Сортировка", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOrder.entries.forEach { option ->
                    FilterChip(
                        selected = sortOrder == option,
                        onClick = { onSetSortOrder(option) },
                        label = { Text(option.label) }
                    )
                }
            }

            if (availableGenres.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Жанр", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedGenre == null,
                        onClick = { onSetGenre(null) },
                        label = { Text("Все жанры") }
                    )
                    availableGenres.forEach { genre ->
                        FilterChip(
                            selected = genre == selectedGenre,
                            onClick = { onSetGenre(if (genre == selectedGenre) null else genre) },
                            label = { Text(genre) }
                        )
                    }
                }
            }

            if (availableYearBounds != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Год выпуска: ${yearSliderValue.start.roundToInt()} – ${yearSliderValue.endInclusive.roundToInt()}",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                RangeSlider(
                    value = yearSliderValue,
                    onValueChange = {
                        yearSliderValue = it
                        onSetYearRange(it.start.roundToInt()..it.endInclusive.roundToInt())
                    },
                    valueRange = availableYearBounds.first.toFloat()..availableYearBounds.last.toFloat(),
                    steps = (availableYearBounds.last - availableYearBounds.first - 1).coerceAtLeast(0)
                )
            }

            Spacer(Modifier.height(20.dp))
            Row {
                TextButton(onClick = {
                    onSetFilter(LibraryFilter.ALL)
                    onSetGenre(null)
                    onSetYearRange(null)
                }) { Text("Сбросить фильтры") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Готово") }
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Offered when the user taps the already-active Библиотека tab/nav item again - narrows down
 * by media type instead of doing nothing. "Всё" always shows; the specific categories only show
 * up if the library actually has at least one title in them (categoryCounts), so someone with
 * no cartoons on disk doesn't see a "Мультфильмы" option that would just be empty. */
@Composable
private fun MediaTypeFilterMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    categoryCounts: Map<LibraryFilter, Int>,
    onSelect: (LibraryFilter) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        FILTER_OPTIONS.forEach { option ->
            if (option.filter == LibraryFilter.ALL || (categoryCounts[option.filter] ?: 0) > 0) {
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.filter)
                        onDismissRequest()
                    }
                )
            }
        }
    }
}

@Composable
private fun UpdateAvailableBanner(
    release: ReleaseInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dialogOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Доступна версия ${release.version}", modifier = Modifier.weight(1f))
            TextButton(onClick = { dialogOpen = true }) { Text("Подробнее") }
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    }

    if (dialogOpen) {
        UpdateDialog(release = release, onDismiss = { dialogOpen = false })
    }
}

/** Nudges the user to rescan right after an app update that may have added new .nfo fields
 * (studio, mpaa, tagline, ...) an older scan never picked up - see
 * HomeCinemaApp.checkForRescanSuggestion for the one-time-per-version trigger logic. */
@Composable
private fun RescanSuggestedBanner(
    onRescan: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Новая версия могла добавить новые данные — пересканировать библиотеку?",
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRescan) { Text("Сканировать") }
            TextButton(onClick = onDismiss) { Text("Позже") }
        }
    }
}

private sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val percent: Int) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

/** A thin thumb tracking a plain [ScrollState]'s position/extent - unlike LazyColumn, a
 * verticalScroll Column has no built-in scrollbar at all, so a long piece of text gave no hint
 * there was more below the fold. Hidden entirely once everything fits (nothing to scroll). */
@Composable
private fun ScrollIndicator(scrollState: androidx.compose.foundation.ScrollState, modifier: Modifier = Modifier) {
    if (scrollState.maxValue <= 0) return
    var trackHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(modifier.width(3.dp).onSizeChanged { trackHeightPx = it.height }) {
        val viewportFraction = (trackHeightPx.toFloat() / (trackHeightPx + scrollState.maxValue)).coerceIn(0.1f, 1f)
        val thumbHeightPx = trackHeightPx * viewportFraction
        val progress = scrollState.value.toFloat() / scrollState.maxValue
        val offsetPx = (trackHeightPx - thumbHeightPx) * progress
        Box(
            Modifier
                .offset { IntOffset(0, offsetPx.roundToInt()) }
                .width(3.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
        )
    }
}

/** Shows the release's changelog and lets the user decide whether to install it - tapping
 * "Установить" downloads the APK straight into the app (no browser round-trip) and hands it to
 * the system installer. Falls back to opening the GitHub release page only if a release somehow
 * has no APK asset attached (shouldn't normally happen, every release here ships one). */
@Composable
private fun UpdateDialog(release: ReleaseInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }

    fun startDownload(apkUrl: String) {
        state = UpdateDownloadState.Downloading(0)
        scope.launch {
            UpdateDownloader.download(context, apkUrl, release.version) { percent ->
                state = UpdateDownloadState.Downloading(percent)
            }.onSuccess { file ->
                installApk(context, file)
                onDismiss()
            }.onFailure { e ->
                state = UpdateDownloadState.Failed(e.message ?: "Не удалось скачать обновление")
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (state !is UpdateDownloadState.Downloading) onDismiss() },
        title = { Text("Версия ${release.version}") },
        text = {
            // No visible scrollbar by default on a plain verticalScroll Column - with a long
            // changelog entry there was nothing on screen hinting there was more to read below
            // the fold. ScrollIndicator draws a thin thumb tracking scroll position/extent.
            val scrollState = rememberScrollState()
            Row(Modifier.heightIn(max = 360.dp)) {
                Column(Modifier.weight(1f).verticalScroll(scrollState)) {
                    val current = state
                    when (current) {
                        is UpdateDownloadState.Downloading -> {
                            Text("Загрузка… ${current.percent}%")
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { current.percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is UpdateDownloadState.Failed -> {
                            Text(current.message, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            ReleaseNoteBody(release.body)
                        }
                        UpdateDownloadState.Idle -> {
                            if (release.apkUrl == null) {
                                Text(
                                    "Файл сборки не найден в этом релизе.",
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            ReleaseNoteBody(release.body)
                        }
                    }
                }
                ScrollIndicator(scrollState, modifier = Modifier.fillMaxHeight().padding(start = 4.dp))
            }
        },
        confirmButton = {
            when (state) {
                UpdateDownloadState.Idle, is UpdateDownloadState.Failed -> {
                    val apkUrl = release.apkUrl
                    if (apkUrl != null) {
                        TextButton(onClick = {
                            if (context.packageManager.canRequestPackageInstalls()) {
                                startDownload(apkUrl)
                            } else {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        }) { Text("Установить") }
                    } else {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                        }) { Text("Открыть на GitHub") }
                    }
                }
                is UpdateDownloadState.Downloading -> {}
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = state !is UpdateDownloadState.Downloading
            ) { Text("Отмена") }
        }
    )
}

private fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/** Builds a short human-readable summary of whatever category/genre/year filter is active,
 * e.g. "Мультфильмы, Комедия, 2010–2020" - shown so an active filter narrowing search results
 * doesn't quietly read as "search is broken." */
private fun describeActiveFilters(filter: LibraryFilter, genre: String?, yearRange: IntRange?): String {
    val parts = mutableListOf<String>()
    if (filter != LibraryFilter.ALL) {
        FILTER_OPTIONS.firstOrNull { it.filter == filter }?.let { parts += it.label }
    }
    if (genre != null) parts += genre
    if (yearRange != null) parts += "${yearRange.first}–${yearRange.last}"
    return parts.joinToString(", ")
}

@Composable
private fun ActiveFilterDuringSearchBanner(
    filterLabel: String,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Поиск ограничен фильтром «$filterLabel»",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearFilter) { Text("Сбросить") }
        }
    }
}

@Composable
private fun ScanProgressBanner(
    progress: ScanProgress,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (progress) {
                is ScanProgress.Scanning -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Обход папок на диске… найдено видео: ${progress.filesFound}")
                }
                is ScanProgress.Parsing -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Разбор ${progress.current}/${progress.total}")
                        Text(
                            progress.currentTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                is ScanProgress.Done -> {
                    Text("Готово: найдено ${progress.itemsFound} тайтлов")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("ОК") }
                }
                is ScanProgress.Error -> {
                    Text(
                        "Ошибка сканирования: ${progress.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            }
        }
    }
}
