package com.masker.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.masker.app.R

/**
 * جایگزین Toast برای همه پیام‌های برنامه: به‌جای پیام گذرا و ناپدیدشونده، یک پنجره
 * (AlertDialog) با دکمه «تأیید» نشان می‌دهد که کاربر باید آن را ببندد.
 */
object MessageDialog {
    fun show(context: Context, message: String) {
        AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    fun show(context: Context, @StringRes messageResId: Int) {
        show(context, context.getString(messageResId))
    }
}
