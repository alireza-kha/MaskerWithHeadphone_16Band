package com.masker.app.wearsync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

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
