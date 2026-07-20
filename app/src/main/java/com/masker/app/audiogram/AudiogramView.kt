package com.masker.app.audiogram

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.ln

/**
 * رسم اودیوگرام: محور افقی فرکانس (مقیاس لگاریتمی، مطابق استاندارد اودیوگرام‌های بالینی)
 * و محور عمودی سطح شنوایی نسبی. گوش راست با دایره قرمز و گوش چپ با ضربدر آبی نمایش
 * داده می‌شود (رنگ‌بندی متعارف در اودیوگرام‌های بالینی).
 */
class AudiogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var result: AudiogramResult? = null

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val marginLeft = 90f
    private val marginRight = 40f
    private val marginTop = 40f
    private val marginBottom = 70f

    private val minAttenuation = 0f
    private val maxAttenuation = 80f

    fun setResult(newResult: AudiogramResult) {
        result = newResult
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        val r = result ?: return
        if (r.frequenciesHz.isEmpty()) return

        val chartLeft = marginLeft
        val chartRight = width - marginRight
        val chartTop = marginTop
        val chartBottom = height - marginBottom

        // خطوط شبکه افقی (سطوح شنوایی) + برچسب
        val steps = 8
        for (i in 0..steps) {
            val atten = minAttenuation + (maxAttenuation - minAttenuation) * i / steps
            val y = attenuationToY(atten, chartTop, chartBottom)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            canvas.drawText(atten.toInt().toString(), marginLeft - 50f, y + 8f, textPaint)
        }

        // محورها
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        // برچسب فرکانس‌ها روی محور افقی
        val minFreq = r.frequenciesHz.minOrNull() ?: 250.0
        val maxFreq = r.frequenciesHz.maxOrNull() ?: 8000.0
        for (freq in r.frequenciesHz) {
            val x = frequencyToX(freq, minFreq, maxFreq, chartLeft, chartRight)
            canvas.drawLine(x, chartTop, x, chartBottom, gridPaint)
            val label = if (freq >= 1000) "${(freq / 1000).toInt()}k" else freq.toInt().toString()
            canvas.drawText(label, x, chartBottom + 40f, textPaint)
        }

        drawEarLine(canvas, r.frequenciesHz, r.rightThresholdsDb, minFreq, maxFreq, chartLeft, chartRight, chartTop, chartBottom, rightPaint, isRight = true)
        drawEarLine(canvas, r.frequenciesHz, r.leftThresholdsDb, minFreq, maxFreq, chartLeft, chartRight, chartTop, chartBottom, leftPaint, isRight = false)
    }

    private fun drawEarLine(
        canvas: Canvas,
        frequencies: List<Double>,
        thresholds: FloatArray,
        minFreq: Double,
        maxFreq: Double,
        chartLeft: Float,
        chartRight: Float,
        chartTop: Float,
        chartBottom: Float,
        paint: Paint,
        isRight: Boolean
    ) {
        val path = Path()
        var started = false
        val symbolRadius = 14f

        for (i in frequencies.indices) {
            val level = thresholds.getOrNull(i) ?: continue
            if (level.isNaN()) continue

            val x = frequencyToX(frequencies[i], minFreq, maxFreq, chartLeft, chartRight)
            val y = attenuationToY(level, chartTop, chartBottom)

            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }

            if (isRight) {
                canvas.drawCircle(x, y, symbolRadius, paint)
            } else {
                canvas.drawLine(x - symbolRadius, y - symbolRadius, x + symbolRadius, y + symbolRadius, paint)
                canvas.drawLine(x - symbolRadius, y + symbolRadius, x + symbolRadius, y - symbolRadius, paint)
            }
        }

        canvas.drawPath(path, paint)
    }

    private fun attenuationToY(atten: Float, chartTop: Float, chartBottom: Float): Float {
        // بالای نمودار = آستانه بالا (شنوایی بهتر)، پایین نمودار = آستانه پایین (نیاز به صدای بلندتر)
        val ratio = (maxAttenuation - atten) / (maxAttenuation - minAttenuation)
        return chartTop + ratio * (chartBottom - chartTop)
    }

    private fun frequencyToX(freq: Double, minFreq: Double, maxFreq: Double, chartLeft: Float, chartRight: Float): Float {
        val logMin = ln(minFreq)
        val logMax = ln(maxFreq)
        val logF = ln(freq)
        val ratio = if (logMax > logMin) (logF - logMin) / (logMax - logMin) else 0.0
        return (chartLeft + ratio * (chartRight - chartLeft)).toFloat()
    }
}
