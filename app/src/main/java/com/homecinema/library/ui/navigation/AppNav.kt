package com.homecinema.library.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homecinema.library.ui.screens.AboutScreen
import com.homecinema.library.ui.screens.AppearanceSettingsScreen
import com.homecinema.library.ui.screens.ChangelogScreen
import com.homecinema.library.ui.screens.DetailScreen
import com.homecinema.library.ui.screens.DownloadsScreen
import com.homecinema.library.ui.screens.LibraryScreen
import com.homecinema.library.ui.screens.LibrarySettingsScreen
import com.homecinema.library.ui.screens.PlaybackSettingsScreen
import com.homecinema.library.ui.screens.PlayerScreen
import com.homecinema.library.ui.screens.SettingsScreen
import com.homecinema.library.ui.screens.ShowDetailScreen
import com.homecinema.library.ui.screens.SourcesSettingsScreen
import com.homecinema.library.ui.screens.StorageSettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val SETTINGS_SOURCES = "settings/sources"
    const val SETTINGS_LIBRARY = "settings/library"
    const val SETTINGS_PLAYBACK = "settings/playback"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_STORAGE = "settings/storage"
    const val SETTINGS_CHANGELOG = "settings/changelog"
    const val SETTINGS_ABOUT = "settings/about"
    const val DOWNLOADS = "downloads"
    const val DETAIL = "detail/{id}"
    const val SHOW_DETAIL = "show/{id}"
    const val PLAYER = "player/{id}"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenDetail = { id -> navController.navigate("detail/$id") },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                onPlayItem = { id -> navController.navigate("player/$id") }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSources = { navController.navigate(Routes.SETTINGS_SOURCES) },
                onOpenLibrarySettings = { navController.navigate(Routes.SETTINGS_LIBRARY) },
                onOpenPlaybackSettings = { navController.navigate(Routes.SETTINGS_PLAYBACK) },
                onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                onOpenStorage = { navController.navigate(Routes.SETTINGS_STORAGE) },
                onOpenChangelog = { navController.navigate(Routes.SETTINGS_CHANGELOG) },
                onOpenAbout = { navController.navigate(Routes.SETTINGS_ABOUT) }
            )
        }
        composable(Routes.SETTINGS_SOURCES) {
            SourcesSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_LIBRARY) {
            LibrarySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_PLAYBACK) {
            PlaybackSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_APPEARANCE) {
            AppearanceSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_STORAGE) {
            StorageSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_CHANGELOG) {
            ChangelogScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onPlay = { id -> navController.navigate("player/$id") }
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            DetailScreen(
                itemId = id,
                onBack = { navController.popBackStack() },
                onPlayInternally = { itemId -> navController.navigate("player/$itemId") },
                onOpenShow = { showId -> navController.navigate("show/$showId") },
                onOpenDetail = { otherId -> navController.navigate("detail/$otherId") }
            )
        }
        composable(
            Routes.SHOW_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            ShowDetailScreen(
                showId = id,
                onBack = { navController.popBackStack() },
                onPlayEpisode = { episodeId -> navController.navigate("player/$episodeId") }
            )
        }
        composable(
            Routes.PLAYER,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            PlayerScreen(itemId = id)
        }
    }
}
