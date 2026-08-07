package com.masker.app.wearsync

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.masker.app.audiogram.AudiogramStorage
import com.masker.app.service.PlaybackService
import org.json.JSONArray
import org.json.JSONObject

/**
 * ثابت‌های پروتکل همگام‌سازی با اپ ساعت هوشمند (ریپوی masker_AndroidWatch) از طریق
 * Wear Data Layer API. این مقادیر باید دقیقاً با com.masker.app.watch.sync.MaskerSyncProtocol
 * در ریپوی ساعت یکسان بمانند؛ در صورت تغییر یکی، باید دیگری هم به‌روزرسانی شود.
 */
object MaskerWearSyncProtocol {
    const val STATE_PATH = "/masker/state"
    const val STATE_KEY = "state_json"
    const val REQUEST_SYNC_PATH = "/masker/request_sync"
}

/**
 * ارسال تنظیمات فعلی تب «ماسکر نویزی» (۱۶ باند، ولوم‌ها، نوچ، مدولاسیون) به‌همراه آخرین
 * اودیوگرام انتخاب‌شده کاربر، به اپ ساعت هوشمند از طریق یک DataItem واحد
 * (مسیر [MaskerWearSyncProtocol.STATE_PATH]). گوگل پلی سرویسز خودش این DataItem را با هر
 * ساعت متصل/جفت‌شده همگام می‌کند؛ نیازی به بررسی دستی اتصال نیست.
 */
object MaskerWearSyncManager {

    private const val DEBOUNCE_DELAY_MS = 400L
    private val handler = Handler(Looper.getMainLooper())
    private var pendingPush: Runnable? = null

    /** ارسال فوری وضعیت فعلی؛ برای پاسخ به درخواست همگام‌سازی ساعت یا اولین بار باز شدن اپ */
    fun pushStateNow(context: Context) {
        pendingPush?.let { handler.removeCallbacks(it) }
        pendingPush = null

        val appContext = context.applicationContext
        val engine = PlaybackService.noiseEngine
        val audiogram = AudiogramStorage.loadSelectedResult(appContext)

        val json = JSONObject()
        json.put("timestamp", System.currentTimeMillis())

        val bandsArr = JSONArray()
        for (gain in engine.bandGains) bandsArr.put(gain.toDouble())
        json.put("bandGains", bandsArr)

        json.put("masterVolume", engine.masterVolume.toDouble())
        json.put("leftVolume", engine.leftVolume.toDouble())
        json.put("rightVolume", engine.rightVolume.toDouble())

        json.put("notchEnabled", engine.notchEnabled)
        json.put("notchFrequencyHz", engine.notchFrequencyHz)
        json.put("notchWidthOctaves", engine.notchWidthOctaves.toDouble())

        json.put("modulationEnabled", engine.modulationEnabled)
        json.put("modulationDepth", engine.modulationDepth.toDouble())

        if (audiogram != null) json.put("audiogram", audiogram.toJson())

        val request = PutDataMapRequest.create(MaskerWearSyncProtocol.STATE_PATH).apply {
            dataMap.putString(MaskerWearSyncProtocol.STATE_KEY, json.toString())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(appContext).putDataItem(request)
    }

    /**
     * نسخه با تأخیر کوتاه، برای فراخوانی از داخل شنونده‌های اسلایدرهایی که ممکن است چند بار
     * در ثانیه صدا زده شوند (مثلاً هنگام کشیدن اسلایدر شدت یک باند)، تا از ارسال بیش‌ازحد به
     * Data Layer جلوگیری شود.
     */
    fun pushStateDebounced(context: Context) {
        val appContext = context.applicationContext
        pendingPush?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { pushStateNow(appContext) }
        pendingPush = runnable
        handler.postDelayed(runnable, DEBOUNCE_DELAY_MS)
    }
}

/**
 * دریافت درخواست همگام‌سازی فوری از اپ ساعت (وقتی کاربر ساعت را باز می‌کند یا دکمه
 * «همگام‌سازی با گوشی» را می‌زند) و پاسخ با آخرین وضعیت ماسکر نویزی.
 */
class WearRequestListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == MaskerWearSyncProtocol.REQUEST_SYNC_PATH) {
            MaskerWearSyncManager.pushStateNow(applicationContext)
        }
    }
}
