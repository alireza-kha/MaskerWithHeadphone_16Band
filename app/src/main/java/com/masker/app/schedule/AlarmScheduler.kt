package com.masker.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * مسئول ثبت و لغو زنگ‌های (Alarm) شروع/پایان پخش برای هر برنامه زمان‌بندی.
 */
object AlarmScheduler {

    const val ACTION_TRIGGER_START = "com.masker.app.schedule.TRIGGER_START"
    const val ACTION_TRIGGER_STOP = "com.masker.app.schedule.TRIGGER_STOP"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"

    fun scheduleAll(context: Context, item: ScheduleItem) {
        cancelAll(context, item.id)
        if (!item.enabled) return

        // برای هر روز فعال، نزدیک‌ترین زمان شروع و پایان در آینده را پیدا و ثبت می‌کنیم
        for (dayOfWeek in 1..7) {
            if (!item.days.getOrElse(dayOfWeek) { false }) continue

            val startTime = nextTimeFor(dayOfWeek, item.startHour, item.startMinute)
            val endTime = nextTimeFor(dayOfWeek, item.endHour, item.endMinute)

            scheduleOne(
                context, item.id, dayOfWeek, startTime.timeInMillis,
                ACTION_TRIGGER_START, requestCodeFor(item.id, dayOfWeek, true)
            )
            scheduleOne(
                context, item.id, dayOfWeek, endTime.timeInMillis,
                ACTION_TRIGGER_STOP, requestCodeFor(item.id, dayOfWeek, false)
            )
        }
    }

    fun cancelAll(context: Context, scheduleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (dayOfWeek in 1..7) {
            listOf(true, false).forEach { isStart ->
                val action = if (isStart) ACTION_TRIGGER_START else ACTION_TRIGGER_STOP
                val intent = Intent(context, ScheduleReceiver::class.java).apply {
                    this.action = action
                    putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, requestCodeFor(scheduleId, dayOfWeek, isStart), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                )
                pendingIntent?.let { alarmManager.cancel(it) }
            }
        }
    }

    /** پس از هر بار اجرا، زنگ را برای هفته بعد دوباره تنظیم می‌کند */
    fun rescheduleNextWeek(context: Context, scheduleId: Long, dayOfWeek: Int, isStart: Boolean, hour: Int, minute: Int) {
        val next = nextTimeFor(dayOfWeek, hour, minute, forceNextWeek = true)
        val action = if (isStart) ACTION_TRIGGER_START else ACTION_TRIGGER_STOP
        scheduleOne(context, scheduleId, dayOfWeek, next.timeInMillis, action, requestCodeFor(scheduleId, dayOfWeek, isStart))
    }

    private fun scheduleOne(
        context: Context, scheduleId: Long, dayOfWeek: Int, triggerAtMillis: Long,
        action: String, requestCode: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } catch (_: SecurityException) {
            // در صورت نبود مجوز زنگ دقیق، به روش تقریبی برمی‌گردیم
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun requestCodeFor(scheduleId: Long, dayOfWeek: Int, isStart: Boolean): Int {
        val base = (scheduleId % 100000).toInt()
        return base * 100 + dayOfWeek * 2 + (if (isStart) 0 else 1)
    }

    private fun nextTimeFor(dayOfWeek: Int, hour: Int, minute: Int, forceNextWeek: Boolean = false): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val now = Calendar.getInstance()

        // جلو بردن تا رسیدن به روز هفته مورد نظر
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        if (forceNextWeek || cal.before(now) || cal == now) {
            cal.add(Calendar.DAY_OF_MONTH, 7)
        }
        return cal
    }
}
