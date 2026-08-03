package com.homecinema.library.data.streaming

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.homecinema.library.HomeCinemaApp
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
private const val NOTIFICATION_ID = 4200

/**
 * Foreground service hosting [LocalStreamingServer] so it (and the process it lives in) keeps
 * running while an external player is actively pulling bytes from it, even if HomeCinema itself
 * gets backgrounded - same reasoning that put downloads on WorkManager rather than a plain
 * app-scoped coroutine earlier. Since there's no reliable signal for "the external player
 * closed," it just stops itself after [IDLE_TIMEOUT_MS] with no HTTP requests.
 */
class StreamingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleWatchdog: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureServerStarted().onRequest = { resetIdleWatchdog() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        resetIdleWatchdog()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

    private fun buildNotification() =
        NotificationCompat.Builder(this, HomeCinemaApp.STREAMING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Воспроизведение во внешнем плеере")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    companion object {
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
    }
}
