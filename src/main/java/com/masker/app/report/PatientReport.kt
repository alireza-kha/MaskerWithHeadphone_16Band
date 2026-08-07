package com.masker.app.report

import android.content.Context
import com.masker.app.SettingsStorage
import com.masker.app.audio.NoiseEngine
import com.masker.app.audio.TonalEngine
import com.masker.app.audiogram.AudiogramResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * بسته‌ای از اطلاعات که هنگام «توقف موقت» آزمون اودیوگرام جمع‌آوری و برای بررسی‌های
 * آزمایشگاهی و بهبود نرم‌افزار برای سازنده ارسال می‌شود: اطلاعات بیمار، وضعیت اودیوگرام
 * تا همان لحظه، نمرات ذهنی شدت وزوز (۰ تا ۱۰)، و تنظیمات فعلی ماسکر (مدل صدا و شدت هر باند).
 */
data class PatientReport(
    val patientName: String,
    val patientAge: Int,
    val timestampMillis: Long,
    val leftTinnitusScore: Int?,
    val rightTinnitusScore: Int?,
    val audiogramJson: JSONObject?,
    val noiseMaskerSettingsJson: JSONObject,
    val tonalMaskerSettingsJson: JSONObject
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("patientName", patientName)
        obj.put("patientAge", patientAge)
        obj.put("timestamp", timestampMillis)
        obj.put("leftTinnitusScore", leftTinnitusScore ?: JSONObject.NULL)
        obj.put("rightTinnitusScore", rightTinnitusScore ?: JSONObject.NULL)
        obj.put("audiogram", audiogramJson ?: JSONObject.NULL)
        obj.put("noiseMaskerSettings", noiseMaskerSettingsJson)
        obj.put("tonalMaskerSettings", tonalMaskerSettingsJson)
        return obj
    }

    companion object {
        fun build(
            context: Context,
            patientName: String,
            patientAge: Int,
            audiogramResult: AudiogramResult?,
            leftTinnitusScore: Int?,
            rightTinnitusScore: Int?
        ): PatientReport {
            val noiseJson = JSONObject().apply {
                put("masterVolume", SettingsStorage.loadMasterVolume(context, 0.7f).toDouble())
                put("leftVolume", SettingsStorage.loadLeftVolume(context, 1.0f).toDouble())
                put("rightVolume", SettingsStorage.loadRightVolume(context, 1.0f).toDouble())

                val bands = JSONArray()
                for (i in 0 until NoiseEngine.BAND_COUNT) {
                    bands.put(SettingsStorage.loadBandGain(context, i, 0.6f).toDouble())
                }
                put("bandGains", bands)

                put("notchEnabled", SettingsStorage.loadNotchEnabled(context))
                put("notchFrequencyHz", SettingsStorage.loadNotchFrequency(context, 4000.0))
                put("notchWidthOctaves", SettingsStorage.loadNotchWidth(context, 0.5f).toDouble())

                put("modulationEnabled", SettingsStorage.loadModulationEnabled(context))
                put("modulationDepth", SettingsStorage.loadModulationDepth(context, 0.6f).toDouble())
            }

            val tonalJson = JSONObject().apply {
                put("masterVolume", SettingsStorage.loadTonalMasterVolume(context, 0.7f).toDouble())
                put("leftVolume", SettingsStorage.loadTonalLeftVolume(context, 1.0f).toDouble())
                put("rightVolume", SettingsStorage.loadTonalRightVolume(context, 1.0f).toDouble())

                val tones = JSONArray()
                for (i in 0 until TonalEngine.TONE_COUNT) {
                    tones.put(SettingsStorage.loadToneGain(context, i, 0f).toDouble())
                }
                put("toneGains", tones)
            }

            return PatientReport(
                patientName = patientName,
                patientAge = patientAge,
                timestampMillis = System.currentTimeMillis(),
                leftTinnitusScore = leftTinnitusScore,
                rightTinnitusScore = rightTinnitusScore,
                audiogramJson = audiogramResult?.toJson(),
                noiseMaskerSettingsJson = noiseJson,
                tonalMaskerSettingsJson = tonalJson
            )
        }

        fun fromJson(obj: JSONObject): PatientReport {
            return PatientReport(
                patientName = obj.optString("patientName", ""),
                patientAge = obj.optInt("patientAge", 0),
                timestampMillis = obj.optLong("timestamp", System.currentTimeMillis()),
                leftTinnitusScore = if (obj.isNull("leftTinnitusScore")) null else obj.optInt("leftTinnitusScore"),
                rightTinnitusScore = if (obj.isNull("rightTinnitusScore")) null else obj.optInt("rightTinnitusScore"),
                audiogramJson = if (obj.isNull("audiogram")) null else obj.optJSONObject("audiogram"),
                noiseMaskerSettingsJson = obj.optJSONObject("noiseMaskerSettings") ?: JSONObject(),
                tonalMaskerSettingsJson = obj.optJSONObject("tonalMaskerSettings") ?: JSONObject()
            )
        }
    }
}
