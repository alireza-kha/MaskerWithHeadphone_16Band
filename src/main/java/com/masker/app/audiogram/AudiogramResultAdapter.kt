package com.masker.app.audiogram

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.masker.app.R
import com.masker.app.databinding.ItemAudiogramResultBinding
import java.util.Date

/** فهرست تمام آزمون‌های ذخیره‌شده (همه افراد)، برای صفحه «مشاهده سابقه نمودارها» */
class AudiogramResultAdapter(
    private val items: MutableList<AudiogramResult>,
    private val onClick: (AudiogramResult) -> Unit
) : RecyclerView.Adapter<AudiogramResultAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAudiogramResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAudiogramResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        val context = holder.itemView.context
        b.resultNameText.text = item.patientName.ifBlank { context.getString(R.string.unnamed_patient) }

        val dateStr = PersianDateUtils.toJalaliString(Date(item.timestampMillis))
        b.resultMetaText.text = context.getString(R.string.audiogram_result_meta_format, item.patientAge, dateStr)

        b.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<AudiogramResult>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
