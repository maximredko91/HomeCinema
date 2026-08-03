package com.homecinema.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.homecinema.library.data.settings.ThemeMode
import com.homecinema.library.ui.navigation.AppNavHost
import com.homecinema.library.ui.theme.AccentColor
import com.homecinema.library.ui.theme.HomeCinemaTheme
import com.homecinema.library.ui.theme.backgroundColorFor
import com.homecinema.library.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Needed to show download progress notifications (DownloadWorker) on API 33+ -
        // asked once up front rather than per-screen; a denial just means downloads run
        // without a visible notification, they still complete.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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
    val systemInDarkTheme = isSystemInDarkTheme()
    val isDark = resolveDarkTheme(themeMode, systemInDarkTheme)
    val barColor = backgroundColorFor(themeMode, systemInDarkTheme).toArgb()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as ComponentActivity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
            // Paint the bars to match the theme background instead of leaving them at
            // the OS default (often a stark white that clashes with a dark theme).
            @Suppress("DEPRECATION")
            window.statusBarColor = barColor
            @Suppress("DEPRECATION")
            window.navigationBarColor = barColor
        }
    }

    HomeCinemaTheme(themeMode = themeMode, accent = accent) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavHost()
        }
    }
}
