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

/**
 * فعالیت آزمون شنوایی (اودیوگرام). سه مرحله دارد: معرفی/تنظیم ولوم، انجام آزمون (پخش
 * پالسی/بیپ‌بیپ تن و دریافت پاسخ کاربر با دو دکمه «می‌شنوم»/«نمی‌شنوم» برای هر گوش/فرکانس)، و
 * نمایش نتیجه به‌صورت نمودار قابل ذخیره و اشتراک‌گذاری.
 *
 * پس از پایان آزمون معمولی، از کاربر پرسیده می‌شود که آیا مایل است «اثر سایه»
 * (Cross-hearing) هم بررسی شود؛ در صورت تأیید، همان آزمون یک‌بار دیگر تکرار می‌شود، این‌بار
 * با پخش هم‌زمان نویز ماسک‌کننده در گوش مقابل — دقیقاً همان روش استاندارد ماسکینگ در
 * اودیومتری بالینی. نتایج ماسک‌شده با نمادهای استاندارد اودیولوژی (مثلث/مربع) رسم می‌شوند.
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
    }

    private lateinit var binding: ActivityAudiogramBinding

    private var controller: HearingTestController? = null
    private var isPaused = false
    private var isMaskedPhase = false

    private var pendingEar: Ear? = null
    private var pendingFreqHz: Double = 0.0
    private var pendingAttenuation: Float = 0f

    private var patientName: String = ""
    private var patientAge: Int = 0
    private var pendingUnmaskedResult: AudiogramResult? = null

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

        runTestPhase(masked = false) { result ->
            pendingUnmaskedResult = result.copy(patientName = patientName, patientAge = patientAge)
            askCrossHearingQuestion()
        }
    }

    private fun runTestPhase(masked: Boolean, onDone: (AudiogramResult) -> Unit) {
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
            onPlayTone = { ear, freqHz, attenuationDb -> playTestTone(ear, freqHz, attenuationDb) },
            onProgress = { ear, freqIndex, totalFreq -> updateProgress(ear, freqIndex, totalFreq) },
            onFinished = { result -> onDone(result) }
        )
        controller = newController
        newController.start()
    }

    private fun askCrossHearingQuestion() {
        binding.testSection.visibility = View.GONE
        AlertDialog.Builder(this)
            .setTitle(R.string.cross_hearing_dialog_title)
            .setMessage(R.string.cross_hearing_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.yes) { _, _ -> runMaskedPhase() }
            .setNegativeButton(R.string.no) { _, _ ->
                pendingUnmaskedResult?.let { finalizeAndShow(it) }
            }
            .show()
    }

    private fun runMaskedPhase() {
        runTestPhase(masked = true) { maskedResult ->
            val combined = pendingUnmaskedResult?.copy(
                rightMaskedThresholdsDb = maskedResult.rightThresholdsDb,
                leftMaskedThresholdsDb = maskedResult.leftThresholdsDb
            )
            combined?.let { finalizeAndShow(it) }
        }
    }

    private fun finalizeAndShow(result: AudiogramResult) {
        AudiogramStorage.saveResult(this, result)

        binding.testSection.visibility = View.GONE
        binding.resultSection.visibility = View.VISIBLE
        binding.audiogramView.setResult(result)
        updatePatientInfoText(result)
        updateLegendText(result)
    }

    private fun updateProgress(ear: Ear, freqIndex: Int, totalFreq: Int) {
        val earLabel = if (ear == Ear.RIGHT) getString(R.string.right_ear) else getString(R.string.left_ear)
        val freqLabel = formatFrequencyLabel(TEST_FREQUENCIES[freqIndex])
        binding.testStatusText.text = getString(R.string.audiogram_progress_format, earLabel, freqIndex + 1, totalFreq, freqLabel)

        val earOffset = if (ear == Ear.RIGHT) 0 else totalFreq
        val doneSteps = earOffset + freqIndex
        val totalSteps = totalFreq * 2
        binding.testProgressBar.progress = (doneSteps * 100 / totalSteps)
    }

    private fun playTestTone(ear: Ear, freqHz: Double, attenuationDb: Float) {
        pendingEar = ear
        pendingFreqHz = freqHz
        pendingAttenuation = attenuationDb

        binding.hearButton.isEnabled = true
        binding.dontHearButton.isEnabled = true

        HearingTestTonePlayer.start(
            this, ear, freqHz, attenuationDb,
            maskingEnabled = isMaskedPhase,
            maskingAttenuationDb = MASKING_ATTENUATION_DB
        )
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

            val ear = pendingEar
            if (ear != null) {
                playTestTone(ear, pendingFreqHz, pendingAttenuation)
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
            audiogramResult = pendingUnmaskedResult,
            leftTinnitusScore = leftScore,
            rightTinnitusScore = rightScore
        )

        if (!SheetsReportSender.isConfigured()) {
            // آدرس مقصد گزارش هنوز در local.properties تنظیم نشده؛ فقط محلی ذخیره می‌شود
            ReportQueueStorage.enqueue(this, report)
            MessageDialog.show(this, R.string.report_not_configured_message)
            return
        }

        ReportSendManager.sendOrQueue(this, report) { success ->
            runOnUiThread {
                val message = if (success) {
                    getString(R.string.report_sent_message)
                } else {
                    getString(R.string.report_queued_message)
                }
                MessageDialog.show(this, message)
            }
        }
    }

    private fun showIntroSection() {
        HearingTestTonePlayer.stop()
        isPaused = false
        pendingEar = null
        controller = null
        isMaskedPhase = false
        pendingUnmaskedResult = null

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
        val hasMasked = result.rightMaskedThresholdsDb != null || result.leftMaskedThresholdsDb != null
        binding.legendText.text = getString(
            if (hasMasked) R.string.audiogram_legend_with_masked else R.string.audiogram_legend
        )
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
