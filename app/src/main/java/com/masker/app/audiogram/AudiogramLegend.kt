package com.masker.app.audiogram

import android.content.Context
import com.masker.app.R

/** متن راهنمای نمودار اودیوگرام، بسته به اینکه چه داده‌هایی در نتیجه موجود است */
object AudiogramLegend {
    fun build(context: Context, result: AudiogramResult): String {
        val hasMasked = result.rightMaskedThresholdsDb != null || result.leftMaskedThresholdsDb != null
        val hasUnreliable = result.unreliableRightFreqIndices.isNotEmpty() || result.unreliableLeftFreqIndices.isNotEmpty()

        val text = StringBuilder()
        text.append(context.getString(if (hasMasked) R.string.audiogram_legend_with_masked else R.string.audiogram_legend))
        if (hasUnreliable) {
            text.append("\n").append(context.getString(R.string.audiogram_legend_unreliable))
        }
        return text.toString()
    }
}
