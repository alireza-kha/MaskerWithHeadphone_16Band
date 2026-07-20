package com.masker.app.audiogram

import android.content.Context
import org.json.JSONObject

/** ذخیره‌سازی آخرین نتیجه آزمون شنوایی (برای پیش‌نمایش در تب و امکان مشاهده بدون تکرار آزمون) */
object AudiogramStorage {
    private const val PREFS_NAME = "masker_audiogram"
    private const val KEY_LAST_RESULT = "last_result"

    fun saveLastResult(context: Context, result: AudiogramResult) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_RESULT, result.toJson().toString()).apply()
    }

    fun loadLastResult(context: Context): AudiogramResult? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LAST_RESULT, null) ?: return null
        return try {
            AudiogramResult.fromJson(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }
}
