package com.masker.app.playlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.masker.app.storage.MaskerStorage
import java.io.File
import java.io.FileOutputStream

/**
 * استخراج و ذخیره تصویر کاور/آلبوم (Embedded Album Art) فایل‌های صوتی پلی‌لیست، برای نمایش
 * به‌عنوان آیکن گرد کوچک کنار نام هر آهنگ. تصویرها در یک زیرپوشه پنهان کنار خود فایل‌های
 * صوتی (Documents/Masker/Playlist/.thumbnails) نگه‌داری می‌شوند.
 */
object PlaylistThumbnails {
    private const val THUMBNAIL_SIZE = 128
    private const val THUMBNAIL_SUBDIR = ".thumbnails"

    fun thumbnailFile(context: Context, trackId: String): File {
        return File(File(MaskerStorage.playlistDir(context), THUMBNAIL_SUBDIR), "$trackId.png")
    }

    /** رمزگشایی فایل صوتی و تصویر کاور کاری سنگین است؛ باید از یک ترد پس‌زمینه فراخوانی شود */
    fun extractAndSave(context: Context, audioFile: File, trackId: String) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)
            val artBytes = retriever.embeddedPicture
            retriever.release()
            if (artBytes == null) return

            val original = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size) ?: return
            val scaled = Bitmap.createScaledBitmap(original, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)

            val outFile = thumbnailFile(context, trackId)
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 90, out) }
        } catch (_: Exception) {
        }
    }

    fun loadBitmap(context: Context, trackId: String): Bitmap? {
        val file = thumbnailFile(context, trackId)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteThumbnail(context: Context, trackId: String) {
        try {
            thumbnailFile(context, trackId).delete()
        } catch (_: Exception) {
        }
    }
}
