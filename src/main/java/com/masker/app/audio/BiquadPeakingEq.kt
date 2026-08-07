package com.masker.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * فیلتر EQ نوع Peaking بر اساس فرمول‌های RBJ Audio Cookbook؛ برای اکولایزر گرافیکی چندباندهٔ
 * تب پلی‌لیست استفاده می‌شود — هر باند شدت صدا را دور فرکانس مرکزی خودش کم یا زیاد می‌کند
 * (بر خلاف [BiquadBandPass] که فقط عبور می‌دهد، یا [NotchFilter] که کاملاً حذف می‌کند).
 */
class BiquadPeakingEq(sampleRate: Int, centerFreqHz: Double, gainDb: Float, q: Double = 1.0) {

    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    private val sr = sampleRate
    private val freq = centerFreqHz
    private val qVal = q

    init {
        setGain(gainDb)
    }

    /** تغییر شدت (dB) این باند؛ قابل فراخوانی حین پخش زنده برای اعمال آنی تغییر اسلایدر */
    fun setGain(newGainDb: Float) {
        val a = 10.0.pow(newGainDb / 40.0)
        val w0 = 2.0 * PI * freq / sr
        val alpha = sin(w0) / (2.0 * qVal)
        val cosw0 = cos(w0)

        val a0 = 1.0 + alpha / a
        b0 = (1.0 + alpha * a) / a0
        b1 = (-2.0 * cosw0) / a0
        b2 = (1.0 - alpha * a) / a0
        a1 = (-2.0 * cosw0) / a0
        a2 = (1.0 - alpha / a) / a0
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
