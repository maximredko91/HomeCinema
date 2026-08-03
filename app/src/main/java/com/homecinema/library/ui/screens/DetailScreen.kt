package com.homecinema.library.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType
import com.homecinema.library.data.settings.PlaybackMode
import com.homecinema.library.ui.components.DownloadControlRow
import com.homecinema.library.ui.components.ZoomableImageDialog
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onPlayInternally: (String) -> Unit,
    onOpenShow: (String) -> Unit
) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val item by app.repository.observeById(itemId).collectAsState(initial = null)
    val liveProgressMap by app.downloadManager.liveProgress.collectAsState()
    val playbackMode by app.settingsStore.playbackModeFlow.collectAsState(initial = PlaybackMode.ASK)
    var zoomedImage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = homeCinemaTopAppBarColors()
            )
        }
    ) { padding ->
        val current = item ?: return@Scaffold
        val isShow = current.mediaType == MediaType.TV_SHOW || current.mediaType == MediaType.CARTOON_SERIES

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (current.fanartLocalPath != null) {
                AsyncImage(
                    model = current.fanartLocalPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clickable { zoomedImage = current.fanartLocalPath }
                )
            }

            Column(modifier = Modifier.padding(20.dp)) {
            Row {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .let { if (current.posterLocalPath != null) it.clickable { zoomedImage = current.posterLocalPath } else it }
                ) {
                    if (current.posterLocalPath != null) {
                        AsyncImage(
                            model = current.posterLocalPath,
                            contentDescription = current.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(current.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    val meta = buildList {
                        current.year?.let { add(it.toString()) }
                        current.rating?.let { add("★ ${"%.1f".format(it)}") }
                        current.runtimeMinutes?.let { add("$it мин") }
                        current.country?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }.joinToString("  •  ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (current.genres.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            current.genres,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isShow) {
                Button(onClick = { onOpenShow(current.id) }) {
                    Text("Список серий")
                }
            } else {
                Row {
                    if (playbackMode != PlaybackMode.EXTERNAL) {
                        Button(onClick = { onPlayInternally(current.id) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Смотреть")
                        }
                    } else {
                        Button(onClick = { playExternally(context, current) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Смотреть во внешнем плеере")
                        }
                    }
                    if (playbackMode == PlaybackMode.ASK) {
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { playExternally(context, current) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Во внешнем плеере")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                DownloadControlRow(
                    item = current,
                    liveProgress = liveProgressMap[current.id],
                    onDownload = { app.downloadManager.start(current) },
                    onCancel = { app.downloadManager.cancel(current.id) },
                    onDelete = { scope.launch { app.downloadManager.deleteDownload(current) } }
                )
            }

            if (current.plot.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text("Описание", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(current.plot, style = MaterialTheme.typography.bodyMedium)
            }

            if (!current.director.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("Режиссёр", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(current.director, style = MaterialTheme.typography.bodyMedium)
            }

            if (!current.actors.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("В ролях", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(current.actors, style = MaterialTheme.typography.bodyMedium)
            }
            }
        }
    }

    zoomedImage?.let { path ->
        ZoomableImageDialog(imagePath = path, onDismiss = { zoomedImage = null })
    }
}

/**
 * Hands the video off to any installed external player. Prefers the locally
 * downloaded copy (via FileProvider) when available, falling back to the
 * smb:// stream URL for players that support SMB directly (e.g. VLC).
 */
fun playExternally(context: Context, item: MediaItemEntity) {
    val intent = Intent(Intent.ACTION_VIEW)
    val localPath = item.localFilePath
    if (localPath != null && File(localPath).exists()) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(localPath))
        intent.setDataAndType(uri, "video/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        intent.setDataAndType(Uri.parse(item.videoFilePath), "video/*")
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
