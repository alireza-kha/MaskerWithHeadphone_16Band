package com.masker.app.schedule

import org.json.JSONArray
import org.json.JSONObject

/**
 * یک برنامه زمان‌بندی برای پخش خودکار صدای ماسکر.
 * days: آرایه ۷ تایی از دوشنبه تا یکشنبه (اندیس ۰ = شنبه در این پروژه، برای سادگی
 * از Calendar.DAY_OF_WEEK استاندارد اندروید استفاده می‌شود: ۱=یکشنبه ... ۷=شنبه)
 */
data class ScheduleItem(
    var id: Long = System.currentTimeMillis(),
    var enabled: Boolean = true,
    var startHour: Int = 22,
    var startMinute: Int = 0,
    var endHour: Int = 7,
    var endMinute: Int = 0,
    var days: BooleanArray = BooleanArray(8), // اندیس ۱..۷ استفاده می‌شود (۰ استفاده نمی‌شود)
    var masterVolume: Float = 0.7f,
    var leftVolume: Float = 1.0f,
    var rightVolume: Float = 1.0f,
    var bandGains: FloatArray = FloatArray(16) { 0.6f },
    var label: String = ""
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("enabled", enabled)
        obj.put("startHour", startHour)
        obj.put("startMinute", startMinute)
        obj.put("endHour", endHour)
        obj.put("endMinute", endMinute)
        obj.put("label", label)
        obj.put("masterVolume", masterVolume.toDouble())
        obj.put("leftVolume", leftVolume.toDouble())
        obj.put("rightVolume", rightVolume.toDouble())

        val daysArr = JSONArray()
        for (d in days) daysArr.put(d)
        obj.put("days", daysArr)

        val bandsArr = JSONArray()
        for (b in bandGains) bandsArr.put(b.toDouble())
        obj.put("bandGains", bandsArr)

        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): ScheduleItem {
            val item = ScheduleItem()
            item.id = obj.optLong("id", System.currentTimeMillis())
            item.enabled = obj.optBoolean("enabled", true)
            item.startHour = obj.optInt("startHour", 22)
            item.startMinute = obj.optInt("startMinute", 0)
            item.endHour = obj.optInt("endHour", 7)
            item.endMinute = obj.optInt("endMinute", 0)
            item.label = obj.optString("label", "")
            item.masterVolume = obj.optDouble("masterVolume", 0.7).toFloat()
            item.leftVolume = obj.optDouble("leftVolume", 1.0).toFloat()
            item.rightVolume = obj.optDouble("rightVolume", 1.0).toFloat()

            val daysArr = obj.optJSONArray("days")
            val days = BooleanArray(8)
            if (daysArr != null) {
                for (i in 0 until minOf(8, daysArr.length())) {
                    days[i] = daysArr.optBoolean(i, false)
                }
            }
            item.days = days

            val bandsArr = obj.optJSONArray("bandGains")
            val bands = FloatArray(16) { 0.6f }
            if (bandsArr != null) {
                for (i in 0 until minOf(16, bandsArr.length())) {
                    bands[i] = bandsArr.optDouble(i, 0.6).toFloat()
                }
            }
            item.bandGains = bands

            return item
        }
    }
}
