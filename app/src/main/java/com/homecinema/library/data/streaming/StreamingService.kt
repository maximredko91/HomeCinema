package com.homecinema.library.data.streaming

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.homecinema.library.MainActivity
import com.homecinema.library.HomeCinemaApp
import com.homecinema.library.R
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

// Deliberately generous. MX Player (confirmed via logcat: it downloads a burst over the fast
// loopback/local-network connection, closes that HTTP connection entirely, then plays for a
// long stretch from its own buffer before opening a new connection for the next chunk) does NOT
// keep one connection open for the whole file, so "no currently-open stream" is common and
// completely normal mid-playback, not just at the real end. There is no reliable signal at all
// for "the external player is done with this stream" - short of tracking that specific player's
// process lifecycle, which is fragile and player-specific. A tight timeout here doesn't detect
// "playback ended", it just guesses wrong and kills a still-playing stream. So this exists purely
// as a long-tail safety net against a truly abandoned/forgotten stream leaking the foreground
// service forever, not as a "playback ended" detector - it needs to comfortably outlast any
// normal single-sitting viewing, not react quickly.
private const val IDLE_TIMEOUT_MS = 6 * 60 * 60 * 1000L
private const val NOTIFICATION_ID = 4200

/**
 * Foreground service hosting [LocalStreamingServer] so it (and the process it lives in) keeps
 * running while an external player is actively pulling bytes from it, even if HomeCinema itself
 * gets backgrounded - same reasoning that put downloads on WorkManager rather than a plain
 * app-scoped coroutine earlier. See [IDLE_TIMEOUT_MS] for why the auto-shutdown timer is so long.
 */
class StreamingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleWatchdog: Job? = null

    /** How many streaming responses are currently open (constructed but not yet closed) on
     * [LocalStreamingServer]. The idle-shutdown countdown only ever runs while this is 0 -
     * NanoHTTPD dispatches concurrent requests on separate worker threads, so this needs to be
     * genuinely thread-safe, not just single-threaded-looking. */
    private val activeStreams = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        ensureServerStarted().apply {
            onStreamOpened = {
                activeStreams.incrementAndGet()
                idleWatchdog?.cancel()
            }
            onStreamClosed = {
                if (activeStreams.decrementAndGet() <= 0) resetIdleWatchdog()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Manual "Остановить" tap from the notification - the idle timer is a 6h long-tail
            // safety net (see IDLE_TIMEOUT_MS), not a "just finished watching" detector, so a
            // user who knows they're actually done has no other way to clear this notification
            // sooner than that.
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        // Don't blindly (re)start the countdown here - a second playback request arriving while
        // the first is still actively streaming would otherwise schedule a shutdown that has
        // nothing to do with that first, still-open connection.
        if (activeStreams.get() <= 0) resetIdleWatchdog()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Fires when the user swipes HomeCinema away from Recents (the app's task is removed, not
    // just backgrounded). User's explicit choice: stop the stream/notification right away
    // instead of waiting out the 6h safety net - accepting that if an external player is still
    // genuinely reading from this proxy at that exact moment, closing the app this way will cut
    // it off. Each Android user profile runs its own separate process for this app, so this
    // only ever affects the profile whose task was actually swiped away.
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        idleWatchdog?.cancel()
        scope.cancel()
        server?.stop()
        server = null
        super.onDestroy()
    }

    private fun resetIdleWatchdog() {
        idleWatchdog?.cancel()
        idleWatchdog = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            stopSelf()
        }
    }

    private fun buildNotification(): android.app.Notification {
        // Tapping used to do nothing at all (no contentIntent was ever set) - opens the app now.
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Manual escape hatch for the 6h safety-net timer above - a user who's actually done
        // watching doesn't need to wait it out.
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, StreamingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, HomeCinemaApp.STREAMING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.streaming_notification_channel_name))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.streaming_notification_stop), stopIntent)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.homecinema.library.streaming.STOP"

        @Volatile private var server: LocalStreamingServer? = null

        private fun ensureServerStarted(): LocalStreamingServer {
            server?.let { if (it.isAlive) return it }
            val newServer = LocalStreamingServer()
            newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer
            return newServer
        }

        /** Starts (or reuses) the loopback server + foreground service and returns the URL
         * external players should open instead of a raw smb:// path. Binding the server itself
         * is fast/synchronous - only the foreground-service notification lags slightly behind,
         * which doesn't block the very first bytes being served. */
        suspend fun streamUrl(context: Context, itemId: String): String = withContext(Dispatchers.IO) {
            val srv = ensureServerStarted()
            ContextCompat.startForegroundService(context, Intent(context, StreamingService::class.java))
            "http://127.0.0.1:${srv.listeningPort}/stream/$itemId"
        }

        /** Only meaningful right after [streamUrl] for the same item, which already ensured
         * the server (and its foreground service) is running - reuses that same server/port
         * rather than starting anything of its own. */
        suspend fun subtitleUrl(itemId: String): String = withContext(Dispatchers.IO) {
            val srv = ensureServerStarted()
            "http://127.0.0.1:${srv.listeningPort}/subtitle/$itemId"
        }
    }
}
