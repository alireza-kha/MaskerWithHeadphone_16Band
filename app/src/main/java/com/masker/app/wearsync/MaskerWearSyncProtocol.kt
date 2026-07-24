package com.masker.app.wearsync

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
