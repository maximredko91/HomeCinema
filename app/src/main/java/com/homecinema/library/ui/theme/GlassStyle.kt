package com.homecinema.library.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * "Стекло" panel look: real backdrop blur via Haze (blurs whatever actually renders behind
 * a surface, the way iOS's Liquid Glass does) instead of the flat-color approximation this
 * used to be. Applied only when [LocalIsGlassTheme] is true - every other theme keeps plain
 * Material3 surfaces untouched, same as before. minSdk is 31+ specifically so this can be
 * unconditional: Haze itself has no real blur-behind below API 31 either (falls back to a
 * flat scrim, i.e. exactly the old approximation), so there was no point keeping two paths.
 */

/** The current screen's [HazeState], provided once near the top of a screen via
 * [ProvideGlassHazeState]. Lets [glassBackdrop]/[glassEffect] stay parameter-free at every
 * call site (matching how [homeCinemaTopAppBarColors]/[glassContainerColor] already read
 * [LocalIsGlassTheme] ambiently) instead of threading a HazeState through every composable
 * in between - several of them (SettingsSection, ScrollJumpButtons, the filter/list-picker
 * sheets) are reused many times per screen or several layers deep from the screen root. */
val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Wraps [content] with a remembered [HazeState], provided via [LocalGlassHazeState] - call
 * once near the top of a screen, around everything that uses [glassBackdrop]/[glassEffect]. */
@Composable
fun ProvideGlassHazeState(content: @Composable () -> Unit) {
    val hazeState = remember { HazeState() }
    CompositionLocalProvider(LocalGlassHazeState provides hazeState, content = content)
}

/** Marks a screen's scrollable background content as the thing every [glassEffect] on that
 * same screen should sample and blur. No-op outside glass theme (or without a provided
 * [LocalGlassHazeState] - defensive, every screen using this also calls
 * [ProvideGlassHazeState]). */
@Composable
fun Modifier.glassBackdrop(): Modifier {
    val state = LocalGlassHazeState.current
    return if (LocalIsGlassTheme.current && state != null) this.hazeSource(state = state) else this
}

/** Applies real blur-behind to a glass surface (top bar, card, sheet, FAB), plus the thin
 * light-catching edge that already read well on the old flat approximation and still does
 * layered on top of real blur. [shape] should match whatever shape the surface is already
 * clipped to (rounded corners for a card, none for a full-width top bar) so the blur and
 * border don't bleed past it. No-op outside glass theme. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.glassEffect(shape: Shape = RectangleShape): Modifier {
    val state = LocalGlassHazeState.current
    return if (LocalIsGlassTheme.current && state != null) {
        this
            .clip(shape)
            .hazeEffect(state = state, style = HazeMaterials.thin(containerColor = GlassSurface))
            .border(1.dp, glassBorderBrush, shape)
    } else {
        this
    }
}

val glassBorderBrush: Brush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.35f),
        Color.White.copy(alpha = 0.05f)
    )
)

/** TopAppBar colors for the glass style - fully transparent container so the real blur
 * (applied separately via [glassEffect] on the same TopAppBar) shows through instead of a
 * flat color sitting on top of it. Falls back to normal Material3 defaults outside glass. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun homeCinemaTopAppBarColors(): TopAppBarColors =
    if (LocalIsGlassTheme.current) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

/** Card/sheet container color for the glass style - transparent so [glassEffect]'s real blur
 * does the work instead of a flat color; otherwise the normal opaque Material3 surface. */
val glassContainerColor: Color
    @Composable
    get() = if (LocalIsGlassTheme.current) Color.Transparent else MaterialTheme.colorScheme.surface

/** Container color for sheets Material3 renders in a separate Android Window
 * (ModalBottomSheet is a Dialog under the hood) - Haze's blur can't cross that window
 * boundary, and passing ANY custom Modifier (even a plain clip+background+border, no Haze
 * involved) into ModalBottomSheet's own `modifier` param was empirically found to corrupt
 * its height measurement, silently rendering the sheet at ~0 height with no crash or log.
 * Use this only via ModalBottomSheet's `containerColor` param, never via its `modifier`. */
val glassSheetContainerColor: Color
    @Composable
    get() = if (LocalIsGlassTheme.current) GlassSurface.copy(alpha = 0.94f) else MaterialTheme.colorScheme.surface
