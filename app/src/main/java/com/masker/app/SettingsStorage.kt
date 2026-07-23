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

    private const val KEY_NOTCH_ENABLED = "notch_enabled"
    private const val KEY_NOTCH_FREQUENCY = "notch_frequency"
    private const val KEY_NOTCH_WIDTH = "notch_width"

    private const val KEY_MODULATION_ENABLED = "modulation_enabled"
    private const val KEY_MODULATION_DEPTH = "modulation_depth"

    private const val KEY_PLAYLIST_NOTCH_ENABLED = "playlist_notch_enabled"
    private const val KEY_PLAYLIST_NOTCH_FREQUENCY = "playlist_notch_frequency"
    private const val KEY_PLAYLIST_NOTCH_WIDTH = "playlist_notch_width"

    private const val KEY_PLAYLIST_LEFT_VOLUME = "playlist_left_volume"
    private const val KEY_PLAYLIST_RIGHT_VOLUME = "playlist_right_volume"
    private const val KEY_PLAYLIST_EQ_RIGHT_PREFIX = "playlist_eq_right_"
    private const val KEY_PLAYLIST_EQ_LEFT_PREFIX = "playlist_eq_left_"

    private const val KEY_HEARING_AID_MASTER_GAIN = "hearing_aid_master_gain"
    private const val KEY_HEARING_AID_LEFT_VOLUME = "hearing_aid_left_volume"
    private const val KEY_HEARING_AID_RIGHT_VOLUME = "hearing_aid_right_volume"
    private const val KEY_HEARING_AID_EQ_RIGHT_PREFIX = "hearing_aid_eq_right_"
    private const val KEY_HEARING_AID_EQ_LEFT_PREFIX = "hearing_aid_eq_left_"

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

    // ---- تنظیمات حذف فرکانس وزوز (Notch) ----

    fun saveNotchSettings(context: Context, enabled: Boolean, frequencyHz: Double, widthOctaves: Float) {
        prefs(context).edit()
            .putBoolean(KEY_NOTCH_ENABLED, enabled)
            .putFloat(KEY_NOTCH_FREQUENCY, frequencyHz.toFloat())
            .putFloat(KEY_NOTCH_WIDTH, widthOctaves)
            .apply()
    }

    fun loadNotchEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_NOTCH_ENABLED, false)
    }

    fun loadNotchFrequency(context: Context, default: Double): Double {
        return prefs(context).getFloat(KEY_NOTCH_FREQUENCY, default.toFloat()).toDouble()
    }

    fun loadNotchWidth(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_NOTCH_WIDTH, default)
    }

    // ---- تنظیمات مدولاسیون دامنه ۱۰ هرتز ----

    fun saveModulationSettings(context: Context, enabled: Boolean, depth: Float) {
        prefs(context).edit()
            .putBoolean(KEY_MODULATION_ENABLED, enabled)
            .putFloat(KEY_MODULATION_DEPTH, depth)
            .apply()
    }

    fun loadModulationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_MODULATION_ENABLED, false)
    }

    fun loadModulationDepth(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_MODULATION_DEPTH, default)
    }

    // ---- تنظیمات حذف فرکانس (Notch) پلی‌لیست ----

    fun savePlaylistNotchSettings(context: Context, enabled: Boolean, frequencyHz: Double, widthOctaves: Float) {
        prefs(context).edit()
            .putBoolean(KEY_PLAYLIST_NOTCH_ENABLED, enabled)
            .putFloat(KEY_PLAYLIST_NOTCH_FREQUENCY, frequencyHz.toFloat())
            .putFloat(KEY_PLAYLIST_NOTCH_WIDTH, widthOctaves)
            .apply()
    }

    fun loadPlaylistNotchEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PLAYLIST_NOTCH_ENABLED, false)
    }

    fun loadPlaylistNotchFrequency(context: Context, default: Double): Double {
        return prefs(context).getFloat(KEY_PLAYLIST_NOTCH_FREQUENCY, default.toFloat()).toDouble()
    }

    fun loadPlaylistNotchWidth(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_PLAYLIST_NOTCH_WIDTH, default)
    }

    // ---- ولوم و اکولایزر مستقل هر گوش در پلی‌لیست ----

    fun savePlaylistLeftVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_PLAYLIST_LEFT_VOLUME, value).apply()
    }

    fun loadPlaylistLeftVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_PLAYLIST_LEFT_VOLUME, default)
    }

    fun savePlaylistRightVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_PLAYLIST_RIGHT_VOLUME, value).apply()
    }

    fun loadPlaylistRightVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_PLAYLIST_RIGHT_VOLUME, default)
    }

    fun savePlaylistRightEqGain(context: Context, band: Int, value: Float) {
        prefs(context).edit().putFloat(KEY_PLAYLIST_EQ_RIGHT_PREFIX + band, value).apply()
    }

    fun loadPlaylistRightEqGain(context: Context, band: Int, default: Float): Float {
        return prefs(context).getFloat(KEY_PLAYLIST_EQ_RIGHT_PREFIX + band, default)
    }

    fun savePlaylistLeftEqGain(context: Context, band: Int, value: Float) {
        prefs(context).edit().putFloat(KEY_PLAYLIST_EQ_LEFT_PREFIX + band, value).apply()
    }

    fun loadPlaylistLeftEqGain(context: Context, band: Int, default: Float): Float {
        return prefs(context).getFloat(KEY_PLAYLIST_EQ_LEFT_PREFIX + band, default)
    }

    // ---- سمعک: بهره کلی، ولوم و اکولایزر مستقل هر گوش ----

    fun saveHearingAidMasterGain(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_HEARING_AID_MASTER_GAIN, value).apply()
    }

    fun loadHearingAidMasterGain(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_HEARING_AID_MASTER_GAIN, default)
    }

    fun saveHearingAidLeftVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_HEARING_AID_LEFT_VOLUME, value).apply()
    }

    fun loadHearingAidLeftVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_HEARING_AID_LEFT_VOLUME, default)
    }

    fun saveHearingAidRightVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_HEARING_AID_RIGHT_VOLUME, value).apply()
    }

    fun loadHearingAidRightVolume(context: Context, default: Float): Float {
        return prefs(context).getFloat(KEY_HEARING_AID_RIGHT_VOLUME, default)
    }

    fun saveHearingAidRightEqGain(context: Context, band: Int, value: Float) {
        prefs(context).edit().putFloat(KEY_HEARING_AID_EQ_RIGHT_PREFIX + band, value).apply()
    }

    fun loadHearingAidRightEqGain(context: Context, band: Int, default: Float): Float {
        return prefs(context).getFloat(KEY_HEARING_AID_EQ_RIGHT_PREFIX + band, default)
    }

    fun saveHearingAidLeftEqGain(context: Context, band: Int, value: Float) {
        prefs(context).edit().putFloat(KEY_HEARING_AID_EQ_LEFT_PREFIX + band, value).apply()
    }

    fun loadHearingAidLeftEqGain(context: Context, band: Int, default: Float): Float {
        return prefs(context).getFloat(KEY_HEARING_AID_EQ_LEFT_PREFIX + band, default)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
