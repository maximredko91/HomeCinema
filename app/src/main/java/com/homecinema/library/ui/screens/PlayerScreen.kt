package com.homecinema.library.ui.screens

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
 * just another text track, toggleable from the custom settings sheet below. */
private fun buildSubtitleConfiguration(uri: Uri, extension: String): MediaItem.SubtitleConfiguration =
    MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(subtitleMimeType(extension))
        .setLanguage("ru")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()

private enum class GestureIndicatorMode { NONE, BRIGHTNESS, VOLUME }

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Don't offer to resume something that's basically already finished - just start over. */
internal fun shouldResume(positionMs: Long, durationMs: Long): Boolean =
    positionMs > 5_000L && (durationMs <= 0L || positionMs < durationMs * 0.95)

/** "1:23:45" once the video passes an hour, "3:45" otherwise. */
internal fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
fun PlayerScreen(itemId: String, onBack: () -> Unit = {}) {
    val app = HomeCinemaApp.instance
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var title by remember { mutableStateOf("") }
    var controllerVisible by remember { mutableStateOf(true) }

    // Custom-UI playback state, mirrored from the player via a Listener below rather than read
    // directly during composition - ExoPlayer's own properties aren't Compose-observable, so
    // reading them inline would silently miss updates instead of triggering recomposition.
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isSeekBarDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf(0L) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var showSettingsSheet by remember { mutableStateOf(false) }

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

    // MX Player-style aspect ratio cycling (fit -> crop -> stretch -> fit...) - PlayerView has
    // no built-in resize-mode control at all, so this needs a dedicated button either way.
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

    // Auto-hide the controls after a few seconds of inactivity while playing, like every real
    // player - restarts every time controllerVisible flips back to true. Suppressed while
    // buffering/paused/dragging the seek bar, since hiding controls mid-interaction is exactly
    // the wrong moment.
    LaunchedEffect(controllerVisible, isPlaying, isSeekBarDragging) {
        if (controllerVisible && isPlaying && !isSeekBarDragging) {
            delay(4000)
            controllerVisible = false
        }
    }

    // Screen lock: blocks every gesture and hides the controls until unlocked again - for
    // handling the phone or pocketing it mid-playback without misfiring seeks/pause.
    // Deliberately a simple always-visible unlock button rather than MX Player's tap-to-reveal-
    // then-fade pattern - same practical effect (nothing responds to touch by accident) with
    // much less state to get wrong.
    var locked by remember { mutableStateOf(false) }

    // Double-tap ±10s: brief "10 сек" flash with a rewind/forward icon on whichever side was
    // tapped. seekFlashTick is the trigger key for the auto-hide LaunchedEffect below - a plain
    // boolean would miss a second double-tap that lands while the first flash is still fading.
    var seekFlashOnLeft by remember { mutableStateOf(true) }
    var seekFlashTick by remember { mutableStateOf(0) }
    var showSeekFlash by remember { mutableStateOf(false) }
    LaunchedEffect(seekFlashTick) {
        if (seekFlashTick == 0) return@LaunchedEffect
        showSeekFlash = true
        delay(600)
        showSeekFlash = false
    }

    // Live target-time readout while swipe-seeking (horizontal drag) - null when not dragging.
    var seekPreviewText by remember { mutableStateOf<String?>(null) }

    // Long-press-and-hold for 2x playback, MX Player-style "hold to fast-forward" - reverts to
    // whatever speed was active (respects a custom speed set from the settings sheet) the
    // moment the finger lifts.
    var fastForwardActive by remember { mutableStateOf(false) }

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
        title = loaded.title

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
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Mirrors ExoPlayer's own state into Compose-observable vars - ExoPlayer's properties
    // aren't snapshot state, so the custom UI (seek bar, play/pause icon, buffering spinner)
    // needs this listener to know when to recompose at all.
    DisposableEffect(activePlayer) {
        isPlaying = activePlayer.isPlaying
        durationMs = activePlayer.duration.takeIf { it > 0 } ?: 0L
        currentTracks = activePlayer.currentTracks
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = activePlayer.duration.takeIf { it > 0 } ?: 0L
                }
                if (state == Player.STATE_ENDED) {
                    val duration = activePlayer.duration
                    if (duration > 0) {
                        scope.launch { app.repository.updatePlaybackProgress(itemId, duration, duration) }
                    }
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }
        }
        activePlayer.addListener(listener)
        onDispose { activePlayer.removeListener(listener) }
    }

    // Drives the seek bar position and the periodic watch-progress checkpoint from one loop -
    // the checkpoint used to run on its own 5s cadence; folding it in here avoids two separate
    // tickers racing to read activePlayer.currentPosition.
    LaunchedEffect(activePlayer) {
        var tick = 0
        while (isActive) {
            if (!isSeekBarDragging) {
                positionMs = activePlayer.currentPosition
            }
            if (activePlayer.isPlaying && durationMs > 0 && tick % 10 == 0) {
                app.repository.updatePlaybackProgress(itemId, activePlayer.currentPosition, durationMs)
            }
            tick++
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Inflated (rather than constructed directly) so the "texture_view" surface
                // type from res/layout/player_view.xml actually takes effect: PlayerView only
                // reads it from XML attrs. The default SurfaceView renders on a separate
                // hardware layer behind the window, which Compose's opaque backgrounds end up
                // covering - sound plays but there's no picture. use_controller is false in
                // that same layout - every control in this screen is custom Compose UI now,
                // this view is purely the video surface.
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                    player = activePlayer
                }
            },
            update = { view ->
                view.player = activePlayer
                view.resizeMode = resizeModes[resizeModeIndex].first
            }
        )

        if (isBuffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        // Gesture layer - always present (unless locked) so double-tap seek, swipe-scrub, and
        // press-and-hold-2x work whether or not the control chrome is showing, matching MX
        // Player. A plain tap toggles the chrome; taps landing on an actual button are consumed
        // by that button first (requireUnconsumed defaults to true below) and never reach this
        // full-screen layer's tap branch.
        if (!locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activePlayer) {
                        var lastTapTime = 0L
                        var lastTapWasLeft = false

                        awaitEachGesture {
                            // requireUnconsumed defaults to true - this gesture layer now stays
                            // active even while the button chrome is showing (so double-tap/
                            // swipe/long-press still work over open video area), unlike before
                            // when it only existed while the old native controller was hidden.
                            // With real buttons now sharing the same screen, grabbing already-
                            // consumed downs here would steal every button tap before its own
                            // clickable ever saw it.
                            val down = awaitFirstDown()
                            val isLeftSide = down.position.x < size.width / 2f
                            val startBrightnessOrVolume = if (isLeftSide) brightness else volume
                            val startPosition = activePlayer.currentPosition
                            val duration = activePlayer.duration.takeIf { it > 0 } ?: 0L
                            // Full-width drag = up to half the video's length, capped at 10min
                            // so scrubbing a very long title doesn't take forever to reach the
                            // point you actually want.
                            val seekRange = (duration / 2).coerceAtMost(10 * 60_000L)

                            var moved = false
                            var horizontal = false
                            var totalDragY = 0f
                            var pendingSeekTarget = startPosition
                            var longPressTriggered = false
                            var savedSpeed = 1f

                            val longPressJob = scope.launch {
                                delay(450)
                                longPressTriggered = true
                                savedSpeed = activePlayer.playbackParameters.speed
                                activePlayer.setPlaybackSpeed(2f)
                                fastForwardActive = true
                            }

                            var consumedElsewhere = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) {
                                    // A real button (drawn on top, sharing this same touch
                                    // point) claimed this gesture - back off entirely instead
                                    // of also toggling controls or seeking underneath it.
                                    consumedElsewhere = true
                                    break
                                }
                                if (!change.pressed) break
                                if (longPressTriggered) continue
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (!moved && (kotlin.math.abs(dx) > 12f || kotlin.math.abs(dy) > 12f)) {
                                    moved = true
                                    longPressJob.cancel()
                                    // If duration isn't known yet, there's nothing sane to scrub
                                    // to - fall back to the vertical brightness/volume gesture
                                    // regardless of drag direction rather than swallowing the
                                    // gesture with a seek that can't happen.
                                    horizontal = duration > 0 && kotlin.math.abs(dx) > kotlin.math.abs(dy)
                                }
                                if (moved) {
                                    change.consume()
                                    if (horizontal) {
                                        val deltaMs = (dx / size.width) * seekRange
                                        pendingSeekTarget = (startPosition + deltaMs.toLong()).coerceIn(0L, duration)
                                        val sign = if (deltaMs >= 0) "+" else "-"
                                        seekPreviewText = "${formatTimeMs(pendingSeekTarget)}   ($sign${formatTimeMs(kotlin.math.abs(deltaMs.toLong()))})"
                                    } else {
                                        val frameDy = change.previousPosition.y - change.position.y
                                        totalDragY += frameDy
                                        indicatorMode = if (isLeftSide) GestureIndicatorMode.BRIGHTNESS else GestureIndicatorMode.VOLUME
                                        val newValue = (startBrightnessOrVolume + totalDragY / size.height).coerceIn(0f, 1f)
                                        if (isLeftSide) applyBrightness(newValue) else applyVolume(newValue)
                                    }
                                }
                            }
                            longPressJob.cancel()

                            when {
                                consumedElsewhere -> {
                                    if (longPressTriggered) activePlayer.setPlaybackSpeed(savedSpeed)
                                    fastForwardActive = false
                                    seekPreviewText = null
                                    indicatorMode = GestureIndicatorMode.NONE
                                }
                                longPressTriggered -> {
                                    activePlayer.setPlaybackSpeed(savedSpeed)
                                    fastForwardActive = false
                                }
                                moved && horizontal -> {
                                    activePlayer.seekTo(pendingSeekTarget)
                                    positionMs = pendingSeekTarget
                                    seekPreviewText = null
                                }
                                moved -> {
                                    indicatorMode = GestureIndicatorMode.NONE
                                }
                                else -> {
                                    // No movement, no long press: a tap. Single vs. double-tap
                                    // is resolved by delaying the show/hide toggle briefly to
                                    // see if a second same-side tap follows in time.
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < 300 && isLeftSide == lastTapWasLeft) {
                                        lastTapTime = 0L
                                        val delta = if (isLeftSide) -10_000L else 10_000L
                                        val cap = activePlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                        val target = (activePlayer.currentPosition + delta).coerceIn(0L, cap)
                                        activePlayer.seekTo(target)
                                        positionMs = target
                                        seekFlashOnLeft = isLeftSide
                                        seekFlashTick++
                                    } else {
                                        lastTapTime = now
                                        lastTapWasLeft = isLeftSide
                                        scope.launch {
                                            delay(300)
                                            if (System.currentTimeMillis() - lastTapTime >= 300) {
                                                controllerVisible = !controllerVisible
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            )
        }

        // Scrims behind the top/bottom bars so light video content underneath doesn't wash out
        // white text/icons - a plain solid bar looked flat and blocked more of the picture than
        // needed; a gradient only darkens near the edges where the controls actually sit.
        if (controllerVisible && !locked) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            )

            // Top bar: back + title.
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.player_back_cd), tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
            }

            // Center transport cluster: replay 10 / play-pause / forward 10.
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val target = (activePlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                        activePlayer.seekTo(target)
                        positionMs = target
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.player_replay_10_cd), tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Surface(
                    onClick = { if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play() },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.player_pause_cd else R.string.player_play_cd),
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        val cap = activePlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        val target = (activePlayer.currentPosition + 10_000L).coerceIn(0L, cap)
                        activePlayer.seekTo(target)
                        positionMs = target
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.player_forward_10_cd), tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }

            // Bottom area: seek bar with time labels, then a row of secondary actions. Insets-
            // padded first, then a fixed padding on top - with system bars hidden, a row flush
            // against the physical bottom edge sits inside the swipe-up-for-system-UI gesture
            // zone and taps there can get intercepted by the OS before the app ever sees them.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .systemGesturesPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val shownPosition = if (isSeekBarDragging) dragPositionMs else positionMs
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTimeMs(shownPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = if (durationMs > 0) shownPosition.toFloat() / durationMs else 0f,
                        onValueChange = { fraction ->
                            isSeekBarDragging = true
                            dragPositionMs = (fraction * durationMs).toLong()
                        },
                        onValueChangeFinished = {
                            activePlayer.seekTo(dragPositionMs)
                            positionMs = dragPositionMs
                            isSeekBarDragging = false
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Text(formatTimeMs(durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { locked = true }) {
                        Icon(Icons.Default.LockOpen, contentDescription = stringResource(R.string.player_lock_cd), tint = Color.White)
                    }
                    Row {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.player_settings_cd), tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                                aspectChangeTick++
                            }
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.player_aspect_ratio_cd), tint = Color.White)
                        }
                    }
                }
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

        if (indicatorMode != GestureIndicatorMode.NONE) {
            GestureIndicator(
                icon = if (indicatorMode == GestureIndicatorMode.BRIGHTNESS) Icons.Default.BrightnessMedium else Icons.AutoMirrored.Filled.VolumeUp,
                level = if (indicatorMode == GestureIndicatorMode.BRIGHTNESS) brightness else volume,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showSeekFlash) {
            Surface(
                modifier = Modifier
                    .align(if (seekFlashOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 32.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (seekFlashOnLeft) Icons.Default.FastRewind else Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_seek_10_sec), color = Color.White)
                }
            }
        }

        seekPreviewText?.let { text ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(text, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }

        if (fastForwardActive) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.player_fast_forward_2x), color = Color.White)
                }
            }
        }

        // Blocks every gesture (this overlay simply consumes and discards every touch) plus the
        // rest of the chrome underneath, until unlocked - see the `locked` state comment above.
        // requireUnconsumed defaults to true so a tap on the unlock button itself (drawn after,
        // on top of this Box) still reaches its own onClick instead of being swallowed here.
        if (locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                            }
                        }
                    }
            )
            IconButton(
                onClick = { locked = false },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.player_unlock_cd), tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }

    if (showSettingsSheet) {
        PlayerSettingsSheet(
            tracks = currentTracks,
            currentSpeed = playbackSpeed,
            subtitlesEnabled = subtitlesEnabled,
            onSpeedSelected = { speed ->
                playbackSpeed = speed
                activePlayer.setPlaybackSpeed(speed)
            },
            onSubtitlesToggled = { enabled ->
                subtitlesEnabled = enabled
                activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                    .build()
            },
            onAudioTrackSelected = { group, index ->
                activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
            },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(
    tracks: Tracks?,
    currentSpeed: Float,
    subtitlesEnabled: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onSubtitlesToggled: (Boolean) -> Unit,
    onAudioTrackSelected: (Tracks.Group, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val audioGroups = remember(tracks) { tracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO } ?: emptyList() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1C)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.player_settings_speed), color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PLAYBACK_SPEEDS.forEach { speed ->
                    FilterChip(
                        selected = speed == currentSpeed,
                        onClick = { onSpeedSelected(speed) },
                        label = { Text(if (speed == 1f) "1x" else "${speed}x") }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.player_settings_subtitles), color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = subtitlesEnabled, onCheckedChange = onSubtitlesToggled)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (subtitlesEnabled) R.string.player_settings_subtitles_on else R.string.player_settings_subtitles_off),
                    color = Color.White
                )
            }

            if (audioGroups.size > 1 || audioGroups.any { it.length > 1 }) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.player_settings_audio_track), color = Color.White, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                audioGroups.forEach { group ->
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        val label = format.label ?: format.language ?: "${format.sampleMimeType} ${format.channelCount}ch"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAudioTrackSelected(group, index) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = group.isTrackSelected(index), onClick = { onAudioTrackSelected(group, index) })
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = Color.White)
                        }
                    }
                }
            }
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
