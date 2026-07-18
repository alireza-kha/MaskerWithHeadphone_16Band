package com.masker.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * فیلتر میان‌گذر (Band-Pass) بر اساس فرمول‌های RBJ Audio Cookbook.
 * هر باند از نویز سفید با یکی از این فیلترها عبور می‌کند تا فقط
 * محدوده فرکانسی مورد نظر (مثلاً ۱۰۰۰ هرتز) باقی بماند.
 */
class BiquadBandPass(sampleRate: Int, centerFreqHz: Double, q: Double = 1.4) {

    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // حافظه فیلتر (نمونه‌های قبلی ورودی و خروجی)
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    init {
        val w0 = 2.0 * PI * centerFreqHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosw0 = cos(w0)

        val rawB0 = alpha
        val rawB1 = 0.0
        val rawB2 = -alpha
        val a0 = 1.0 + alpha
        val rawA1 = -2.0 * cosw0
        val rawA2 = 1.0 - alpha

        b0 = rawB0 / a0
        b1 = rawB1 / a0
        b2 = rawB2 / a0
        a1 = rawA1 / a0
        a2 = rawA2 / a0
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
