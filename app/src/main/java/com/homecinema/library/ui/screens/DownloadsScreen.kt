package com.homecinema.library.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.R
import com.homecinema.library.ui.components.MediaPosterCard
import com.homecinema.library.ui.theme.ProvideGlassHazeState
import com.homecinema.library.ui.theme.glassBackdrop
import com.homecinema.library.ui.theme.collapsingChrome
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors
import kotlinx.coroutines.launch

/** Poster grid, same look as the main library - used to be a plain list (title text + play/
 * delete icons). Tapping a card opens the same DetailScreen a library card would, which already
 * has everything a downloaded title needs (play internally/externally, delete the download,
 * subtitle availability). Long-pressing a card instead starts multi-select, for clearing out
 * several (or all) downloads at once without opening each one individually. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val app = HomeCinemaApp.instance
    val scope = rememberCoroutineScope()
    val downloaded by app.repository.observeDownloaded().collectAsState(initial = emptyList())
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()

    // Selection is by id, but the underlying list can change (a download finishes/disappears)
    // while it's active - drop anything no longer present so "N выбрано" and "delete all
    // selected" never refer to a title that's already gone.
    LaunchedEffect(downloaded) {
        val stillPresent = downloaded.map { it.id }.toSet()
        if (selectedIds.any { it !in stillPresent }) {
            selectedIds = selectedIds.filterTo(mutableSetOf()) { it in stillPresent }
        }
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    ProvideGlassHazeState {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) stringResource(R.string.downloads_selected_count, selectedIds.size) else stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (selectionMode) selectedIds = emptySet() else onBack() }) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectionMode) stringResource(R.string.downloads_cancel_selection) else stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        val allSelected = selectedIds.size == downloaded.size
                        IconButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else downloaded.map { it.id }.toSet()
                        }) {
                            Icon(
                                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) stringResource(R.string.downloads_deselect_all) else stringResource(R.string.downloads_select_all)
                            )
                        }
                        IconButton(onClick = {
                            val toDelete = downloaded.filter { it.id in selectedIds }
                            selectedIds = emptySet()
                            scope.launch { toDelete.forEach { app.downloadManager.deleteDownload(it) } }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.downloads_delete_selected))
                        }
                    }
                },
                colors = homeCinemaTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                modifier = Modifier.collapsingChrome(scrollBehavior)
            )
        }
    ) { padding ->
        if (downloaded.isEmpty()) {
            Box(
                Modifier.fillMaxSize().glassBackdrop().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.downloads_empty_message),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().glassBackdrop(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                top = padding.calculateTopPadding() + 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(downloaded, key = { it.id }) { item ->
                MediaPosterCard(
                    item = item,
                    onClick = { if (selectionMode) toggleSelection(item.id) else onOpenDetail(item.id) },
                    selectionMode = selectionMode,
                    selected = item.id in selectedIds,
                    onLongClick = { toggleSelection(item.id) }
                )
            }
        }
    }
    }
}
