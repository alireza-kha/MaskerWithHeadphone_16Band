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
import com.masker.app.audio.TonalEngine

/**
 * سرویس فورگراند برای پخش صدای ماسکر در پس‌زمینه (مثلاً هنگام خواب یا طبق زمان‌بندی).
 * از دو موتور پشتیبانی می‌کند: ماسکر نویزی ([NoiseEngine]) و ماسکر تونال ([TonalEngine]).
 * در هر لحظه فقط یکی از این دو در حال پخش است.
 */
class PlaybackService : Service() {

    companion object {
        const val ACTION_START = "com.masker.app.action.START"
        const val ACTION_STOP = "com.masker.app.action.STOP"

        const val MODE_NOISE = "noise"
        const val MODE_TONAL = "tonal"

        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_MASTER_VOLUME = "extra_master_volume"
        const val EXTRA_LEFT_VOLUME = "extra_left_volume"
        const val EXTRA_RIGHT_VOLUME = "extra_right_volume"
        const val EXTRA_BAND_GAINS = "extra_band_gains"

        private const val CHANNEL_ID = "masker_playback_channel"
        private const val NOTIFICATION_ID = 1001

        // نمونه‌های مشترک موتورهای صدا تا فعالیت اصلی هم بتواند وضعیت را بخواند/تغییر دهد
        val noiseEngine = NoiseEngine()
        val tonalEngine = TonalEngine()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                noiseEngine.stop(applicationContext)
                tonalEngine.stop(applicationContext)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_NOISE
                applySettings(intent, mode)
                startForeground(NOTIFICATION_ID, buildNotification())
                if (mode == MODE_TONAL) {
                    noiseEngine.stop(applicationContext)
                    tonalEngine.start(applicationContext)
                } else {
                    tonalEngine.stop(applicationContext)
                    noiseEngine.start(applicationContext)
                }
            }
        }
        return START_STICKY
    }

    private fun applySettings(intent: Intent?, mode: String) {
        if (intent == null) return
        val master = if (intent.hasExtra(EXTRA_MASTER_VOLUME)) intent.getFloatExtra(EXTRA_MASTER_VOLUME, 0.7f) else null
        val left = if (intent.hasExtra(EXTRA_LEFT_VOLUME)) intent.getFloatExtra(EXTRA_LEFT_VOLUME, 1.0f) else null
        val right = if (intent.hasExtra(EXTRA_RIGHT_VOLUME)) intent.getFloatExtra(EXTRA_RIGHT_VOLUME, 1.0f) else null
        val bands = intent.getFloatArrayExtra(EXTRA_BAND_GAINS)

        if (mode == MODE_TONAL) {
            master?.let { tonalEngine.masterVolume = it }
            left?.let { tonalEngine.leftVolume = it }
            right?.let { tonalEngine.rightVolume = it }
            if (bands != null && bands.size == tonalEngine.toneGains.size) {
                for (i in bands.indices) tonalEngine.toneGains[i] = bands[i]
            }
        } else {
            master?.let { noiseEngine.masterVolume = it }
            left?.let { noiseEngine.leftVolume = it }
            right?.let { noiseEngine.rightVolume = it }
            if (bands != null && bands.size == noiseEngine.bandGains.size) {
                for (i in bands.indices) noiseEngine.bandGains[i] = bands[i]
            }
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
        noiseEngine.stop(applicationContext)
        tonalEngine.stop(applicationContext)
        super.onDestroy()
    }
}
