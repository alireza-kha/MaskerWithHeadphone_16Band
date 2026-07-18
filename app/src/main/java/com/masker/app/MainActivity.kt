package com.masker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.masker.app.audio.NoiseEngine
import com.masker.app.databinding.ActivityMainBinding
import com.masker.app.databinding.ItemBandSliderBinding
import com.masker.app.schedule.ScheduleActivity
import com.masker.app.service.PlaybackService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // مقادیر فعلی UI (مستقل از موتور صدا؛ برای رندر ذخیره‌سازی استفاده می‌شود)
    private val bandGains = FloatArray(NoiseEngine.BAND_COUNT)
    private var masterVolume = 0.7f
    private var leftVolume = 1.0f
    private var rightVolume = 1.0f

    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadLastSettings()
        buildBandSliders()
        setupVolumeSliders()
        setupButtons()
        requestNotificationPermissionIfNeeded()
    }

    /** بارگذاری آخرین مقادیر ذخیره‌شده (یا مقدار پیش‌فرض در صورت نبود مقدار قبلی) */
    private fun loadLastSettings() {
        for (i in bandGains.indices) {
            bandGains[i] = SettingsStorage.loadBandGain(this, i, 0.6f)
        }
        masterVolume = SettingsStorage.loadMasterVolume(this, 0.7f)
        leftVolume = SettingsStorage.loadLeftVolume(this, 1.0f)
        rightVolume = SettingsStorage.loadRightVolume(this, 1.0f)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }
    }

    private fun buildBandSliders() {
        val inflater = LayoutInflater.from(this)
        for (i in 0 until NoiseEngine.BAND_COUNT) {
            val rowBinding = ItemBandSliderBinding.inflate(inflater, binding.bandsContainer, false)
            val freq = NoiseEngine.BAND_FREQUENCIES[i]
            rowBinding.bandLabel.text = formatFrequencyLabel(freq)

            val initialProgress = (bandGains[i] * 100).toInt()
            rowBinding.bandSeekBar.progress = initialProgress
            rowBinding.bandEditText.setText(initialProgress.toString())

            // فلگ برای جلوگیری از حلقه بی‌نهایت هنگام همگام‌سازی ترکبار و باکس عددی
            var isUpdatingProgrammatically = false

            rowBinding.bandSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || isUpdatingProgrammatically) return
                    val value = progress / 100f
                    bandGains[i] = value
                    PlaybackService.engine.bandGains[i] = value
                    SettingsStorage.saveBandGain(this@MainActivity, i, value)

                    isUpdatingProgrammatically = true
                    val currentText = rowBinding.bandEditText.text?.toString()
                    if (currentText != progress.toString()) {
                        rowBinding.bandEditText.setText(progress.toString())
                        rowBinding.bandEditText.setSelection(rowBinding.bandEditText.text?.length ?: 0)
                    }
                    isUpdatingProgrammatically = false
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            rowBinding.bandEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isUpdatingProgrammatically) return
                    val raw = s?.toString()?.trim().orEmpty()
                    val typed = raw.toIntOrNull() ?: return
                    val clamped = typed.coerceIn(0, 100)

                    val value = clamped / 100f
                    bandGains[i] = value
                    PlaybackService.engine.bandGains[i] = value
                    SettingsStorage.saveBandGain(this@MainActivity, i, value)

                    isUpdatingProgrammatically = true
                    rowBinding.bandSeekBar.progress = clamped
                    isUpdatingProgrammatically = false
                }
            })

            binding.bandsContainer.addView(rowBinding.root)
        }
    }

    private fun formatFrequencyLabel(freqHz: Double): String {
        return if (freqHz >= 1000) {
            "${(freqHz / 1000).let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }} کیلوهرتز"
        } else {
            "${freqHz.toInt()} هرتز"
        }
    }

    private fun setupVolumeSliders() {
        binding.masterVolumeSeekBar.progress = (masterVolume * 100).toInt()
        binding.masterVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            masterVolume = value
            PlaybackService.engine.masterVolume = value
            SettingsStorage.saveMasterVolume(this, value)
        })

        binding.leftVolumeSeekBar.progress = (leftVolume * 100).toInt()
        binding.leftVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            leftVolume = value
            PlaybackService.engine.leftVolume = value
            SettingsStorage.saveLeftVolume(this, value)
        })

        binding.rightVolumeSeekBar.progress = (rightVolume * 100).toInt()
        binding.rightVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            rightVolume = value
            PlaybackService.engine.rightVolume = value
            SettingsStorage.saveRightVolume(this, value)
        })
    }

    private fun simpleListener(onChange: (Float) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                onChange(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun setupButtons() {
        binding.playStopButton.setOnClickListener {
            if (isPlaying) stopPlayback() else startPlayback()
        }
        binding.saveButton.setOnClickListener { showSaveDurationDialog() }
        binding.scheduleButton.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        binding.helpButton.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
    }

    private fun startPlayback() {
        // انتقال تنظیمات فعلی UI به موتور صدای سرویس
        for (i in bandGains.indices) PlaybackService.engine.bandGains[i] = bandGains[i]
        PlaybackService.engine.masterVolume = masterVolume
        PlaybackService.engine.leftVolume = leftVolume
        PlaybackService.engine.rightVolume = rightVolume

        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isPlaying = true
        binding.playStopButton.text = getString(R.string.stop)
    }

    private fun stopPlayback() {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP
        }
        startService(intent)
        isPlaying = false
        binding.playStopButton.text = getString(R.string.play)
    }

    private fun showSaveDurationDialog() {
        val durations = intArrayOf(30, 60, 300, 600, 1800, 3600)
        val labels = arrayOf("۳۰ ثانیه", "۱ دقیقه", "۵ دقیقه", "۱۰ دقیقه", "۳۰ دقیقه", "۱ ساعت")

        AlertDialog.Builder(this)
            .setTitle(R.string.save_duration_title)
            .setItems(labels) { _, which ->
                renderAndSave(durations[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renderAndSave(durationSeconds: Int) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage(R.string.saving_in_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        thread {
            val engine = NoiseEngine()
            for (i in bandGains.indices) engine.bandGains[i] = bandGains[i]
            engine.masterVolume = masterVolume
            engine.leftVolume = leftVolume
            engine.rightVolume = rightVolume

            val outDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "MaskerSounds")
            if (!outDir.exists()) outDir.mkdirs()

            val fileName = "masker_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".wav"
            val outFile = File(outDir, fileName)

            val success = engine.renderToFile(outFile, durationSeconds)

            runOnUiThread {
                progressDialog.dismiss()
                val message = if (success) {
                    getString(R.string.save_success) + "\n" + outFile.absolutePath
                } else {
                    getString(R.string.save_failed)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
