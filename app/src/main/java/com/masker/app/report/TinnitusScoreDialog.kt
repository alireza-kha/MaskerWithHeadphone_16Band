package com.masker.app.report

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.masker.app.R

/**
 * پنجره مشترک پرسیدن نمره ذهنی شدت وزوز هر گوش (۰ تا ۱۰)، هم از صفحه آزمون اودیوگرام
 * (هر بار که آزمون موقتاً متوقف می‌شود) و هم از صفحه اصلی (هر بار که پخش ماسکر متوقف
 * می‌شود) استفاده می‌شود. با زدن «ثبت»، [onSubmit] با نمرات وارد‌شده فراخوانی می‌شود.
 */
object TinnitusScoreDialog {

    fun show(context: Context, onSubmit: (leftScore: Int?, rightScore: Int?) -> Unit) {
        try {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_tinnitus_score, null)
            val leftScoreEditText = dialogView.findViewById<EditText>(R.id.leftScoreEditText)
            val rightScoreEditText = dialogView.findViewById<EditText>(R.id.rightScoreEditText)

            AlertDialog.Builder(context)
                .setTitle(R.string.tinnitus_score_dialog_title)
                .setView(dialogView)
                .setCancelable(true)
                .setPositiveButton(R.string.submit) { _, _ ->
                    val left = leftScoreEditText.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(0, 10)
                    val right = rightScoreEditText.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(0, 10)
                    onSubmit(left, right)
                }
                .setNegativeButton(R.string.skip, null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("TinnitusScoreDialog", "Failed to show tinnitus score dialog", e)
            Toast.makeText(context, "خطا در نمایش پنجره امتیاز: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
