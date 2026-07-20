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
import com.google.android.material.tabs.TabLayout
import com.masker.app.audio.NoiseEngine
import com.masker.app.audio.TonalEngine
import com.masker.app.audiogram.AudiogramActivity
import com.masker.app.audiogram.AudiogramStorage
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

    // ------- مقادیر تب «ماسکر نویزی» -------
    private val bandGains = FloatArray(NoiseEngine.BAND_COUNT)
    private var masterVolume = 0.7f
    private var leftVolume = 1.0f
    private var rightVolume = 1.0f
    private var isPlaying = false

    // ------- مقادیر تب «ماسکر تونال» -------
    private val toneGains = FloatArray(TonalEngine.TONE_COUNT)
    private var tonalMasterVolume = 0.7f
    private var tonalLeftVolume = 1.0f
    private var tonalRightVolume = 1.0f
    private var isTonalPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadLastSettings()
        buildBandSliders()
        buildToneSliders()
        setupVolumeSliders()
        setupTonalVolumeSliders()
        setupButtons()
        setupTabs()
        updateLastAudiogramSummary()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateLastAudiogramSummary()
    }

    private fun setupTabs() {
        binding.modeTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.noiseTabContent.visibility = if (tab.position == 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.tonalTabContent.visibility = if (tab.position == 1) android.view.View.VISIBLE else android.view.View.GONE
                binding.audiogramTabContent.visibility = if (tab.position == 2) android.view.View.VISIBLE else android.view.View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    /** بارگذاری آخرین مقادیر ذخیره‌شده برای هر دو تب (یا مقدار پیش‌فرض در صورت نبود مقدار قبلی) */
    private fun loadLastSettings() {
        for (i in bandGains.indices) {
            bandGains[i] = SettingsStorage.loadBandGain(this, i, 0.6f)
        }
        masterVolume = SettingsStorage.loadMasterVolume(this, 0.7f)
        leftVolume = SettingsStorage.loadLeftVolume(this, 1.0f)
        rightVolume = SettingsStorage.loadRightVolume(this, 1.0f)

        for (i in toneGains.indices) {
            toneGains[i] = SettingsStorage.loadToneGain(this, i, 0f)
        }
        tonalMasterVolume = SettingsStorage.loadTonalMasterVolume(this, 0.7f)
        tonalLeftVolume = SettingsStorage.loadTonalLeftVolume(this, 1.0f)
        tonalRightVolume = SettingsStorage.loadTonalRightVolume(this, 1.0f)
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

    // ==================== تب «ماسکر نویزی» ====================

    private fun buildBandSliders() {
        val inflater = LayoutInflater.from(this)
        for (i in 0 until NoiseEngine.BAND_COUNT) {
            val rowBinding = ItemBandSliderBinding.inflate(inflater, binding.bandsContainer, false)
            val freq = NoiseEngine.BAND_FREQUENCIES[i]
            rowBinding.bandLabel.text = formatFrequencyLabel(freq)

            val initialProgress = (bandGains[i] * 100).toInt()
            rowBinding.bandSeekBar.progress = initialProgress
            rowBinding.bandEditText.setText(initialProgress.toString())

            var isUpdatingProgrammatically = false

            rowBinding.bandSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || isUpdatingProgrammatically) return
                    val value = progress / 100f
                    bandGains[i] = value
                    PlaybackService.noiseEngine.bandGains[i] = value
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
                    PlaybackService.noiseEngine.bandGains[i] = value
                    SettingsStorage.saveBandGain(this@MainActivity, i, value)

                    isUpdatingProgrammatically = true
                    rowBinding.bandSeekBar.progress = clamped
                    isUpdatingProgrammatically = false
                }
            })

            binding.bandsContainer.addView(rowBinding.root)
        }
    }

    private fun setupVolumeSliders() {
        binding.masterVolumeSeekBar.progress = (masterVolume * 100).toInt()
        binding.masterVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            masterVolume = value
            PlaybackService.noiseEngine.masterVolume = value
            SettingsStorage.saveMasterVolume(this, value)
        })

        binding.leftVolumeSeekBar.progress = (leftVolume * 100).toInt()
        binding.leftVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            leftVolume = value
            PlaybackService.noiseEngine.leftVolume = value
            SettingsStorage.saveLeftVolume(this, value)
        })

        binding.rightVolumeSeekBar.progress = (rightVolume * 100).toInt()
        binding.rightVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            rightVolume = value
            PlaybackService.noiseEngine.rightVolume = value
            SettingsStorage.saveRightVolume(this, value)
        })
    }

    // ==================== تب «ماسکر تونال» ====================

    private fun buildToneSliders() {
        val inflater = LayoutInflater.from(this)
        for (i in 0 until TonalEngine.TONE_COUNT) {
            val rowBinding = ItemBandSliderBinding.inflate(inflater, binding.tonalBandsContainer, false)
            val freq = TonalEngine.TONE_FREQUENCIES[i]
            rowBinding.bandLabel.text = formatFrequencyLabel(freq)

            val initialProgress = (toneGains[i] * 100).toInt()
            rowBinding.bandSeekBar.progress = initialProgress
            rowBinding.bandEditText.setText(initialProgress.toString())

            var isUpdatingProgrammatically = false

            rowBinding.bandSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || isUpdatingProgrammatically) return
                    val value = progress / 100f
                    toneGains[i] = value
                    PlaybackService.tonalEngine.toneGains[i] = value
                    SettingsStorage.saveToneGain(this@MainActivity, i, value)

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
                    toneGains[i] = value
                    PlaybackService.tonalEngine.toneGains[i] = value
                    SettingsStorage.saveToneGain(this@MainActivity, i, value)

                    isUpdatingProgrammatically = true
                    rowBinding.bandSeekBar.progress = clamped
                    isUpdatingProgrammatically = false
                }
            })

            binding.tonalBandsContainer.addView(rowBinding.root)
        }
    }

    private fun setupTonalVolumeSliders() {
        binding.tonalMasterVolumeSeekBar.progress = (tonalMasterVolume * 100).toInt()
        binding.tonalMasterVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            tonalMasterVolume = value
            PlaybackService.tonalEngine.masterVolume = value
            SettingsStorage.saveTonalMasterVolume(this, value)
        })

        binding.tonalLeftVolumeSeekBar.progress = (tonalLeftVolume * 100).toInt()
        binding.tonalLeftVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            tonalLeftVolume = value
            PlaybackService.tonalEngine.leftVolume = value
            SettingsStorage.saveTonalLeftVolume(this, value)
        })

        binding.tonalRightVolumeSeekBar.progress = (tonalRightVolume * 100).toInt()
        binding.tonalRightVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            tonalRightVolume = value
            PlaybackService.tonalEngine.rightVolume = value
            SettingsStorage.saveTonalRightVolume(this, value)
        })
    }

    // ==================== مشترک ====================

    private fun formatFrequencyLabel(freqHz: Double): String {
        return if (freqHz >= 1000) {
            "${(freqHz / 1000).let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }} کیلوهرتز"
        } else {
            "${freqHz.toInt()} هرتز"
        }
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
            if (isPlaying) stopPlayback(PlaybackService.MODE_NOISE) else startPlayback(PlaybackService.MODE_NOISE)
        }
        binding.saveButton.setOnClickListener { showSaveDurationDialog(PlaybackService.MODE_NOISE) }
        binding.scheduleButton.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        binding.helpButton.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        binding.tonalPlayStopButton.setOnClickListener {
            if (isTonalPlaying) stopPlayback(PlaybackService.MODE_TONAL) else startPlayback(PlaybackService.MODE_TONAL)
        }
        binding.tonalSaveButton.setOnClickListener { showSaveDurationDialog(PlaybackService.MODE_TONAL) }

        binding.openAudiogramButton.setOnClickListener {
            startActivity(Intent(this, AudiogramActivity::class.java))
        }
    }

    private fun updateLastAudiogramSummary() {
        val last = AudiogramStorage.loadLastResult(this)
        if (last != null) {
            val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(last.timestampMillis))
            binding.lastAudiogramText.text = getString(R.string.last_audiogram_format, dateStr)
            binding.lastAudiogramText.visibility = android.view.View.VISIBLE
        } else {
            binding.lastAudiogramText.visibility = android.view.View.GONE
        }
    }

    private fun startPlayback(mode: String) {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START
            putExtra(PlaybackService.EXTRA_MODE, mode)
        }

        if (mode == PlaybackService.MODE_TONAL) {
            for (i in toneGains.indices) PlaybackService.tonalEngine.toneGains[i] = toneGains[i]
            PlaybackService.tonalEngine.masterVolume = tonalMasterVolume
            PlaybackService.tonalEngine.leftVolume = tonalLeftVolume
            PlaybackService.tonalEngine.rightVolume = tonalRightVolume
        } else {
            for (i in bandGains.indices) PlaybackService.noiseEngine.bandGains[i] = bandGains[i]
            PlaybackService.noiseEngine.masterVolume = masterVolume
            PlaybackService.noiseEngine.leftVolume = leftVolume
            PlaybackService.noiseEngine.rightVolume = rightVolume
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        if (mode == PlaybackService.MODE_TONAL) {
            isTonalPlaying = true
            isPlaying = false
            binding.tonalPlayStopButton.text = getString(R.string.stop)
            binding.playStopButton.text = getString(R.string.play)
        } else {
            isPlaying = true
            isTonalPlaying = false
            binding.playStopButton.text = getString(R.string.stop)
            binding.tonalPlayStopButton.text = getString(R.string.play)
        }
    }

    private fun stopPlayback(mode: String) {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP
        }
        startService(intent)

        if (mode == PlaybackService.MODE_TONAL) {
            isTonalPlaying = false
            binding.tonalPlayStopButton.text = getString(R.string.play)
        } else {
            isPlaying = false
            binding.playStopButton.text = getString(R.string.play)
        }
    }

    private fun showSaveDurationDialog(mode: String) {
        val durations = intArrayOf(30, 60, 300, 600, 1800, 3600)
        val labels = arrayOf("۳۰ ثانیه", "۱ دقیقه", "۵ دقیقه", "۱۰ دقیقه", "۳۰ دقیقه", "۱ ساعت")

        AlertDialog.Builder(this)
            .setTitle(R.string.save_duration_title)
            .setItems(labels) { _, which ->
                renderAndSave(durations[which], mode)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renderAndSave(durationSeconds: Int, mode: String) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage(R.string.saving_in_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        thread {
            val outDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "MaskerSounds")
            if (!outDir.exists()) outDir.mkdirs()

            val prefix = if (mode == PlaybackService.MODE_TONAL) "masker_tonal_" else "masker_"
            val fileName = prefix + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".wav"
            val outFile = File(outDir, fileName)

            val success = if (mode == PlaybackService.MODE_TONAL) {
                val engine = TonalEngine()
                for (i in toneGains.indices) engine.toneGains[i] = toneGains[i]
                engine.masterVolume = tonalMasterVolume
                engine.leftVolume = tonalLeftVolume
                engine.rightVolume = tonalRightVolume
                engine.renderToFile(outFile, durationSeconds)
            } else {
                val engine = NoiseEngine()
                for (i in bandGains.indices) engine.bandGains[i] = bandGains[i]
                engine.masterVolume = masterVolume
                engine.leftVolume = leftVolume
                engine.rightVolume = rightVolume
                engine.renderToFile(outFile, durationSeconds)
            }

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
