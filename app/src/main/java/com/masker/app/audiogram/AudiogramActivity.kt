package com.masker.app.audiogram

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.masker.app.R
import com.masker.app.databinding.ActivityAudiogramBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * فعالیت آزمون شنوایی (اودیوگرام). سه مرحله دارد: معرفی/تنظیم ولوم، انجام آزمون (پخش تن
 * و دریافت پاسخ کاربر برای هر گوش/فرکانس)، و نمایش نتیجه به‌صورت نمودار قابل ذخیره و اشتراک‌گذاری.
 */
class AudiogramActivity : AppCompatActivity() {

    companion object {
        // فرکانس‌های استاندارد غربالگری شنوایی (بر اساس دستورالعمل‌های رایج علمی/بالینی)
        val TEST_FREQUENCIES = listOf(250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)
        private const val TONE_DURATION_MS = 1200
        private const val RESPONSE_WINDOW_MS = 2200L
    }

    private lateinit var binding: ActivityAudiogramBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingTimeoutRunnable: Runnable? = null
    private var controller: HearingTestController? = null
    private var lastResult: AudiogramResult? = null
    private var waitingForResponse = false

    // برای امکان توقف موقت و ادامه آزمون: آخرین کارآزمایی (trial) در حال انجام را نگه می‌داریم
    // تا بتوانیم پس از «ادامه»، همان تن را دوباره از ابتدا پخش کنیم
    private var isPaused = false
    private var pendingEar: Ear? = null
    private var pendingFreqHz: Double = 0.0
    private var pendingAttenuation: Float = 0f

    private var patientName: String = ""
    private var patientAge: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudiogramBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playCalibrationToneButton.setOnClickListener {
            HearingTestTonePlayer.playTone(this, Ear.BOTH, 1000.0, attenuationDb = 20f, durationMs = 2500) {}
        }

        binding.startTestButton.setOnClickListener { startTest() }
        binding.heardButton.setOnClickListener { onUserRespondedHeard() }
        binding.pauseResumeButton.setOnClickListener { onPauseResumeClicked() }
        binding.retakeTestButton.setOnClickListener { showIntroSection() }
        binding.saveAudiogramButton.setOnClickListener { saveAudiogramImage(share = false) }
        binding.shareAudiogramButton.setOnClickListener { saveAudiogramImage(share = true) }
    }

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
            onFinished = { result -> onTestFinished(result) }
        )
        controller = newController
        newController.start()
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

        waitingForResponse = false
        binding.heardButton.isEnabled = false

        HearingTestTonePlayer.playTone(this, ear, freqHz, attenuationDb, TONE_DURATION_MS) {
            // اگر کاربر در همین حین آزمون را موقتاً متوقف کرده باشد، پنجره پاسخ باز نمی‌شود؛
            // با زدن «ادامه» همین کارآزمایی دوباره از ابتدا پخش خواهد شد
            if (isPaused) return@playTone

            // پس از پایان پخش، پنجره زمانی برای دریافت پاسخ باز می‌شود
            waitingForResponse = true
            binding.heardButton.isEnabled = true

            val timeoutRunnable = Runnable {
                if (waitingForResponse) {
                    waitingForResponse = false
                    binding.heardButton.isEnabled = false
                    controller?.onResponse(false)
                }
            }
            pendingTimeoutRunnable = timeoutRunnable
            mainHandler.postDelayed(timeoutRunnable, RESPONSE_WINDOW_MS)
        }
    }

    private fun onPauseResumeClicked() {
        if (isPaused) {
            // ادامه: همان کارآزمایی متوقف‌شده را دوباره از ابتدا پخش می‌کنیم
            isPaused = false
            binding.pauseResumeButton.text = getString(R.string.pause_test)
            binding.pausedStatusText.visibility = View.GONE

            val ear = pendingEar
            if (ear != null) {
                playTestTone(ear, pendingFreqHz, pendingAttenuation)
            }
        } else {
            isPaused = true
            waitingForResponse = false
            binding.heardButton.isEnabled = false
            pendingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }

            binding.pauseResumeButton.text = getString(R.string.resume_test)
            binding.pausedStatusText.visibility = View.VISIBLE
        }
    }

    private fun onUserRespondedHeard() {
        if (!waitingForResponse || isPaused) return
        waitingForResponse = false
        binding.heardButton.isEnabled = false
        pendingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        controller?.onResponse(true)
    }

    private fun onTestFinished(result: AudiogramResult) {
        val fullResult = result.copy(patientName = patientName, patientAge = patientAge)
        lastResult = fullResult
        AudiogramStorage.saveLastResult(this, fullResult)

        binding.testSection.visibility = View.GONE
        binding.resultSection.visibility = View.VISIBLE
        binding.audiogramView.setResult(fullResult)
        updatePatientInfoText(fullResult)
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

    private fun showIntroSection() {
        pendingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        waitingForResponse = false
        isPaused = false
        pendingEar = null
        controller = null

        binding.pauseResumeButton.text = getString(R.string.pause_test)
        binding.pausedStatusText.visibility = View.GONE

        binding.testSection.visibility = View.GONE
        binding.resultSection.visibility = View.GONE
        binding.introSection.visibility = View.VISIBLE
    }

    private fun formatFrequencyLabel(freqHz: Double): String {
        return if (freqHz >= 1000) "${(freqHz / 1000).toInt()} کیلوهرتز" else "${freqHz.toInt()} هرتز"
    }

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
        pendingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        super.onDestroy()
    }
}
