package com.homecinema.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.homecinema.library.R
import com.homecinema.library.data.db.DownloadState
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType

@Composable
fun MediaPosterCard(
    item: MediaItemEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Selection mode (Downloads screen's "select several, delete together" - see
    // DownloadsScreen) is opt-in via onLongClick being non-null, rather than a screen having to
    // route around the whole component - every other caller (the main library grids) is
    // unaffected just by not passing it.
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val isShow = item.mediaType == MediaType.TV_SHOW || item.mediaType == MediaType.CARTOON_SERIES

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (item.posterLocalPath != null) {
                AsyncImage(
                    model = item.posterLocalPath,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isShow) Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            if (item.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = stringResource(R.string.poster_favorite_cd),
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (selectionMode) {
                // Replaces the "downloaded" badge in the same corner rather than sitting next
                // to it - every card in a selectable list (Downloads) is already downloaded, so
                // that badge is redundant there anyway once selection is the thing being shown.
                if (selected) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.poster_selected_cd),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else if (item.downloadState == DownloadState.COMPLETED) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.poster_downloaded_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            val progressFraction = if (item.durationMs > 0) {
                (item.playbackPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
            } else 0f

            if (progressFraction >= 0.95f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.poster_watched_cd),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else if (progressFraction > 0.02f) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // A cheap length-based step-down rather than true auto-fit (BasicText's
        // TextAutoSize) - that ran its multi-pass measurement search fresh for every card
        // as it scrolled into view in the grid (item recomposition/reuse), which was
        // expensive enough to visibly jank/stutter the scroll itself. This is O(1) per card
        // and still keeps a long title readable at 3 columns instead of just truncating at
        // a single fixed size.
        val titleFontSize = when {
            item.title.length > 40 -> 12.sp
            item.title.length > 25 -> 14.sp
            else -> 16.sp
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = titleFontSize, fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )

        val subtitle = buildList {
            item.genres.takeIf { it.isNotBlank() }?.let { add(it.split(",").first().trim()) }
            item.year?.let { add(it.toString()) }
        }.joinToString(" • ")

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
