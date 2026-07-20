package com.masker.app.audiogram

/**
 * موتور منطقی آزمون شنوایی؛ پیاده‌سازی ساده‌شده روش «هیوز-وستلیک» (Hughson-Westlake) که
 * روش استاندارد و رایج علمی برای یافتن آستانه شنوایی در سنجش شنوایی بالینی است
 * (کاهش ۱۰ واحدی پس از شنیدن، افزایش ۵ واحدی پس از نشنیدن، و تأیید آستانه با حداقل
 * دو پاسخ مثبت هم‌سطح در مسیر صعودی).
 *
 * نکته: چون این آزمون روی خروجی هدفون کاربر و بدون کالیبراسیون آزمایشگاهی انجام می‌شود،
 * نتیجه یک سنجش نسبی و شخصی است، نه معادل تشخیص بالینی dB HL.
 */
class HearingTestController(
    private val frequenciesHz: List<Double>,
    private val onPlayTone: (ear: Ear, freqHz: Double, attenuationDb: Float) -> Unit,
    private val onProgress: (ear: Ear, freqIndex: Int, totalFreq: Int) -> Unit,
    private val onFinished: (AudiogramResult) -> Unit
) {
    companion object {
        private const val START_ATTENUATION = 20f   // شروع از سطحی نسبتاً بلند و واضح
        private const val MIN_ATTENUATION = 0f       // بلندترین حالت ممکن
        private const val MAX_ATTENUATION = 80f      // آرام‌ترین سطح قابل آزمایش
        private const val MAX_TRIALS_PER_FREQUENCY = 12
    }

    private enum class Phase { AFTER_HEARD, AFTER_NOT_HEARD }

    private val rightThresholds = FloatArray(frequenciesHz.size) { Float.NaN }
    private val leftThresholds = FloatArray(frequenciesHz.size) { Float.NaN }

    private val earQueue = mutableListOf(Ear.RIGHT, Ear.LEFT)
    private var currentEar = Ear.RIGHT
    private var freqIndex = 0

    private var currentLevel = START_ATTENUATION
    private var phase = Phase.AFTER_HEARD
    private val hitsAtLevel = mutableMapOf<Float, Int>()
    private var trialsThisFrequency = 0

    fun start() {
        currentEar = earQueue.removeAt(0)
        freqIndex = 0
        beginFrequency()
    }

    private fun beginFrequency() {
        currentLevel = START_ATTENUATION
        phase = Phase.AFTER_HEARD
        hitsAtLevel.clear()
        trialsThisFrequency = 0
        onProgress(currentEar, freqIndex, frequenciesHz.size)
        presentTone()
    }

    private fun presentTone() {
        onPlayTone(currentEar, frequenciesHz[freqIndex], currentLevel)
    }

    /** باید پس از هر بار پخش، با پاسخ کاربر (شنیدم / نشنیدم) صدا زده شود */
    fun onResponse(heard: Boolean) {
        trialsThisFrequency++

        if (phase == Phase.AFTER_HEARD) {
            if (heard) {
                currentLevel = (currentLevel + 10f).coerceAtMost(MAX_ATTENUATION)
                if (currentLevel >= MAX_ATTENUATION) {
                    finalizeThreshold(MAX_ATTENUATION)
                    return
                }
            } else {
                phase = Phase.AFTER_NOT_HEARD
                currentLevel = (currentLevel - 5f).coerceAtLeast(MIN_ATTENUATION)
            }
        } else { // AFTER_NOT_HEARD
            if (heard) {
                val count = (hitsAtLevel[currentLevel] ?: 0) + 1
                hitsAtLevel[currentLevel] = count
                if (count >= 2) {
                    finalizeThreshold(currentLevel)
                    return
                }
                // بازگشت به حالت پرصداتر و صعود دوباره برای تأیید
                phase = Phase.AFTER_HEARD
                currentLevel = (currentLevel + 10f).coerceAtMost(MAX_ATTENUATION)
            } else {
                if (currentLevel <= MIN_ATTENUATION) {
                    // حتی در بلندترین حالت هم پاسخی دریافت نشد
                    finalizeThreshold(Float.NaN)
                    return
                }
                currentLevel = (currentLevel - 5f).coerceAtLeast(MIN_ATTENUATION)
            }
        }

        if (trialsThisFrequency >= MAX_TRIALS_PER_FREQUENCY) {
            // تعداد تلاش‌ها از حد مجاز گذشت؛ بهترین برآورد موجود را ثبت می‌کنیم
            val bestGuess = hitsAtLevel.keys.maxOrNull() ?: currentLevel
            finalizeThreshold(bestGuess)
            return
        }

        presentTone()
    }

    private fun finalizeThreshold(level: Float) {
        if (currentEar == Ear.RIGHT) rightThresholds[freqIndex] = level
        else leftThresholds[freqIndex] = level

        freqIndex++
        if (freqIndex < frequenciesHz.size) {
            beginFrequency()
        } else if (earQueue.isNotEmpty()) {
            currentEar = earQueue.removeAt(0)
            freqIndex = 0
            beginFrequency()
        } else {
            onFinished(AudiogramResult(frequenciesHz, rightThresholds, leftThresholds, System.currentTimeMillis()))
        }
    }
}
