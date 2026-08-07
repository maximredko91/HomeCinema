package com.homecinema.library.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.ui.components.MediaPosterCard
import com.homecinema.library.ui.theme.ProvideGlassHazeState
import com.homecinema.library.ui.theme.glassBackdrop
import com.homecinema.library.ui.theme.floatingChrome
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors

/** Poster grid, same look as the main library - used to be a plain list (title text + play/
 * delete icons). Tapping a card opens the same DetailScreen a library card would, which already
 * has everything a downloaded title needs (play internally/externally, delete the download,
 * subtitle availability) - no reason to duplicate a second, smaller set of actions here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val app = HomeCinemaApp.instance
    val downloaded by app.repository.observeDownloaded().collectAsState(initial = emptyList())

    ProvideGlassHazeState {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Загрузки") },
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
        if (downloaded.isEmpty()) {
            Box(
                Modifier.fillMaxSize().glassBackdrop().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Пока ничего не скачано.\nСкачанные фильмы, мультфильмы и серии можно смотреть без сети.",
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
                MediaPosterCard(item = item, onClick = { onOpenDetail(item.id) })
            }
        }
    }
    }
}
