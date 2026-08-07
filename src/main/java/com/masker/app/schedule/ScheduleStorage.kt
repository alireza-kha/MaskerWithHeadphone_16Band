package com.masker.app.schedule

import android.content.Context
import org.json.JSONArray

/**
 * ذخیره‌سازی ساده لیست برنامه‌های زمان‌بندی در SharedPreferences (به فرمت JSON).
 */
object ScheduleStorage {

    private const val PREFS_NAME = "masker_schedules"
    private const val KEY_LIST = "schedule_list"

    fun loadAll(context: Context): MutableList<ScheduleItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LIST, null) ?: return mutableListOf()
        val result = mutableListOf<ScheduleItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                result.add(ScheduleItem.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun saveAll(context: Context, items: List<ScheduleItem>) {
        val arr = JSONArray()
        for (item in items) arr.put(item.toJson())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun upsert(context: Context, item: ScheduleItem) {
        val list = loadAll(context)
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(item)
        saveAll(context, list)
    }

    fun delete(context: Context, id: Long) {
        val list = loadAll(context)
        list.removeAll { it.id == id }
        saveAll(context, list)
    }

    fun findById(context: Context, id: Long): ScheduleItem? {
        return loadAll(context).firstOrNull { it.id == id }
    }
}
