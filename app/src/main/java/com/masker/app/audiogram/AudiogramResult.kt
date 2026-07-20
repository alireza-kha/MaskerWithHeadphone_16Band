package com.masker.app.audiogram

import org.json.JSONArray
import org.json.JSONObject

/**
 * نتیجه یک آزمون شنوایی کامل: آستانه شنوایی هر گوش در فرکانس‌های استاندارد آزمون.
 * مقادیر threshold بر حسب "dB کاهش نسبت به حداکثر خروجی دستگاه" هستند (مقیاس نسبی و
 * غیرکالیبره، نه dB HL بالینی). مقدار Float.NaN یعنی حتی در بلندترین سطح هم پاسخی ثبت نشد.
 */
data class AudiogramResult(
    val frequenciesHz: List<Double>,
    val rightThresholdsDb: FloatArray,
    val leftThresholdsDb: FloatArray,
    val timestampMillis: Long
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("timestamp", timestampMillis)

        val freqArr = JSONArray()
        for (f in frequenciesHz) freqArr.put(f)
        obj.put("frequencies", freqArr)

        val rightArr = JSONArray()
        for (v in rightThresholdsDb) rightArr.put(if (v.isNaN()) JSONObject.NULL else v.toDouble())
        obj.put("right", rightArr)

        val leftArr = JSONArray()
        for (v in leftThresholdsDb) leftArr.put(if (v.isNaN()) JSONObject.NULL else v.toDouble())
        obj.put("left", leftArr)

        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): AudiogramResult {
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())

            val freqArr = obj.optJSONArray("frequencies")
            val frequencies = mutableListOf<Double>()
            if (freqArr != null) {
                for (i in 0 until freqArr.length()) frequencies.add(freqArr.optDouble(i))
            }

            val rightArr = obj.optJSONArray("right")
            val right = FloatArray(frequencies.size) { Float.NaN }
            if (rightArr != null) {
                for (i in 0 until minOf(right.size, rightArr.length())) {
                    right[i] = if (rightArr.isNull(i)) Float.NaN else rightArr.optDouble(i).toFloat()
                }
            }

            val leftArr = obj.optJSONArray("left")
            val left = FloatArray(frequencies.size) { Float.NaN }
            if (leftArr != null) {
                for (i in 0 until minOf(left.size, leftArr.length())) {
                    left[i] = if (leftArr.isNull(i)) Float.NaN else leftArr.optDouble(i).toFloat()
                }
            }

            return AudiogramResult(frequencies, right, left, timestamp)
        }
    }
}
