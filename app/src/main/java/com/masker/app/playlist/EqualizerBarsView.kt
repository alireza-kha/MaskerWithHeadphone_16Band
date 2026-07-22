package com.masker.app.playlist

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * انیمیشن ساده «رقص نور» (نوارهای اکولایزر متحرک)، برای نشان‌دادن اینکه کدام آهنگ پلی‌لیست
 * در حال حاضر در حال پخش است.
 */
class EqualizerBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barCount = 3
    private val barHeights = FloatArray(barCount) { 0.3f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D6B")
        style = Paint.Style.FILL
    }
    private val animators = mutableListOf<ValueAnimator>()

    fun start() {
        if (animators.isNotEmpty()) return
        for (i in 0 until barCount) {
            val animator = ValueAnimator.ofFloat(0.25f, 1f).apply {
                duration = 380L + i * 90L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                startDelay = i * 120L
                addUpdateListener {
                    barHeights[i] = it.animatedValue as Float
                    invalidate()
                }
            }
            animators.add(animator)
            animator.start()
        }
    }

    fun stop() {
        animators.forEach { it.cancel() }
        animators.clear()
        for (i in barHeights.indices) barHeights[i] = 0.3f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val gap = width * 0.15f
        val barWidth = (width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val barHeight = height * barHeights[i]
            val left = i * (barWidth + gap)
            val top = height - barHeight
            canvas.drawRect(left, top, left + barWidth, height.toFloat(), paint)
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
