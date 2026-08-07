package com.masker.app.audiogram

import android.content.Context
import com.masker.app.storage.MaskerStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ذخیره‌سازی تاریخچه کامل آزمون‌های شنوایی (برای همه افراد)، به‌همراه امکان جست‌وجو
 * بر اساس نام و نام خانوادگی برای دسترسی به آخرین نتیجه هر فرد.
 *
 * از نگارش ۲ به بعد، این تاریخچه به‌صورت یک فایل JSON در پوشه عمومی
 * Documents/Masker/History نگه‌داری می‌شود (نه در حافظه اختصاصی برنامه) تا با حذف یا
 * نصب مجدد برنامه از بین نرود. تاریخچه قدیمی‌تر که در SharedPreferences ذخیره شده بود،
 * در اولین اجرا به‌طور خودکار به این فایل منتقل می‌شود.
 */
object AudiogramStorage {
    private const val PREFS_NAME = "masker_audiogram"
    private const val KEY_RESULTS_LIST = "results_list"
    private const val KEY_SELECTED_TIMESTAMP = "selected_result_timestamp"
    private const val FILE_NAME = "audiogram_history.json"

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

    /** حذف یک نتیجه مشخص از تاریخچه (مثلاً از صفحه مشاهده سابقه) */
    fun deleteResult(context: Context, timestampMillis: Long) {
        val all = loadAllResults(context).toMutableList()
        all.removeAll { it.timestampMillis == timestampMillis }
        saveAll(context, all)
    }

    fun loadAllResults(context: Context): List<AudiogramResult> {
        migrateFromPrefsIfNeeded(context)
        val file = historyFile(context)
        val json = try {
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        } ?: return emptyList()

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
        try {
            historyFile(context).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun historyFile(context: Context): File {
        return File(MaskerStorage.historyDir(context), FILE_NAME)
    }

    /** انتقال یک‌باره تاریخچه قدیمی از SharedPreferences به فایل جدید در Documents/Masker */
    private fun migrateFromPrefsIfNeeded(context: Context) {
        val file = historyFile(context)
        if (file.exists()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldJson = prefs.getString(KEY_RESULTS_LIST, null) ?: return
        try {
            file.parentFile?.mkdirs()
            file.writeText(oldJson)
        } catch (_: Exception) {
        }
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

    /**
     * علامت‌گذاری یک نتیجه به‌عنوان «سابقهٔ انتخاب‌شده» — مبنای تمام اقدام‌های
     * «فراخوانی تنظیمات از اودیوگرام» در سراسر برنامه (نوچ، اکولایزر پلی‌لیست، سمعک و...)
     */
    fun setSelectedResult(context: Context, timestampMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SELECTED_TIMESTAMP, timestampMillis)
            .apply()
    }

    /**
     * نتیجهٔ انتخاب‌شده توسط کاربر؛ اگر چیزی انتخاب نشده یا دیگر در تاریخچه نیست،
     * به‌صورت پیش‌فرض جدیدترین نتیجهٔ ثبت‌شده بازگردانده می‌شود.
     */
    fun loadSelectedResult(context: Context): AudiogramResult? {
        val selectedTimestamp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SELECTED_TIMESTAMP, -1L)
        if (selectedTimestamp <= 0L) return loadLastResult(context)
        val all = loadAllResults(context)
        return all.find { it.timestampMillis == selectedTimestamp } ?: loadLastResult(context)
    }

    data class PatientSummary(
        val patientName: String,
        val patientAge: Int,
        val latestTimestampMillis: Long,
        val testCount: Int
    )
}
