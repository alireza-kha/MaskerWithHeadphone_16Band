package com.masker.app.storage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * ذخیره‌سازی دائمی برنامه در پوشه عمومی «Documents/Masker» (به‌جای حافظه اختصاصی برنامه)
 * تا سوابق آزمون‌های شنوایی، عکس‌های اودیوگرام، فایل‌های صوتی ذخیره‌شده و پلی‌لیست موسیقی،
 * با حذف نصب یا نصب مجدد برنامه از بین نروند و در باز شدن بعدی دوباره از همان‌جا خوانده شوند.
 *
 * در اندروید ۱۱ به بالا (API 30+) دسترسی کامل با مجوز ویژه «دسترسی به همه فایل‌ها»
 * (MANAGE_EXTERNAL_STORAGE) گرفته می‌شود؛ در نسخه‌های قدیمی‌تر با مجوز معمول
 * WRITE_EXTERNAL_STORAGE. تا وقتی این مجوز داده نشده، به‌صورت خودکار از حافظه اختصاصی
 * برنامه استفاده می‌شود تا برنامه در همان حین هم از کار نیفتد.
 */
object MaskerStorage {

    const val REQUEST_CODE_LEGACY_STORAGE = 501
    private const val FOLDER_NAME = "Masker"

    const val SUB_AUDIOGRAMS = "Audiograms"
    const val SUB_HISTORY = "History"
    const val SUB_SOUNDS = "Sounds"
    const val SUB_PLAYLIST = "Playlist"

    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
            } catch (e: Exception) {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_LEGACY_STORAGE
            )
        }
    }

    /** ریشه پوشه Documents/Masker؛ در نبود مجوز، حافظه اختصاصی برنامه به‌عنوان جایگزین موقت */
    fun rootDir(context: Context): File {
        if (hasPermission(context)) {
            try {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    FOLDER_NAME
                )
                dir.mkdirs()
                if (dir.isDirectory) return dir
            } catch (_: Exception) {
            }
        }
        val fallback = File(context.getExternalFilesDir(null), FOLDER_NAME)
        fallback.mkdirs()
        return fallback
    }

    fun subDir(context: Context, name: String): File {
        val dir = File(rootDir(context), name)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun audiogramsDir(context: Context): File = subDir(context, SUB_AUDIOGRAMS)
    fun historyDir(context: Context): File = subDir(context, SUB_HISTORY)
    fun soundsDir(context: Context): File = subDir(context, SUB_SOUNDS)
    fun playlistDir(context: Context): File = subDir(context, SUB_PLAYLIST)
}
