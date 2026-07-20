package com.masker.app.audiogram

import kotlin.math.ln

/**
 * تولید مجموعه‌ای از میزان‌های پیشنهادی برای باندهای نویز ماسکر، بر اساس نتیجه آزمون
 * اودیوگرام کاربر — ایده «نویز شکل‌داده‌شده بر اساس افت شنوایی» (Hearing-Loss Matched /
 * Enriched Acoustic Environment) که طبق چند مطالعه اخیر (Cuesta et al., 2022 — نرخ تسکین
 * ۹۶٪ در ۸۰ نفر از ۸۳ بیمار پس از ۴ ماه؛ Fernandez-Ledesma et al., 2025 — مقایسه مستقیم با
 * نویز سفید ساده) یکی از مؤثرترین رویکردهای شخصی‌سازی‌شده در میان روش‌های ماسکینگ صوتی
 * شناخته شده است: نواحی فرکانسی‌ای که کاربر در آن‌ها افت شنوایی بیشتری دارد (که معمولاً با
 * ناحیه فرکانسی وزوز هم‌پوشانی دارد)، با شدت بیشتری ماسک می‌شوند.
 */
object AudiogramNoiseShaper {

    private const val MIN_GAIN = 0.3f
    private const val MAX_GAIN = 1.0f
    private const val MAX_ATTENUATION = 80f

    fun computeBandGains(result: AudiogramResult, bandFrequencies: DoubleArray): FloatArray {
        val measuredFreqs = result.frequenciesHz
        val avgThresholds = FloatArray(measuredFreqs.size) { i ->
            val r = result.rightThresholdsDb.getOrNull(i)?.takeIf { !it.isNaN() }
            val l = result.leftThresholdsDb.getOrNull(i)?.takeIf { !it.isNaN() }
            when {
                r != null && l != null -> (r + l) / 2f
                r != null -> r
                l != null -> l
                else -> Float.NaN
            }
        }

        return FloatArray(bandFrequencies.size) { i ->
            val threshold = interpolateThreshold(bandFrequencies[i], measuredFreqs, avgThresholds)
            thresholdToGain(threshold)
        }
    }

    /** آستانه پایین‌تر (نیاز به صدای بلندتر = افت شنوایی بیشتر) → شدت ماسکینگ بیشتر */
    private fun thresholdToGain(thresholdDb: Float): Float {
        if (thresholdDb.isNaN()) return 0.6f // مقدار پیش‌فرض در نبود داده برای آن فرکانس
        val badness = ((MAX_ATTENUATION - thresholdDb) / MAX_ATTENUATION).coerceIn(0f, 1f)
        return MIN_GAIN + badness * (MAX_GAIN - MIN_GAIN)
    }

    /** میان‌یابی خطی روی مقیاس لگاریتمی فرکانس بین نزدیک‌ترین نقاط اندازه‌گیری‌شده */
    private fun interpolateThreshold(freq: Double, measuredFreqs: List<Double>, thresholds: FloatArray): Float {
        if (measuredFreqs.isEmpty()) return Float.NaN
        if (freq <= measuredFreqs.first()) return thresholds.first()
        if (freq >= measuredFreqs.last()) return thresholds.last()

        for (i in 0 until measuredFreqs.size - 1) {
            val f1 = measuredFreqs[i]
            val f2 = measuredFreqs[i + 1]
            if (freq in f1..f2) {
                val t1 = thresholds[i]
                val t2 = thresholds[i + 1]
                if (t1.isNaN()) return t2
                if (t2.isNaN()) return t1
                val logF1 = ln(f1)
                val logF2 = ln(f2)
                val logF = ln(freq)
                val ratio = if (logF2 > logF1) (logF - logF1) / (logF2 - logF1) else 0.0
                return (t1 + (t2 - t1) * ratio).toFloat()
            }
        }
        return thresholds.last()
    }
}
