package com.masker.app.schedule

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.masker.app.databinding.ActivityScheduleBinding

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private lateinit var adapter: ScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ScheduleAdapter(
            mutableListOf(),
            onClick = { item -> openEditor(item.id) },
            onToggle = { item, enabled ->
                ScheduleStorage.upsert(this, item)
                if (enabled) {
                    AlarmScheduler.scheduleAll(this, item)
                } else {
                    AlarmScheduler.cancelAll(this, item.id)
                }
            }
        )
        binding.scheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.scheduleRecyclerView.adapter = adapter

        binding.addScheduleButton.setOnClickListener { openEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val items = ScheduleStorage.loadAll(this)
        adapter.updateData(items)
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openEditor(scheduleId: Long?) {
        val intent = Intent(this, ScheduleEditActivity::class.java)
        if (scheduleId != null) {
            intent.putExtra(ScheduleEditActivity.EXTRA_SCHEDULE_ID, scheduleId)
        }
        startActivity(intent)
    }
}
