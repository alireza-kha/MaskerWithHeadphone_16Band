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
 * نکته فنی: Google Apps Script Web App ها معمولاً با یک ریدایرکت ۳۰۲ به یک آدرس
 * script.googleusercontent.com پاسخ می‌دهند که فقط پاسخ از پیش آماده‌شده (نتیجه همان اجرای
 * doPost که در همین درخواست اول انجام شد) را برمی‌گرداند — نه یک اجرای تازه. آن آدرس فقط
 * GET را قبول می‌کند؛ ارسال دوباره POST به آن باعث کد پاسخ غیرموفق می‌شود، با اینکه اسکریپت
 * قبلاً با موفقیت اجرا و در گوگل‌شیت ثبت شده است. برای همین، درخواست اول POST است ولی دنبال
 * کردن ریدایرکت همیشه با GET (بدون بدنه) انجام می‌شود.
 */
object SheetsReportSender {

    private const val TAG = "SheetsReportSender"
    private const val TIMEOUT_MS = 15000

    fun isConfigured(): Boolean {
        return BuildConfig.SHEETS_WEBHOOK_URL.isNotBlank()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            // اگر خود بررسی وضعیت شبکه با خطا مواجه شد، به‌جای گزارش نادرست «اینترنت نیست»،
            // اجازه می‌دهیم ارسال واقعی امتحان شود تا دلیل واقعی ناموفق بودن (در صورت وجود) مشخص شود
            Log.w(TAG, "Failed to check network availability; assuming available", e)
            true
        }
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
            val success = responseCode in 200..299
            if (!success) Log.w(TAG, "Sheets webhook returned unexpected response code: $responseCode")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send report to Sheets webhook", e)
            false
        }
    }

    private fun postJson(urlString: String, body: ByteArray): Int {
        return sendRequest("POST", urlString, body, redirectsLeft = 5)
    }

    private fun sendRequest(method: String, urlString: String, body: ByteArray?, redirectsLeft: Int): Int {
        if (redirectsLeft <= 0) return -1

        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (method == "POST" && body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body) }
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 303 ||
                responseCode == 307
            ) {
                val redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                // اجرای doPost همین‌جا (در همین درخواست POST اول) قبلاً کامل شده و در گوگل‌شیت
                // ثبت شده؛ آدرس ریدایرکت فقط برای خواندن همان پاسخ آماده است، پس با GET دنبال
                // می‌شود، نه POST دوباره
                return if (redirectUrl != null) sendRequest("GET", redirectUrl, null, redirectsLeft - 1) else responseCode
            }
            return responseCode
        } finally {
            connection.disconnect()
        }
    }
}
