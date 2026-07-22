package com.masker.app.audiogram

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.masker.app.R
import com.masker.app.databinding.ActivityAudiogramChartViewBinding
import java.util.Date

/** نمایش فقط‌خواندنی یک آزمون اودیوگرام قبلی مشخص (از صفحه «مشاهده سابقه نمودارها») */
class AudiogramChartViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TIMESTAMP = "extra_timestamp"
    }

    private lateinit var binding: ActivityAudiogramChartViewBinding
    private var result: AudiogramResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudiogramChartViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, -1L)
        val loaded = AudiogramStorage.loadAllResults(this).find { it.timestampMillis == timestamp }
        if (loaded == null) {
            Toast.makeText(this, R.string.history_empty, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        result = loaded

        binding.audiogramView.setResult(loaded)
        updatePatientInfoText(loaded)
        updateLegendText(loaded)

        binding.saveAudiogramButton.setOnClickListener {
            AudiogramImageExporter.saveAndMaybeShare(this, binding.resultCaptureContainer, share = false)
        }
        binding.shareAudiogramButton.setOnClickListener {
            AudiogramImageExporter.saveAndMaybeShare(this, binding.resultCaptureContainer, share = true)
        }
        binding.deleteAudiogramButton.setOnClickListener { confirmDelete() }
    }

    private fun confirmDelete() {
        val r = result ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_audiogram_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                AudiogramStorage.deleteResult(this, r.timestampMillis)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
}
