package com.masker.app.tinnitus

import org.json.JSONObject

/**
 * یک اندازه‌گیری «بلندی صدای وزوز» با تطبیق نسبت به سطح صدای ماسکر در حال پخش: کاربر ولوم هر
 * گوش را (بر حسب دسی‌بلِ نسبی، صفر = هم‌سطح با صدای فعلی ماسکر) کم یا زیاد می‌کند تا با شدت
 * وزوز گوش خودش یکی شود. عدد ثبت‌شده یک سنجش نسبی و شخصی است، نه معادل dB HL بالینی.
 */
data class TinnitusLoudnessRecord(
    val timestampMillis: Long,
    val rightDb: Float,
    val leftDb: Float
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("timestamp", timestampMillis)
        obj.put("right", rightDb)
        obj.put("left", leftDb)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): TinnitusLoudnessRecord {
            return TinnitusLoudnessRecord(
                timestampMillis = obj.optLong("timestamp", System.currentTimeMillis()),
                rightDb = obj.optDouble("right", 0.0).toFloat(),
                leftDb = obj.optDouble("left", 0.0).toFloat()
            )
        }
    }
}
