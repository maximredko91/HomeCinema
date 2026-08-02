package com.homecinema.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.homecinema.library.data.settings.ThemeMode
import com.homecinema.library.ui.navigation.AppNavHost
import com.homecinema.library.ui.theme.AccentColor
import com.homecinema.library.ui.theme.HomeCinemaTheme
import com.homecinema.library.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeCinemaRoot()
        }
    }
}

@Composable
private fun HomeCinemaRoot() {
    val app = HomeCinemaApp.instance
    val themeMode by app.settingsStore.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val accentName by app.settingsStore.accentColorNameFlow.collectAsState(initial = "GOLD")
    val accent = AccentColor.fromName(accentName)
    val isDark = resolveDarkTheme(themeMode, isSystemInDarkTheme())

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as ComponentActivity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    HomeCinemaTheme(themeMode = themeMode, accent = accent) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavHost()
        }
    }
}
