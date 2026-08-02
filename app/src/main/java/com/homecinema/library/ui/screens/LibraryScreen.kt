package com.homecinema.library.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.homecinema.library.data.scanner.ScanProgress
import com.homecinema.library.ui.components.MediaPosterCard
import com.homecinema.library.ui.viewmodel.CollectionSummary
import com.homecinema.library.ui.viewmodel.LibraryFilter
import com.homecinema.library.ui.viewmodel.LibraryTab
import com.homecinema.library.ui.viewmodel.LibraryViewModel
import com.homecinema.library.ui.viewmodel.SortOrder
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
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

    var sortMenuOpen by remember { mutableStateOf(false) }

    // Without this, the system back gesture/button falls straight through to the
    // Activity (LibraryScreen is the nav graph's start destination, so there's no
    // back-stack entry to pop) and exits the whole app instead of leaving the collection.
    BackHandler(enabled = selectedCollection != null) { viewModel.clearCollectionSelection() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (libraryTab == LibraryTab.COLLECTIONS && selectedCollection != null) selectedCollection!!
                        else "Моя коллекция"
                    )
                },
                navigationIcon = {
                    if (libraryTab == LibraryTab.COLLECTIONS && selectedCollection != null) {
                        IconButton(onClick = { viewModel.clearCollectionSelection() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "К списку коллекций")
                        }
                    }
                },
                actions = {
                    if (libraryTab == LibraryTab.ALL || selectedCollection != null) {
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Сортировка")
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                SortOrder.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            viewModel.setSortOrder(option)
                                            sortMenuOpen = false
                                        },
                                        leadingIcon = {
                                            RadioButton(selected = option == sortOrder, onClick = null)
                                        }
                                    )
                                }
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
                    inCollection = selectedCollection != null,
                    selectedGenre = selectedGenre,
                    availableGenres = availableGenres,
                    yearRange = yearRange,
                    availableYearBounds = availableYearBounds,
                    onOpenDetail = onOpenDetail,
                    onOpenSettings = onOpenSettings,
                    onSetFilter = viewModel::setFilter,
                    onSetQuery = viewModel::setQuery,
                    onSetGenre = viewModel::setGenre,
                    onSetYearRange = viewModel::setYearRange,
                    onRescan = viewModel::rescan,
                    onDismissProgress = viewModel::dismissProgress
                )
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    configured: Boolean,
    items: List<com.homecinema.library.data.db.MediaItemEntity>,
    progress: ScanProgress?,
    filter: LibraryFilter,
    query: String,
    sortOrder: SortOrder,
    alphabetIndexEnabled: Boolean,
    inCollection: Boolean,
    selectedGenre: String?,
    availableGenres: List<String>,
    yearRange: IntRange?,
    availableYearBounds: IntRange?,
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSetFilter: (LibraryFilter) -> Unit,
    onSetQuery: (String) -> Unit,
    onSetGenre: (String?) -> Unit,
    onSetYearRange: (IntRange?) -> Unit,
    onRescan: () -> Unit,
    onDismissProgress: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    if (configured) {
        SearchField(query = query, onQueryChange = onSetQuery)
    }
    if (!inCollection) {
        FilterRow(current = filter, onSelect = onSetFilter)
        if (availableGenres.isNotEmpty() || availableYearBounds != null) {
            AdvancedFilterRow(
                selectedGenre = selectedGenre,
                availableGenres = availableGenres,
                yearRange = yearRange,
                availableYearBounds = availableYearBounds,
                onSetGenre = onSetGenre,
                onSetYearRange = onSetYearRange
            )
        }
    }

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

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                !configured -> EmptyState(
                    title = "Подключение не настроено",
                    subtitle = "Укажите адрес SMB-шары на роутере в настройках, чтобы начать сканирование библиотеки.",
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
                    columns = GridCells.Adaptive(minSize = 130.dp),
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

@Composable
private fun CollectionsGrid(
    configured: Boolean,
    collections: List<CollectionSummary>,
    onOpenSettings: () -> Unit,
    onSelectCollection: (String) -> Unit
) {
    when {
        !configured -> EmptyState(
            title = "Подключение не настроено",
            subtitle = "Укажите адрес SMB-шары на роутере в настройках, чтобы начать сканирование библиотеки.",
            actionLabel = "Открыть настройки",
            onAction = onOpenSettings
        )
        collections.isEmpty() -> EmptyState(
            title = "Коллекций не найдено",
            subtitle = "Коллекции определяются по тегу <set> в .nfo файлах (например, наборы фильмов из Kodi/Radarr).",
            actionLabel = "Открыть настройки",
            onAction = onOpenSettings
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(collections, key = { it.name }) { collection ->
                CollectionCard(collection = collection, onClick = { onSelectCollection(collection.name) })
            }
        }
    }
}

@Composable
private fun CollectionCard(collection: CollectionSummary, onClick: () -> Unit) {
    Column(modifier = Modifier.width(140.dp).clickable(onClick = onClick)) {
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

private fun filmsWord(count: Int): String {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Поиск по названию, актёрам, описанию") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                }
            }
        }
    )
}

@Composable
private fun FilterRow(current: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(FILTER_OPTIONS) { option ->
            FilterChip(
                selected = current == option.filter,
                onClick = { onSelect(option.filter) },
                label = { Text(option.label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFilterRow(
    selectedGenre: String?,
    availableGenres: List<String>,
    yearRange: IntRange?,
    availableYearBounds: IntRange?,
    onSetGenre: (String?) -> Unit,
    onSetYearRange: (IntRange?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (availableGenres.isNotEmpty()) {
            GenreFilterChip(selectedGenre = selectedGenre, availableGenres = availableGenres, onSelect = onSetGenre)
        }
        if (availableYearBounds != null) {
            YearFilterChip(yearRange = yearRange, bounds = availableYearBounds, onApply = onSetYearRange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreFilterChip(
    selectedGenre: String?,
    availableGenres: List<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedGenre != null,
            onClick = { expanded = true },
            label = { Text(selectedGenre ?: "Жанр") },
            trailingIcon = {
                if (selectedGenre != null) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Сбросить жанр",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onSelect(null) }
                    )
                }
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Все жанры") },
                onClick = { onSelect(null); expanded = false },
                leadingIcon = { RadioButton(selected = selectedGenre == null, onClick = null) }
            )
            availableGenres.forEach { genre ->
                DropdownMenuItem(
                    text = { Text(genre) },
                    onClick = { onSelect(genre); expanded = false },
                    leadingIcon = { RadioButton(selected = genre == selectedGenre, onClick = null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearFilterChip(
    yearRange: IntRange?,
    bounds: IntRange,
    onApply: (IntRange?) -> Unit
) {
    var dialogOpen by remember { mutableStateOf(false) }
    FilterChip(
        selected = yearRange != null,
        onClick = { dialogOpen = true },
        label = { Text(yearRange?.let { "${it.first}–${it.last}" } ?: "Год") },
        trailingIcon = {
            if (yearRange != null) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Сбросить год",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onApply(null) }
                )
            }
        }
    )
    if (dialogOpen) {
        var sliderValue by remember {
            mutableStateOf(
                (yearRange?.first ?: bounds.first).toFloat()..(yearRange?.last ?: bounds.last).toFloat()
            )
        }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("Год выпуска") },
            text = {
                Column {
                    Text(
                        "${sliderValue.start.roundToInt()} – ${sliderValue.endInclusive.roundToInt()}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    RangeSlider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = bounds.first.toFloat()..bounds.last.toFloat(),
                        steps = (bounds.last - bounds.first - 1).coerceAtLeast(0)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onApply(sliderValue.start.roundToInt()..sliderValue.endInclusive.roundToInt())
                    dialogOpen = false
                }) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onApply(null)
                    dialogOpen = false
                }) { Text("Сбросить") }
            }
        )
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
