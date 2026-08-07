package com.masker.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * پس از روشن شدن مجدد گوشی، تمام برنامه‌های زمان‌بندی فعال را دوباره ثبت می‌کند
 * (چون AlarmManager با ری‌استارت گوشی پاک می‌شود).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val items = ScheduleStorage.loadAll(context)
            for (item in items) {
                if (item.enabled) {
                    AlarmScheduler.scheduleAll(context, item)
                }
            }
        }
    }
}
