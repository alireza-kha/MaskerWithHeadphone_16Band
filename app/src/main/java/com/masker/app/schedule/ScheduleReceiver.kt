package com.masker.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.masker.app.service.PlaybackService
import java.util.Calendar

/**
 * دریافت‌کننده زنگ‌های زمان‌بندی؛ سرویس پخش را روشن/خاموش کرده
 * و زنگ را برای هفته بعد دوباره تنظیم می‌کند.
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId == -1L) return

        val item = ScheduleStorage.findById(context, scheduleId) ?: return
        if (!item.enabled) return

        val todayDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

        when (intent.action) {
            AlarmScheduler.ACTION_TRIGGER_START -> {
                startPlayback(context, item)
                AlarmScheduler.rescheduleNextWeek(
                    context, scheduleId, todayDayOfWeek, true, item.startHour, item.startMinute
                )
            }
            AlarmScheduler.ACTION_TRIGGER_STOP -> {
                stopPlayback(context)
                AlarmScheduler.rescheduleNextWeek(
                    context, scheduleId, todayDayOfWeek, false, item.endHour, item.endMinute
                )
            }
        }
    }

    private fun startPlayback(context: Context, item: ScheduleItem) {
        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START
            putExtra(PlaybackService.EXTRA_MASTER_VOLUME, item.masterVolume)
            putExtra(PlaybackService.EXTRA_LEFT_VOLUME, item.leftVolume)
            putExtra(PlaybackService.EXTRA_RIGHT_VOLUME, item.rightVolume)
            putExtra(PlaybackService.EXTRA_BAND_GAINS, item.bandGains)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopPlayback(context: Context) {
        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }
}
