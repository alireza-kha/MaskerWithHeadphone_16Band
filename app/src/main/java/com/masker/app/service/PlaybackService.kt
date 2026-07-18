package com.masker.app.service

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
import com.masker.app.audio.NoiseEngine

/**
 * سرویس فورگراند برای پخش صدای ماسکر در پس‌زمینه (مثلاً هنگام خواب یا طبق زمان‌بندی).
 */
class PlaybackService : Service() {

    companion object {
        const val ACTION_START = "com.masker.app.action.START"
        const val ACTION_STOP = "com.masker.app.action.STOP"

        const val EXTRA_MASTER_VOLUME = "extra_master_volume"
        const val EXTRA_LEFT_VOLUME = "extra_left_volume"
        const val EXTRA_RIGHT_VOLUME = "extra_right_volume"
        const val EXTRA_BAND_GAINS = "extra_band_gains"

        private const val CHANNEL_ID = "masker_playback_channel"
        private const val NOTIFICATION_ID = 1001

        // نمونه مشترک موتور صدا تا فعالیت اصلی هم بتواند وضعیت را بخواند
        val engine = NoiseEngine()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                engine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                applySettings(intent)
                startForeground(NOTIFICATION_ID, buildNotification())
                engine.start()
            }
        }
        return START_STICKY
    }

    private fun applySettings(intent: Intent?) {
        if (intent == null) return
        if (intent.hasExtra(EXTRA_MASTER_VOLUME)) {
            engine.masterVolume = intent.getFloatExtra(EXTRA_MASTER_VOLUME, engine.masterVolume)
        }
        if (intent.hasExtra(EXTRA_LEFT_VOLUME)) {
            engine.leftVolume = intent.getFloatExtra(EXTRA_LEFT_VOLUME, engine.leftVolume)
        }
        if (intent.hasExtra(EXTRA_RIGHT_VOLUME)) {
            engine.rightVolume = intent.getFloatExtra(EXTRA_RIGHT_VOLUME, engine.rightVolume)
        }
        val bands = intent.getFloatArrayExtra(EXTRA_BAND_GAINS)
        if (bands != null && bands.size == engine.bandGains.size) {
            for (i in bands.indices) engine.bandGains[i] = bands[i]
        }
    }

    private fun buildNotification(): Notification {
        createChannelIfNeeded()

        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_masker_notification)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, getString(R.string.stop), stopPendingIntent)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
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
