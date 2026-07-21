package com.masker.app.audiogram

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ذخیره‌سازی تاریخچه کامل آزمون‌های شنوایی (برای همه افراد)، به‌همراه امکان جست‌وجو
 * بر اساس نام و نام خانوادگی برای دسترسی به آخرین نتیجه هر فرد.
 */
object AudiogramStorage {
    private const val PREFS_NAME = "masker_audiogram"
    private const val KEY_RESULTS_LIST = "results_list"

    /** افزودن یک نتیجه جدید به تاریخچه (تاریخچه قبلی حفظ می‌شود) */
    fun saveResult(context: Context, result: AudiogramResult) {
        val all = loadAllResults(context).toMutableList()
        all.add(result)
        saveAll(context, all)
    }

    /** جایگزینی یک نتیجه موجود (مثلاً پس از افزودن نتایج ماسک‌شده به همان آزمون) */
    fun updateResult(context: Context, oldTimestamp: Long, newResult: AudiogramResult) {
        val all = loadAllResults(context).toMutableList()
        val index = all.indexOfFirst { it.timestampMillis == oldTimestamp }
        if (index >= 0) {
            all[index] = newResult
        } else {
            all.add(newResult)
        }
        saveAll(context, all)
    }

    fun loadAllResults(context: Context): List<AudiogramResult> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RESULTS_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<AudiogramResult>()
            for (i in 0 until arr.length()) {
                list.add(AudiogramResult.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(context: Context, results: List<AudiogramResult>) {
        val arr = JSONArray()
        for (r in results) arr.put(r.toJson())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RESULTS_LIST, arr.toString()).apply()
    }

    /** جدیدترین نتیجه ثبت‌شده در کل برنامه (صرف‌نظر از نام)، برای دکمه‌های میان‌بر مثل Notch/بهینه‌سازی */
    fun loadLastResult(context: Context): AudiogramResult? {
        return loadAllResults(context).maxByOrNull { it.timestampMillis }
    }

    /** لیست نام‌های یکتای ثبت‌شده، به‌همراه تاریخ آخرین آزمون هرکدام، مرتب بر اساس جدیدترین */
    fun loadPatientSummaries(context: Context): List<PatientSummary> {
        val all = loadAllResults(context).filter { it.patientName.isNotBlank() }
        return all.groupBy { it.patientName }
            .map { (name, results) ->
                val latest = results.maxByOrNull { it.timestampMillis }!!
                PatientSummary(name, latest.patientAge, latest.timestampMillis, results.size)
            }
            .sortedByDescending { it.latestTimestampMillis }
    }

    /** آخرین نتیجه ثبت‌شده برای یک نام مشخص */
    fun loadLatestResultForPatient(context: Context, patientName: String): AudiogramResult? {
        return loadAllResults(context)
            .filter { it.patientName == patientName }
            .maxByOrNull { it.timestampMillis }
    }

    data class PatientSummary(
        val patientName: String,
        val patientAge: Int,
        val latestTimestampMillis: Long,
        val testCount: Int
    )
}
