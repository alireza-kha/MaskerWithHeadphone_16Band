package com.masker.app.audiogram

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.masker.app.databinding.ActivityAudiogramHistoryBinding

class AudiogramHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudiogramHistoryBinding
    private lateinit var adapter: AudiogramHistoryAdapter
    private var allSummaries: List<AudiogramStorage.PatientSummary> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudiogramHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AudiogramHistoryAdapter(mutableListOf()) { summary ->
            val intent = Intent(this, AudiogramActivity::class.java)
            intent.putExtra(AudiogramActivity.EXTRA_VIEW_PATIENT_NAME, summary.patientName)
            startActivity(intent)
        }
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter

        binding.searchNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString().orEmpty())
            }
        })
    }

    override fun onResume() {
        super.onResume()
        allSummaries = AudiogramStorage.loadPatientSummaries(this)
        applyFilter(binding.searchNameEditText.text?.toString().orEmpty())
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isBlank()) {
            allSummaries
        } else {
            allSummaries.filter { it.patientName.contains(query.trim(), ignoreCase = true) }
        }
        adapter.updateData(filtered)
        binding.historyEmptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
