package com.homecinema.library.ui.screens

import android.content.ClipData
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.CustomListEntity
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType
import com.homecinema.library.data.media.DownloadStorage
import com.homecinema.library.data.media.extractReleaseQuality
import com.homecinema.library.data.media.findLocalSiblingSubtitle
import com.homecinema.library.data.media.isContentUri
import com.homecinema.library.data.settings.PlaybackMode
import com.homecinema.library.data.smb.toSmbConfig
import com.homecinema.library.data.streaming.StreamingService
import com.homecinema.library.data.streaming.mimeTypeForExtension
import com.homecinema.library.ui.components.DetailActionButtonHeight
import com.homecinema.library.ui.components.DownloadControlRow
import com.homecinema.library.ui.components.MediaPosterCard
import com.homecinema.library.ui.components.ZoomableImageDialog
import com.homecinema.library.ui.theme.ProvideGlassHazeState
import com.homecinema.library.ui.theme.glassBackdrop
import com.homecinema.library.ui.theme.glassEffect
import com.homecinema.library.ui.theme.collapsingChrome
import com.homecinema.library.ui.theme.glassSheetContainerColor
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    var actorSheetName by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    ProvideGlassHazeState {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(item?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Добавить в список")
                        }
                    }
                },
                colors = homeCinemaTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                modifier = Modifier.collapsingChrome(scrollBehavior)
            )
        }
    ) { padding ->
        val current = item ?: return@Scaffold
        val isShow = current.mediaType == MediaType.TV_SHOW || current.mediaType == MediaType.CARTOON_SERIES

        // Same sidecar-subtitle lookup playExternally() already does (local file listing, or a
        // best-effort/timeout-guarded SMB folder listing for a not-yet-downloaded title) - run
        // once per item just to know whether to show the "Субтитры" badge below, not to
        // actually attach anything.
        var hasSubtitles by remember(current.id) { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(current.id) {
            val localPath = current.localFilePath
            hasSubtitles = if (localPath != null && isContentUri(localPath)) {
                val customTreeUri = app.settingsStore.downloadFolderUriFlow.first().takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                DownloadStorage.findDownloadedSubtitle(context, current, Uri.parse(localPath), customTreeUri) != null
            } else if (localPath != null && File(localPath).exists()) {
                findLocalSiblingSubtitle(File(localPath)) != null
            } else {
                val source = app.repository.getSource(current.sourceId)
                source != null && withTimeoutOrNull(3000) {
                    runCatching { app.smbManager.findSiblingSubtitle(source.id, source.toSmbConfig(), current.videoFilePath) }
                        .getOrNull()
                } != null
            }
        }

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
                    if (!current.tagline.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            current.tagline,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val releaseQuality = remember(current.videoFilePath) {
                        extractReleaseQuality(current.videoFilePath.substringAfterLast('/'))
                    }
                    // Each chip gets an icon, not just a bare number/word - "2021" or "110 мин"
                    // reads fine once you know it's a year or a runtime, but nothing on the
                    // chip itself said which was which.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        current.year?.let { MetaChip(it.toString(), icon = Icons.Default.CalendarMonth) }
                        current.rating?.let { MetaChip("%.1f".format(it), icon = Icons.Default.Star) }
                        current.runtimeMinutes?.let { MetaChip("$it мин", icon = Icons.Default.Schedule) }
                        current.country?.takeIf { it.isNotBlank() }?.let { MetaChip(it, icon = Icons.Default.Public) }
                        current.mpaa?.takeIf { it.isNotBlank() }?.let { MetaChip(it, icon = Icons.Default.Shield) }
                        releaseQuality?.let { MetaChip(it, icon = Icons.Default.HighQuality) }
                        if (hasSubtitles == true) MetaChip("Субтитры", icon = Icons.Default.ClosedCaption)
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
                Button(onClick = { onOpenShow(current.id) }, modifier = Modifier.fillMaxWidth().height(DetailActionButtonHeight)) {
                    Text("Список серий")
                }
            } else {
                // All three actions share one row, equal width - previously "Внешний плеер"
                // only got whatever space "Смотреть" left over and wrapped to two lines, and
                // "Скачать" lived in a separate row with its own sizing, so the three read as
                // mismatched instead of one family of actions.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playbackMode != PlaybackMode.EXTERNAL) {
                        Button(
                            onClick = { onPlayInternally(current.id) },
                            modifier = Modifier.weight(1f).height(DetailActionButtonHeight)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Смотреть")
                        }
                    } else {
                        // Forced external is the only option here, so it doesn't need its own
                        // "во внешнем плеере" label to distinguish it from anything - that
                        // wording only earns its keep in ASK mode, next to a real second
                        // button offering the other choice.
                        Button(
                            onClick = { scope.launch { playExternally(context, current) } },
                            modifier = Modifier.weight(1f).height(DetailActionButtonHeight)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Смотреть")
                        }
                    }
                    if (playbackMode == PlaybackMode.ASK) {
                        // Tonal, not outlined - reads as "same family, secondary emphasis"
                        // rather than a differently-weighted control next to a filled button.
                        FilledTonalButton(
                            onClick = { scope.launch { playExternally(context, current) } },
                            modifier = Modifier.weight(1f).height(DetailActionButtonHeight),
                            colors = ButtonDefaults.filledTonalButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Внешний", maxLines = 1)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // Its own row rather than a third equal-width slot above - the downloading/
                // completed states carry real text (progress %, a cancel/delete action) that a
                // narrow third-of-the-screen column would just crowd. Matched to the same pill
                // height/shape as the row above instead, so it still reads as the same family
                // of controls rather than a visually unrelated one.
                DownloadControlRow(
                    item = current,
                    liveProgress = liveProgressMap[current.id],
                    onDownload = { app.downloadManager.start(current) },
                    onCancel = { app.downloadManager.cancel(current.id) },
                    onDelete = { scope.launch { app.downloadManager.deleteDownload(current) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!current.studio.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("Студия", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(current.studio, style = MaterialTheme.typography.bodyMedium)
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
                val clickableDirectors = remember(directors, libraryItems, current.id) {
                    namesWithOtherCredits(directors, libraryItems, current.id) { it.director }
                }
                FlowRow {
                    directors.forEachIndexed { index, name ->
                        val hasFilmography = name in clickableDirectors
                        Text(
                            text = if (index < directors.lastIndex) "$name, " else name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasFilmography) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            modifier = if (hasFilmography) Modifier.clickable { directorSheetName = name } else Modifier
                        )
                    }
                }
            }

            if (!current.actors.isNullOrBlank()) {
                val actorsText = current.actors
                Spacer(Modifier.height(16.dp))
                Text("В ролях", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                val actors = remember(actorsText) {
                    actorsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                }
                val clickableActors = remember(actors, libraryItems, current.id) {
                    namesWithOtherCredits(actors, libraryItems, current.id) { it.actors }
                }
                FlowRow {
                    actors.forEachIndexed { index, name ->
                        val hasFilmography = name in clickableActors
                        Text(
                            text = if (index < actors.lastIndex) "$name, " else name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasFilmography) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            modifier = if (hasFilmography) Modifier.clickable { actorSheetName = name } else Modifier
                        )
                    }
                }
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
        PersonFilmographySheet(
            title = "Фильмы режиссёра «$director»",
            emptyMessage = "Другие фильмы этого режиссёра в библиотеке не найдены.",
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

    actorSheetName?.let { actor ->
        PersonFilmographySheet(
            title = "Фильмы с «$actor»",
            emptyMessage = "Другие фильмы с этим актёром в библиотеке не найдены.",
            items = libraryItems.filter { other ->
                other.id != itemId &&
                    other.actors.orEmpty().split(",").map { it.trim() }
                        .any { it.equals(actor, ignoreCase = true) }
            },
            onOpenDetail = { id ->
                actorSheetName = null
                onOpenDetail(id)
            },
            onDismiss = { actorSheetName = null }
        )
    }
    }
}

/** One small pill for a single fact - year, rating, runtime, country, age rating, release
 * quality, subtitle availability. Replaces one long "2021  •  ★ 7.2  •  110 мин  •  ..." line,
 * which read as a single undifferentiated wall of text and always fully truncated on a narrow
 * screen instead of wrapping - a FlowRow of chips wraps naturally and lets each fact stay
 * legible on its own. */
@Composable
private fun MetaChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shared by the director and actor "click a name -> see their other films" sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonFilmographySheet(
    title: String,
    emptyMessage: String,
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
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                Text(
                    emptyMessage,
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

/** Which of [names] (director or actor names, already split from a comma-separated field) have
 * at least one OTHER item in [libraryItems] crediting them via [creditsOf] - used so a name with
 * nothing else to show isn't highlighted/clickable as if tapping it would lead somewhere. */
private fun namesWithOtherCredits(
    names: List<String>,
    libraryItems: List<MediaItemEntity>,
    currentId: String,
    creditsOf: (MediaItemEntity) -> String?
): Set<String> = names.filter { name ->
    libraryItems.any { other ->
        other.id != currentId &&
            creditsOf(other).orEmpty().split(",").map { it.trim() }.any { it.equals(name, ignoreCase = true) }
    }
}.toSet()

/**
 * Hands the video off to any installed external player. Prefers the locally downloaded copy
 * (via FileProvider) when available. Otherwise, since only VLC understands a raw smb:// URI
 * passed via ACTION_VIEW, bridges the SMB stream through a local loopback HTTP server
 * ([StreamingService]) so any external player can read it.
 *
 * If there's a meaningful saved position (same [shouldResume] check the internal player uses),
 * passes it via the "position" intent extra (milliseconds) - a de facto convention popularized
 * by MX Player and honored by several other players, though not a formal Android standard.
 * One-way only: there's no reliable way to get playback position back from an external player
 * on exit (no shared result contract), so this can seek to where you left off but can't update
 * that position based on what happens in the external player afterward.
 */
suspend fun playExternally(context: Context, item: MediaItemEntity) {
    val intent = Intent(Intent.ACTION_VIEW)
    val localPath = item.localFilePath
    var subtitleUri: Uri? = null

    if (localPath != null && isContentUri(localPath)) {
        // Already a shareable content:// URI (MediaStore, or a user-picked folder) - no
        // FileProvider wrapping needed, unlike the plain-file case below.
        val videoUri = Uri.parse(localPath)
        intent.setDataAndType(videoUri, "video/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val customTreeUri = HomeCinemaApp.instance.settingsStore.downloadFolderUriFlow.first().takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        subtitleUri = DownloadStorage.findDownloadedSubtitle(context, item, videoUri, customTreeUri)?.first
    } else if (localPath != null && File(localPath).exists()) {
        val videoFile = File(localPath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
        intent.setDataAndType(uri, "video/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Only present if DownloadWorker found and saved one alongside the video.
        subtitleUri = findLocalSiblingSubtitle(videoFile)?.let {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        }
    } else {
        val streamUrl = StreamingService.streamUrl(context, item.id)
        val mimeType = mimeTypeForExtension(item.videoFilePath.substringAfterLast('.', ""))
        intent.setDataAndType(Uri.parse(streamUrl), mimeType)

        val app = HomeCinemaApp.instance
        val source = app.repository.getSource(item.sourceId)
        // Best-effort and defensive on purpose - see the matching comment in PlayerScreen.kt.
        // A failed/slow lookup here must never be able to stop external playback from
        // starting at all.
        val hasSubtitle = source != null && withTimeoutOrNull(3000) {
            runCatching { app.smbManager.findSiblingSubtitle(source.id, source.toSmbConfig(), item.videoFilePath) }
                .getOrNull()
        } != null
        if (hasSubtitle) {
            // A plain http:// URL over the loopback proxy - no permission grant needed,
            // unlike the content:// case below.
            subtitleUri = Uri.parse(StreamingService.subtitleUrl(item.id))
        }
    }

    // Best-effort: different external players expect the sidecar subtitle via different,
    // non-standardized intent extras, so several conventions are set at once and whichever
    // the installed player understands takes effect ("subs"/"subs.name" - MX Player and a few
    // others copying it; "subtitles_location" - VLC). A content:// subtitle URI (the local-
    // download case) needs an explicit ClipData grant since FLAG_GRANT_READ_URI_PERMISSION on
    // its own only covers the Intent's main data URI, not URIs passed as extras.
    subtitleUri?.let { uri ->
        intent.putExtra("subs", arrayOf(uri))
        intent.putExtra("subs.name", arrayOf(item.title))
        intent.putExtra("subtitles_location", uri.toString())
        if (uri.scheme == "content") {
            intent.clipData = ClipData.newUri(context.contentResolver, "subtitle", uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    if (shouldResume(item.playbackPositionMs, item.durationMs)) {
        intent.putExtra("position", item.playbackPositionMs.toInt())
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // External players are a separate, black-box app - they never report position/progress
    // back to us, so "Продолжить просмотр"/История had no way to know this title was ever
    // opened at all. Best available proxy: record it as played now, keeping any already-known
    // position/duration (e.g. from a previous internal-playback session) rather than
    // clobbering it, and only inventing a minimal "just started" position - using .nfo runtime
    // as a stand-in duration - for a title with no progress on record yet.
    val knownDuration = item.durationMs.takeIf { it > 0 }
        ?: item.runtimeMinutes?.let { it.toLong() * 60_000L }?.takeIf { it > 0 }
    if (knownDuration != null) {
        val position = item.playbackPositionMs.takeIf { it > 0 } ?: 6_000L
        HomeCinemaApp.instance.repository.updatePlaybackProgress(item.id, position, knownDuration)
    }

    val preferredPackage = HomeCinemaApp.instance.settingsStore.preferredExternalPlayerPackageFlow.first()
    if (preferredPackage.isNotBlank()) {
        intent.setPackage(preferredPackage)
        // Fall back to the system chooser if the preferred app was uninstalled since it was
        // picked, rather than silently doing nothing.
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) {
            intent.setPackage(null)
            runCatching { context.startActivity(intent) }
        }
    } else {
        runCatching { context.startActivity(intent) }
    }
}
