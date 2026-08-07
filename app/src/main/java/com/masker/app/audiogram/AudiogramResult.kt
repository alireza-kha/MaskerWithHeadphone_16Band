package com.masker.app.audiogram

import org.json.JSONArray
import org.json.JSONObject

/**
 * نتیجه یک آزمون شنوایی کامل: آستانه شنوایی هر گوش در فرکانس‌های استاندارد آزمون،
 * به‌همراه اطلاعات فرد آزمون‌شونده (نام و سن) و تاریخ آزمون.
 *
 * علاوه بر آستانه‌های معمول (بدون ماسک)، به‌صورت اختیاری آستانه‌های «ماسک‌شده» هم نگه
 * داشته می‌شوند — این‌ها یا از یک بررسی جزئی و خودکار (فقط در فرکانس‌هایی که بر پایه معیار
 * تضعیف بین‌گوشی نیاز به تأیید داشتند) پر می‌شوند، یا اگر کاربر آزمون کامل «اثر سایه»
 * (Cross-hearing / Shadow Curve) را هم انتخاب کرده باشد، برای همه فرکانس‌ها. این دقیقاً همان
 * روش استاندارد ماسکینگ در اودیومتری بالینی است.
 *
 * [unreliableRightFreqIndices] و [unreliableLeftFreqIndices] فرکانس‌هایی را مشخص می‌کنند که
 * بلافاصله پس از یک «کوشش کنترلی» (Catch Trial — کوششی بدون پخش هیچ صدایی) آزموده شدند و
 * کاربر در همان کوشش کنترلی هم پاسخ «شنیدم» داده بود (پاسخ مثبت کاذب)؛ یعنی ممکن است کاربر
 * در آن نقطه به‌جای شنیدن واقعی صدا، در حال حدس زدن الگو بوده باشد.
 *
 * مقادیر threshold بر حسب "dB کاهش نسبت به حداکثر خروجی دستگاه" هستند (مقیاس نسبی و
 * غیرکالیبره، نه dB HL بالینی). مقدار Float.NaN یعنی حتی در بلندترین سطح هم پاسخی ثبت نشد.
 */
data class AudiogramResult(
    val frequenciesHz: List<Double>,
    val rightThresholdsDb: FloatArray,
    val leftThresholdsDb: FloatArray,
    val timestampMillis: Long,
    val patientName: String = "",
    val patientAge: Int = 0,
    val rightMaskedThresholdsDb: FloatArray? = null,
    val leftMaskedThresholdsDb: FloatArray? = null,
    val unreliableRightFreqIndices: Set<Int> = emptySet(),
    val unreliableLeftFreqIndices: Set<Int> = emptySet()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("timestamp", timestampMillis)
        obj.put("patientName", patientName)
        obj.put("patientAge", patientAge)

        val freqArr = JSONArray()
        for (f in frequenciesHz) freqArr.put(f)
        obj.put("frequencies", freqArr)

        obj.put("right", floatArrayToJson(rightThresholdsDb))
        obj.put("left", floatArrayToJson(leftThresholdsDb))

        rightMaskedThresholdsDb?.let { obj.put("rightMasked", floatArrayToJson(it)) }
        leftMaskedThresholdsDb?.let { obj.put("leftMasked", floatArrayToJson(it)) }

        obj.put("unreliableRight", intSetToJson(unreliableRightFreqIndices))
        obj.put("unreliableLeft", intSetToJson(unreliableLeftFreqIndices))

        return obj
    }

    private fun floatArrayToJson(arr: FloatArray): JSONArray {
        val jsonArr = JSONArray()
        for (v in arr) jsonArr.put(if (v.isNaN()) JSONObject.NULL else v.toDouble())
        return jsonArr
    }

    private fun intSetToJson(set: Set<Int>): JSONArray {
        val jsonArr = JSONArray()
        for (v in set) jsonArr.put(v)
        return jsonArr
    }

    companion object {
        fun fromJson(obj: JSONObject): AudiogramResult {
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            val patientName = obj.optString("patientName", "")
            val patientAge = obj.optInt("patientAge", 0)

            val freqArr = obj.optJSONArray("frequencies")
            val frequencies = mutableListOf<Double>()
            if (freqArr != null) {
                for (i in 0 until freqArr.length()) frequencies.add(freqArr.optDouble(i))
            }

            val right = jsonToFloatArray(obj.optJSONArray("right"), frequencies.size)
            val left = jsonToFloatArray(obj.optJSONArray("left"), frequencies.size)
            val rightMasked = if (obj.has("rightMasked")) jsonToFloatArray(obj.optJSONArray("rightMasked"), frequencies.size) else null
            val leftMasked = if (obj.has("leftMasked")) jsonToFloatArray(obj.optJSONArray("leftMasked"), frequencies.size) else null
            val unreliableRight = jsonToIntSet(obj.optJSONArray("unreliableRight"))
            val unreliableLeft = jsonToIntSet(obj.optJSONArray("unreliableLeft"))

            return AudiogramResult(
                frequencies, right, left, timestamp, patientName, patientAge,
                rightMasked, leftMasked, unreliableRight, unreliableLeft
            )
        }

        private fun jsonToFloatArray(arr: JSONArray?, size: Int): FloatArray {
            val result = FloatArray(size) { Float.NaN }
            if (arr != null) {
                for (i in 0 until minOf(size, arr.length())) {
                    result[i] = if (arr.isNull(i)) Float.NaN else arr.optDouble(i).toFloat()
                }
            }
            return result
        }

        private fun jsonToIntSet(arr: JSONArray?): Set<Int> {
            if (arr == null) return emptySet()
            val result = mutableSetOf<Int>()
            for (i in 0 until arr.length()) result.add(arr.optInt(i))
            return result
        }
    }
}
