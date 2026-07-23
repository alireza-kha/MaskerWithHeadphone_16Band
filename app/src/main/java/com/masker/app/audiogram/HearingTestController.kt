package com.masker.app.audiogram

/**
 * موتور منطقی آزمون شنوایی؛ برای هر بلوک (یک گوش + یک فرکانس مشخص) از روش استاندارد
 * «هیوز-وستلیک» (Hughson-Westlake) استفاده می‌کند (کاهش ۱۰ واحدی پس از شنیدن، افزایش ۴
 * واحدی پس از نشنیدن، و تأیید آستانه با حداقل دو پاسخ مثبت هم‌سطح در مسیر صعودی).
 *
 * فهرست [blocks] دقیقاً همان (گوش، فرکانس) هایی است که باید آزموده شوند — می‌تواند همه
 * ترکیب‌های گوش×فرکانس باشد (آزمون اصلی)، یا فقط زیرمجموعه‌ای خاص (مثلاً بررسی خودکار
 * ماسکینگ فقط در فرکانس‌هایی که معیار تضعیف بین‌گوشی را رد کرده‌اند).
 *
 * برای افزایش پایایی پاسخ‌ها، بر پایه پژوهش‌های اودیومتری خودکار امروزی، دو تکنیک اضافه
 * شده است:
 *  ۱) ترتیب بلوک‌ها به‌صورت تصادفی به هم می‌ریزد (نه گوش راست کامل سپس گوش چپ کامل) تا مغز
 *     نتواند الگوی آزمون را حدس بزند و پیش از شنیدن واقعی صدا پاسخ ندهد.
 *  ۲) چند «کوشش کنترلی» (Catch Trial) بدون پخش هیچ صدایی به‌طور تصادفی در میان بلوک‌ها درج
 *     می‌شود؛ اگر کاربر در این کوشش‌ها هم دکمه «شنیدم» را بزند (پاسخ مثبت کاذب، نشانه حدس
 *     زدن یا انتظار الگو)، بلوک واقعی بعدی به‌عنوان «نیازمند احتیاط در تفسیر» علامت می‌خورد.
 *
 * نکته: چون این آزمون روی خروجی هدفون کاربر و بدون کالیبراسیون آزمایشگاهی انجام می‌شود،
 * نتیجه یک سنجش نسبی و شخصی است، نه معادل تشخیص بالینی dB HL.
 */
class HearingTestController(
    private val frequenciesHz: List<Double>,
    blocks: List<Pair<Ear, Int>>,
    catchTrialCount: Int = 0,
    private val onPlayTone: (ear: Ear, freqHz: Double, attenuationDb: Float) -> Unit,
    private val onPlayCatchTrial: () -> Unit,
    private val onProgress: (ear: Ear, freqIndex: Int, completedBlocks: Int, totalBlocks: Int) -> Unit,
    private val onBlockFinished: (ear: Ear, freqIndex: Int, thresholdDb: Float) -> Unit,
    private val onCatchTrialFailed: (flaggedEar: Ear?, flaggedFreqIndex: Int?) -> Unit,
    private val onFinished: () -> Unit
) {
    companion object {
        private const val START_ATTENUATION = 20f   // شروع از سطحی نسبتاً بلند و واضح
        private const val MIN_ATTENUATION = 0f       // بلندترین حالت ممکن
        private const val MAX_ATTENUATION = 80f      // آرام‌ترین سطح قابل آزمایش
        private const val MAX_TRIALS_PER_FREQUENCY = 12
        private const val THRESHOLD_STEP_DB = 4f      // گام صعودی پیدا کردن آستانه (بنا به درخواست کاربر: ۴ دسی‌بل)
    }

    private sealed class TestBlock {
        data class Tone(val ear: Ear, val freqIndex: Int) : TestBlock()
        object Catch : TestBlock()
    }

    private enum class Phase { AFTER_HEARD, AFTER_NOT_HEARD }

    private val queue: MutableList<TestBlock> = mutableListOf()
    private val totalToneBlocks: Int = blocks.size
    private var completedBlocks = 0
    private var awaitingCatchResponse = false

    private var currentEar = Ear.RIGHT
    private var freqIndex = 0
    private var currentLevel = START_ATTENUATION
    private var phase = Phase.AFTER_HEARD
    private val hitsAtLevel = mutableMapOf<Float, Int>()
    private var trialsThisFrequency = 0

    init {
        queue.addAll(blocks.map { TestBlock.Tone(it.first, it.second) }.shuffled())
        // درج تصادفی کوشش‌های کنترلی در میان بلوک‌های واقعی (نه دقیقاً در ابتدا یا انتهای صف)
        repeat(catchTrialCount) {
            if (queue.size > 1) {
                val insertAt = (1 until queue.size).random()
                queue.add(insertAt, TestBlock.Catch)
            }
        }
    }

    fun start() {
        advanceToNextBlock()
    }

    private fun advanceToNextBlock() {
        if (queue.isEmpty()) {
            onFinished()
            return
        }
        when (val block = queue.removeAt(0)) {
            is TestBlock.Catch -> {
                awaitingCatchResponse = true
                onPlayCatchTrial()
            }
            is TestBlock.Tone -> {
                currentEar = block.ear
                freqIndex = block.freqIndex
                beginFrequencyBlock()
            }
        }
    }

    private fun beginFrequencyBlock() {
        currentLevel = START_ATTENUATION
        phase = Phase.AFTER_HEARD
        hitsAtLevel.clear()
        trialsThisFrequency = 0
        onProgress(currentEar, freqIndex, completedBlocks, totalToneBlocks)
        presentTone()
    }

    private fun presentTone() {
        onPlayTone(currentEar, frequenciesHz[freqIndex], currentLevel)
    }

    /** باید پس از هر بار پخش (یا هر کوشش کنترلی)، با پاسخ کاربر (شنیدم / نشنیدم) صدا زده شود */
    fun onResponse(heard: Boolean) {
        if (awaitingCatchResponse) {
            awaitingCatchResponse = false
            if (heard) {
                val nextTone = queue.firstOrNull { it is TestBlock.Tone } as? TestBlock.Tone
                onCatchTrialFailed(nextTone?.ear, nextTone?.freqIndex)
            }
            advanceToNextBlock()
            return
        }

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
                currentLevel = (currentLevel - THRESHOLD_STEP_DB).coerceAtLeast(MIN_ATTENUATION)
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
                currentLevel = (currentLevel - THRESHOLD_STEP_DB).coerceAtLeast(MIN_ATTENUATION)
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
        onBlockFinished(currentEar, freqIndex, level)
        completedBlocks++
        advanceToNextBlock()
    }
}
