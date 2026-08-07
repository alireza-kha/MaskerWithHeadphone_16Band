package com.masker.app.tinnitus

import android.content.Context
import com.masker.app.storage.MaskerStorage
import org.json.JSONArray
import java.io.File

/**
 * تاریخچه اندازه‌گیری‌های «بلندی صدای وزوز»، به‌صورت یک فایل JSON در
 * Documents/Masker/History (کنار سوابق اودیوگرام و پلی‌لیست).
 */
object TinnitusLoudnessStorage {
    private const val FILE_NAME = "tinnitus_loudness_history.json"

    fun saveRecord(context: Context, record: TinnitusLoudnessRecord) {
        val all = loadAllRecords(context).toMutableList()
        all.add(record)
        saveAll(context, all)
    }

    fun loadAllRecords(context: Context): List<TinnitusLoudnessRecord> {
        val file = storageFile(context)
        val json = try {
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<TinnitusLoudnessRecord>()
            for (i in 0 until arr.length()) {
                list.add(TinnitusLoudnessRecord.fromJson(arr.getJSONObject(i)))
            }
            list.sortedBy { it.timestampMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(context: Context, records: List<TinnitusLoudnessRecord>) {
        val arr = JSONArray()
        for (r in records) arr.put(r.toJson())
        try {
            storageFile(context).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun storageFile(context: Context): File = File(MaskerStorage.historyDir(context), FILE_NAME)
}
