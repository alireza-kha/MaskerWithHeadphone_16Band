package com.masker.app.playlist

/**
 * یک آهنگ در پلی‌لیست موسیقی برنامه. فایل واقعی در Documents/Masker/Playlist با نام
 * [fileName] ذخیره شده است؛ [title] فقط برای نمایش در لیست استفاده می‌شود.
 */
data class PlaylistTrack(
    val id: String,
    val fileName: String,
    val title: String,
    val addedAtMillis: Long
)
