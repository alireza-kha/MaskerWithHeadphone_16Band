package com.masker.app

import android.content.Context

/**
 * ذخیره‌سازی آخرین مقادیر تنظیم‌شده توسط کاربر (۱۶ باند، ولوم کلی، ولوم چپ/راست)
 * تا با بستن و باز کردن دوباره برنامه، همان تنظیمات بازیابی شوند.
 */
object SettingsStorage {

    private const val PREFS_NAME = "masker_last_settings"
    private const val KEY_BAND_PREFIX = "band_"
    private const val KEY_MASTER = "master_volume"
    private const val KEY_LEFT = "left_volume"
    private const val KEY_RIGHT = "right_volume"

    private const val KEY_TONE_PREFIX = "tone_"
    private const val KEY_TONAL_MASTER = "tonal_master_volume"
    private const val KEY_TONAL_LEFT = "tonal_left_volume"
    private const val KEY_TONAL_RIGHT = "tonal_right_volume"

    fun saveBandGain(context: Context, index: Int, value: Float) {
        prefs(context).edit().putFloat(KEY_BAND_PREFIX + index, value).apply()
    }

    fun loadBandGain(context: Context, index: Int, default: Float): Float {
        return prefs(context).getFloat(KEY_BAND_PREFIX + index, default)
    }

    fun saveMasterVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_MASTER, value).apply()
    }

    fun loadMasterVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_MASTER, default)
    }

    fun saveLeftVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_LEFT, value).apply()
    }

    fun loadLeftVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_LEFT, default)
    }

    fun saveRightVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_RIGHT, value).apply()
    }

    fun loadRightVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_RIGHT, default)
    }

    // ---- تنظیمات ماسکر تونال ----

    fun saveToneGain(context: Context, index: Int, value: Float) {
        prefs(context).edit().putFloat(KEY_TONE_PREFIX + index, value).apply()
    }

    fun loadToneGain(context: Context, index: Int, default: Float): Float {
        return prefs(context).getFloat(KEY_TONE_PREFIX + index, default)
    }

    fun saveTonalMasterVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_TONAL_MASTER, value).apply()
    }

    fun loadTonalMasterVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_TONAL_MASTER, default)
    }

    fun saveTonalLeftVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_TONAL_LEFT, value).apply()
    }

    fun loadTonalLeftVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_TONAL_LEFT, default)
    }

    fun saveTonalRightVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_TONAL_RIGHT, value).apply()
    }

    fun loadTonalRightVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_TONAL_RIGHT, default)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
