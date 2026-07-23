package com.masker.app.hearingaid

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

/**
 * سرویس فورگراند شبیه‌سازی سمعک؛ صدای میکروفون را در پس‌زمینه دریافت و در هدفون پخش می‌کند
 * تا هم‌زمان با تعویض تب یا خاموش‌شدن صفحه هم فعال بماند — درست مثل سرویس‌های پخش دیگر برنامه.
 */
class HearingAidService : Service() {

    companion object {
        const val ACTION_START = "com.masker.app.hearingaid.action.START"
        const val ACTION_STOP = "com.masker.app.hearingaid.action.STOP"

        private const val CHANNEL_ID = "masker_hearing_aid_channel"
        private const val NOTIFICATION_ID = 1003

        /** نمونه مشترک موتور سمعک، تا صفحه اصلی هم بتواند تنظیمات را بخواند/تغییر دهد */
        val engine = HearingAidEngine()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                engine.start(this)
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_STOP -> {
                engine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        createChannelIfNeeded()

        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tab_hearing_aid))
            .setContentText(getString(R.string.notification_hearing_aid_text))
            .setSmallIcon(R.drawable.ic_masker_notification)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_hearing_aid_channel_name),
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
