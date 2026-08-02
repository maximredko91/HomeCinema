package com.homecinema.library.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homecinema.library.data.scanner.ScanProgress
import com.homecinema.library.ui.components.MediaPosterCard
import com.homecinema.library.ui.viewmodel.LibraryFilter
import com.homecinema.library.ui.viewmodel.LibraryViewModel

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Моя коллекция") },
                actions = {
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
            FilterRow(current = filter, onSelect = viewModel::setFilter)

            Box(Modifier.fillMaxSize()) {
                when {
                    !configured -> EmptyState(
                        title = "Подключение не настроено",
                        subtitle = "Укажите адрес SMB-шары на роутере в настройках, чтобы начать сканирование библиотеки.",
                        actionLabel = "Открыть настройки",
                        onAction = onOpenSettings
                    )
                    items.isEmpty() && progress == null -> EmptyState(
                        title = if (filter == LibraryFilter.ALL) "Библиотека пуста" else "Ничего не найдено в этой категории",
                        subtitle = "Нажмите на значок обновления вверху, чтобы просканировать диск и найти фильмы, сериалы и мультфильмы по .nfo файлам.",
                        actionLabel = "Сканировать сейчас",
                        onAction = { viewModel.rescan() }
                    )
                    else -> LazyVerticalGrid(
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
                        onDismiss = { viewModel.dismissProgress() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
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
