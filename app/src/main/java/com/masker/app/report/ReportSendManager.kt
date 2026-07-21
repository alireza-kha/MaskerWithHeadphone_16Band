package com.masker.app.report

import android.content.Context
import kotlin.concurrent.thread

/**
 * هماهنگ‌کننده ارسال گزارش‌ها: ابتدا تلاش برای ارسال فوری می‌کند؛ اگر اینترنت نبود یا ارسال
 * ناموفق بود، گزارش را در صف محلی نگه می‌دارد. تابع [flushPending] هم برای تلاش مجدد روی
 * گزارش‌های صف‌شده استفاده می‌شود (مثلاً هر بار برنامه باز می‌شود).
 */
object ReportSendManager {

    /** تلاش فوری برای ارسال؛ نتیجه (موفق/ناموفق) از طریق onResult روی همان ترد پس‌زمینه اعلام می‌شود */
    fun sendOrQueue(context: Context, report: PatientReport, onResult: (Boolean) -> Unit) {
        thread(name = "MaskerReportSendThread") {
            val sent = if (SheetsReportSender.isNetworkAvailable(context)) {
                SheetsReportSender.sendReportSync(report)
            } else {
                false
            }
            if (!sent) {
                ReportQueueStorage.enqueue(context, report)
            }
            onResult(sent)
        }
    }

    /** تلاش برای ارسال تمام گزارش‌های در صف (مثلاً در زمان باز شدن برنامه) */
    fun flushPending(context: Context) {
        thread(name = "MaskerReportFlushThread") {
            if (!SheetsReportSender.isNetworkAvailable(context)) return@thread
            val pending = ReportQueueStorage.loadPending(context)
            for ((file, report) in pending) {
                if (SheetsReportSender.sendReportSync(report)) {
                    ReportQueueStorage.remove(file)
                }
            }
        }
    }
}
