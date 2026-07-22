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
 * و محور عمودی سطح شنوایی — درست مثل اودیوگرام‌های بالینی، عدد ۰ (بهترین شنوایی) بالای
 * نمودار و عدد ۸۰ (نیاز به بلندترین صدا) پایین نمودار نوشته می‌شود.
 *
 * نمادها مطابق قرارداد متعارف در اودیوگرام‌های بالینی رسم می‌شوند:
 *  - گوش راست، بدون ماسک: دایره قرمز (○)
 *  - گوش چپ، بدون ماسک: ضربدر آبی (×)
 *  - گوش راست، با ماسک (بررسی اثر سایه، خودکار یا کامل): مثلث نارنجی (△)
 *  - گوش چپ، با ماسک (بررسی اثر سایه، خودکار یا کامل): مربع بنفش (□)
 *  - حلقه توخالی نارنجی دور یک نقطه: در آن نقطه، بلافاصله پیش از آزمودنش یک «کوشش کنترلی»
 *    (بدون پخش صدا) هم پاسخ مثبت کاذب داشته؛ ممکن است آستانه آن نقطه کاملاً قابل‌اعتماد نباشد.
 */
class AudiogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private enum class Symbol { CIRCLE, CROSS, TRIANGLE, SQUARE }

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
    // رنگ‌های تازه و کاملاً متفاوت از دایره/ضربدر معمولی، تا خطوط ماسک‌شده (چه بررسی جزئی
    // خودکار، چه آزمون کامل اثر سایه) به‌وضوح از خطوط بدون‌ماسک قابل تشخیص باشند
    private val rightMaskedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF8F00") // نارنجی/کهربایی
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val leftMaskedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7B1FA2") // بنفش
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val reliabilityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FB8C00")
        strokeWidth = 3f
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

        // خطوط شبکه افقی (سطوح شنوایی) + برچسب؛ برچسب برعکسِ مقدار داخلی «تضعیف» است، تا
        // مثل اودیوگرام‌های بالینی، ۰ بالای نمودار و ۸۰ پایین نمودار نوشته شود
        val steps = 8
        for (i in 0..steps) {
            val atten = minAttenuation + (maxAttenuation - minAttenuation) * i / steps
            val y = attenuationToY(atten, chartTop, chartBottom)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            val displayLabel = (maxAttenuation - atten).toInt().toString()
            canvas.drawText(displayLabel, marginLeft - 50f, y + 8f, textPaint)
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

        drawEarLine(canvas, r.frequenciesHz, r.rightThresholdsDb, minFreq, maxFreq, chartLeft, chartRight, chartTop, chartBottom, rightPaint, Symbol.CIRCLE)
        drawEarLine(canvas, r.frequenciesHz, r.leftThresholdsDb, minFreq, maxFreq, chartLeft, chartRight, chartTop, chartBottom, leftPaint, Symbol.CROSS)

        drawReliabilityFlags(canvas, r.frequenciesHz, r.rightThresholdsDb, r.unreliableRightFreqIndices, minFreq, maxFreq, chartLeft, chartRight, chartTop, chartBottom)
        drawReliabilityFlags(canvas, r.frequenciesHz, r.leftThresholdsDb, r.unreliableLeftFreqIndices, minFreq, maxFreq, chartLeft, chartRight, chartTop, chartBottom)

        r.rightMaskedThresholdsDb?.let {
            drawEarLine(canvas, r.frequenciesHz, it, minFreq, maxFreq, chartLeft, chartRight, chartTop, chartBottom, rightMaskedPaint, Symbol.TRIANGLE)
        }
        r.leftMaskedThresholdsDb?.let {
            drawEarLine(canvas, r.frequenciesHz, it, minFreq, maxFreq, chartLeft, chartRight, chartTop, chartBottom, leftMaskedPaint, Symbol.SQUARE)
        }
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
        symbol: Symbol
    ) {
        val path = Path()
        var started = false
        val r = 14f

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

            when (symbol) {
                Symbol.CIRCLE -> canvas.drawCircle(x, y, r, paint)
                Symbol.CROSS -> {
                    canvas.drawLine(x - r, y - r, x + r, y + r, paint)
                    canvas.drawLine(x - r, y + r, x + r, y - r, paint)
                }
                Symbol.TRIANGLE -> {
                    val trianglePath = Path()
                    trianglePath.moveTo(x, y - r)
                    trianglePath.lineTo(x - r, y + r)
                    trianglePath.lineTo(x + r, y + r)
                    trianglePath.close()
                    val fillPaint = Paint(paint)
                    fillPaint.style = Paint.Style.STROKE
                    canvas.drawPath(trianglePath, fillPaint)
                }
                Symbol.SQUARE -> {
                    val squarePaint = Paint(paint)
                    squarePaint.style = Paint.Style.STROKE
                    canvas.drawRect(x - r, y - r, x + r, y + r, squarePaint)
                }
            }
        }

        canvas.drawPath(path, paint)
    }

    /** حلقه توخالی نارنجی دور نقاطی که با یک کوشش کنترلی ناموفق (پاسخ مثبت کاذب) همراه بودند */
    private fun drawReliabilityFlags(
        canvas: Canvas,
        frequencies: List<Double>,
        thresholds: FloatArray,
        flaggedIndices: Set<Int>,
        minFreq: Double,
        maxFreq: Double,
        chartLeft: Float,
        chartRight: Float,
        chartTop: Float,
        chartBottom: Float
    ) {
        if (flaggedIndices.isEmpty()) return
        for (i in frequencies.indices) {
            if (i !in flaggedIndices) continue
            val level = thresholds.getOrNull(i) ?: continue
            if (level.isNaN()) continue
            val x = frequencyToX(frequencies[i], minFreq, maxFreq, chartLeft, chartRight)
            val y = attenuationToY(level, chartTop, chartBottom)
            canvas.drawCircle(x, y, 22f, reliabilityPaint)
        }
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
