package com.masker.app.audiogram

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.masker.app.R
import com.masker.app.databinding.ActivityAudiogramBinding
import com.masker.app.report.PatientReport
import com.masker.app.report.ReportSendManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                Toast.makeText(
                    this,
                    getString(R.string.history_loaded_toast, stored.patientName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ==================== شروع و اجرای آزمون ====================

    private fun startTest() {
        val name = binding.patientNameEditText.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.patient_name_required, Toast.LENGTH_LONG).show()
            return
        }
        val age = binding.patientAgeEditText.text?.toString()?.trim()?.toIntOrNull()
        if (age == null || age <= 0 || age > 130) {
            Toast.makeText(this, R.string.patient_age_required, Toast.LENGTH_LONG).show()
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_tinnitus_score, null)
        val leftScoreEditText = dialogView.findViewById<EditText>(R.id.leftScoreEditText)
        val rightScoreEditText = dialogView.findViewById<EditText>(R.id.rightScoreEditText)

        AlertDialog.Builder(this)
            .setTitle(R.string.tinnitus_score_dialog_title)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton(R.string.submit) { _, _ ->
                val left = leftScoreEditText.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(0, 10)
                val right = rightScoreEditText.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(0, 10)
                sendCheckpointReport(left, right)
            }
            .setNegativeButton(R.string.skip, null)
            .show()
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
        ReportSendManager.sendOrQueue(this, report) { _ ->
            runOnUiThread {
                Toast.makeText(this, R.string.report_sent_message, Toast.LENGTH_LONG).show()
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

    private fun renderResultBitmap(): Bitmap? {
        val view = binding.resultCaptureContainer
        if (view.width == 0 || view.height == 0) return null
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return bitmap
    }

    private fun saveAudiogramImage(share: Boolean) {
        val bitmap = renderResultBitmap()
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_LONG).show()
            return
        }

        val outDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MaskerAudiograms")
        if (!outDir.exists()) outDir.mkdirs()

        val fileName = "audiogram_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val outFile = File(outDir, fileName)

        try {
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_LONG).show()
            return
        }

        if (share) {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } else {
            Toast.makeText(this, getString(R.string.save_success) + "\n" + outFile.absolutePath, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        HearingTestTonePlayer.stop()
        super.onDestroy()
    }
}
