package com.homecinema.library.ui.screens

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.homecinema.library.R
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.media.DownloadStorage
import com.homecinema.library.data.media.PlaybackNotificationService
import com.homecinema.library.data.media.findLocalSiblingSubtitle
import com.homecinema.library.data.media.isContentUri
import com.homecinema.library.data.media.subtitleMimeType
import com.homecinema.library.data.smb.SmbDataSource
import com.homecinema.library.data.smb.toSmbConfig
import com.homecinema.library.ui.theme.LocalIsGlassTheme
import com.homecinema.library.ui.theme.glassBorderBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.roundToInt

/** A sidecar subtitle file, attached to the MediaItem so DefaultMediaSourceFactory wires it
 * up itself - SELECTION_FLAG_DEFAULT auto-selects it for playback, after which it shows up as
 * just another text track in the player's own native settings menu (audio/captions/speed),
 * same as any subtitle track muxed inside the container. */
private fun buildSubtitleConfiguration(uri: Uri, extension: String): MediaItem.SubtitleConfiguration =
    MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(subtitleMimeType(extension))
        .setLanguage("ru")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()

private enum class GestureIndicatorMode { NONE, BRIGHTNESS, VOLUME }

/** Don't offer to resume something that's basically already finished - just start over. */
internal fun shouldResume(positionMs: Long, durationMs: Long): Boolean =
    positionMs > 5_000L && (durationMs <= 0L || positionMs < durationMs * 0.95)

@Composable
fun PlayerScreen(itemId: String) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var controllerVisible by remember { mutableStateOf(true) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()) }
    var brightness by remember {
        mutableStateOf(
            activity.window.attributes.screenBrightness.takeIf { it in 0f..1f }
                ?: runCatching {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                }.getOrDefault(128) / 255f
        )
    }
    var indicatorMode by remember { mutableStateOf(GestureIndicatorMode.NONE) }

    // MX Player-style aspect ratio cycling (fit -> crop -> stretch -> fit...), tap-triggered via
    // a dedicated button rather than folded into the native settings gear since PlayerView has
    // no built-in resize-mode control at all - see the settings-gear comment below for why
    // playback speed/audio track don't need the same treatment (already native).
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to R.string.player_aspect_fit,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to R.string.player_aspect_zoom,
            AspectRatioFrameLayout.RESIZE_MODE_FILL to R.string.player_aspect_fill,
        )
    }
    var resizeModeIndex by remember { mutableStateOf(0) }
    var aspectChangeTick by remember { mutableStateOf(0) }
    var showAspectLabel by remember { mutableStateOf(false) }

    LaunchedEffect(aspectChangeTick) {
        if (aspectChangeTick == 0) return@LaunchedEffect
        showAspectLabel = true
        delay(1200)
        showAspectLabel = false
    }

    fun applyBrightness(value: Float) {
        brightness = value.coerceIn(0.01f, 1f)
        val params = activity.window.attributes
        params.screenBrightness = brightness
        activity.window.attributes = params
    }

    fun applyVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).roundToInt(), 0)
    }

    // Fullscreen video: hide system bars (they otherwise sit on top of the dark player
    // as a stray light strip and never go away on their own) and keep the screen awake
    // for the duration of playback. Restored when leaving the screen.
    DisposableEffect(Unit) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val originalBehavior = insetsController.systemBarsBehavior
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = originalBehavior
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(itemId) {
        val loaded = app.repository.getById(itemId) ?: return@LaunchedEffect

        // Play the local copy if it's been downloaded - no network needed, and it works even
        // if the phone has temporarily lost its connection to the router. Downloads made after
        // the move to Download/HomeCinema store a content:// URI here instead of a plain file
        // path (see DownloadStorage) - older, pre-migration downloads still have a real path
        // and keep working exactly as before.
        val localPath = loaded.localFilePath
        val localContentUri = localPath?.takeIf { isContentUri(it) }?.let { Uri.parse(it) }
        val localFile = localPath?.takeIf { !isContentUri(it) }?.let { File(it) }?.takeIf { it.exists() }

        val dataSourceFactory: DataSource.Factory
        val videoUri: Uri
        val subtitleConfiguration: MediaItem.SubtitleConfiguration?

        if (localContentUri != null) {
            dataSourceFactory = DefaultDataSource.Factory(context)
            videoUri = localContentUri
            val subtitle = withTimeoutOrNull(3000) {
                runCatching {
                    val customTreeUri = app.settingsStore.downloadFolderUriFlow.first().takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                    DownloadStorage.findDownloadedSubtitle(context, loaded, localContentUri, customTreeUri)
                }.getOrNull()
            }
            subtitleConfiguration = subtitle?.let { (uri, extension) -> buildSubtitleConfiguration(uri, extension) }
        } else if (localFile != null) {
            dataSourceFactory = DefaultDataSource.Factory(context)
            videoUri = Uri.fromFile(localFile)
            // Best-effort and defensive on purpose: this is a "nice to have" lookup that must
            // never be able to stop the video itself from playing, whether it fails outright
            // or (an SMB share behaving oddly) just never returns.
            val subtitleFile = withTimeoutOrNull(3000) {
                runCatching { findLocalSiblingSubtitle(localFile) }.getOrNull()
            }
            subtitleConfiguration = subtitleFile?.let {
                buildSubtitleConfiguration(Uri.fromFile(it), it.extension)
            }
        } else {
            val source = app.repository.getSource(loaded.sourceId) ?: return@LaunchedEffect
            val cifsContext = app.smbManager.authenticatedContext(source.id, source.toSmbConfig())
            dataSourceFactory = SmbDataSource.Factory(cifsContext)
            videoUri = Uri.parse(loaded.videoFilePath)
            val subtitlePath = withTimeoutOrNull(3000) {
                runCatching { app.smbManager.findSiblingSubtitle(source.id, source.toSmbConfig(), loaded.videoFilePath) }
                    .getOrNull()
            }
            subtitleConfiguration = subtitlePath?.let { path ->
                buildSubtitleConfiguration(Uri.parse(path), path.substringAfterLast('.', ""))
            }
        }

        // DefaultMediaSourceFactory rather than manually building
        // ProgressiveMediaSource/SingleSampleMediaSource/MergingMediaSource - the latter
        // produces subtitle samples in their raw sidecar format (text/vtt etc.), which recent
        // Media3 versions refuse to render directly ("Legacy decoding is disabled, can't
        // handle text/vtt samples") since legacy in-renderer subtitle decoding was disabled
        // by default. DefaultMediaSourceFactory wires the modern SubtitleParser-based
        // extraction pipeline correctly on its own.
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .apply { subtitleConfiguration?.let { setSubtitleConfigurations(listOf(it)) } }
            .build()
        val mediaSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)

        val newPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            if (shouldResume(loaded.playbackPositionMs, loaded.durationMs)) {
                seekTo(loaded.playbackPositionMs)
            }
            playWhenReady = true
        }
        player = newPlayer
        // Keeps a system notification (title, poster, tap to come back) alive while this plays
        // in the background - previously there was none for internal playback at all, so
        // leaving the app mid-movie meant no way back into it short of reopening from scratch.
        PlaybackNotificationService.attach(context, newPlayer, loaded.title, loaded.posterLocalPath, itemId)
    }

    // Saves the current position before releasing, so playback can resume next time.
    DisposableEffect(Unit) {
        onDispose {
            val p = player
            if (p != null) {
                val duration = p.duration
                if (duration > 0) {
                    val position = p.currentPosition
                    app.applicationScope.launch { app.repository.updatePlaybackProgress(itemId, position, duration) }
                }
                p.release()
            }
            PlaybackNotificationService.detach(context)
        }
    }

    val activePlayer = player
    if (activePlayer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Periodic checkpoint while playing, in addition to the on-dispose save above, so
    // progress survives a process kill (not just navigating away normally). The delay used to
    // run BEFORE the first write, so lastPlayedAt (and with it, this title showing up in
    // История) lagged the actual start of playback by up to 5s - checking right after opening
    // something could miss it entirely. Delay now runs after, so the first checkpoint fires as
    // soon as duration is known instead of waiting out a full cycle first.
    LaunchedEffect(activePlayer) {
        while (isActive) {
            val duration = activePlayer.duration
            if (activePlayer.isPlaying && duration > 0) {
                app.repository.updatePlaybackProgress(itemId, activePlayer.currentPosition, duration)
            }
            delay(5000)
        }
    }

    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val duration = activePlayer.duration
                    if (duration > 0) {
                        scope.launch { app.repository.updatePlaybackProgress(itemId, duration, duration) }
                    }
                }
            }
        }
        activePlayer.addListener(listener)
        onDispose { activePlayer.removeListener(listener) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Inflated (rather than constructed directly) so the "texture_view" surface
                // type from res/layout/player_view.xml actually takes effect: PlayerView only
                // reads it from XML attrs. The default SurfaceView renders on a separate
                // hardware layer behind the window, which Compose's opaque backgrounds end up
                // covering - sound plays but there's no picture or visible controller.
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                    player = activePlayer
                    // The native control bar already has a settings (⚙) button - handles audio
                    // track + playback speed automatically, no code needed. Subtitle on/off
                    // wasn't part of that by default though, so this turns on its dedicated CC
                    // button in the same bar. A custom Compose settings menu used to duplicate
                    // this - removed, since it just meant two "gear" controls fighting for the
                    // same corner instead of the one native panel doing the whole job.
                    setShowSubtitleButton(true)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility -> controllerVisible = visibility == View.VISIBLE }
                    )
                    playerViewRef = this
                }
            },
            update = { view ->
                view.player = activePlayer
                view.resizeMode = resizeModes[resizeModeIndex].first
            }
        )

        if (controllerVisible) {
            IconButton(
                onClick = {
                    resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                    aspectChangeTick++
                },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.player_aspect_ratio_cd), tint = Color.White)
            }
        }

        if (showAspectLabel) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = stringResource(resizeModes[resizeModeIndex].second),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Only present while the native controller is hidden: it's a full-screen overlay,
        // so if it stayed up while the controller (play/pause/seek buttons, which sit
        // roughly mid-screen, not just in a bottom strip) is showing, it would eat every
        // tap meant for those buttons before the AndroidView underneath ever sees it.
        // Handles tap-to-reveal-controls plus swipe-to-adjust brightness (left half) /
        // volume (right half), MX Player-style; once controls are visible, native
        // PlayerView touch handling takes over directly (including tap-to-hide).
        if (!controllerVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activePlayer) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val isLeftSide = down.position.x < size.width / 2f
                            val startValue = if (isLeftSide) brightness else volume
                            var totalDrag = 0f
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dy = change.previousPosition.y - change.position.y
                                if (!moved && kotlin.math.abs(change.position.y - down.position.y) > 12f) moved = true
                                if (moved) {
                                    totalDrag += dy
                                    change.consume()
                                    indicatorMode = if (isLeftSide) GestureIndicatorMode.BRIGHTNESS else GestureIndicatorMode.VOLUME
                                    val newValue = (startValue + totalDrag / size.height).coerceIn(0f, 1f)
                                    if (isLeftSide) applyBrightness(newValue) else applyVolume(newValue)
                                }
                            }
                            if (moved) {
                                indicatorMode = GestureIndicatorMode.NONE
                            } else {
                                playerViewRef?.showController()
                            }
                        }
                    }
            )
        }

        if (indicatorMode != GestureIndicatorMode.NONE) {
            GestureIndicator(
                icon = if (indicatorMode == GestureIndicatorMode.BRIGHTNESS) Icons.Default.BrightnessMedium else Icons.AutoMirrored.Filled.VolumeUp,
                level = if (indicatorMode == GestureIndicatorMode.BRIGHTNESS) brightness else volume,
                modifier = Modifier.align(Alignment.Center)
            )
        }

    }
}

@Composable
private fun GestureIndicator(icon: ImageVector, level: Float, modifier: Modifier = Modifier) {
    val isGlass = LocalIsGlassTheme.current
    Surface(
        modifier = if (isGlass) modifier.border(1.dp, glassBorderBrush, RoundedCornerShape(16.dp)) else modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { level },
                modifier = Modifier.width(90.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}
