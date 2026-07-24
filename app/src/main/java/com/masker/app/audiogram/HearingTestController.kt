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
 * برای افزایش پایایی پاسخ‌ها، بر پایه پژوهش‌های اودیومتری خودکار امروزی، چند تکنیک اضافه
 * شده است:
 *  ۱) بلوک‌های هر گوش جداگانه به‌هم می‌ریزند و سپس با تناوب کنترل‌شده (حداکثر دو بلوک
 *     پشت‌سرهم از یک گوش) ادغام می‌شوند — نه فقط یک شافل کاملاً تصادفی که می‌تواند به‌طور
 *     شانسی چند بلوک از یک گوش را پشت‌سرهم قرار دهد، و نه ترتیب قابل‌پیش‌بینی راست-چپ منظم —
 *     تا مغز نتواند الگوی آزمون را حدس بزند و پیش از شنیدن واقعی صدا پاسخ ندهد.
 *  ۲) چند «کوشش کنترلی» (Catch Trial) بدون پخش هیچ صدایی، در بازه‌های تقریباً مساوی از صف
 *     (نه صرفاً موقعیت‌های کاملاً تصادفی مستقل که می‌تواند چند سکوت را کنار هم بیندازد) درج
 *     می‌شود؛ اگر کاربر در این کوشش‌ها هم دکمه «شنیدم» را بزند (پاسخ مثبت کاذب، نشانه حدس
 *     زدن یا انتظار الگو)، بلوک واقعی بعدی به‌عنوان «نیازمند احتیاط در تفسیر» علامت می‌خورد.
 *  ۳) تأیید آستانه با تکرار مستقیم همان سطح (به‌جای بازگشت کامل به سطح بلندتر و نزول دوباره)
 *     انجام می‌شود تا با کوشش‌های کمتر، آزمون سریع‌تر پیش برود.
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
        queue.addAll(buildBalancedToneQueue(blocks))
        insertCatchTrialsSpread(catchTrialCount)
    }

    /**
     * به‌جای یک به‌هم‌ریزی کاملاً تصادفی ساده (که به‌طور شانسی می‌تواند چند بلوک پشت‌سرهم از
     * همان گوش تولید کند)، بلوک‌های هر گوش را جداگانه به‌هم می‌ریزد و سپس با تناوب کنترل‌شده
     * (حداکثر دو بلوک پشت‌سرهم از یک گوش) با هم ادغام می‌کند تا واقعاً بین دو گوش پخش شوند —
     * ولی همچنان کاملاً قابل‌پیش‌بینی نباشد (مغز نتواند دقیقاً الگوی راست-چپ-راست-چپ را حدس بزند).
     */
    private fun buildBalancedToneQueue(blocks: List<Pair<Ear, Int>>): List<TestBlock.Tone> {
        val byEar = blocks.groupBy { it.first }
        val remaining = byEar.mapValues { (_, list) -> list.shuffled().toMutableList() }.toMutableMap()
        val result = mutableListOf<TestBlock.Tone>()
        var lastEar: Ear? = null
        var sameEarStreak = 0

        while (remaining.values.any { it.isNotEmpty() }) {
            val availableEars = remaining.filterValues { it.isNotEmpty() }.keys
            val chosenEar = when {
                availableEars.size == 1 -> availableEars.first()
                sameEarStreak >= 2 -> availableEars.first { it != lastEar }
                else -> availableEars.random()
            }
            val (ear, freqIdx) = remaining.getValue(chosenEar).removeAt(0)
            result.add(TestBlock.Tone(ear, freqIdx))
            sameEarStreak = if (chosenEar == lastEar) sameEarStreak + 1 else 1
            lastEar = chosenEar
        }
        return result
    }

    /**
     * کوشش‌های کنترلی را به‌جای انتخاب کاملاً تصادفیِ مستقل (که می‌تواند چند سکوت را کنار هم
     * قرار دهد)، در بازه‌های تقریباً مساوی از صف پخش می‌کند — یک سکوت در هر بازه، در نقطه‌ای
     * تصادفی از همان بازه. بلوک اول صف هرگز کوشش کنترلی نیست تا آزمون با یک تن واقعی شروع شود.
     */
    private fun insertCatchTrialsSpread(catchTrialCount: Int) {
        if (catchTrialCount <= 0 || queue.size <= 1) return
        val segments = catchTrialCount
        val insertPositions = mutableListOf<Int>()
        for (s in 0 until segments) {
            val segStart = (1 + s * (queue.size - 1) / segments)
            val segEnd = (1 + (s + 1) * (queue.size - 1) / segments).coerceAtMost(queue.size)
            val pos = if (segEnd > segStart) (segStart until segEnd).random() else segStart.coerceAtMost(queue.size)
            insertPositions.add(pos)
        }
        // درج از انتها به ابتدا تا اندیس‌های محاسبه‌شده برای بازه‌های قبلی جابه‌جا نشوند
        insertPositions.sortedDescending().forEach { pos ->
            queue.add(pos.coerceIn(0, queue.size), TestBlock.Catch)
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
                // برای تأیید سریع‌تر (به‌جای بازگشت کامل به سطح بلندتر و نزول دوباره تا همین
                // سطح که چند کوشش اضافه می‌طلبید)، بلافاصله همین سطح یک‌بار دیگر ارائه می‌شود؛
                // معیار «دو پاسخ مثبت» همچنان رعایت می‌شود، فقط با کوشش‌های کمتر. این نسخهٔ
                // ساده‌شدهٔ روش هیوز-وستلیک، در پژوهش‌های اودیومتری خودکار، دقتی مشابه با
                // زمان آزمون به‌مراتب کوتاه‌تر نشان داده است.
                // phase و currentLevel بدون تغییر می‌مانند تا presentTone() دوباره همین‌جا اجرا شود.
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
