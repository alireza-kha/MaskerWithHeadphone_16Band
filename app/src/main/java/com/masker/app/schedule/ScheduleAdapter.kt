package com.masker.app.schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.masker.app.databinding.ItemScheduleBinding

class ScheduleAdapter(
    private val items: MutableList<ScheduleItem>,
    private val onClick: (ScheduleItem) -> Unit,
    private val onToggle: (ScheduleItem, Boolean) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    private val dayNames = arrayOf("", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")

    inner class ViewHolder(val binding: ItemScheduleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.timeRangeText.text = String.format(
            "%02d:%02d تا %02d:%02d", item.startHour, item.startMinute, item.endHour, item.endMinute
        )

        val activeDays = (1..7).filter { item.days.getOrElse(it) { false } }.joinToString("، ") { dayNames[it] }
        b.daysText.text = activeDays.ifEmpty { "هیچ روزی انتخاب نشده" }

        b.labelText.text = item.label
        b.labelText.visibility = if (item.label.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

        b.enabledSwitch.setOnCheckedChangeListener(null)
        b.enabledSwitch.isChecked = item.enabled
        b.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            item.enabled = checked
            onToggle(item, checked)
        }

        b.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<ScheduleItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
