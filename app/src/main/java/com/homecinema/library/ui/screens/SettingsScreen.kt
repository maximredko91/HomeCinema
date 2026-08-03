package com.homecinema.library.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.db.SmbSourceEntity
import com.homecinema.library.data.settings.PlaybackMode
import com.homecinema.library.data.settings.SmbConfig
import com.homecinema.library.data.settings.ThemeMode
import com.homecinema.library.ui.theme.AccentColor
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = HomeCinemaApp.instance
    val scope = rememberCoroutineScope()

    val playbackMode by app.settingsStore.playbackModeFlow.collectAsState(initial = PlaybackMode.ASK)
    val alphabetIndexEnabled by app.settingsStore.alphabetIndexEnabledFlow.collectAsState(initial = true)
    val themeMode by app.settingsStore.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val accentName by app.settingsStore.accentColorNameFlow.collectAsState(initial = "GOLD")
    val autoRescanEnabled by app.settingsStore.autoRescanEnabledFlow.collectAsState(initial = true)
    val gridColumns by app.settingsStore.gridColumnsFlow.collectAsState(initial = 2)
    var clearingCache by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SourcesSection()

            SettingsSection(title = "Библиотека", icon = Icons.Default.Refresh) {
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

            SettingsSection(title = "Воспроизведение видео", icon = Icons.Default.PlayArrow) {
                Column(Modifier.selectableGroup()) {
                    RadioOption(
                        label = "Встроенный плеер",
                        selected = playbackMode == PlaybackMode.INTERNAL,
                        onSelect = { scope.launch { app.settingsStore.savePlaybackMode(PlaybackMode.INTERNAL) } }
                    )
                    RadioOption(
                        label = "Внешний плеер",
                        selected = playbackMode == PlaybackMode.EXTERNAL,
                        onSelect = { scope.launch { app.settingsStore.savePlaybackMode(PlaybackMode.EXTERNAL) } }
                    )
                    RadioOption(
                        label = "Спрашивать каждый раз",
                        selected = playbackMode == PlaybackMode.ASK,
                        onSelect = { scope.launch { app.settingsStore.savePlaybackMode(PlaybackMode.ASK) } }
                    )
                }
            }

            SettingsSection(title = "Внешний вид", icon = Icons.Default.Palette) {
                Text("Тема", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.selectableGroup()) {
                    RadioOption(
                        label = "Как в системе",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onSelect = { scope.launch { app.settingsStore.saveThemeMode(ThemeMode.SYSTEM) } }
                    )
                    RadioOption(
                        label = "Светлая",
                        selected = themeMode == ThemeMode.LIGHT,
                        onSelect = { scope.launch { app.settingsStore.saveThemeMode(ThemeMode.LIGHT) } }
                    )
                    RadioOption(
                        label = "Тёмная",
                        selected = themeMode == ThemeMode.DARK,
                        onSelect = { scope.launch { app.settingsStore.saveThemeMode(ThemeMode.DARK) } }
                    )
                    RadioOption(
                        label = "OLED (глубокий чёрный)",
                        selected = themeMode == ThemeMode.OLED,
                        onSelect = { scope.launch { app.settingsStore.saveThemeMode(ThemeMode.OLED) } }
                    )
                }

                Spacer(Modifier.height(16.dp))
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

                Spacer(Modifier.height(16.dp))
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

                Spacer(Modifier.height(16.dp))
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

            SettingsSection(title = "Хранилище", icon = Icons.Default.Folder) {
                Text(
                    "Обложки и кадры фильмов кэшируются локально, чтобы не грузить их с диска заново при каждом открытии.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
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

            AboutSection()
        }
    }
}

@Composable
private fun SourcesSection() {
    val app = HomeCinemaApp.instance
    val scope = rememberCoroutineScope()
    val sources by app.repository.observeSources().collectAsState(initial = emptyList())

    var dialogOpen by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SmbSourceEntity?>(null) }

    SettingsSection(title = "Источники (SMB)", icon = Icons.Default.Wifi) {
        Text(
            "Библиотека может собираться сразу с нескольких общих папок на роутере/NAS — " +
                "например, отдельно фильмы и отдельно сериалы на разных дисках.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))

        if (sources.isEmpty()) {
            Text("Источники ещё не настроены", style = MaterialTheme.typography.bodyMedium)
        } else {
            sources.forEach { source ->
                SourceRow(
                    source = source,
                    onEdit = { editingSource = source; dialogOpen = true },
                    onDelete = { scope.launch { app.repository.deleteSource(source.id) } }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
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
                            val ok = runCatching { app.smbManager.testConnection(config) }.getOrDefault(false)
                            testResult = if (ok) "Подключение успешно" else "Не удалось подключиться — проверьте адрес и доступ"
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
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    expandable: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val showContent = !expandable || expanded

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (expandable) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть"
                    )
                }
            }
            AnimatedVisibility(
                visible = showContent,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
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
private fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    SettingsSection(title = "О приложении", icon = Icons.Default.Info, expandable = true, initiallyExpanded = false) {
        Text("Home Cinema", style = MaterialTheme.typography.titleMedium)
        Text(
            "Версия $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Личный медиацентр для просмотра фильмов, сериалов, мультфильмов и мультсериалов " +
                "прямо с сетевого диска (SMB) — без стриминговых сервисов и подписок.",
            style = MaterialTheme.typography.bodyMedium
        )

        AboutSubsection(title = "Возможности", icon = Icons.Default.Movie) {
            listOf(
                "Автосканирование библиотеки по SMB с разбором .nfo (формат Kodi), несколько источников одновременно",
                "Обложки и кадры фильмов, отдельные категории для фильмов/сериалов/мультфильмов/мультсериалов",
                "Поиск (включая описание и актёров), фильтры по типу, жанру и году, сортировка, коллекции фильмов",
                "Продолжение просмотра с прерванного места, отметка просмотренного",
                "Загрузка на устройство для просмотра офлайн",
                "Встроенный плеер: субтитры, переключение аудиодорожек, жесты яркости/громкости, полноэкранный режим",
                "Светлая, тёмная и OLED-темы с выбором акцентного цвета"
            ).forEach { BulletLine(it) }
        }

        AboutSubsection(title = "Технологии", icon = Icons.Default.Code) {
            listOf(
                "Kotlin + Jetpack Compose (Material 3)",
                "Media3 / ExoPlayer — воспроизведение видео",
                "Room — локальный кэш библиотеки",
                "Jetpack DataStore — настройки приложения",
                "Coil — загрузка изображений",
                "jcifs-ng — клиент SMB2/3",
                "Kotlin Coroutines & Flow"
            ).forEach { BulletLine(it) }
        }

        AboutSubsection(title = "Лицензии открытого кода", icon = Icons.Default.Description) {
            listOf(
                "Kotlin, kotlinx.coroutines — Apache License 2.0",
                "AndroidX (Compose, Navigation, Room, DataStore, Media3, Lifecycle) — Apache License 2.0",
                "Coil — Apache License 2.0",
                "jcifs-ng — GNU Lesser General Public License v2.1"
            ).forEach { BulletLine(it) }
        }
    }
}

@Composable
private fun AboutSubsection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(16.dp))
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

@Composable
private fun BulletLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
