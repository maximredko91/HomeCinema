package com.homecinema.library.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * "Стекло" panel look: a real backdrop blur (blurring whatever renders behind a surface,
 * like iOS's Liquid Glass) needs either API 31+ RenderEffect plumbing or a third-party
 * blur-behind library we don't depend on - so this approximates the same read with three
 * flat layers instead: a translucent tint, a diagonal specular-highlight gradient, and a
 * thin light-catching border. Applied only when [LocalIsGlassTheme] is true.
 */
fun Modifier.glassPanel(shape: Shape = RoundedCornerShape(16.dp)): Modifier = this
    .clip(shape)
    .background(GlassSurface.copy(alpha = 0.55f), shape)
    .background(glassHighlightBrush, shape)
    .border(1.dp, glassBorderBrush, shape)

val glassHighlightBrush: Brush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.14f),
        Color.White.copy(alpha = 0.02f),
        Color.Transparent
    ),
    start = Offset(0f, 0f),
    end = Offset(400f, 400f)
)

val glassBorderBrush: Brush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.35f),
        Color.White.copy(alpha = 0.05f)
    )
)

/** TopAppBar colors for the glass style - a translucent tinted container instead of a
 * flat opaque one. Falls back to the normal Material3 defaults outside glass theme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun homeCinemaTopAppBarColors(): TopAppBarColors {
    val isGlass = LocalIsGlassTheme.current
    return if (isGlass) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = GlassSurface.copy(alpha = 0.6f),
            scrolledContainerColor = GlassSurface.copy(alpha = 0.75f)
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }
}

/** Card/sheet container color for the glass style - translucent, otherwise the normal
 * opaque Material3 surface color. */
val glassContainerColor: Color
    @Composable
    get() = if (LocalIsGlassTheme.current) GlassSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
