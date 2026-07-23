package com.masker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import com.masker.app.audio.NoiseEngine
import com.masker.app.audio.TonalEngine
import com.masker.app.audiogram.AudiogramActivity
import com.masker.app.audiogram.AudiogramGalleryActivity
import com.masker.app.audiogram.AudiogramNoiseShaper
import com.masker.app.audiogram.AudiogramResult
import com.masker.app.audiogram.AudiogramStorage
import com.masker.app.databinding.ActivityMainBinding
import com.masker.app.databinding.ItemBandSliderBinding
import com.masker.app.databinding.ItemEqBandSliderBinding
import com.masker.app.playlist.PlaylistAdapter
import com.masker.app.playlist.PlaylistPlaybackService
import com.masker.app.playlist.PlaylistPlayerEngine
import com.masker.app.playlist.PlaylistStorage
import com.masker.app.playlist.PlaylistThumbnails
import com.masker.app.playlist.PlaylistTrack
import com.masker.app.report.PatientReport
import com.masker.app.report.ReportQueueStorage
import com.masker.app.report.ReportSendManager
import com.masker.app.report.SheetsReportSender
import com.masker.app.report.TinnitusScoreDialog
import com.masker.app.schedule.ScheduleActivity
import com.masker.app.service.PlaybackService
import com.masker.app.storage.MaskerStorage
import com.masker.app.ui.MessageDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // ------- مقادیر تب «ماسکر نویزی» -------
    private val bandGains = FloatArray(NoiseEngine.BAND_COUNT)
    private var masterVolume = 0.7f
    private var leftVolume = 1.0f
    private var rightVolume = 1.0f
    private var isPlaying = false

    // ------- حذف فرکانس وزوز (Notch) - فقط برای تب نویزی -------
    private var notchEnabled = false
    private var notchFrequencyHz = 4000.0
    private var notchWidthOctaves = 0.5f
    private val notchWidthValues = floatArrayOf(0.5f, 1.0f, 2.0f)

    // ------- مدولاسیون دامنه ۱۰ هرتز - فقط برای تب نویزی -------
    private var modulationEnabled = false
    private var modulationDepth = 0.6f

    // ردیف‌های اسلایدر باند نویز، برای امکان به‌روزرسانی برنامه‌ای (مثلاً پس از بهینه‌سازی خودکار)
    private val bandRowBindings = mutableListOf<ItemBandSliderBinding>()

    // ------- مقادیر تب «ماسکر تونال» -------
    private val toneGains = FloatArray(TonalEngine.TONE_COUNT)
    private var tonalMasterVolume = 0.7f
    private var tonalLeftVolume = 1.0f
    private var tonalRightVolume = 1.0f
    private var isTonalPlaying = false

    // ------- تب «پلی‌لیست» -------
    private lateinit var playlistAdapter: PlaylistAdapter
    private val eqRightRowBindings = mutableListOf<ItemEqBandSliderBinding>()
    private val eqLeftRowBindings = mutableListOf<ItemEqBandSliderBinding>()
    private var playlistNotchEnabled = false
    private var playlistNotchFrequencyHz = 4000.0
    private var playlistNotchWidthOctaves = 0.5f
    private var userIsDraggingPlaylistSeekBar = false

    private val playlistUiHandler = Handler(Looper.getMainLooper())
    private val playlistUiUpdater = object : Runnable {
        override fun run() {
            updatePlaylistPlaybackUi()
            playlistUiHandler.postDelayed(this, 500)
        }
    }

    private val pickAudioFilesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) importPlaylistFiles(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadLastSettings()
        buildBandSliders()
        buildToneSliders()
        setupVolumeSliders()
        setupTonalVolumeSliders()
        setupNotchControls()
        setupOptimizeButton()
        setupModulationControls()
        setupButtons()
        setupTabs()
        setupPlaylistTab()
        updateLastAudiogramSummary()
        requestNotificationPermissionIfNeeded()
        requestStoragePermissionIfNeeded()
        ReportSendManager.flushPending(this)
    }

    override fun onResume() {
        super.onResume()
        updateLastAudiogramSummary()
        refreshPlaylistUI()
        playlistUiHandler.post(playlistUiUpdater)
    }

    override fun onPause() {
        super.onPause()
        playlistUiHandler.removeCallbacks(playlistUiUpdater)
    }

    private fun setupTabs() {
        binding.modeTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.noiseTabContent.visibility = if (tab.position == 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.tonalTabContent.visibility = if (tab.position == 1) android.view.View.VISIBLE else android.view.View.GONE
                binding.audiogramTabContent.visibility = if (tab.position == 2) android.view.View.VISIBLE else android.view.View.GONE
                binding.playlistTabContent.visibility = if (tab.position == 3) android.view.View.VISIBLE else android.view.View.GONE
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

        notchEnabled = SettingsStorage.loadNotchEnabled(this)
        notchFrequencyHz = SettingsStorage.loadNotchFrequency(this, 4000.0)
        notchWidthOctaves = SettingsStorage.loadNotchWidth(this, 0.5f)
        PlaybackService.noiseEngine.setNotch(notchEnabled, notchFrequencyHz, notchWidthOctaves)

        modulationEnabled = SettingsStorage.loadModulationEnabled(this)
        modulationDepth = SettingsStorage.loadModulationDepth(this, 0.6f)
        PlaybackService.noiseEngine.modulationEnabled = modulationEnabled
        PlaybackService.noiseEngine.modulationDepth = modulationDepth

        playlistNotchEnabled = SettingsStorage.loadPlaylistNotchEnabled(this)
        playlistNotchFrequencyHz = SettingsStorage.loadPlaylistNotchFrequency(this, 4000.0)
        playlistNotchWidthOctaves = SettingsStorage.loadPlaylistNotchWidth(this, 0.5f)
        PlaylistPlaybackService.engine.setNotch(playlistNotchEnabled, playlistNotchFrequencyHz, playlistNotchWidthOctaves)

        PlaylistPlaybackService.engine.leftVolume = SettingsStorage.loadPlaylistLeftVolume(this, 1.0f)
        PlaylistPlaybackService.engine.rightVolume = SettingsStorage.loadPlaylistRightVolume(this, 1.0f)
        for (band in 0 until PlaylistPlayerEngine.EQ_BAND_COUNT) {
            val rightGain = SettingsStorage.loadPlaylistRightEqGain(this, band, 0f)
            val leftGain = SettingsStorage.loadPlaylistLeftEqGain(this, band, 0f)
            PlaylistPlaybackService.engine.setRightEqBandGain(band, rightGain)
            PlaylistPlaybackService.engine.setLeftEqBandGain(band, leftGain)
        }
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

    /**
     * درخواست مجوز دسترسی کامل به حافظه (در اندروید ۱۱ به بالا) تا سوابق، عکس‌ها، صداهای
     * ذخیره‌شده و پلی‌لیست موسیقی در پوشه عمومی Documents/Masker نگه‌داری شوند و با حذف یا
     * نصب مجدد برنامه از بین نروند. بدون این مجوز، برنامه همچنان کار می‌کند اما این اطلاعات
     * فقط در حافظه اختصاصی برنامه (که با حذف نصب پاک می‌شود) ذخیره خواهند شد.
     */
    private fun requestStoragePermissionIfNeeded() {
        if (MaskerStorage.hasPermission(this)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_title)
            .setMessage(R.string.storage_permission_message)
            .setCancelable(true)
            .setPositiveButton(R.string.storage_permission_grant) { _, _ ->
                MaskerStorage.requestPermission(this)
            }
            .setNegativeButton(R.string.skip, null)
            .show()
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
            bandRowBindings.add(rowBinding)
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

    // ==================== حذف فرکانس وزوز (Notch) ====================

    private fun setupNotchControls() {
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.notch_width_options, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.notchWidthSpinner.adapter = adapter

        val savedWidthIndex = notchWidthValues.indexOfFirst { it == notchWidthOctaves }.let { if (it >= 0) it else 0 }
        binding.notchWidthSpinner.setSelection(savedWidthIndex)

        binding.notchFrequencyEditText.setText(notchFrequencyHz.toInt().toString())
        binding.toggleNotchButton.text = getString(if (notchEnabled) R.string.disable_notch else R.string.enable_notch)
        updateNotchStatusText()

        binding.toggleNotchButton.setOnClickListener {
            if (notchEnabled) {
                notchEnabled = false
                PlaybackService.noiseEngine.setNotch(false, notchFrequencyHz, notchWidthOctaves)
                SettingsStorage.saveNotchSettings(this, false, notchFrequencyHz, notchWidthOctaves)
                binding.toggleNotchButton.text = getString(R.string.enable_notch)
                updateNotchStatusText()
            } else {
                val freqText = binding.notchFrequencyEditText.text?.toString()?.trim().orEmpty()
                val freq = freqText.toDoubleOrNull()
                if (freq == null || freq < 60.0 || freq > 16000.0) {
                    MessageDialog.show(this, R.string.notch_invalid_frequency)
                    return@setOnClickListener
                }
                val widthIndex = binding.notchWidthSpinner.selectedItemPosition
                val width = notchWidthValues.getOrElse(widthIndex) { 0.5f }

                notchEnabled = true
                notchFrequencyHz = freq
                notchWidthOctaves = width
                PlaybackService.noiseEngine.setNotch(true, freq, width)
                SettingsStorage.saveNotchSettings(this, true, freq, width)
                binding.toggleNotchButton.text = getString(R.string.disable_notch)
                updateNotchStatusText()
            }
        }

        binding.notchFromAudiogramButton.setOnClickListener {
            val result = AudiogramStorage.loadLastResult(this)
            val suggestedFreq = result?.let { findSuggestedNotchFrequency(it) }
            if (suggestedFreq == null) {
                MessageDialog.show(this, R.string.notch_no_audiogram)
                return@setOnClickListener
            }

            binding.notchFrequencyEditText.setText(suggestedFreq.toInt().toString())
            MessageDialog.show(this, getString(R.string.notch_frequency_from_audiogram_toast, suggestedFreq.toInt().toString()))
        }
    }

    /**
     * فرکانسی که بدترین آستانه شنوایی (نیاز به بلندترین صدا) را در هر یک از دو گوش دارد،
     * به‌عنوان برآورد اولیه فرکانس وزوز استفاده می‌شود؛ هم برای Notch تب ماسکر نویزی و هم
     * برای Notch تب پلی‌لیست به‌کار می‌رود.
     */
    private fun findSuggestedNotchFrequency(result: AudiogramResult): Double? {
        var suggestedFreq: Double? = null
        var worstThreshold = Float.POSITIVE_INFINITY
        for (i in result.frequenciesHz.indices) {
            val r = result.rightThresholdsDb.getOrNull(i)?.takeIf { !it.isNaN() }
            val l = result.leftThresholdsDb.getOrNull(i)?.takeIf { !it.isNaN() }
            val worseOfTwo = listOfNotNull(r, l).minOrNull()
            if (worseOfTwo != null && worseOfTwo < worstThreshold) {
                worstThreshold = worseOfTwo
                suggestedFreq = result.frequenciesHz[i]
            }
        }
        return suggestedFreq
    }

    private fun updateNotchStatusText() {
        binding.notchStatusText.text = if (notchEnabled) {
            val widthLabels = resources.getStringArray(R.array.notch_width_options)
            val widthIndex = notchWidthValues.indexOfFirst { it == notchWidthOctaves }.coerceAtLeast(0)
            val widthLabel = widthLabels.getOrElse(widthIndex) { widthLabels[0] }
            getString(R.string.notch_enabled_status_format, notchFrequencyHz.toInt().toString(), widthLabel)
        } else {
            getString(R.string.notch_disabled_status)
        }
    }

    /**
     * بهینه‌سازی خودکار شدت ۱۶ باند نویز بر اساس نتیجه آخرین آزمون اودیوگرام کاربر
     * (ایده «نویز شکل‌داده‌شده بر اساس افت شنوایی» / Enriched Acoustic Environment).
     */
    private fun setupOptimizeButton() {
        binding.optimizeFromAudiogramButton.setOnClickListener {
            val result = AudiogramStorage.loadLastResult(this)
            if (result == null) {
                MessageDialog.show(this, R.string.optimize_no_audiogram)
                return@setOnClickListener
            }

            val newGains = AudiogramNoiseShaper.computeBandGains(result, NoiseEngine.BAND_FREQUENCIES)
            for (i in newGains.indices) {
                if (i >= bandRowBindings.size) break
                val progress = (newGains[i] * 100).toInt().coerceIn(0, 100)
                // تنظیم متن باکس عددی هر ردیف، که به‌طور خودکار اسلایدر، آرایه bandGains،
                // موتور صدا و حافظه ذخیره‌سازی را هم به‌روزرسانی می‌کند (از طریق TextWatcher موجود)
                bandRowBindings[i].bandEditText.setText(progress.toString())
            }

            MessageDialog.show(this, R.string.optimize_applied_toast)
        }
    }

    /** تنظیمات مدولاسیون دامنه ۱۰ هرتز (بر پایه پژوهش Neff و همکاران، ۲۰۱۷) */
    private fun setupModulationControls() {
        binding.modulationDepthSeekBar.progress = (modulationDepth * 100).toInt()
        binding.toggleModulationButton.text = getString(
            if (modulationEnabled) R.string.disable_modulation else R.string.enable_modulation
        )
        updateModulationStatusText()

        binding.modulationDepthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                modulationDepth = progress / 100f
                PlaybackService.noiseEngine.modulationDepth = modulationDepth
                SettingsStorage.saveModulationSettings(this@MainActivity, modulationEnabled, modulationDepth)
                updateModulationStatusText()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.toggleModulationButton.setOnClickListener {
            modulationEnabled = !modulationEnabled
            PlaybackService.noiseEngine.modulationEnabled = modulationEnabled
            SettingsStorage.saveModulationSettings(this, modulationEnabled, modulationDepth)
            binding.toggleModulationButton.text = getString(
                if (modulationEnabled) R.string.disable_modulation else R.string.enable_modulation
            )
            updateModulationStatusText()
        }
    }

    private fun updateModulationStatusText() {
        binding.modulationStatusText.text = if (modulationEnabled) {
            val depthPercent = (modulationDepth * 100).toInt().toString()
            getString(R.string.modulation_enabled_status_format, depthPercent)
        } else {
            getString(R.string.modulation_disabled_status)
        }
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
        binding.viewAudiogramGalleryButton.setOnClickListener {
            startActivity(Intent(this, AudiogramGalleryActivity::class.java))
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
            PlaybackService.noiseEngine.setNotch(notchEnabled, notchFrequencyHz, notchWidthOctaves)
            PlaybackService.noiseEngine.modulationEnabled = modulationEnabled
            PlaybackService.noiseEngine.modulationDepth = modulationDepth
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

        TinnitusScoreDialog.show(this) { left, right -> sendMaskerCheckpointReport(left, right) }
    }

    /**
     * هر بار که کاربر پخش ماسکر (نویزی یا تونال) را متوقف می‌کند، نمره ذهنی شدت وزوز هر
     * گوش پرسیده و به‌همراه آخرین وضعیت اودیوگرام و تنظیمات فعلی ماسکر، برای بررسی‌های
     * آزمایشگاهی برای سازنده ارسال (یا در نبود اینترنت، برای ارسال بعدی ذخیره) می‌شود؛
     * دقیقاً مثل توقف موقت آزمون اودیوگرام.
     */
    private fun sendMaskerCheckpointReport(leftScore: Int?, rightScore: Int?) {
        val lastAudiogram = AudiogramStorage.loadLastResult(this)
        val report = PatientReport.build(
            context = this,
            patientName = lastAudiogram?.patientName.orEmpty(),
            patientAge = lastAudiogram?.patientAge ?: 0,
            audiogramResult = lastAudiogram,
            leftTinnitusScore = leftScore,
            rightTinnitusScore = rightScore
        )

        if (!SheetsReportSender.isConfigured()) {
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
            val outDir = MaskerStorage.soundsDir(this@MainActivity)

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
                engine.setNotch(notchEnabled, notchFrequencyHz, notchWidthOctaves)
                engine.modulationEnabled = modulationEnabled
                engine.modulationDepth = modulationDepth
                engine.renderToFile(outFile, durationSeconds)
            }

            runOnUiThread {
                progressDialog.dismiss()
                val message = if (success) {
                    getString(R.string.save_success) + "\n" + outFile.absolutePath
                } else {
                    getString(R.string.save_failed)
                }
                MessageDialog.show(this, message)
            }
        }
    }

    // ==================== تب «پلی‌لیست» ====================

    private fun setupPlaylistTab() {
        playlistAdapter = PlaylistAdapter(
            mutableListOf(),
            onClick = { position -> playPlaylistTrack(position) },
            onLongPress = { position -> confirmRemovePlaylistTrack(position) }
        )
        val screenWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val spanCount = (screenWidthDp / 78).toInt().coerceAtLeast(3)
        binding.playlistRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
        binding.playlistRecyclerView.adapter = playlistAdapter

        binding.addPlaylistFileButton.setOnClickListener {
            pickAudioFilesLauncher.launch(arrayOf("audio/*"))
        }

        binding.playlistPrevButton.setOnClickListener { sendPlaylistAction(PlaylistPlaybackService.ACTION_PREV) }
        binding.playlistNextButton.setOnClickListener { sendPlaylistAction(PlaylistPlaybackService.ACTION_NEXT) }
        binding.playlistStopButton.setOnClickListener { sendPlaylistAction(PlaylistPlaybackService.ACTION_STOP) }
        binding.playlistPlayPauseButton.setOnClickListener {
            if (!PlaylistPlaybackService.engine.isPlaying) {
                if (PlaylistPlaybackService.tracks.isNotEmpty()) {
                    val startIndex = PlaylistPlaybackService.currentIndex.takeIf { it >= 0 } ?: 0
                    playPlaylistTrack(startIndex)
                }
            } else {
                sendPlaylistAction(PlaylistPlaybackService.ACTION_PAUSE_RESUME)
            }
        }

        binding.playlistSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userIsDraggingPlaylistSeekBar = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userIsDraggingPlaylistSeekBar = false
                val duration = PlaylistPlaybackService.engine.durationMs
                if (duration > 0) {
                    val targetMs = duration * (seekBar?.progress ?: 0) / 1000
                    PlaylistPlaybackService.engine.seekTo(this@MainActivity, targetMs)
                }
            }
        })

        setupPlaylistVolumeControls()
        setupPlaylistSpeedControl()
        setupPlaylistEqualizer()
        setupPlaylistNotchControls()
        refreshPlaylistUI()
    }

    /** ولوم جداگانه گوش چپ و راست برای پلی‌لیست، دقیقاً مثل تب ماسکر نویزی */
    private fun setupPlaylistVolumeControls() {
        val engine = PlaylistPlaybackService.engine

        binding.playlistLeftVolumeSeekBar.progress = (engine.leftVolume * 100).toInt()
        binding.playlistLeftVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            PlaylistPlaybackService.engine.leftVolume = value
            SettingsStorage.savePlaylistLeftVolume(this, value)
        })

        binding.playlistRightVolumeSeekBar.progress = (engine.rightVolume * 100).toInt()
        binding.playlistRightVolumeSeekBar.setOnSeekBarChangeListener(simpleListener { value ->
            PlaylistPlaybackService.engine.rightVolume = value
            SettingsStorage.savePlaylistRightVolume(this, value)
        })
    }

    private fun setupPlaylistSpeedControl() {
        val engine = PlaylistPlaybackService.engine
        binding.playlistSpeedSeekBar.progress = (engine.speed * 100).toInt() - 50
        updatePlaylistSpeedLabel(engine.speed)
        binding.playlistSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val newSpeed = (progress + 50) / 100f
                PlaylistPlaybackService.engine.speed = newSpeed
                updatePlaylistSpeedLabel(newSpeed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePlaylistSpeedLabel(speed: Float) {
        binding.playlistSpeedValueText.text = getString(R.string.playlist_speed_format, speed)
    }

    private fun setupPlaylistEqualizer() {
        val inflater = LayoutInflater.from(this)
        val engine = PlaylistPlaybackService.engine

        for (band in 0 until PlaylistPlayerEngine.EQ_BAND_COUNT) {
            val rowBinding = ItemEqBandSliderBinding.inflate(inflater, binding.playlistEqRightContainer, false)
            bindPlaylistEqRow(rowBinding, band, engine.rightEqBandGainsDb[band]) { newGain ->
                PlaylistPlaybackService.engine.setRightEqBandGain(band, newGain)
                SettingsStorage.savePlaylistRightEqGain(this, band, newGain)
            }
            binding.playlistEqRightContainer.addView(rowBinding.root)
            eqRightRowBindings.add(rowBinding)
        }

        for (band in 0 until PlaylistPlayerEngine.EQ_BAND_COUNT) {
            val rowBinding = ItemEqBandSliderBinding.inflate(inflater, binding.playlistEqLeftContainer, false)
            bindPlaylistEqRow(rowBinding, band, engine.leftEqBandGainsDb[band]) { newGain ->
                PlaylistPlaybackService.engine.setLeftEqBandGain(band, newGain)
                SettingsStorage.savePlaylistLeftEqGain(this, band, newGain)
            }
            binding.playlistEqLeftContainer.addView(rowBinding.root)
            eqLeftRowBindings.add(rowBinding)
        }

        binding.playlistEqFromAudiogramButton.setOnClickListener { applyPlaylistEqFromAudiogram() }
    }

    private fun bindPlaylistEqRow(
        rowBinding: ItemEqBandSliderBinding,
        band: Int,
        initialGainDb: Float,
        onChanged: (Float) -> Unit
    ) {
        rowBinding.eqBandLabel.text = formatFrequencyLabel(PlaylistPlayerEngine.EQ_BAND_FREQUENCIES[band])
        rowBinding.eqBandSeekBar.progress = (initialGainDb + 15).toInt()
        rowBinding.eqBandValueText.text = getString(R.string.playlist_eq_gain_format, initialGainDb.toInt())

        rowBinding.eqBandSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val newGain = (progress - 15).toFloat()
                onChanged(newGain)
                rowBinding.eqBandValueText.text = getString(R.string.playlist_eq_gain_format, newGain.toInt())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * تنظیم خودکار اکولایزر مستقل هر گوش بر اساس نتیجه آخرین آزمون اودیوگرام: فرکانس‌هایی که
     * در آن‌ها افت شنوایی بیشتری وجود دارد (آستانه پایین‌تر)، با شدت بیشتری تقویت می‌شوند —
     * فقط تقویت مثبت اعمال می‌شود، نه کاهش فرکانس‌های شنوایی خوب.
     */
    private fun applyPlaylistEqFromAudiogram() {
        val result = AudiogramStorage.loadLastResult(this)
        if (result == null) {
            MessageDialog.show(this, R.string.optimize_no_audiogram)
            return
        }

        val rightGains = AudiogramNoiseShaper.computeEarEqBoostDb(
            result.rightThresholdsDb, result.frequenciesHz, PlaylistPlayerEngine.EQ_BAND_FREQUENCIES
        )
        val leftGains = AudiogramNoiseShaper.computeEarEqBoostDb(
            result.leftThresholdsDb, result.frequenciesHz, PlaylistPlayerEngine.EQ_BAND_FREQUENCIES
        )

        for (band in rightGains.indices) {
            PlaylistPlaybackService.engine.setRightEqBandGain(band, rightGains[band])
            SettingsStorage.savePlaylistRightEqGain(this, band, rightGains[band])
        }
        for (band in leftGains.indices) {
            PlaylistPlaybackService.engine.setLeftEqBandGain(band, leftGains[band])
            SettingsStorage.savePlaylistLeftEqGain(this, band, leftGains[band])
        }

        refreshPlaylistEqualizerUI()
        MessageDialog.show(this, R.string.playlist_eq_applied_toast)
    }

    private fun refreshPlaylistEqualizerUI() {
        val engine = PlaylistPlaybackService.engine
        for (band in eqRightRowBindings.indices) {
            val gainDb = engine.rightEqBandGainsDb.getOrNull(band) ?: continue
            eqRightRowBindings[band].eqBandSeekBar.progress = (gainDb + 15).toInt()
            eqRightRowBindings[band].eqBandValueText.text = getString(R.string.playlist_eq_gain_format, gainDb.toInt())
        }
        for (band in eqLeftRowBindings.indices) {
            val gainDb = engine.leftEqBandGainsDb.getOrNull(band) ?: continue
            eqLeftRowBindings[band].eqBandSeekBar.progress = (gainDb + 15).toInt()
            eqLeftRowBindings[band].eqBandValueText.text = getString(R.string.playlist_eq_gain_format, gainDb.toInt())
        }
    }

    private fun setupPlaylistNotchControls() {
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.notch_width_options, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.playlistNotchWidthSpinner.adapter = adapter

        val savedWidthIndex = notchWidthValues.indexOfFirst { it == playlistNotchWidthOctaves }.let { if (it >= 0) it else 0 }
        binding.playlistNotchWidthSpinner.setSelection(savedWidthIndex)
        binding.playlistNotchFrequencyEditText.setText(playlistNotchFrequencyHz.toInt().toString())
        binding.playlistToggleNotchButton.text = getString(if (playlistNotchEnabled) R.string.disable_notch else R.string.enable_notch)
        updatePlaylistNotchStatusText()

        binding.playlistToggleNotchButton.setOnClickListener {
            if (playlistNotchEnabled) {
                playlistNotchEnabled = false
                PlaylistPlaybackService.engine.setNotch(false, playlistNotchFrequencyHz, playlistNotchWidthOctaves)
                SettingsStorage.savePlaylistNotchSettings(this, false, playlistNotchFrequencyHz, playlistNotchWidthOctaves)
                binding.playlistToggleNotchButton.text = getString(R.string.enable_notch)
                updatePlaylistNotchStatusText()
            } else {
                val freqText = binding.playlistNotchFrequencyEditText.text?.toString()?.trim().orEmpty()
                val freq = freqText.toDoubleOrNull()
                if (freq == null || freq < 60.0 || freq > 16000.0) {
                    MessageDialog.show(this, R.string.notch_invalid_frequency)
                    return@setOnClickListener
                }
                val widthIndex = binding.playlistNotchWidthSpinner.selectedItemPosition
                val width = notchWidthValues.getOrElse(widthIndex) { 0.5f }

                playlistNotchEnabled = true
                playlistNotchFrequencyHz = freq
                playlistNotchWidthOctaves = width
                PlaylistPlaybackService.engine.setNotch(true, freq, width)
                SettingsStorage.savePlaylistNotchSettings(this, true, freq, width)
                binding.playlistToggleNotchButton.text = getString(R.string.disable_notch)
                updatePlaylistNotchStatusText()
            }
        }

        binding.playlistNotchFromAudiogramButton.setOnClickListener {
            val result = AudiogramStorage.loadLastResult(this)
            val suggestedFreq = result?.let { findSuggestedNotchFrequency(it) }
            if (suggestedFreq == null) {
                MessageDialog.show(this, R.string.notch_no_audiogram)
                return@setOnClickListener
            }

            binding.playlistNotchFrequencyEditText.setText(suggestedFreq.toInt().toString())
            MessageDialog.show(this, getString(R.string.notch_frequency_from_audiogram_toast, suggestedFreq.toInt().toString()))
        }
    }

    private fun updatePlaylistNotchStatusText() {
        binding.playlistNotchStatusText.text = if (playlistNotchEnabled) {
            val widthLabels = resources.getStringArray(R.array.notch_width_options)
            val widthIndex = notchWidthValues.indexOfFirst { it == playlistNotchWidthOctaves }.coerceAtLeast(0)
            val widthLabel = widthLabels.getOrElse(widthIndex) { widthLabels[0] }
            getString(R.string.notch_enabled_status_format, playlistNotchFrequencyHz.toInt().toString(), widthLabel)
        } else {
            getString(R.string.notch_disabled_status)
        }
    }

    private fun playPlaylistTrack(position: Int) {
        if (position < 0 || position >= PlaylistPlaybackService.tracks.size) return
        val intent = Intent(this, PlaylistPlaybackService::class.java).apply {
            action = PlaylistPlaybackService.ACTION_PLAY_INDEX
            putExtra(PlaylistPlaybackService.EXTRA_INDEX, position)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * برای دکمه‌های کنترلی (توقف/قبلی/بعدی/مکث-ادامه) همیشه startService ساده استفاده می‌شود
     * (نه startForegroundService)، چون این اکشن‌ها همیشه startForeground را فراخوانی نمی‌کنند
     * (مثلاً وقتی چیزی در حال پخش نیست) و در آن صورت startForegroundService باعث کرش می‌شد.
     */
    private fun sendPlaylistAction(action: String) {
        val intent = Intent(this, PlaylistPlaybackService::class.java).apply { this.action = action }
        startService(intent)
    }

    private fun confirmRemovePlaylistTrack(position: Int) {
        val track = PlaylistPlaybackService.tracks.getOrNull(position) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.playlist_remove_confirm, track.title))
            .setPositiveButton(R.string.delete) { _, _ -> removePlaylistTrack(position) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun removePlaylistTrack(position: Int) {
        val track = PlaylistPlaybackService.tracks.getOrNull(position) ?: return
        val isCurrentlyPlaying = position == PlaylistPlaybackService.currentIndex
        if (isCurrentlyPlaying) {
            sendPlaylistAction(PlaylistPlaybackService.ACTION_STOP)
        }
        PlaylistStorage.removeTrack(this, track.id)
        refreshPlaylistUI()
    }

    private fun importPlaylistFiles(uris: List<Uri>) {
        thread {
            var addedCount = 0
            for (uri in uris) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
                val displayName = queryDisplayName(uri) ?: "track_${System.currentTimeMillis()}"
                val destFileName = "${System.currentTimeMillis()}_${sanitizeFileName(displayName)}"
                val destFile = File(MaskerStorage.playlistDir(this@MainActivity), destFileName)
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val track = PlaylistTrack(
                        id = UUID.randomUUID().toString(),
                        fileName = destFile.name,
                        title = displayName.substringBeforeLast('.'),
                        addedAtMillis = System.currentTimeMillis()
                    )
                    PlaylistStorage.addTrack(this@MainActivity, track)
                    PlaylistThumbnails.extractAndSave(this@MainActivity, destFile, track.id)
                    addedCount++
                } catch (_: Exception) {
                    try {
                        destFile.delete()
                    } catch (_: Exception) {
                    }
                }
            }
            runOnUiThread {
                refreshPlaylistUI()
                if (addedCount == 0) {
                    MessageDialog.show(this, R.string.playlist_import_failed)
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9._\\-\\u0600-\\u06FF ]"), "_")
    }

    private fun refreshPlaylistUI() {
        PlaylistPlaybackService.tracks = PlaylistStorage.loadTracks(this).toMutableList()
        playlistAdapter.updateData(PlaylistPlaybackService.tracks)
        binding.playlistEmptyText.visibility =
            if (PlaylistPlaybackService.tracks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        updatePlaylistPlaybackUi()
    }

    private fun updatePlaylistPlaybackUi() {
        val engine = PlaylistPlaybackService.engine
        val currentTrack = PlaylistPlaybackService.tracks.getOrNull(PlaylistPlaybackService.currentIndex)

        playlistAdapter.playingTrackId = if (engine.isPlaying) currentTrack?.id else null

        binding.nowPlayingTrackText.text = currentTrack?.title ?: getString(R.string.playlist_nothing_playing)
        binding.playlistPlayPauseButton.text = getString(
            if (engine.isPlaying && !engine.isPaused) R.string.pause else R.string.play
        )

        if (!userIsDraggingPlaylistSeekBar) {
            val duration = engine.durationMs
            val position = engine.positionMs
            binding.playlistSeekBar.progress = if (duration > 0) ((position * 1000) / duration).toInt().coerceIn(0, 1000) else 0
            binding.playlistPositionText.text = formatPlaylistTime(position)
            binding.playlistDurationText.text = formatPlaylistTime(duration)
        }
    }

    private fun formatPlaylistTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
