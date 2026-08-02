package com.homecinema.library.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homecinema.library.ui.screens.DetailScreen
import com.homecinema.library.ui.screens.DownloadsScreen
import com.homecinema.library.ui.screens.LibraryScreen
import com.homecinema.library.ui.screens.PlayerScreen
import com.homecinema.library.ui.screens.SettingsScreen
import com.homecinema.library.ui.screens.ShowDetailScreen

private object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
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
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
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
                onOpenShow = { showId -> navController.navigate("show/$showId") }
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
