package com.homecinema.library.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.CustomListEntity
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType
import com.homecinema.library.data.settings.PlaybackMode
import com.homecinema.library.data.streaming.StreamingService
import com.homecinema.library.data.streaming.mimeTypeForExtension
import com.homecinema.library.ui.components.DownloadControlRow
import com.homecinema.library.ui.components.MediaPosterCard
import com.homecinema.library.ui.components.ZoomableImageDialog
import com.homecinema.library.ui.theme.ProvideGlassHazeState
import com.homecinema.library.ui.theme.glassBackdrop
import com.homecinema.library.ui.theme.glassEffect
import com.homecinema.library.ui.theme.glassSheetContainerColor
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onPlayInternally: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val item by app.repository.observeById(itemId).collectAsState(initial = null)
    val libraryItems by app.repository.observeLibrary().collectAsState(initial = emptyList())
    val liveProgressMap by app.downloadManager.liveProgress.collectAsState()
    val playbackMode by app.settingsStore.playbackModeFlow.collectAsState(initial = PlaybackMode.ASK)
    val customLists by app.repository.observeLists().collectAsState(initial = emptyList())
    val listIdsForItem by app.repository.observeListIdsForItem(itemId).collectAsState(initial = emptyList())
    var zoomedImage by remember { mutableStateOf<String?>(null) }
    var addToListSheetOpen by remember { mutableStateOf(false) }
    var directorSheetName by remember { mutableStateOf<String?>(null) }

    ProvideGlassHazeState {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    item?.let { current ->
                        IconButton(onClick = { scope.launch { app.repository.setFavorite(current.id, !current.isFavorite) } }) {
                            Icon(
                                imageVector = if (current.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (current.isFavorite) "Убрать из избранного" else "В избранное"
                            )
                        }
                        IconButton(onClick = { addToListSheetOpen = true }) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "Добавить в список")
                        }
                    }
                },
                colors = homeCinemaTopAppBarColors(),
                modifier = Modifier.glassEffect()
            )
        }
    ) { padding ->
        val current = item ?: return@Scaffold
        val isShow = current.mediaType == MediaType.TV_SHOW || current.mediaType == MediaType.CARTOON_SERIES

        Column(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
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
                    if (!current.originalTitle.isNullOrBlank()) {
                        Text(
                            "(${current.originalTitle})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
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
                        Button(onClick = { scope.launch { playExternally(context, current) } }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Смотреть во внешнем плеере")
                        }
                    }
                    if (playbackMode == PlaybackMode.ASK) {
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { scope.launch { playExternally(context, current) } }) {
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
                val directorText = current.director
                Spacer(Modifier.height(16.dp))
                Text("Режиссёр", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                val directors = remember(directorText) {
                    directorText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                }
                FlowRow {
                    directors.forEachIndexed { index, name ->
                        Text(
                            text = if (index < directors.lastIndex) "$name, " else name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { directorSheetName = name }
                        )
                    }
                }
            }

            if (!current.actors.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("В ролях", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(current.actors, style = MaterialTheme.typography.bodyMedium)
            }

            val collectionName = current.collectionName
            if (!collectionName.isNullOrBlank()) {
                val collectionItems = remember(libraryItems, collectionName, current.id) {
                    libraryItems.filter { it.collectionName == collectionName && it.id != current.id }
                }
                if (collectionItems.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text("Другие фильмы из коллекции «$collectionName»", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(collectionItems, key = { it.id }) { collectionItem ->
                            MediaPosterCard(
                                item = collectionItem,
                                onClick = { onOpenDetail(collectionItem.id) },
                                modifier = Modifier.width(110.dp)
                            )
                        }
                    }
                }
            }
            }
        }
    }

    zoomedImage?.let { path ->
        ZoomableImageDialog(imagePath = path, onDismiss = { zoomedImage = null })
    }

    if (addToListSheetOpen) {
        AddToListSheet(
            customLists = customLists,
            selectedListIds = listIdsForItem.toSet(),
            onToggle = { listId, checked ->
                scope.launch {
                    if (checked) app.repository.addItemToList(listId, itemId) else app.repository.removeItemFromList(listId, itemId)
                }
            },
            onCreateAndAdd = { name ->
                scope.launch {
                    val newListId = app.repository.createList(name)
                    app.repository.addItemToList(newListId, itemId)
                }
            },
            onDismiss = { addToListSheetOpen = false }
        )
    }

    directorSheetName?.let { director ->
        DirectorFilmographySheet(
            director = director,
            items = libraryItems.filter { other ->
                other.id != itemId &&
                    other.director.orEmpty().split(",").map { it.trim() }
                        .any { it.equals(director, ignoreCase = true) }
            },
            onOpenDetail = { id ->
                directorSheetName = null
                onOpenDetail(id)
            },
            onDismiss = { directorSheetName = null }
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectorFilmographySheet(
    director: String,
    items: List<MediaItemEntity>,
    onOpenDetail: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassSheetContainerColor
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Фильмы режиссёра «$director»", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                Text(
                    "Другие фильмы этого режиссёра в библиотеке не найдены.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.heightIn(max = 480.dp)
                ) {
                    gridItems(items, key = { it.id }) { filmItem ->
                        MediaPosterCard(item = filmItem, onClick = { onOpenDetail(filmItem.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToListSheet(
    customLists: List<CustomListEntity>,
    selectedListIds: Set<String>,
    onToggle: (listId: String, checked: Boolean) -> Unit,
    onCreateAndAdd: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var newListName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassSheetContainerColor
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Добавить в список", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            if (customLists.isEmpty()) {
                Text(
                    "Пока нет ни одного списка - создайте его ниже.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
            } else {
                customLists.forEach { list ->
                    val checked = list.id in selectedListIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(list.id, !checked) }
                            .padding(vertical = 6.dp)
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(list.id, it) })
                        Spacer(Modifier.width(8.dp))
                        Text(list.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    singleLine = true,
                    label = { Text("Новый список") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = newListName.isNotBlank(),
                    onClick = {
                        onCreateAndAdd(newListName.trim())
                        newListName = ""
                    }
                ) { Text("Создать и добавить") }
            }
        }
    }
}

/**
 * Hands the video off to any installed external player. Prefers the locally downloaded copy
 * (via FileProvider) when available. Otherwise, since only VLC understands a raw smb:// URI
 * passed via ACTION_VIEW, bridges the SMB stream through a local loopback HTTP server
 * ([StreamingService]) so any external player can read it.
 */
suspend fun playExternally(context: Context, item: MediaItemEntity) {
    val intent = Intent(Intent.ACTION_VIEW)
    val localPath = item.localFilePath
    if (localPath != null && File(localPath).exists()) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(localPath))
        intent.setDataAndType(uri, "video/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        val streamUrl = StreamingService.streamUrl(context, item.id)
        val mimeType = mimeTypeForExtension(item.videoFilePath.substringAfterLast('.', ""))
        intent.setDataAndType(Uri.parse(streamUrl), mimeType)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
