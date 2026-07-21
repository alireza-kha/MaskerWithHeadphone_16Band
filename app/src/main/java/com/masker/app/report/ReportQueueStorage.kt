package com.masker.app.report

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * صف محلی گزارش‌هایی که به دلیل نبود اینترنت هنوز ایمیل نشده‌اند. این گزارش‌ها در حافظه
 * اختصاصی برنامه (غیرقابل‌دسترس برای سایر برنامه‌ها) نگه‌داری می‌شوند تا در اولین فرصتی که
 * اینترنت وصل شود، دوباره تلاش برای ارسال آن‌ها انجام شود.
 */
object ReportQueueStorage {

    private const val DIR_NAME = "pending_reports"

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun enqueue(context: Context, report: PatientReport) {
        try {
            val file = File(dir(context), "report_${report.timestampMillis}.json")
            file.writeText(report.toJson().toString())
        } catch (_: Exception) {
        }
    }

    /** لیست فایل‌های در صف، به همراه گزارش تجزیه‌شده هرکدام */
    fun loadPending(context: Context): List<Pair<File, PatientReport>> {
        val files = dir(context).listFiles { f -> f.extension == "json" } ?: emptyArray()
        return files.mapNotNull { f ->
            try {
                val json = JSONObject(f.readText())
                f to PatientReport.fromJson(json)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun remove(file: File) {
        try {
            file.delete()
        } catch (_: Exception) {
        }
    }

    fun pendingCount(context: Context): Int {
        return dir(context).listFiles { f -> f.extension == "json" }?.size ?: 0
    }
}
