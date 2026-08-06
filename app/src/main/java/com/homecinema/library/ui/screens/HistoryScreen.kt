package com.homecinema.library.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.ui.theme.ProvideGlassHazeState
import com.homecinema.library.ui.theme.glassBackdrop
import com.homecinema.library.ui.theme.glassEffect
import com.homecinema.library.ui.theme.floatingChrome
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything ever played, most recent first - unlike the library's "Продолжить просмотр" row
 * this includes finished titles too, since it's meant for recalling what was watched rather
 * than only resuming what's in progress. See LibraryDao.observeWatchHistory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenDetail: (String) -> Unit) {
    val app = HomeCinemaApp.instance
    val history by app.repository.observeWatchHistory().collectAsState(initial = emptyList())

    ProvideGlassHazeState {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История просмотров") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = homeCinemaTopAppBarColors(),
                modifier = Modifier.floatingChrome()
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(
                Modifier.fillMaxSize().glassBackdrop().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Здесь появится то, что вы уже смотрели.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().glassBackdrop(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                top = padding.calculateTopPadding() + 16.dp
            )
        ) {
            items(history, key = { it.id }) { item ->
                HistoryRow(item = item, onClick = { onOpenDetail(item.id) })
                HorizontalDivider()
            }
        }
    }
    }
}

private val historyDateFormat = SimpleDateFormat("d MMMM, HH:mm", Locale.Builder().setLanguage("ru").build())

@Composable
private fun HistoryRow(item: MediaItemEntity, onClick: () -> Unit) {
    val progressFraction = if (item.durationMs > 0) {
        (item.playbackPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else 0f
    val watched = progressFraction >= 0.95f

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (item.posterLocalPath != null) {
                AsyncImage(
                    model = item.posterLocalPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                if (watched) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "Просмотрено",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            val subtitle = buildList {
                item.season?.let { s -> item.episodeNumber?.let { e -> add("Сезон $s, серия $e") } }
                item.year?.let { add(it.toString()) }
            }.joinToString(" • ")
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                historyDateFormat.format(Date(item.lastPlayedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (!watched && progressFraction > 0.02f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth(0.7f).height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )
            }
        }
    }
}
