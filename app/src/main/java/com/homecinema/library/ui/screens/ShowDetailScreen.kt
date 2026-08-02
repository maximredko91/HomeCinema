package com.homecinema.library.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.ui.components.DownloadControlRow
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

    val bySeason = episodes.groupBy { it.season ?: 1 }.toSortedMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(show?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
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
                        onPlayExternally = { playExternally(context, episode) },
                        onDownload = { app.downloadManager.start(episode) },
                        onCancelDownload = { app.downloadManager.cancel(episode.id) },
                        onDeleteDownload = { scope.launch { app.downloadManager.deleteDownload(episode) } }
                    )
                    HorizontalDivider()
                }
            }
        }
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Серия ${episode.episodeNumber ?: "?"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(episode.title, style = MaterialTheme.typography.titleMedium)
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
