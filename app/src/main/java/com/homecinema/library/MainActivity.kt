package com.homecinema.library

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.homecinema.library.data.settings.LocaleHelper
import com.homecinema.library.data.settings.ThemeMode
import com.homecinema.library.ui.navigation.AppNavHost
import com.homecinema.library.ui.theme.AccentColor
import com.homecinema.library.ui.theme.GlassBackgroundColor
import com.homecinema.library.ui.theme.HomeCinemaTheme
import com.homecinema.library.ui.theme.backgroundColorFor
import com.homecinema.library.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Tapping the playback notification re-opens this same Activity instance (FLAG_ACTIVITY_
    // CLEAR_TOP) rather than creating a new one, which means onCreate() won't run again - the
    // extra has to be picked up in onNewIntent() too, and both paths feed the same mutable
    // state so AppNavHost can react to either.
    private val pendingPlayerItemId = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

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

        pendingPlayerItemId.value = intent?.getStringExtra(EXTRA_OPEN_PLAYER_ITEM_ID)

        setContent {
            HomeCinemaRoot(pendingPlayerItemId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPlayerItemId.value = intent.getStringExtra(EXTRA_OPEN_PLAYER_ITEM_ID)
    }

    companion object {
        const val EXTRA_OPEN_PLAYER_ITEM_ID = "open_player_item_id"
    }
}

@Composable
private fun HomeCinemaRoot(pendingPlayerItemId: MutableState<String?>) {
    val app = HomeCinemaApp.instance
    val themeMode by app.settingsStore.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val accentName by app.settingsStore.accentColorNameFlow.collectAsState(initial = "GOLD")
    val accent = AccentColor.fromName(accentName)
    val glassBackgroundName by app.settingsStore.glassBackgroundColorNameFlow.collectAsState(initial = "INDIGO")
    val glassBackground = GlassBackgroundColor.fromName(glassBackgroundName)
    val glassOpacity by app.settingsStore.glassOpacityFlow.collectAsState(initial = 65)
    val systemInDarkTheme = isSystemInDarkTheme()
    val isDark = resolveDarkTheme(themeMode, systemInDarkTheme)
    val barColor = backgroundColorFor(themeMode, systemInDarkTheme, glassBackground).toArgb()

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

    HomeCinemaTheme(
        themeMode = themeMode,
        accent = accent,
        glassBackground = glassBackground,
        glassOpacityPercent = glassOpacity
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavHost(pendingPlayerItemId)
        }
    }
}
