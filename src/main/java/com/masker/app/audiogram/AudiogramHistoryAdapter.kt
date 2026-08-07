package com.masker.app.audiogram

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.masker.app.R
import com.masker.app.databinding.ItemPatientSummaryBinding
import java.util.Date

class AudiogramHistoryAdapter(
    private val items: MutableList<AudiogramStorage.PatientSummary>,
    private val onClick: (AudiogramStorage.PatientSummary) -> Unit
) : RecyclerView.Adapter<AudiogramHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPatientSummaryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPatientSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.patientNameText.text = item.patientName

        val dateStr = PersianDateUtils.toJalaliString(Date(item.latestTimestampMillis))
        b.patientMetaText.text = holder.itemView.context.getString(
            R.string.patient_meta_format, item.patientAge, dateStr, item.testCount
        )

        b.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<AudiogramStorage.PatientSummary>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
