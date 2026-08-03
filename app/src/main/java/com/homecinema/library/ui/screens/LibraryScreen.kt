package com.homecinema.library.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.homecinema.library.R
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.scanner.ScanProgress
import com.homecinema.library.data.update.ReleaseInfo
import com.homecinema.library.ui.components.MediaPosterCard
import com.homecinema.library.ui.viewmodel.CollectionSummary
import com.homecinema.library.ui.viewmodel.LibraryFilter
import com.homecinema.library.ui.viewmodel.LibraryTab
import com.homecinema.library.ui.viewmodel.LibraryViewModel
import com.homecinema.library.ui.viewmodel.SortOrder
import com.homecinema.library.ui.theme.LocalIsGlassTheme
import com.homecinema.library.ui.theme.glassContainerColor
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
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

    var searchActive by remember { mutableStateOf(false) }
    var filtersSheetOpen by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // Hoisted here (rather than inside CollectionsGrid) because LibraryScreen itself stays
    // mounted while browsing into and out of a collection - a state remembered inside
    // CollectionsGrid would reset every time that composable unmounts/remounts.
    val collectionsGridState = rememberLazyGridState()

    // Closing search takes priority over leaving a collection when both are active - it's
    // the more local, more recently opened piece of state.
    BackHandler(enabled = selectedCollection != null) { viewModel.clearCollectionSelection() }
    BackHandler(enabled = searchActive) {
        searchActive = false
        viewModel.setQuery("")
    }

    val showSearchAndFilters = libraryTab == LibraryTab.ALL || selectedCollection != null
    val filtersActive = filter != LibraryFilter.ALL || selectedGenre != null || yearRange != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        TextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            placeholder = { Text("Поиск по названию, актёрам, описанию") },
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
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "Home Cinema",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                navigationIcon = {
                    if (searchActive) {
                        IconButton(onClick = { searchActive = false; viewModel.setQuery("") }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Закрыть поиск")
                        }
                    } else if (libraryTab == LibraryTab.COLLECTIONS && selectedCollection != null) {
                        IconButton(onClick = { viewModel.clearCollectionSelection() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "К списку коллекций")
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
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (configured) {
                TabRow(selectedTabIndex = libraryTab.ordinal) {
                    Tab(
                        selected = libraryTab == LibraryTab.ALL,
                        onClick = { viewModel.setLibraryTab(LibraryTab.ALL) },
                        text = { Text("Библиотека") }
                    )
                    Tab(
                        selected = libraryTab == LibraryTab.COLLECTIONS,
                        onClick = { viewModel.setLibraryTab(LibraryTab.COLLECTIONS) },
                        text = { Text("Коллекции") }
                    )
                }
            }

            when {
                libraryTab == LibraryTab.COLLECTIONS && selectedCollection == null -> CollectionsGrid(
                    configured = configured,
                    collections = collections,
                    gridColumns = gridColumns,
                    gridState = collectionsGridState,
                    onOpenSettings = onOpenSettings,
                    onSelectCollection = viewModel::selectCollection
                )
                else -> LibraryGrid(
                    configured = configured,
                    items = items,
                    progress = progress,
                    filter = filter,
                    query = query,
                    sortOrder = sortOrder,
                    alphabetIndexEnabled = alphabetIndexEnabled,
                    gridColumns = gridColumns,
                    continueWatching = if (selectedCollection == null) continueWatching else emptyList(),
                    onOpenDetail = onOpenDetail,
                    onOpenSettings = onOpenSettings,
                    onSetQuery = viewModel::setQuery,
                    onPlayItem = onPlayItem,
                    onDismissContinueWatching = viewModel::dismissContinueWatching,
                    onRescan = viewModel::rescan,
                    onDismissProgress = viewModel::dismissProgress,
                    availableUpdate = availableUpdate,
                    onDismissUpdate = viewModel::dismissUpdate
                )
            }
        }
    }

    if (filtersSheetOpen) {
        FiltersSheet(
            filter = filter,
            sortOrder = sortOrder,
            selectedGenre = selectedGenre,
            availableGenres = availableGenres,
            yearRange = yearRange,
            availableYearBounds = availableYearBounds,
            onSetFilter = viewModel::setFilter,
            onSetSortOrder = viewModel::setSortOrder,
            onSetGenre = viewModel::setGenre,
            onSetYearRange = viewModel::setYearRange,
            onDismiss = { filtersSheetOpen = false }
        )
    }
}

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
    continueWatching: List<MediaItemEntity>,
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSetQuery: (String) -> Unit,
    onPlayItem: (String) -> Unit,
    onDismissContinueWatching: (String) -> Unit,
    onRescan: () -> Unit,
    onDismissProgress: () -> Unit,
    availableUpdate: ReleaseInfo?,
    onDismissUpdate: (String) -> Unit
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

    Column(Modifier.fillMaxSize()) {
        availableUpdate?.let { release ->
            UpdateAvailableBanner(
                release = release,
                onDismiss = { onDismissUpdate(release.version) }
            )
        }
        if (continueWatching.isNotEmpty()) {
            ContinueWatchingRow(items = continueWatching, onClick = onPlayItem, onDismiss = onDismissContinueWatching)
        }

        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f)) {
                when {
                    !configured -> EmptyState(
                        title = "Подключение не настроено",
                        subtitle = "Добавьте источник SMB-шары в настройках, чтобы начать сканирование библиотеки.",
                        actionLabel = "Открыть настройки",
                        onAction = onOpenSettings
                    )
                    items.isEmpty() && progress == null && query.isNotBlank() -> EmptyState(
                        title = "Ничего не найдено",
                        subtitle = "По запросу «$query» ничего не нашлось. Попробуйте изменить фильтр или поисковый запрос.",
                        actionLabel = "Очистить поиск",
                        onAction = { onSetQuery("") }
                    )
                    items.isEmpty() && progress == null -> EmptyState(
                        title = if (filter == LibraryFilter.ALL) "Библиотека пуста" else "Ничего не найдено в этой категории",
                        subtitle = "Нажмите на значок обновления вверху, чтобы просканировать диск и найти фильмы, сериалы и мультфильмы по .nfo файлам.",
                        actionLabel = "Сканировать сейчас",
                        onAction = onRescan
                    )
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(16.dp),
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
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // The scan banner floats at the same bottom edge and would otherwise sit
                // right under these buttons - push them up above it while it's visible.
                ScrollJumpButtons(
                    gridState = gridState,
                    itemCount = items.size,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = if (progress != null) 88.dp else 16.dp)
                )
            }

            if (showAlphabetBar) {
                AlphabetIndexBar(
                    letters = letterIndex.keys.toList(),
                    onLetterSelected = { letter ->
                        letterIndex[letter]?.let { index -> scope.launch { gridState.scrollToItem(index) } }
                    }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    items: List<MediaItemEntity>,
    onClick: (String) -> Unit,
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
                    onClick = { onClick(item.id) },
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
                progress = progressFraction,
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
    onSelectCollection: (String) -> Unit
) {
    when {
        !configured -> EmptyState(
            title = "Подключение не настроено",
            subtitle = "Добавьте источник SMB-шары в настройках, чтобы начать сканирование библиотеки.",
            actionLabel = "Открыть настройки",
            onAction = onOpenSettings
        )
        collections.isEmpty() -> EmptyState(
            title = "Коллекций не найдено",
            subtitle = "Коллекции определяются по тегу <set> в .nfo файлах (например, наборы фильмов из Kodi/Radarr).",
            actionLabel = "Открыть настройки",
            onAction = onOpenSettings
        )
        else -> Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(16.dp),
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
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

/** Small floating buttons to jump straight to the top or bottom of a long grid. */
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
    val isGlass = LocalIsGlassTheme.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canScrollUp) {
            SmallFloatingActionButton(
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                containerColor = if (isGlass) glassContainerColor else FloatingActionButtonDefaults.containerColor
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "В начало списка")
            }
        }
        if (canScrollDown) {
            SmallFloatingActionButton(
                onClick = { scope.launch { gridState.animateScrollToItem((itemCount - 1).coerceAtLeast(0)) } },
                containerColor = if (isGlass) glassContainerColor else FloatingActionButtonDefaults.containerColor
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "В конец списка")
            }
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
    onLetterSelected: (String) -> Unit
) {
    var heightPx by remember { mutableStateOf(0f) }
    var activeLetter by remember { mutableStateOf<String?>(null) }

    fun selectAt(y: Float) {
        if (heightPx <= 0f || letters.isEmpty()) return
        val index = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter != activeLetter) {
            activeLetter = letter
            onLetterSelected(letter)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
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
                    activeLetter = null
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { letter ->
            Text(
                letter,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                color = if (letter == activeLetter) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = glassContainerColor) {
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
                    FilterChip(
                        selected = filter == option.filter,
                        onClick = { onSetFilter(option.filter) },
                        label = { Text(option.label) }
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
    actionLabel: String,
    onAction: () -> Unit
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
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun UpdateAvailableBanner(
    release: ReleaseInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
            }) { Text("Скачать") }
            TextButton(onClick = onDismiss) { Text("Закрыть") }
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
                    Text("Обход папок на диске… найдено файлов: ${progress.filesFound}")
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
