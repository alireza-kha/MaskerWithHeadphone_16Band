package com.masker.app.audiogram

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.masker.app.databinding.ActivityAudiogramGalleryBinding

/**
 * سابقه همه آزمون‌های اودیوگرام ذخیره‌شده (برای همه افراد)، مرتب بر اساس جدیدترین آزمون؛
 * با زدن هر مورد، همان نمودار قابل مشاهده دوباره است. از دکمه زیر «شروع آزمون شنوایی» در
 * صفحه اصلی باز می‌شود.
 */
class AudiogramGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudiogramGalleryBinding
    private lateinit var adapter: AudiogramResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudiogramGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AudiogramResultAdapter(mutableListOf()) { result ->
            val intent = Intent(this, AudiogramChartViewActivity::class.java)
            intent.putExtra(AudiogramChartViewActivity.EXTRA_TIMESTAMP, result.timestampMillis)
            startActivity(intent)
        }
        binding.galleryRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.galleryRecyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        val all = AudiogramStorage.loadAllResults(this).sortedByDescending { it.timestampMillis }
        adapter.updateData(all)
        binding.galleryEmptyText.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
    }
}
