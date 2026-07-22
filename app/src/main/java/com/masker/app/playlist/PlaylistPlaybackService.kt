package com.masker.app.playlist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.masker.app.MainActivity
import com.masker.app.R
import com.masker.app.storage.MaskerStorage
import java.io.File

/**
 * سرویس فورگراند پخش پلی‌لیست موسیقی در پس‌زمینه، با کنترل «قبلی / پخش‌-مکث / بعدی» از طریق
 * اعلان — مشابه سرویس پخش ماسکر ([com.masker.app.service.PlaybackService]) اما برای پلی‌لیست.
 */
class PlaylistPlaybackService : Service() {

    companion object {
        const val ACTION_PLAY_INDEX = "com.masker.app.playlist.action.PLAY_INDEX"
        const val ACTION_PAUSE_RESUME = "com.masker.app.playlist.action.PAUSE_RESUME"
        const val ACTION_NEXT = "com.masker.app.playlist.action.NEXT"
        const val ACTION_PREV = "com.masker.app.playlist.action.PREV"
        const val ACTION_STOP = "com.masker.app.playlist.action.STOP"
        const val EXTRA_INDEX = "extra_index"

        private const val CHANNEL_ID = "masker_playlist_channel"
        private const val NOTIFICATION_ID = 1002

        /** نمونه مشترک موتور پخش، تا صفحه اصلی هم بتواند وضعیت را بخواند/تغییر دهد */
        val engine = PlaylistPlayerEngine()

        /** لیست فعلی پلی‌لیست (بارگذاری‌شده از Documents/Masker/History/playlist.json) */
        var tracks: MutableList<PlaylistTrack> = mutableListOf()

        var currentIndex: Int = -1
            private set

        /** برای مطلع کردن صفحه اصلی از تغییر آهنگ/وضعیت پخش (به‌روزرسانی UI) */
        var onStateChanged: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        engine.onCompletion = { playNextInternal() }
        engine.onError = { playNextInternal() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_INDEX -> playIndex(intent.getIntExtra(EXTRA_INDEX, -1))
            ACTION_PAUSE_RESUME -> {
                if (engine.isPaused) engine.resume() else engine.pause()
                if (engine.isPlaying) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
                onStateChanged?.invoke()
            }
            ACTION_NEXT -> playNextInternal()
            ACTION_PREV -> playPrevInternal()
            ACTION_STOP -> {
                engine.stop()
                currentIndex = -1
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                onStateChanged?.invoke()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun playIndex(index: Int) {
        if (index < 0 || index >= tracks.size) return
        currentIndex = index
        val track = tracks[index]
        val file = File(MaskerStorage.playlistDir(this), track.fileName)
        if (!file.exists()) {
            playNextInternal()
            return
        }
        engine.play(this, file)
        startForeground(NOTIFICATION_ID, buildNotification())
        onStateChanged?.invoke()
    }

    private fun playNextInternal() {
        if (tracks.isEmpty()) return
        val next = if (currentIndex + 1 < tracks.size) currentIndex + 1 else 0
        playIndex(next)
    }

    private fun playPrevInternal() {
        if (tracks.isEmpty()) return
        val prev = if (currentIndex - 1 >= 0) currentIndex - 1 else tracks.size - 1
        playIndex(prev)
    }

    private fun buildNotification(): Notification {
        createChannelIfNeeded()

        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val trackTitle = tracks.getOrNull(currentIndex)?.title ?: getString(R.string.tab_playlist)
        val playPauseLabel = if (engine.isPaused) getString(R.string.play) else getString(R.string.pause)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(trackTitle)
            .setContentText(getString(R.string.notification_playlist_text))
            .setSmallIcon(R.drawable.ic_masker_notification)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, getString(R.string.playlist_previous), servicePendingIntent(ACTION_PREV, 1))
            .addAction(0, playPauseLabel, servicePendingIntent(ACTION_PAUSE_RESUME, 2))
            .addAction(0, getString(R.string.playlist_next), servicePendingIntent(ACTION_NEXT, 3))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaylistPlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_playlist_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }
}
