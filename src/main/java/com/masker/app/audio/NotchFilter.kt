package com.masker.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh

/**
 * فیلتر Notch (Band-Reject) بر اساس فرمول‌های RBJ Audio Cookbook.
 * ایده این فیلتر برگرفته از پژوهش‌های «نویز notch‌شده» (Notched Sound Therapy) است که
 * اولین بار توسط Pantev و همکاران (۲۰۱۲) با استفاده از مگنتوانسفالوگرافی مطرح شد: با حذف
 * یک باند باریک دقیقاً روی فرکانس وزوز گوش کاربر از نویز، از طریق مهار جانبی (lateral
 * inhibition) نورون‌های همسایه، فعالیت قشر شنوایی مرتبط با آن فرکانس کاهش می‌یابد.
 *
 * پهنای باند (bandwidthOctaves) بر حسب اکتاو تعریف می‌شود که مرسوم‌ترین واحد در ادبیات
 * علمی مربوطه است (معمولاً بین ۰٫۲۵ تا ۲ اکتاو).
 */
class NotchFilter(sampleRate: Int, centerFreqHz: Double, bandwidthOctaves: Float) {

    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    init {
        val safeFreq = centerFreqHz.coerceIn(60.0, sampleRate / 2.5)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val sinW0 = sin(w0)
        val safeBandwidth = bandwidthOctaves.coerceIn(0.05f, 4f)
        val alpha = sinW0 * sinh((ln(2.0) / 2.0) * safeBandwidth * (w0 / sinW0))
        val cosw0 = cos(w0)

        val a0 = 1.0 + alpha
        b0 = 1.0 / a0
        b1 = (-2.0 * cosw0) / a0
        b2 = 1.0 / a0
        a1 = (-2.0 * cosw0) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun process(input: Double): Double {
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }

    fun reset() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }
}
