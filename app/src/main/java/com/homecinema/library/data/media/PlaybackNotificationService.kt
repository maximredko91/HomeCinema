package com.homecinema.library.data.media

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerNotificationManager
import com.homecinema.library.MainActivity
import com.homecinema.library.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val NOTIFICATION_ID = 4300
private const val CHANNEL_ID = "playback_channel"

/**
 * Foreground service that keeps a system notification (title, poster, play/pause, tap to jump
 * back into the player) alive while a video is playing internally - there was previously no
 * notification for internal playback at all, so once the app left the foreground there was no
 * way back into what was playing short of reopening the app and finding the title again by hand.
 *
 * Deliberately built on [PlayerNotificationManager] (mirrors an existing [Player]'s state into a
 * notification) instead of this service owning the ExoPlayer itself - PlayerScreen keeps
 * creating/controlling its own player exactly as before (subtitle/resume/gesture logic all
 * untouched), this only attaches to it once it's already playing.
 */
class PlaybackNotificationService : Service() {

    private var notificationManager: PlayerNotificationManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notificationManager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
            .setChannelNameResourceId(R.string.playback_notification_channel_name)
            .setSmallIconResourceId(android.R.drawable.ic_media_play)
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence = currentTitle

                override fun createCurrentContentIntent(player: Player): PendingIntent {
                    val intent = Intent(this@PlaybackNotificationService, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_PLAYER_ITEM_ID, currentItemId)
                    return PendingIntent.getActivity(
                        this@PlaybackNotificationService, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }

                override fun getCurrentContentText(player: Player): CharSequence? = null

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? {
                    val path = currentPosterPath ?: return null
                    // Cached rather than decoded fresh every call - the adapter is queried
                    // repeatedly as the notification updates (e.g. every play/pause toggle),
                    // and re-reading the same poster file from disk each time is wasted work.
                    if (path == cachedPosterPath) return cachedPosterBitmap
                    serviceScope.launch {
                        val bitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                        cachedPosterPath = path
                        cachedPosterBitmap = bitmap
                        if (bitmap != null) callback.onBitmap(bitmap)
                    }
                    return null
                }
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                    if (ongoing) {
                        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        } else 0
                        ServiceCompat.startForeground(this@PlaybackNotificationService, notificationId, notification, type)
                    } else {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopSelf()
                }
            })
            .build()
        notificationManager?.setUseStopAction(false)
        notificationManager?.setUsePreviousAction(false)
        notificationManager?.setUseNextAction(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Not just onCreate() - if the service is already running (chaining from one video
        // straight into another without it ever stopping), a fresh attach() call only reaches
        // this callback, not onCreate() again, so the notification would otherwise keep
        // showing the previous title/poster and controlling a now-released player.
        notificationManager?.setPlayer(attachedPlayer)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        notificationManager?.setPlayer(null)
        notificationManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile private var currentTitle: CharSequence = ""
        @Volatile private var currentItemId: String = ""
        @Volatile private var currentPosterPath: String? = null
        @Volatile private var cachedPosterPath: String? = null
        @Volatile private var cachedPosterBitmap: Bitmap? = null
        @Volatile private var attachedPlayer: Player? = null

        /** Called once a video's ExoPlayer instance is prepared and playing - starts (or
         * updates, if already running from a previous title) the notification. */
        fun attach(context: Context, player: Player, title: String, posterLocalPath: String?, itemId: String) {
            currentTitle = title
            currentItemId = itemId
            currentPosterPath = posterLocalPath
            attachedPlayer = player
            ContextCompat.startForegroundService(context, Intent(context, PlaybackNotificationService::class.java))
        }

        /** Called when PlayerScreen releases its player (leaving the screen, or process
         * teardown) - stops the service, which also cancels the notification. */
        fun detach(context: Context) {
            attachedPlayer = null
            context.stopService(Intent(context, PlaybackNotificationService::class.java))
        }
    }
}
