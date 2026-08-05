package com.homecinema.library.ui.screens

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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.SmbSourceEntity
import com.homecinema.library.data.media.queryExternalPlayerApps
import com.homecinema.library.data.settings.PlaybackMode
import com.homecinema.library.data.settings.SmbConfig
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

    SettingsSubScreenScaffold(title = "Настройки", onBack = onBack) {
        SettingsNavRow(
            title = "Источники (SMB)",
            subtitle = if (sources.isEmpty()) "Не настроено" else "${sources.size} ${sourceWord(sources.size)}",
            icon = Icons.Default.Wifi,
            onClick = onOpenSources
        )
        SettingsNavRow(
            title = "Библиотека",
            subtitle = if (autoRescanEnabled) "Автообновление включено" else "Автообновление выключено",
            icon = Icons.Default.Refresh,
            onClick = onOpenLibrarySettings
        )
        SettingsNavRow(
            title = "Воспроизведение видео",
            subtitle = playbackModeLabel(playbackMode),
            icon = Icons.Default.PlayArrow,
            onClick = onOpenPlaybackSettings
        )
        SettingsNavRow(
            title = "Внешний вид",
            subtitle = themeModeLabel(themeMode),
            icon = Icons.Default.Palette,
            onClick = onOpenAppearance
        )
        SettingsNavRow(
            title = "Хранилище",
            subtitle = "Кэш обложек и кадров",
            icon = Icons.Default.Folder,
            onClick = onOpenStorage
        )
        SettingsNavRow(
            title = "История изменений",
            subtitle = null,
            icon = Icons.Default.History,
            onClick = onOpenChangelog
        )
        SettingsNavRow(
            title = "О приложении",
            subtitle = "Версия $versionName",
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

    SettingsSubScreenScaffold(title = "Источники (SMB)", onBack = onBack) {
        Text(
            "Библиотека может собираться сразу с нескольких общих папок на роутере/NAS — " +
                "например, отдельно фильмы и отдельно сериалы на разных дисках.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        if (sources.isEmpty()) {
            Text("Источники ещё не настроены", style = MaterialTheme.typography.bodyMedium)
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
            Text("Добавить источник")
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

    SettingsSubScreenScaffold(title = "Библиотека", onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = autoRescanEnabled,
                onCheckedChange = { enabled -> scope.launch { app.settingsStore.setAutoRescanEnabled(enabled) } }
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Автообновление")
                Text(
                    "Проверять источники на новые файлы при запуске приложения (не чаще раза в сутки)",
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

    SettingsSubScreenScaffold(title = "Воспроизведение видео", onBack = onBack) {
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
                Text("Внешний плеер", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Какое приложение открывать для внешнего воспроизведения",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    RadioOption(
                        label = "Всегда спрашивать",
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

    SettingsSubScreenScaffold(title = "Внешний вид", onBack = onBack) {
        Column {
            Text("Тема", style = MaterialTheme.typography.bodyMedium)
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
                Text("Цвет фона «Стекла»", style = MaterialTheme.typography.bodyMedium)
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
                Text("Непрозрачность стекла: $glassOpacity%", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Выше — панели читаются чётче, ниже — сильнее эффект прозрачного стекла",
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
            Text("Колонок в сетке", style = MaterialTheme.typography.bodyMedium)
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
            Text("Акцентный цвет", style = MaterialTheme.typography.bodyMedium)
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
                Text("Алфавитный указатель")
                Text(
                    "Полоса с буквами сбоку от списка для быстрой прокрутки",
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
    val scope = rememberCoroutineScope()
    var clearingCache by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }

    SettingsSubScreenScaffold(title = "Хранилище", onBack = onBack) {
        Text(
            "Обложки и кадры фильмов кэшируются локально, чтобы не грузить их с диска заново при каждом открытии.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Column {
            OutlinedButton(
                enabled = !clearingCache,
                onClick = {
                    clearingCache = true
                    scope.launch {
                        app.repository.clearPosterCache()
                        clearingCache = false
                        storageMessage = "Кэш обложек очищен, обновите библиотеку"
                    }
                }
            ) { Text(if (clearingCache) "Очистка..." else "Очистить кэш обложек") }

            storageMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    // Baked into the app (LOCAL_CHANGELOG) instead of fetched from the GitHub Releases API -
    // this used to be the one place in the app that needed a network connection just to show
    // static text, which meant it was unreadable offline.
    var expandedVersion by remember { mutableStateOf<String?>(null) }

    SettingsSubScreenScaffold(title = "История изменений", onBack = onBack) {
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
                    contentDescription = if (expanded) "Свернуть" else "Развернуть"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(release.body, style = MaterialTheme.typography.bodyMedium)
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

    SettingsSubScreenScaffold(title = "О приложении", onBack = onBack) {
        Column {
            Text("Home Cinema", style = MaterialTheme.typography.titleMedium)
            Text(
                "Версия $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Text(
            "Личный медиацентр для просмотра фильмов, сериалов, мультфильмов и мультсериалов " +
                "прямо с сетевого диска (SMB) — без стриминговых сервисов и подписок.",
            style = MaterialTheme.typography.bodyMedium
        )

        AboutSubsection(title = "Возможности", icon = Icons.Default.Movie) {
            listOf(
                "Несколько источников SMB одновременно, автообновление библиотеки при запуске",
                "Автосканирование по .nfo (формат Kodi): обложки, кадры (fanart) с просмотром на весь экран, миниатюры серий",
                "Категории (фильмы/сериалы/мультфильмы/мультсериалы) и коллекции по тегу <set>",
                "Поиск по названию, описанию и актёрам; фильтры по жанру и году; сортировка; настраиваемое число колонок; алфавитный указатель",
                "«Продолжить просмотр» с прерванного места и отметка просмотренного",
                "Загрузка на устройство для просмотра офлайн",
                "Плеер: субтитры, переключение аудиодорожек, жесты яркости/громкости, полноэкранный режим",
                "Темы: системная, светлая, тёмная, OLED и «Стекло» — с выбором акцентного цвета",
                "Пароли SMB-источников хранятся зашифрованными (Android Keystore), не в открытом виде"
            ).forEach { BulletLine(it) }
        }

        AboutSubsection(title = "Технологии", icon = Icons.Default.Code) {
            listOf(
                "Kotlin + Jetpack Compose (Material 3)",
                "Media3 / ExoPlayer — воспроизведение видео",
                "Room — локальный кэш библиотеки",
                "Jetpack DataStore — настройки приложения",
                "androidx.security-crypto — шифрование сохранённых паролей",
                "Coil — загрузка изображений",
                "jcifs-ng + Bouncy Castle — клиент SMB2/3 и NTLM-аутентификация",
                "Kotlin Coroutines & Flow"
            ).forEach { BulletLine(it) }
        }

        AboutSubsection(title = "Лицензии открытого кода", icon = Icons.Default.Description) {
            listOf(
                "Kotlin, kotlinx.coroutines — Apache License 2.0",
                "AndroidX (Compose, Navigation, Room, DataStore, Media3, Lifecycle, Security-Crypto) — Apache License 2.0",
                "Coil — Apache License 2.0",
                "jcifs-ng — GNU Lesser General Public License v2.1",
                "Bouncy Castle — Bouncy Castle License (аналог MIT)"
            ).forEach { BulletLine(it) }
        }
    }
}

private fun playbackModeLabel(mode: PlaybackMode): String = when (mode) {
    PlaybackMode.INTERNAL -> "Встроенный плеер"
    PlaybackMode.EXTERNAL -> "Внешний плеер"
    PlaybackMode.ASK -> "Спрашивать каждый раз"
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Как в системе"
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
    ThemeMode.OLED -> "OLED (глубокий чёрный)"
    ThemeMode.GLASS -> "Стекло"
}

private fun sourceWord(count: Int): String {
    val rem100 = count % 100
    val rem10 = count % 10
    return when {
        rem100 in 11..14 -> "источников"
        rem10 == 1 -> "источник"
        rem10 in 2..4 -> "источника"
        else -> "источников"
    }
}

/** Shared scaffold for the settings hub and every sub-screen - top bar with a back arrow, glass
 * theming wired the same way every other screen does it, content column spaced evenly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ProvideGlassHazeState {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = homeCinemaTopAppBarColors(),
                modifier = Modifier.glassEffect()
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
            Icon(Icons.Default.Edit, contentDescription = "Изменить «${source.name}»")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить «${source.name}»")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый источник" else "Изменить источник") },
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
                    label = { Text("Название, например «Гостиная NAS»") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP адрес, например 192.168.1.1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = share,
                    onValueChange = { share = it },
                    label = { Text("Имя общей папки (share)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = rootPath,
                    onValueChange = { rootPath = it },
                    label = { Text("Подпапка внутри шары (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = guest, onCheckedChange = { guest = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Гостевой доступ")
                }
                if (!guest) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Имя пользователя") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Домен (необязательно)") },
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
                                    if (ok) "Подключение успешно" else "Общая папка не найдена — проверьте её название и путь."
                                },
                                onFailure = { e -> e.toSmbUserMessage() }
                            )
                            testing = false
                        }
                    }
                ) { Text(if (testing) "Проверка..." else "Проверить подключение") }

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
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
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
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accent.color)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                else Modifier
            )
            .clickable(onClickLabel = accent.label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = accent.label,
                tint = if (accent.color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun GlassBackgroundSwatch(preset: GlassBackgroundColor, selected: Boolean, onClick: () -> Unit) {
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
            .clickable(onClickLabel = preset.label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = preset.label,
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
