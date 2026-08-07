package com.homecinema.library.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.homecinema.library.R
import com.homecinema.library.data.db.MediaType
import com.homecinema.library.data.nfo.looksAnimated

@Composable
private fun categoryLabel(mediaType: MediaType): String = when (mediaType) {
    MediaType.MOVIE -> stringResource(R.string.category_movie)
    MediaType.CARTOON -> stringResource(R.string.category_cartoon)
    MediaType.TV_SHOW -> stringResource(R.string.category_tv_show)
    MediaType.CARTOON_SERIES -> stringResource(R.string.category_cartoon_series)
    MediaType.EPISODE -> ""
}

/** Automatic classification straight from the stored, comma-separated genre string - the
 * same [looksAnimated] heuristic LibraryScanner applies at scan time, recomputed here so
 * "сбросить на автоматическое" doesn't need to wait for a rescan to take effect. */
fun autoDetectedMediaType(genres: String, isShow: Boolean): MediaType {
    val animated = genres.split(",").map { it.trim() }.looksAnimated()
    return when {
        isShow && animated -> MediaType.CARTOON_SERIES
        isShow -> MediaType.TV_SHOW
        animated -> MediaType.CARTOON
        else -> MediaType.MOVIE
    }
}

/** Small tappable chip showing a title's current category (Фильм/Мультфильм or
 * Сериал/Мультсериал) - opens [CategoryOverrideDialog] to reclassify it when the .nfo
 * genre-keyword heuristic got it wrong or the source .nfo just didn't have a genre filled
 * in at all. A small pencil badge shows when the current value is a manual override rather
 * than the automatic guess. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChip(mediaType: MediaType, overridden: Boolean, onClick: () -> Unit) {
    val icon = when {
        mediaType == MediaType.CARTOON || mediaType == MediaType.CARTOON_SERIES -> Icons.Default.ChildCare
        mediaType == MediaType.TV_SHOW -> Icons.Default.Tv
        else -> Icons.Default.Theaters
    }
    // Disables Material3's default 48dp minimum touch target on this clickable Surface - this
    // chip sits inline with plain (non-clickable) MetaChips in DetailScreen's metadata row,
    // which don't get that enforced minimum, so leaving it on made this one visibly taller than
    // its siblings in the same row.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(categoryLabel(mediaType), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (overridden) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.category_manual_override_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}

/** Lets the user pick between the two [MediaType]s valid for this title's structural kind
 * (movie-like: MOVIE/CARTOON, or show-like: TV_SHOW/CARTOON_SERIES) - or hand classification
 * back to the automatic .nfo genre-keyword heuristic. [genres] is the title's own stored
 * genre string, used to compute what "automatic" currently means. [current] is the title's
 * currently-effective MediaType (which radio option starts selected). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOverrideDialog(
    isShow: Boolean,
    genres: String,
    current: MediaType,
    currentOverridden: Boolean,
    onDismiss: () -> Unit,
    onSelect: (mediaType: MediaType, overridden: Boolean) -> Unit
) {
    val plainType = if (isShow) MediaType.TV_SHOW else MediaType.MOVIE
    val animatedType = if (isShow) MediaType.CARTOON_SERIES else MediaType.CARTOON
    val autoDetected = autoDetectedMediaType(genres, isShow)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.category_dialog_description, categoryLabel(autoDetected)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                CategoryRadioOption(
                    label = categoryLabel(plainType),
                    selected = current == plainType,
                    onSelect = { onSelect(plainType, true); onDismiss() }
                )
                CategoryRadioOption(
                    label = categoryLabel(animatedType),
                    selected = current == animatedType,
                    onSelect = { onSelect(animatedType, true); onDismiss() }
                )
                if (currentOverridden) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onSelect(autoDetected, false); onDismiss() }) {
                        Text(stringResource(R.string.category_reset_to_auto))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun CategoryRadioOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}
