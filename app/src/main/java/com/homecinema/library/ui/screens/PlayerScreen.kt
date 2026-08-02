package com.homecinema.library.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.smb.SmbDataSource
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(itemId: String) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current

    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(itemId) {
        val loaded = app.repository.getById(itemId) ?: return@LaunchedEffect

        // Play the local copy if it's been downloaded - no network needed, and it
        // works even if the phone has temporarily lost its connection to the router.
        val localFile = loaded.localFilePath?.let { File(it) }?.takeIf { it.exists() }

        val mediaSource = if (localFile != null) {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val mediaItem = MediaItem.fromUri(Uri.fromFile(localFile))
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            val cifsContext = app.smbManager.authenticatedContext()
            val dataSourceFactory = SmbDataSource.Factory(cifsContext)
            val mediaItem = MediaItem.fromUri(Uri.parse(loaded.videoFilePath))
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        player = ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    val activePlayer = player
    if (activePlayer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PlayerView(it).apply {
                player = activePlayer
                useController = true
            }
        }
    )
}
