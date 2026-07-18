package com.masker.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.masker.app.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildSteps()
        setupDeveloperSection()
    }

    private fun buildSteps() {
        val steps = resources.getStringArray(R.array.help_steps)
        for ((index, step) in steps.withIndex()) {
            val textView = TextView(this)
            textView.text = "${index + 1}. $step"
            textView.textSize = 15f
            textView.setPadding(0, 0, 0, dpToPx(14))
            binding.stepsContainer.addView(textView)
        }
    }

    private fun setupDeveloperSection() {
        binding.developerNameText.text = getString(R.string.developer_name_line, getString(R.string.developer_name))
        binding.developerPhoneText.text = getString(R.string.developer_phone_line, getString(R.string.developer_phone))
        binding.developerEmailText.text = getString(R.string.developer_email_line, getString(R.string.developer_email))

        binding.developerPhoneText.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${getString(R.string.developer_phone)}"))
            startActivity(intent)
        }

        binding.developerEmailText.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${getString(R.string.developer_email)}"))
            startActivity(intent)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
