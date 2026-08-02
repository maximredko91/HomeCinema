package com.homecinema.library.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.settings.SmbConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = HomeCinemaApp.instance
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(SmbConfig()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        config = app.settingsStore.currentConfig()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки подключения") },
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
                .padding(20.dp)
        ) {
            Text(
                "Укажите адрес общей папки на Keenetic Titan, где лежит SSD с фильмами. " +
                    "Обычно это IP роутера в локальной сети и имя расшаренной папки.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = config.host,
                onValueChange = { config = config.copy(host = it) },
                label = { Text("IP адрес роутера, например 192.168.1.1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = config.share,
                onValueChange = { config = config.copy(share = it) },
                label = { Text("Имя общей папки (share), например disk1_ssd") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = config.rootPath,
                onValueChange = { config = config.copy(rootPath = it) },
                label = { Text("Подпапка внутри шары (необязательно), например Video") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = config.guest, onCheckedChange = { config = config.copy(guest = it) })
                Spacer(Modifier.width(8.dp))
                Text("Гостевой доступ (без логина/пароля)")
            }

            if (!config.guest) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = config.username,
                    onValueChange = { config = config.copy(username = it) },
                    label = { Text("Имя пользователя") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = config.password,
                    onValueChange = { config = config.copy(password = it) },
                    label = { Text("Пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = config.domain,
                    onValueChange = { config = config.copy(domain = it) },
                    label = { Text("Домен (обычно можно оставить пустым)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(24.dp))
            Row {
                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val ok = runCatching { app.smbManager.testConnection(config) }.getOrDefault(false)
                            testResult = if (ok) "Подключение успешно" else "Не удалось подключиться — проверьте адрес и доступ"
                            testing = false
                        }
                    }
                ) { Text(if (testing) "Проверка..." else "Проверить подключение") }

                Spacer(Modifier.width(12.dp))

                Button(onClick = {
                    scope.launch {
                        app.settingsStore.saveConfig(config)
                        app.smbManager.invalidateCache()
                        onBack()
                    }
                }) { Text("Сохранить") }
            }

            testResult?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
