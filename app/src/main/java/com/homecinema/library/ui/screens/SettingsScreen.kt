package com.homecinema.library.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.homecinema.library.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.SmbSourceEntity
import com.homecinema.library.data.media.queryExternalPlayerApps
import com.homecinema.library.data.settings.AppLanguage
import com.homecinema.library.data.settings.LocaleHelper
import com.homecinema.library.data.settings.PlaybackMode
import com.homecinema.library.data.settings.SmbConfig
import com.homecinema.library.data.settings.LibraryLayout
import com.homecinema.library.data.settings.ThemeMode
import com.homecinema.library.data.smb.toSmbUserMessage
import com.homecinema.library.data.update.LOCAL_CHANGELOG
import com.homecinema.library.data.update.ReleaseNote
import com.homecinema.library.ui.theme.AccentColor
import com.homecinema.library.ui.theme.GlassBackgroundColor
import com.homecinema.library.ui.theme.LocalIsGlassTheme
import com.homecinema.library.ui.theme.ProvideGlassHazeState
import com.homecinema.library.ui.theme.glassBackdrop
import com.homecinema.library.ui.theme.glassContainerColor
import com.homecinema.library.ui.theme.glassEffect
import com.homecinema.library.ui.theme.collapsingChrome
import com.homecinema.library.ui.theme.floatingChrome
import com.homecinema.library.ui.theme.homeCinemaTopAppBarColors
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Settings hub - a short list of navigation rows, each opening its own screen (see the various
 * *SettingsScreen/ChangelogScreen/AboutScreen composables below). Used to be one long
 * scrollable page with every section inline/expandable - split up because that page had grown
 * too long to comfortably scroll to reach "История изменений"/"О приложении" at the bottom.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenLibrarySettings: () -> Unit,
    onOpenPlaybackSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val app = HomeCinemaApp.instance
    val sources by app.repository.observeSources().collectAsState(initial = emptyList())
    val autoRescanEnabled by app.settingsStore.autoRescanEnabledFlow.collectAsState(initial = true)
    val playbackMode by app.settingsStore.playbackModeFlow.collectAsState(initial = PlaybackMode.ASK)
    val themeMode by app.settingsStore.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    SettingsSubScreenScaffold(title = stringResource(R.string.common_settings), onBack = onBack) {
        SettingsNavRow(
            title = stringResource(R.string.settings_sources_title),
            subtitle = if (sources.isEmpty()) stringResource(R.string.settings_not_configured) else pluralStringResource(R.plurals.sources_count, sources.size, sources.size),
            icon = Icons.Default.Wifi,
            onClick = onOpenSources
        )
        SettingsNavRow(
            title = stringResource(R.string.lib_tab_library),
            subtitle = if (autoRescanEnabled) stringResource(R.string.settings_autoupdate_on) else stringResource(R.string.settings_autoupdate_off),
            icon = Icons.Default.Refresh,
            onClick = onOpenLibrarySettings
        )
        SettingsNavRow(
            title = stringResource(R.string.settings_playback_title),
            subtitle = playbackModeLabel(playbackMode),
            icon = Icons.Default.PlayArrow,
            onClick = onOpenPlaybackSettings
        )
        SettingsNavRow(
            title = stringResource(R.string.settings_appearance_title),
            subtitle = themeModeLabel(themeMode),
            icon = Icons.Default.Palette,
            onClick = onOpenAppearance
        )
        SettingsNavRow(
            title = stringResource(R.string.settings_storage_title),
            subtitle = stringResource(R.string.settings_storage_subtitle),
            icon = Icons.Default.Folder,
            onClick = onOpenStorage
        )
        SettingsNavRow(
            title = stringResource(R.string.settings_changelog_title),
            subtitle = null,
            icon = Icons.Default.History,
            onClick = onOpenChangelog
        )
        SettingsNavRow(
            title = stringResource(R.string.settings_about_title),
            subtitle = stringResource(R.string.lib_version_x, versionName ?: "—"),
            icon = Icons.Default.Info,
            onClick = onOpenAbout
        )
    }
}

@Composable
fun SourcesSettingsScreen(onBack: () -> Unit) {
    val app = HomeCinemaApp.instance
    val scope = rememberCoroutineScope()
    val sources by app.repository.observeSources().collectAsState(initial = emptyList())

    var dialogOpen by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SmbSourceEntity?>(null) }

    SettingsSubScreenScaffold(title = stringResource(R.string.settings_sources_title), onBack = onBack) {
        Text(
            stringResource(R.string.settings_sources_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        if (sources.isEmpty()) {
            Text(stringResource(R.string.settings_sources_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            Column {
                sources.forEach { source ->
                    SourceRow(
                        source = source,
                        // The observed list never carries the real password (it's encrypted,
                        // stored outside Room) - resolve it before opening the edit form so
                        // the field isn't blank as if it had been lost.
                        onEdit = {
                            scope.launch {
                                editingSource = app.repository.getSource(source.id)
                                dialogOpen = true
                            }
                        },
                        onDelete = { scope.launch { app.repository.deleteSource(source.id) } }
                    )
                }
            }
        }

        OutlinedButton(onClick = { editingSource = null; dialogOpen = true }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_add_source))
        }
    }

    if (dialogOpen) {
        SourceEditDialog(
            initial = editingSource,
            onDismiss = { dialogOpen = false },
            onSave = { source ->
                scope.launch {
                    app.repository.saveSource(source)
                    dialogOpen = false
                }
            }
        )
    }
}

@Composable
fun LibrarySettingsScreen(onBack: () -> Unit) {
    val app = HomeCinemaApp.instance
    val scope = rememberCoroutineScope()
    val autoRescanEnabled by app.settingsStore.autoRescanEnabledFlow.collectAsState(initial = true)

    SettingsSubScreenScaffold(title = stringResource(R.string.lib_tab_library), onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = autoRescanEnabled,
                onCheckedChange = { enabled -> scope.launch { app.settingsStore.setAutoRescanEnabled(enabled) } }
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.settings_autoupdate_label))
                Text(
                    stringResource(R.string.settings_autoupdate_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playbackMode by app.settingsStore.playbackModeFlow.collectAsState(initial = PlaybackMode.ASK)
    val preferredExternalPlayer by app.settingsStore.preferredExternalPlayerPackageFlow.collectAsState(initial = "")
    val externalPlayerApps = remember { queryExternalPlayerApps(context) }

    SettingsSubScreenScaffold(title = stringResource(R.string.settings_playback_title), onBack = onBack) {
        Column(Modifier.selectableGroup()) {
            PlaybackMode.entries.forEach { mode ->
                RadioOption(
                    label = playbackModeLabel(mode),
                    selected = playbackMode == mode,
                    onSelect = { scope.launch { app.settingsStore.savePlaybackMode(mode) } }
                )
            }
        }

        AnimatedVisibility(visible = playbackMode != PlaybackMode.INTERNAL) {
            Column {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.settings_external_player_label), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_external_player_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    RadioOption(
                        label = stringResource(R.string.settings_always_ask),
                        selected = preferredExternalPlayer.isBlank(),
                        onSelect = { scope.launch { app.settingsStore.savePreferredExternalPlayerPackage("") } }
                    )
                    externalPlayerApps.forEach { playerApp ->
                        RadioOption(
                            label = playerApp.label,
                            selected = preferredExternalPlayer == playerApp.packageName,
                            onSelect = {
                                scope.launch { app.settingsStore.savePreferredExternalPlayerPackage(playerApp.packageName) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val app = HomeCinemaApp.instance
    val scope = rememberCoroutineScope()
    val themeMode by app.settingsStore.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val accentName by app.settingsStore.accentColorNameFlow.collectAsState(initial = "GOLD")
    val glassBackgroundName by app.settingsStore.glassBackgroundColorNameFlow.collectAsState(initial = "INDIGO")
    val glassOpacity by app.settingsStore.glassOpacityFlow.collectAsState(initial = 65)
    val gridColumns by app.settingsStore.gridColumnsFlow.collectAsState(initial = 2)
    val alphabetIndexEnabled by app.settingsStore.alphabetIndexEnabledFlow.collectAsState(initial = true)
    val libraryLayout by app.settingsStore.libraryLayoutFlow.collectAsState(initial = LibraryLayout.BOTTOM_NAV)
    val appLanguage by app.settingsStore.appLanguageFlow.collectAsState(initial = AppLanguage.RUSSIAN)
    val context = LocalContext.current

    SettingsSubScreenScaffold(title = stringResource(R.string.settings_appearance_title), onBack = onBack) {
        Column {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.selectableGroup()) {
                RadioOption(
                    label = stringResource(R.string.settings_language_russian),
                    selected = appLanguage == AppLanguage.RUSSIAN,
                    onSelect = {
                        scope.launch {
                            app.settingsStore.saveAppLanguage(AppLanguage.RUSSIAN)
                            LocaleHelper.restartApp(context)
                        }
                    }
                )
                RadioOption(
                    label = stringResource(R.string.settings_language_english),
                    selected = appLanguage == AppLanguage.ENGLISH,
                    onSelect = {
                        scope.launch {
                            app.settingsStore.saveAppLanguage(AppLanguage.ENGLISH)
                            LocaleHelper.restartApp(context)
                        }
                    }
                )
            }
        }

        Column {
            Text(stringResource(R.string.settings_nav_style_label), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.selectableGroup()) {
                RadioOption(
                    label = stringResource(R.string.settings_nav_bottom),
                    selected = libraryLayout == LibraryLayout.BOTTOM_NAV,
                    onSelect = { scope.launch { app.settingsStore.saveLibraryLayout(LibraryLayout.BOTTOM_NAV) } }
                )
                RadioOption(
                    label = stringResource(R.string.settings_nav_classic),
                    selected = libraryLayout == LibraryLayout.CLASSIC,
                    onSelect = { scope.launch { app.settingsStore.saveLibraryLayout(LibraryLayout.CLASSIC) } }
                )
            }
        }

        Column {
            Text(stringResource(R.string.settings_theme_label), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    RadioOption(
                        label = themeModeLabel(mode),
                        selected = themeMode == mode,
                        onSelect = { scope.launch { app.settingsStore.saveThemeMode(mode) } }
                    )
                }
            }
        }

        AnimatedVisibility(visible = themeMode == ThemeMode.GLASS) {
            Column {
                Text(stringResource(R.string.settings_glass_bg_label), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassBackgroundColor.entries.forEach { preset ->
                        GlassBackgroundSwatch(
                            preset = preset,
                            selected = preset.name == glassBackgroundName,
                            onClick = { scope.launch { app.settingsStore.saveGlassBackgroundColorName(preset.name) } }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.settings_glass_opacity, glassOpacity), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_glass_opacity_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Slider(
                    value = glassOpacity.toFloat(),
                    onValueChange = { scope.launch { app.settingsStore.setGlassOpacity(it.toInt()) } },
                    valueRange = 40f..95f,
                    steps = 10
                )
            }
        }

        Column {
            Text(stringResource(R.string.settings_grid_columns_label), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3).forEach { count ->
                    FilterChip(
                        selected = gridColumns == count,
                        onClick = { scope.launch { app.settingsStore.setGridColumns(count) } },
                        label = { Text("$count") }
                    )
                }
            }
        }

        Column {
            Text(stringResource(R.string.settings_accent_color_label), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColor.entries.forEach { accent ->
                    AccentSwatch(
                        accent = accent,
                        selected = accent.name == accentName,
                        onClick = { scope.launch { app.settingsStore.saveAccentColorName(accent.name) } }
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = alphabetIndexEnabled,
                onCheckedChange = { enabled -> scope.launch { app.settingsStore.setAlphabetIndexEnabled(enabled) } }
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.settings_alphabet_index_label))
                Text(
                    stringResource(R.string.settings_alphabet_index_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun StorageSettingsScreen(onBack: () -> Unit) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var clearingCache by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    val downloadFolderUriString by app.settingsStore.downloadFolderUriFlow.collectAsState(initial = "")

    // Storage Access Framework - lets the user point downloads at literally any folder
    // (including an SD card), not just the default Download/HomeCinema. The returned tree URI
    // only grants access for as long as a *persistable* permission is explicitly taken here -
    // without that call it would silently stop working again after the next app restart.
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch { app.settingsStore.saveDownloadFolderUri(uri.toString()) }
        }
    }

    SettingsSubScreenScaffold(title = stringResource(R.string.settings_storage_title), onBack = onBack) {
        Text(
            stringResource(R.string.settings_cache_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Column {
            val clearedMessage = stringResource(R.string.settings_cache_cleared_message)
            OutlinedButton(
                enabled = !clearingCache,
                onClick = {
                    clearingCache = true
                    scope.launch {
                        app.repository.clearPosterCache()
                        clearingCache = false
                        storageMessage = clearedMessage
                    }
                }
            ) { Text(if (clearingCache) stringResource(R.string.settings_clearing) else stringResource(R.string.settings_clear_cache)) }

            storageMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column {
            Text(stringResource(R.string.settings_download_location_label), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                downloadFolderLabel(context, downloadFolderUriString),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.settings_choose_folder)) }
                if (downloadFolderUriString.isNotBlank()) {
                    TextButton(onClick = { scope.launch { app.settingsStore.saveDownloadFolderUri("") } }) {
                        Text(stringResource(R.string.settings_default))
                    }
                }
            }
            Text(
                stringResource(R.string.settings_download_location_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

/** A human-readable stand-in for the raw tree URI Android hands back from the folder picker
 * (something like "content://com.android.externalstorage.documents/tree/1734-1F12%3A") -
 * DocumentFile exposes the picked folder's own display name at least, which reads far better
 * than the encoded URI ever would. */
@Composable
private fun downloadFolderLabel(context: android.content.Context, uriString: String): String {
    val defaultLabel = stringResource(R.string.settings_default_download_path)
    val fallbackLabel = stringResource(R.string.settings_selected_folder_fallback)
    if (uriString.isBlank()) return defaultLabel
    return remember(uriString) {
        runCatching {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
        }.getOrNull() ?: fallbackLabel
    }
}

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    // Baked into the app (LOCAL_CHANGELOG) instead of fetched from the GitHub Releases API -
    // this used to be the one place in the app that needed a network connection just to show
    // static text, which meant it was unreadable offline.
    var expandedVersion by remember { mutableStateOf<String?>(null) }

    SettingsSubScreenScaffold(title = stringResource(R.string.settings_changelog_title), onBack = onBack) {
        LOCAL_CHANGELOG.forEach { release ->
            ChangelogEntryRow(
                release = release,
                expanded = expandedVersion == release.version,
                onToggle = {
                    expandedVersion = if (expandedVersion == release.version) null else release.version
                }
            )
        }
    }
}

/** Every ReleaseNote.body in LocalChangelog is already written as blank-line-separated points
 * (one per feature/fix) - this was rendered as one dense wall of text before, with nothing
 * visually separating a version's five different changes. Splitting on the same blank line and
 * bulleting each piece needs no changes to the text itself, just how it's laid out. Shared by
 * both the changelog screen and the "what's new" update dialog. */
@Composable
fun ReleaseNoteBody(body: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        val points = body.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        points.forEachIndexed { index, point ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Text("•  $point", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One version's row on the changelog screen - collapsed to just the version number by
 * default (accordion, only one open at a time) so a long release history doesn't turn back
 * into one long scroll, the exact problem the settings redesign just fixed elsewhere. */
@Composable
private fun ChangelogEntryRow(release: ReleaseNote, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(shape = MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = glassContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (LocalIsGlassTheme.current) 0.dp else 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "v${release.version}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    ReleaseNoteBody(stringResource(release.bodyRes))
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    SettingsSubScreenScaffold(title = stringResource(R.string.settings_about_title), onBack = onBack) {
        Column {
            Text("Home Cinema", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.lib_version_x, versionName ?: "—"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Text(
            stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium
        )

        AboutSubsection(title = stringResource(R.string.about_features_title), icon = Icons.Default.Movie) {
            listOf(
                R.string.about_feature_1, R.string.about_feature_2, R.string.about_feature_3,
                R.string.about_feature_4, R.string.about_feature_5, R.string.about_feature_6,
                R.string.about_feature_7, R.string.about_feature_8, R.string.about_feature_9,
                R.string.about_feature_10, R.string.about_feature_11, R.string.about_feature_12
            ).forEach { BulletLine(stringResource(it)) }
        }

        AboutSubsection(title = stringResource(R.string.about_tech_title), icon = Icons.Default.Code) {
            listOf(
                R.string.about_tech_1, R.string.about_tech_2, R.string.about_tech_3, R.string.about_tech_4,
                R.string.about_tech_5, R.string.about_tech_6, R.string.about_tech_7, R.string.about_tech_8
            ).forEach { BulletLine(stringResource(it)) }
        }

        AboutSubsection(title = stringResource(R.string.about_licenses_title), icon = Icons.Default.Description) {
            listOf(
                R.string.about_license_1, R.string.about_license_2, R.string.about_license_3,
                R.string.about_license_4, R.string.about_license_5, R.string.about_license_6
            ).forEach { BulletLine(stringResource(it)) }
        }
    }
}

@Composable
private fun playbackModeLabel(mode: PlaybackMode): String = stringResource(
    when (mode) {
        PlaybackMode.INTERNAL -> R.string.playback_mode_internal
        PlaybackMode.EXTERNAL -> R.string.playback_mode_external
        PlaybackMode.ASK -> R.string.playback_mode_ask
    }
)

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_mode_system
        ThemeMode.LIGHT -> R.string.theme_mode_light
        ThemeMode.DARK -> R.string.theme_mode_dark
        ThemeMode.OLED -> R.string.theme_mode_oled
        ThemeMode.GLASS -> R.string.theme_mode_glass
    }
)

/** Shared scaffold for the settings hub and every sub-screen - top bar with a back arrow, glass
 * theming wired the same way every other screen does it, content column spaced evenly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    ProvideGlassHazeState {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = homeCinemaTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                modifier = Modifier.collapsingChrome(scrollBehavior)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
    }
}

@Composable
private fun SettingsNavRow(title: String, subtitle: String?, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(shape = MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = glassContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (LocalIsGlassTheme.current) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SourceRow(source: SmbSourceEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${source.host}/${source.share}${if (source.rootPath.isBlank()) "" else "/${source.rootPath}"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.settings_edit_source_cd, source.name))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_delete_source_cd, source.name))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceEditDialog(
    initial: SmbSourceEntity?,
    onDismiss: () -> Unit,
    onSave: (SmbSourceEntity) -> Unit
) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember { mutableStateOf(initial?.host.orEmpty()) }
    var share by remember { mutableStateOf(initial?.share.orEmpty()) }
    var rootPath by remember { mutableStateOf(initial?.rootPath.orEmpty()) }
    var guest by remember { mutableStateOf(initial?.guest ?: false) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var domain by remember { mutableStateOf(initial?.domain.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val connectionSuccessMessage = stringResource(R.string.settings_connection_success)
    val shareNotFoundMessage = stringResource(R.string.settings_share_not_found)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.settings_new_source_title) else stringResource(R.string.settings_edit_source_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_source_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.settings_source_host_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = share,
                    onValueChange = { share = it },
                    label = { Text(stringResource(R.string.settings_source_share_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = rootPath,
                    onValueChange = { rootPath = it },
                    label = { Text(stringResource(R.string.settings_source_rootpath_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = guest, onCheckedChange = { guest = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_guest_access))
                }
                if (!guest) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.settings_username_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.settings_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text(stringResource(R.string.settings_domain_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        val config = SmbConfig(host, share, rootPath, domain, username, password, guest)
                        scope.launch {
                            testResult = runCatching { app.smbManager.testConnection(config) }.fold(
                                onSuccess = { ok ->
                                    if (ok) connectionSuccessMessage else shareNotFoundMessage
                                },
                                onFailure = { e -> e.toSmbUserMessage(context) }
                            )
                            testing = false
                        }
                    }
                ) { Text(if (testing) stringResource(R.string.settings_testing) else stringResource(R.string.settings_test_connection)) }

                testResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && host.isNotBlank() && share.isNotBlank(),
                onClick = {
                    onSave(
                        SmbSourceEntity(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            host = host.trim(),
                            share = share.trim(),
                            rootPath = rootPath.trim(),
                            domain = domain.trim(),
                            username = username.trim(),
                            password = password,
                            guest = guest
                        )
                    )
                }
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun RadioOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun AccentSwatch(accent: AccentColor, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(accent.labelRes)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accent.color)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                else Modifier
            )
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = label,
                tint = if (accent.color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun GlassBackgroundSwatch(preset: GlassBackgroundColor, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(preset.labelRes)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(preset.swatch)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onBackground else Color.White.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = label,
                tint = if (preset.swatch.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AboutSubsection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column {
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        Column(content = content)
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
