package com.masker.app.schedule

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity
import com.masker.app.databinding.ActivityScheduleEditBinding
import com.masker.app.service.PlaybackService

class ScheduleEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    }

    private lateinit var binding: ActivityScheduleEditBinding
    private var currentItem: ScheduleItem = ScheduleItem()
    private val dayNames = arrayOf("", "ی", "د", "س", "چ", "پ", "ج", "ش") // یکشنبه..شنبه به اختصار
    private val dayCheckBoxes = arrayOfNulls<CheckBox>(8)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        currentItem = if (scheduleId != -1L) {
            ScheduleStorage.findById(this, scheduleId) ?: ScheduleItem()
        } else {
            // پیش‌فرض: کپی تنظیمات فعلی صدا از صفحه اصلی
            ScheduleItem(
                masterVolume = PlaybackService.noiseEngine.masterVolume,
                leftVolume = PlaybackService.noiseEngine.leftVolume,
                rightVolume = PlaybackService.noiseEngine.rightVolume,
                bandGains = PlaybackService.noiseEngine.bandGains.copyOf()
            )
        }

        binding.labelEditText.setText(currentItem.label)
        buildDayCheckboxes()
        updateTimeButtons()

        binding.startTimeButton.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                currentItem.startHour = hour
                currentItem.startMinute = minute
                updateTimeButtons()
            }, currentItem.startHour, currentItem.startMinute, true).show()
        }

        binding.endTimeButton.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                currentItem.endHour = hour
                currentItem.endMinute = minute
                updateTimeButtons()
            }, currentItem.endHour, currentItem.endMinute, true).show()
        }

        binding.saveScheduleButton.setOnClickListener { saveAndFinish() }

        if (scheduleId != -1L) {
            binding.deleteScheduleButton.visibility = android.view.View.VISIBLE
            binding.deleteScheduleButton.setOnClickListener {
                AlarmScheduler.cancelAll(this, currentItem.id)
                ScheduleStorage.delete(this, currentItem.id)
                finish()
            }
        }
    }

    private fun buildDayCheckboxes() {
        binding.daysGrid.columnCount = 4
        for (dayOfWeek in 1..7) {
            val cb = CheckBox(this)
            cb.text = dayNames[dayOfWeek]
            cb.isChecked = currentItem.days.getOrElse(dayOfWeek) { false }
            cb.setOnCheckedChangeListener { _, checked ->
                currentItem.days[dayOfWeek] = checked
            }
            val params = GridLayout.LayoutParams()
            params.width = GridLayout.LayoutParams.WRAP_CONTENT
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.setMargins(8, 8, 8, 8)
            binding.daysGrid.addView(cb, params)
            dayCheckBoxes[dayOfWeek] = cb
        }
    }

    private fun updateTimeButtons() {
        binding.startTimeButton.text = String.format("%02d:%02d", currentItem.startHour, currentItem.startMinute)
        binding.endTimeButton.text = String.format("%02d:%02d", currentItem.endHour, currentItem.endMinute)
    }

    private fun saveAndFinish() {
        currentItem.label = binding.labelEditText.text?.toString()?.trim().orEmpty()
        currentItem.enabled = true

        ScheduleStorage.upsert(this, currentItem)
        AlarmScheduler.scheduleAll(this, currentItem)
        finish()
    }
}
