package com.masker.app.audiogram

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.masker.app.R
import com.masker.app.storage.MaskerStorage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * رسم یک view (شامل نمودار اودیوگرام) به بیت‌مپ و ذخیره/اشتراک‌گذاری آن به‌صورت PNG در پوشه
 * Documents/Masker/Audiograms؛ هم از صفحه آزمون (بلافاصله پس از پایان آزمون) و هم از صفحه
 * مشاهده سابقه (برای آزمون‌های قبلی) استفاده می‌شود.
 */
object AudiogramImageExporter {

    private fun renderBitmap(view: View): Bitmap? {
        if (view.width == 0 || view.height == 0) return null
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return bitmap
    }

    fun saveAndMaybeShare(activity: Activity, captureView: View, share: Boolean) {
        val bitmap = renderBitmap(captureView)
        if (bitmap == null) {
            Toast.makeText(activity, R.string.save_failed, Toast.LENGTH_LONG).show()
            return
        }

        val outDir = MaskerStorage.audiogramsDir(activity)
        val fileName = "audiogram_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val outFile = File(outDir, fileName)

        try {
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.save_failed, Toast.LENGTH_LONG).show()
            return
        }

        if (share) {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share)))
        } else {
            Toast.makeText(
                activity,
                activity.getString(R.string.save_success) + "\n" + outFile.absolutePath,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
