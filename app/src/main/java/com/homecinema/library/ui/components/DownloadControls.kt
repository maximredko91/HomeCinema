package com.homecinema.library.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homecinema.library.data.db.DownloadState
import com.homecinema.library.data.db.MediaItemEntity

/** Shared height for the primary action buttons on the detail screen (Смотреть/Внешний/
 * Скачать) - kept as one constant so DetailScreen.kt and this file can't silently drift apart
 * and start looking like two different button families again. */
val DetailActionButtonHeight = 48.dp

/** Same pill outline as an [OutlinedButton], for the downloading/completed states below - they
 * need a real clickable action (cancel/delete) nested inside, which a disabled-looking Button
 * used purely for its shape would make ambiguous for accessibility. */
@Composable
private fun PillOutline(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = modifier.height(DetailActionButtonHeight),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun DownloadControlRow(
    item: MediaItemEntity,
    liveProgress: Int?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        liveProgress != null -> {
            PillOutline(modifier) {
                CircularProgressIndicator(
                    progress = { liveProgress / 100f },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Загрузка… $liveProgress%")
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCancel, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Отмена")
                }
            }
        }
        item.downloadState == DownloadState.COMPLETED -> {
            PillOutline(modifier) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                // Full text, no truncation - the delete action lost its text label instead (an
                // icon-only button below) to free up the room, rather than trading the two off
                // against each other. Ellipsis on this text left it unclear what it even said.
                Text("Скачано, доступно офлайн")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить загрузку", modifier = Modifier.size(18.dp))
                }
            }
        }
        else -> {
            OutlinedButton(onClick = onDownload, modifier = modifier.height(DetailActionButtonHeight)) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (item.downloadState == DownloadState.FAILED) "Повторить загрузку" else "Скачать")
            }
        }
    }
}
