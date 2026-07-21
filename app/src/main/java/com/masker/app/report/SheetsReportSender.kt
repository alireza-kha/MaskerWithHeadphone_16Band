package com.masker.app.report

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.masker.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * ارسال گزارش‌ها به یک Google Sheet، از طریق یک Google Apps Script منتشرشده به‌عنوان
 * Web App. این روش هیچ رمز عبور یا کلید حساسی نیاز ندارد — فقط یک آدرس وب (Webhook) که
 * از BuildConfig (مقداردهی‌شده از local.properties، فایلی که هرگز به گیت‌هاب نمی‌رود)
 * خوانده می‌شود.
 *
 * نکته فنی: Google Apps Script Web App ها معمولاً با یک ریدایرکت ۳۰۲ به آدرس واقعی پاسخ
 * می‌دهند. HttpURLConnection به‌صورت پیش‌فرض بدنه POST را هنگام دنبال کردن ریدایرکت درست
 * منتقل نمی‌کند، برای همین اینجا ریدایرکت به‌صورت دستی و با ارسال مجدد همان بدنه دنبال
 * می‌شود.
 */
object SheetsReportSender {

    private const val TAG = "SheetsReportSender"
    private const val TIMEOUT_MS = 15000

    fun isConfigured(): Boolean {
        return BuildConfig.SHEETS_WEBHOOK_URL.isNotBlank()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** ارسال همزمان (Blocking)؛ باید روی یک ترد پس‌زمینه فراخوانی شود */
    fun sendReportSync(report: PatientReport): Boolean {
        if (!isConfigured()) {
            Log.w(TAG, "Sheets webhook URL not configured; skipping send")
            return false
        }
        return try {
            val body = report.toJson().toString().toByteArray(Charsets.UTF_8)
            val responseCode = postJson(BuildConfig.SHEETS_WEBHOOK_URL, body)
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send report to Sheets webhook", e)
            false
        }
    }

    private fun postJson(urlString: String, body: ByteArray, redirectsLeft: Int = 5): Int {
        if (redirectsLeft <= 0) return -1

        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            connection.outputStream.use { it.write(body) }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307
            ) {
                val redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                return if (redirectUrl != null) postJson(redirectUrl, body, redirectsLeft - 1) else responseCode
            }
            return responseCode
        } finally {
            connection.disconnect()
        }
    }
}
