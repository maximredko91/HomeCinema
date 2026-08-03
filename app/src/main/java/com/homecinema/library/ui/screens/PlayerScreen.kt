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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.homecinema.library.R
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.data.smb.SmbDataSource
import com.homecinema.library.data.smb.toSmbConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class GestureIndicatorMode { NONE, BRIGHTNESS, VOLUME }

private data class AudioTrackOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean
)

private fun audioOptionsFrom(tracks: Tracks): List<AudioTrackOption> =
    tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group ->
        (0 until group.length).map { i ->
            val format = group.getTrackFormat(i)
            val label = format.language?.uppercase() ?: format.label ?: "Дорожка ${i + 1}"
            AudioTrackOption(group, i, label, group.isTrackSelected(i))
        }
    }

/** Don't offer to resume something that's basically already finished - just start over. */
internal fun shouldResume(positionMs: Long, durationMs: Long): Boolean =
    positionMs > 5_000L && (durationMs <= 0L || positionMs < durationMs * 0.95)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(itemId: String) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var controllerVisible by remember { mutableStateOf(true) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var hasSubtitleTrack by remember { mutableStateOf(false) }
    var audioOptions by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var audioMenuOpen by remember { mutableStateOf(false) }

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

        // Play the local copy if it's been downloaded - no network needed, and it
        // works even if the phone has temporarily lost its connection to the router.
        val localFile = loaded.localFilePath?.let { File(it) }?.takeIf { it.exists() }

        val mediaSource = if (localFile != null) {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val mediaItem = MediaItem.fromUri(Uri.fromFile(localFile))
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            val source = app.repository.getSource(loaded.sourceId) ?: return@LaunchedEffect
            val cifsContext = app.smbManager.authenticatedContext(source.id, source.toSmbConfig())
            val dataSourceFactory = SmbDataSource.Factory(cifsContext)
            val mediaItem = MediaItem.fromUri(Uri.parse(loaded.videoFilePath))
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        player = ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            if (shouldResume(loaded.playbackPositionMs, loaded.durationMs)) {
                seekTo(loaded.playbackPositionMs)
            }
            playWhenReady = true
        }
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
    // progress survives a process kill (not just navigating away normally).
    LaunchedEffect(activePlayer) {
        while (isActive) {
            delay(5000)
            val duration = activePlayer.duration
            if (activePlayer.isPlaying && duration > 0) {
                app.repository.updatePlaybackProgress(itemId, activePlayer.currentPosition, duration)
            }
        }
    }

    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                hasSubtitleTrack = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
                audioOptions = audioOptionsFrom(tracks)
            }

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
        hasSubtitleTrack = activePlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
        audioOptions = audioOptionsFrom(activePlayer.currentTracks)
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
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility -> controllerVisible = visibility == View.VISIBLE }
                    )
                    playerViewRef = this
                }
            },
            update = { view -> view.player = activePlayer }
        )

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
                icon = if (indicatorMode == GestureIndicatorMode.BRIGHTNESS) Icons.Default.BrightnessMedium else Icons.Default.VolumeUp,
                level = if (indicatorMode == GestureIndicatorMode.BRIGHTNESS) brightness else volume,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Row(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            if (audioOptions.size > 1) {
                Box {
                    IconButton(onClick = { audioMenuOpen = true }) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "Аудиодорожка", tint = Color.White)
                    }
                    DropdownMenu(expanded = audioMenuOpen, onDismissRequest = { audioMenuOpen = false }) {
                        audioOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
                                        .build()
                                    audioMenuOpen = false
                                },
                                leadingIcon = { RadioButton(selected = option.selected, onClick = null) }
                            )
                        }
                    }
                }
            }

            if (hasSubtitleTrack) {
                IconButton(
                    onClick = {
                        subtitlesEnabled = !subtitlesEnabled
                        activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                            .build()
                    }
                ) {
                    Icon(
                        imageVector = if (subtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff,
                        contentDescription = "Субтитры",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureIndicator(icon: ImageVector, level: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
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
                progress = level,
                modifier = Modifier.width(90.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}
