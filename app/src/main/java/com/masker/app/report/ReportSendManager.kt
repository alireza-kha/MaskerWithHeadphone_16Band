package com.masker.app.report

import android.content.Context
import kotlin.concurrent.thread

/**
 * هماهنگ‌کننده ارسال گزارش‌ها: ابتدا تلاش برای ارسال فوری می‌کند؛ اگر اینترنت نبود یا ارسال
 * ناموفق بود، گزارش را در صف محلی نگه می‌دارد. تابع [flushPending] هم برای تلاش مجدد روی
 * گزارش‌های صف‌شده استفاده می‌شود (مثلاً هر بار برنامه باز می‌شود).
 */
object ReportSendManager {

    /** نتیجه تلاش برای ارسال گزارش، برای نمایش پیام دقیق به کاربر (به‌جای همیشه یک پیام ثابت) */
    enum class SendOutcome { SENT, NO_NETWORK, SEND_FAILED }

    /** تلاش فوری برای ارسال؛ نتیجه دقیق از طریق onResult روی همان ترد پس‌زمینه اعلام می‌شود */
    fun sendOrQueue(context: Context, report: PatientReport, onResult: (SendOutcome) -> Unit) {
        thread(name = "MaskerReportSendThread") {
            val outcome = if (!SheetsReportSender.isNetworkAvailable(context)) {
                ReportQueueStorage.enqueue(context, report)
                SendOutcome.NO_NETWORK
            } else if (SheetsReportSender.sendReportSync(report)) {
                SendOutcome.SENT
            } else {
                ReportQueueStorage.enqueue(context, report)
                SendOutcome.SEND_FAILED
            }
            onResult(outcome)
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
