package com.masker.app.audiogram

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.masker.app.R
import com.masker.app.databinding.ActivityAudiogramBinding
import com.masker.app.report.PatientReport
import com.masker.app.report.ReportQueueStorage
import com.masker.app.report.ReportSendManager
import com.masker.app.report.SheetsReportSender
import com.masker.app.report.TinnitusScoreDialog
import com.masker.app.ui.MessageDialog
import java.util.Date
import kotlin.math.abs

/**
 * فعالیت آزمون شنوایی (اودیوگرام). سه مرحله دارد: معرفی/تنظیم ولوم، انجام آزمون (پخش
 * پالسی/بیپ‌بیپ تن و دریافت پاسخ کاربر با دو دکمه «می‌شنوم»/«نمی‌شنوم» برای هر گوش/فرکانس)، و
 * نمایش نتیجه به‌صورت نمودار قابل ذخیره و اشتراک‌گذاری.
 *
 * ترتیب آزمون گوش/فرکانس‌ها تصادفی است (نه ابتدا گوش راست کامل سپس چپ کامل) و چند «کوشش
 * کنترلی» بدون پخش صدا هم به‌طور تصادفی درج می‌شود — هر دو برای کاهش احتمال پاسخ نابه‌جا
 * ناشی از حدس‌زدن الگوی آزمون توسط مغز (به‌جای شنیدن واقعی صدا).
 *
 * پس از پایان آزمون اصلی، بر پایه معیار بالینی «تضعیف بین‌گوشی» (Interaural Attenuation)،
 * برنامه به‌طور خودکار بررسی می‌کند که آیا در هر فرکانس نیاز به تأیید با نویز ماسک‌کننده
 * («اثر سایه» / Cross-hearing) هست یا نه؛ در صورت نیاز، همان‌جا (در همین آزمون اول) فقط آن
 * نقاط با ماسک بازآزموده می‌شوند. جدای از این، از کاربر پرسیده می‌شود که آیا مایل است آزمون
 * کامل «اثر سایه» را هم برای همه فرکانس‌ها انجام دهد؛ در صورت تأیید، همان آزمون یک‌بار دیگر
 * با ماسک تکرار می‌شود. نتایج ماسک‌شده با نمادهای استاندارد اودیولوژی (مثلث/مربع) رسم می‌شوند.
 *
 * از صفحه سوابق («جست‌وجوی سابقه») می‌توان نام و سن یک فرد قبلی را انتخاب کرد تا این
 * اطلاعات در فیلدهای این صفحه از پیش پر شوند و آماده اجرای یک آزمون جدید برای همان فرد باشند.
 *
 * هر بار که آزمون موقتاً متوقف شود، نمره ذهنی شدت وزوز هر گوش پرسیده و به‌همراه وضعیت
 * اودیوگرام و تنظیمات فعلی ماسکر، برای بررسی‌های آزمایشگاهی به سازنده ایمیل می‌شود (یا در
 * نبود اینترنت، برای ارسال خودکار بعدی ذخیره می‌شود).
 */
class AudiogramActivity : AppCompatActivity() {

    companion object {
        // فرکانس‌های استاندارد غربالگری شنوایی (بر اساس دستورالعمل‌های رایج علمی/بالینی)
        val TEST_FREQUENCIES = listOf(250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)

        // سطح نویز ماسک‌کننده در گوش مقابل، هنگام بررسی اثر سایه (مقیاس نسبی ساده‌شده)
        private const val MASKING_ATTENUATION_DB = 15f

        // معیار بالینی رایج: با هدفون معمولی (Supra-aural)، اگر اختلاف آستانه دو گوش در یک
        // فرکانس ۴۰ دسی‌بل یا بیشتر باشد، ممکن است پاسخ گوش ضعیف‌تر در واقع نتیجه شنیدن همان
        // صدا توسط گوش قوی‌تر (تضعیف بین‌گوشی/انتقال درون‌جمجمه‌ای) باشد، نه گوش آزموده؛ در
        // این حالت باید با نویز ماسک‌کننده در گوش قوی‌تر، آستانه گوش ضعیف‌تر تأیید شود.
        private const val INTERAURAL_MASKING_THRESHOLD_DB = 40f

        private const val MAIN_PHASE_CATCH_TRIALS = 3
        private const val FULL_MASKED_PHASE_CATCH_TRIALS = 2
    }

    private lateinit var binding: ActivityAudiogramBinding

    private var controller: HearingTestController? = null
    private var isPaused = false
    private var isMaskedPhase = false

    private var pendingEar: Ear? = null
    private var pendingFreqHz: Double = 0.0
    private var pendingAttenuation: Float = 0f
    private var pendingIsCatchTrial = false

    private var patientName: String = ""
    private var patientAge: Int = 0

    // انباشت تدریجی نتایج آزمون در حین اجرا (چون ترتیب بلوک‌ها تصادفی است و ممکن است چند
    // مرحله پشت سر هم اجرا شود: اصلی، بررسی خودکار ماسکینگ، و در صورت انتخاب کاربر، اثر سایه کامل)
    private var rightThresholds = FloatArray(TEST_FREQUENCIES.size) { Float.NaN }
    private var leftThresholds = FloatArray(TEST_FREQUENCIES.size) { Float.NaN }
    private var rightMasked = FloatArray(TEST_FREQUENCIES.size) { Float.NaN }
    private var leftMasked = FloatArray(TEST_FREQUENCIES.size) { Float.NaN }
    private var hasMaskedData = false
    private val unreliableRight = mutableSetOf<Int>()
    private val unreliableLeft = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudiogramBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playCalibrationToneButton.setOnClickListener {
            HearingTestTonePlayer.playBrief(this, Ear.BOTH, 1000.0, attenuationDb = 20f, durationMs = 2500) {}
        }

        binding.startTestButton.setOnClickListener { startTest() }
        binding.viewHistoryButton.setOnClickListener {
            startActivity(Intent(this, AudiogramHistoryActivity::class.java))
        }

        binding.hearButton.setOnClickListener { onUserResponded(true) }
        binding.dontHearButton.setOnClickListener { onUserResponded(false) }
        binding.pauseResumeButton.setOnClickListener { onPauseResumeClicked() }

        binding.retakeTestButton.setOnClickListener { showIntroSection() }
        binding.saveAudiogramButton.setOnClickListener { saveAudiogramImage(share = false) }
        binding.shareAudiogramButton.setOnClickListener { saveAudiogramImage(share = true) }
    }

    override fun onResume() {
        super.onResume()
        val selectedName = AudiogramHistoryActivity.pendingSelectedName
        if (selectedName != null) {
            AudiogramHistoryActivity.pendingSelectedName = null
            val stored = AudiogramStorage.loadLatestResultForPatient(this, selectedName)
            if (stored != null) {
                showIntroSection()
                binding.patientNameEditText.setText(stored.patientName)
                binding.patientAgeEditText.setText(stored.patientAge.toString())
                binding.patientNameEditText.setSelection(binding.patientNameEditText.text?.length ?: 0)
                MessageDialog.show(this, getString(R.string.history_loaded_toast, stored.patientName))
            }
        }
    }

    // ==================== شروع و اجرای آزمون ====================

    private fun startTest() {
        val name = binding.patientNameEditText.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            MessageDialog.show(this, R.string.patient_name_required)
            return
        }
        val age = binding.patientAgeEditText.text?.toString()?.trim()?.toIntOrNull()
        if (age == null || age <= 0 || age > 130) {
            MessageDialog.show(this, R.string.patient_age_required)
            return
        }
        patientName = name
        patientAge = age
        resetAccumulators()

        runTestPhase(masked = false, blocks = allEarFrequencyBlocks(), catchTrialCount = MAIN_PHASE_CATCH_TRIALS) {
            onMainPhaseFinished()
        }
    }

    private fun allEarFrequencyBlocks(): List<Pair<Ear, Int>> {
        return TEST_FREQUENCIES.indices.flatMap { idx -> listOf(Ear.RIGHT to idx, Ear.LEFT to idx) }
    }

    private fun resetAccumulators() {
        rightThresholds = FloatArray(TEST_FREQUENCIES.size) { Float.NaN }
        leftThresholds = FloatArray(TEST_FREQUENCIES.size) { Float.NaN }
        rightMasked = FloatArray(TEST_FREQUENCIES.size) { Float.NaN }
        leftMasked = FloatArray(TEST_FREQUENCIES.size) { Float.NaN }
        hasMaskedData = false
        unreliableRight.clear()
        unreliableLeft.clear()
    }

    private fun runTestPhase(
        masked: Boolean,
        blocks: List<Pair<Ear, Int>>,
        catchTrialCount: Int,
        onPhaseDone: () -> Unit
    ) {
        isMaskedPhase = masked
        binding.testMaskingBadge.visibility = if (masked) View.VISIBLE else View.GONE

        isPaused = false
        binding.pauseResumeButton.text = getString(R.string.pause_test)
        binding.pausedStatusText.visibility = View.GONE

        binding.introSection.visibility = View.GONE
        binding.resultSection.visibility = View.GONE
        binding.testSection.visibility = View.VISIBLE

        val newController = HearingTestController(
            frequenciesHz = TEST_FREQUENCIES,
            blocks = blocks,
            catchTrialCount = catchTrialCount,
            onPlayTone = { ear, freqHz, attenuationDb -> playTestTone(ear, freqHz, attenuationDb) },
            onPlayCatchTrial = { playCatchTrial() },
            onProgress = { ear, freqIndex, completed, total -> updateProgress(ear, freqIndex, completed, total) },
            onBlockFinished = { ear, freqIndex, thresholdDb -> recordBlockResult(masked, ear, freqIndex, thresholdDb) },
            onCatchTrialFailed = { ear, freqIndex -> recordUnreliable(ear, freqIndex) },
            onFinished = { onPhaseDone() }
        )
        controller = newController
        newController.start()
    }

    private fun recordBlockResult(masked: Boolean, ear: Ear, freqIndex: Int, thresholdDb: Float) {
        if (masked) {
            hasMaskedData = true
            if (ear == Ear.RIGHT) rightMasked[freqIndex] = thresholdDb else leftMasked[freqIndex] = thresholdDb
        } else {
            if (ear == Ear.RIGHT) rightThresholds[freqIndex] = thresholdDb else leftThresholds[freqIndex] = thresholdDb
        }
    }

    private fun recordUnreliable(ear: Ear?, freqIndex: Int?) {
        if (ear == null || freqIndex == null) return
        if (ear == Ear.RIGHT) unreliableRight.add(freqIndex) else unreliableLeft.add(freqIndex)
    }

    private fun onMainPhaseFinished() {
        val flaggedBlocks = findFrequenciesNeedingMasking()
        if (flaggedBlocks.isNotEmpty()) {
            runTestPhase(masked = true, blocks = flaggedBlocks, catchTrialCount = 0) {
                askCrossHearingQuestion()
            }
        } else {
            askCrossHearingQuestion()
        }
    }

    /**
     * بر پایه معیار بالینی رایج تضعیف بین‌گوشی (Interaural Attenuation): فرکانس‌هایی که
     * اختلاف آستانه دو گوش در آن‌ها به [INTERAURAL_MASKING_THRESHOLD_DB] یا بیشتر می‌رسد را
     * برمی‌گرداند، به‌همراه گوشِ ضعیف‌تر (که باید با ماسک دوباره آزموده شود).
     */
    private fun findFrequenciesNeedingMasking(): List<Pair<Ear, Int>> {
        val result = mutableListOf<Pair<Ear, Int>>()
        for (i in TEST_FREQUENCIES.indices) {
            val r = rightThresholds.getOrNull(i)?.takeIf { !it.isNaN() } ?: continue
            val l = leftThresholds.getOrNull(i)?.takeIf { !it.isNaN() } ?: continue
            if (abs(r - l) >= INTERAURAL_MASKING_THRESHOLD_DB) {
                // آستانه پایین‌تر یعنی به صدای بلندتری نیاز داشته = گوش ضعیف‌تر همان‌جاست
                val weakerEar = if (r < l) Ear.RIGHT else Ear.LEFT
                result.add(weakerEar to i)
            }
        }
        return result
    }

    private fun askCrossHearingQuestion() {
        binding.testSection.visibility = View.GONE
        AlertDialog.Builder(this)
            .setTitle(R.string.cross_hearing_dialog_title)
            .setMessage(R.string.cross_hearing_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.yes) { _, _ -> runFullMaskedPhase() }
            .setNegativeButton(R.string.no) { _, _ -> finalizeAndShow() }
            .show()
    }

    private fun runFullMaskedPhase() {
        runTestPhase(masked = true, blocks = allEarFrequencyBlocks(), catchTrialCount = FULL_MASKED_PHASE_CATCH_TRIALS) {
            finalizeAndShow()
        }
    }

    private fun buildCurrentResult(): AudiogramResult {
        return AudiogramResult(
            frequenciesHz = TEST_FREQUENCIES,
            rightThresholdsDb = rightThresholds,
            leftThresholdsDb = leftThresholds,
            timestampMillis = System.currentTimeMillis(),
            patientName = patientName,
            patientAge = patientAge,
            rightMaskedThresholdsDb = if (hasMaskedData) rightMasked else null,
            leftMaskedThresholdsDb = if (hasMaskedData) leftMasked else null,
            unreliableRightFreqIndices = unreliableRight.toSet(),
            unreliableLeftFreqIndices = unreliableLeft.toSet()
        )
    }

    private fun finalizeAndShow() {
        val result = buildCurrentResult()
        AudiogramStorage.saveResult(this, result)

        binding.testSection.visibility = View.GONE
        binding.resultSection.visibility = View.VISIBLE
        binding.audiogramView.setResult(result)
        updatePatientInfoText(result)
        updateLegendText(result)
    }

    private fun updateProgress(ear: Ear, freqIndex: Int, completedBlocks: Int, totalBlocks: Int) {
        val earLabel = if (ear == Ear.RIGHT) getString(R.string.right_ear) else getString(R.string.left_ear)
        val freqLabel = formatFrequencyLabel(TEST_FREQUENCIES[freqIndex])
        binding.testStatusText.text = getString(
            R.string.audiogram_progress_format, earLabel, completedBlocks + 1, totalBlocks, freqLabel
        )
        binding.testProgressBar.progress = if (totalBlocks > 0) (completedBlocks * 100 / totalBlocks) else 0
    }

    private fun playTestTone(ear: Ear, freqHz: Double, attenuationDb: Float) {
        pendingEar = ear
        pendingFreqHz = freqHz
        pendingAttenuation = attenuationDb
        pendingIsCatchTrial = false

        binding.hearButton.isEnabled = true
        binding.dontHearButton.isEnabled = true

        HearingTestTonePlayer.start(
            this, ear, freqHz, attenuationDb,
            maskingEnabled = isMaskedPhase,
            maskingAttenuationDb = MASKING_ATTENUATION_DB
        )
    }

    /** کوشش کنترلی: هیچ صدایی پخش نمی‌شود، فقط منتظر پاسخ کاربر می‌مانیم (برای سنجش پاسخ مثبت کاذب) */
    private fun playCatchTrial() {
        pendingIsCatchTrial = true
        binding.hearButton.isEnabled = true
        binding.dontHearButton.isEnabled = true
    }

    private fun onUserResponded(heard: Boolean) {
        if (isPaused) return
        HearingTestTonePlayer.stop()
        binding.hearButton.isEnabled = false
        binding.dontHearButton.isEnabled = false
        controller?.onResponse(heard)
    }

    private fun onPauseResumeClicked() {
        if (isPaused) {
            isPaused = false
            binding.pauseResumeButton.text = getString(R.string.pause_test)
            binding.pausedStatusText.visibility = View.GONE

            if (pendingIsCatchTrial) {
                binding.hearButton.isEnabled = true
                binding.dontHearButton.isEnabled = true
            } else {
                val ear = pendingEar
                if (ear != null) {
                    playTestTone(ear, pendingFreqHz, pendingAttenuation)
                }
            }
        } else {
            isPaused = true
            HearingTestTonePlayer.stop()
            binding.hearButton.isEnabled = false
            binding.dontHearButton.isEnabled = false

            binding.pauseResumeButton.text = getString(R.string.resume_test)
            binding.pausedStatusText.visibility = View.VISIBLE

            showTinnitusScoreDialog()
        }
    }

    /**
     * هر بار که کاربر آزمون را موقتاً متوقف می‌کند، نمره ذهنی شدت وزوز هر گوش (۰ تا ۱۰)
     * پرسیده و به‌همراه وضعیت فعلی اودیوگرام و تنظیمات ماسکر، برای بررسی‌های آزمایشگاهی و
     * بهبود نرم‌افزار برای سازنده ارسال (یا در صورت نبود اینترنت، برای ارسال بعدی ذخیره) می‌شود.
     */
    private fun showTinnitusScoreDialog() {
        TinnitusScoreDialog.show(this) { left, right -> sendCheckpointReport(left, right) }
    }

    private fun sendCheckpointReport(leftScore: Int?, rightScore: Int?) {
        val report = PatientReport.build(
            context = this,
            patientName = patientName.ifBlank { binding.patientNameEditText.text?.toString().orEmpty() },
            patientAge = if (patientAge > 0) patientAge else (binding.patientAgeEditText.text?.toString()?.toIntOrNull() ?: 0),
            audiogramResult = buildCurrentResult(),
            leftTinnitusScore = leftScore,
            rightTinnitusScore = rightScore
        )

        if (!SheetsReportSender.isConfigured()) {
            // آدرس مقصد گزارش هنوز در local.properties تنظیم نشده؛ فقط محلی ذخیره می‌شود
            ReportQueueStorage.enqueue(this, report)
            MessageDialog.show(this, R.string.report_not_configured_message)
            return
        }

        ReportSendManager.sendOrQueue(this, report) { outcome ->
            runOnUiThread {
                val message = when (outcome) {
                    ReportSendManager.SendOutcome.SENT -> getString(R.string.report_sent_message)
                    ReportSendManager.SendOutcome.NO_NETWORK -> getString(R.string.report_queued_message)
                    ReportSendManager.SendOutcome.SEND_FAILED -> getString(R.string.report_send_failed_message)
                }
                MessageDialog.show(this, message)
            }
        }
    }

    private fun showIntroSection() {
        HearingTestTonePlayer.stop()
        isPaused = false
        pendingEar = null
        pendingIsCatchTrial = false
        controller = null
        isMaskedPhase = false
        resetAccumulators()

        binding.pauseResumeButton.text = getString(R.string.pause_test)
        binding.pausedStatusText.visibility = View.GONE
        binding.testMaskingBadge.visibility = View.GONE

        binding.testSection.visibility = View.GONE
        binding.resultSection.visibility = View.GONE
        binding.introSection.visibility = View.VISIBLE
    }

    private fun formatFrequencyLabel(freqHz: Double): String {
        return if (freqHz >= 1000) "${(freqHz / 1000).toInt()} کیلوهرتز" else "${freqHz.toInt()} هرتز"
    }

    private fun updatePatientInfoText(result: AudiogramResult) {
        val date = Date(result.timestampMillis)
        val jalaliDate = PersianDateUtils.toJalaliString(date)
        val gregorianDate = PersianDateUtils.toGregorianString(date)
        val ageDigits = PersianDateUtils.toPersianDigits(result.patientAge.toString())
        binding.patientInfoText.text = getString(
            R.string.patient_info_format, result.patientName, ageDigits, jalaliDate, gregorianDate
        )
    }

    private fun updateLegendText(result: AudiogramResult) {
        binding.legendText.text = AudiogramLegend.build(this, result)
    }

    // ==================== ذخیره و اشتراک‌گذاری تصویر ====================

    private fun saveAudiogramImage(share: Boolean) {
        AudiogramImageExporter.saveAndMaybeShare(this, binding.resultCaptureContainer, share)
    }

    override fun onDestroy() {
        HearingTestTonePlayer.stop()
        super.onDestroy()
    }
}
