package com.homecinema.library.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.ui.components.DownloadControlRow
import com.homecinema.library.ui.components.ZoomableImageDialog
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailScreen(
    showId: String,
    onBack: () -> Unit,
    onPlayEpisode: (String) -> Unit
) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val show by app.repository.observeById(showId).collectAsState(initial = null)
    val episodes by app.repository.observeEpisodesForShow(showId).collectAsState(initial = emptyList())
    val liveProgressMap by app.downloadManager.liveProgress.collectAsState()
    var zoomedImage by remember { mutableStateOf<String?>(null) }

    val bySeason = episodes.groupBy { it.season ?: 1 }.toSortedMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(show?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = homeCinemaTopAppBarColors()
            )
        }
    ) { padding ->
        if (episodes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Серии не найдены — попробуйте пересканировать библиотеку")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            show?.let { current ->
                item { ShowHeader(current, onImageClick = { path -> zoomedImage = path }) }
            }
            bySeason.forEach { (season, seasonEpisodes) ->
                item {
                    Text(
                        "Сезон $season",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                items(seasonEpisodes.sortedBy { it.episodeNumber ?: 0 }, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        liveProgress = liveProgressMap[episode.id],
                        onPlay = { onPlayEpisode(episode.id) },
                        onPlayExternally = { scope.launch { playExternally(context, episode) } },
                        onDownload = { app.downloadManager.start(episode) },
                        onCancelDownload = { app.downloadManager.cancel(episode.id) },
                        onDeleteDownload = { scope.launch { app.downloadManager.deleteDownload(episode) } }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    zoomedImage?.let { path ->
        ZoomableImageDialog(imagePath = path, onDismiss = { zoomedImage = null })
    }
}

@Composable
private fun ShowHeader(show: MediaItemEntity, onImageClick: (String) -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        if (show.fanartLocalPath != null) {
            AsyncImage(
                model = show.fanartLocalPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onImageClick(show.fanartLocalPath) }
            )
            Spacer(Modifier.height(16.dp))
        }

        Row {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .let { if (show.posterLocalPath != null) it.clickable { onImageClick(show.posterLocalPath) } else it }
            ) {
                if (show.posterLocalPath != null) {
                    AsyncImage(
                        model = show.posterLocalPath,
                        contentDescription = show.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(show.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                val meta = buildList {
                    show.year?.let { add(it.toString()) }
                    show.rating?.let { add("★ ${"%.1f".format(it)}") }
                    show.runtimeMinutes?.let { add("$it мин/серия") }
                    show.country?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString("  •  ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodyMedium)
                }
                if (show.genres.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        show.genres,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (show.plot.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("Описание", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(show.plot, style = MaterialTheme.typography.bodyMedium)
        }

        if (!show.director.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("Режиссёр", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(show.director, style = MaterialTheme.typography.bodyMedium)
        }

        if (!show.actors.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("В ролях", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(show.actors, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
    }
}

@Composable
private fun EpisodeRow(
    episode: MediaItemEntity,
    liveProgress: Int?,
    onPlay: () -> Unit,
    onPlayExternally: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit
) {
    val progressFraction = if (episode.durationMs > 0) {
        (episode.playbackPositionMs.toFloat() / episode.durationMs).coerceIn(0f, 1f)
    } else 0f

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (episode.posterLocalPath != null) {
                AsyncImage(
                    model = episode.posterLocalPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (progressFraction > 0.02f) {
                LinearProgressIndicator(
                    progress = progressFraction,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Серия ${episode.episodeNumber ?: "?"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(episode.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                if (progressFraction >= 0.95f) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "Просмотрено",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (episode.plot.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    episode.plot,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Смотреть")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onPlayExternally) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Внешний плеер")
                }
            }
            Spacer(Modifier.height(4.dp))
            DownloadControlRow(
                item = episode,
                liveProgress = liveProgress,
                onDownload = onDownload,
                onCancel = onCancelDownload,
                onDelete = onDeleteDownload
            )
        }
    }
}
