package com.jarvis.assistant.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.jarvis.assistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("jarvis_settings", MODE_PRIVATE)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.apply {
            switchWakeWord.isChecked      = prefs.getBoolean("wake_word_enabled", true)
            switchNotifications.isChecked = prefs.getBoolean("notifications_enabled", true)
            switchVibration.isChecked     = prefs.getBoolean("vibration_enabled", true)
            switchSoundFeedback.isChecked = prefs.getBoolean("sound_feedback", true)
            switchAutoListen.isChecked    = prefs.getBoolean("auto_listen", false)
            switchDarkMode.isChecked      = prefs.getBoolean("dark_mode", true)

            val speechRate = prefs.getFloat("speech_rate", 1.0f)
            seekBarSpeechRate.progress = (speechRate * 10).toInt()
            tvSpeechRateValue.text     = String.format("%.1fx", speechRate)

            val wakeWord = prefs.getString("wake_word", "arise")
            etWakeWord.setText(wakeWord)
        }
    }

    private fun setupListeners() {
        binding.apply {

            btnBack.setOnClickListener { finish() }

            switchWakeWord.setOnCheckedChangeListener { _, checked ->
                prefs.edit { putBoolean("wake_word_enabled", checked) }
                showSaved()
            }

            switchNotifications.setOnCheckedChangeListener { _, checked ->
                prefs.edit { putBoolean("notifications_enabled", checked) }
                showSaved()
            }

            switchVibration.setOnCheckedChangeListener { _, checked ->
                prefs.edit { putBoolean("vibration_enabled", checked) }
                showSaved()
            }

            switchSoundFeedback.setOnCheckedChangeListener { _, checked ->
                prefs.edit { putBoolean("sound_feedback", checked) }
                showSaved()
            }

            switchAutoListen.setOnCheckedChangeListener { _, checked ->
                prefs.edit { putBoolean("auto_listen", checked) }
                showSaved()
            }

            switchDarkMode.setOnCheckedChangeListener { _, checked ->
                prefs.edit { putBoolean("dark_mode", checked) }
                showSaved()
            }

            seekBarSpeechRate.setOnSeekBarChangeListener(object :
                android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val rate = progress / 10f
                    tvSpeechRateValue.text = String.format("%.1fx", rate)
                    if (fromUser) prefs.edit { putFloat("speech_rate", rate) }
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) { showSaved() }
            })

            btnSaveWakeWord.setOnClickListener {
                val word = etWakeWord.text.toString().trim().lowercase()
                if (word.isNotEmpty()) {
                    prefs.edit { putString("wake_word", word) }
                    showSaved()
                } else {
                    Toast.makeText(this@SettingsActivity, "Wake word cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }

            btnClearHistory.setOnClickListener {
                getSharedPreferences("jarvis_history", MODE_PRIVATE).edit { clear() }
                Toast.makeText(this@SettingsActivity, "History cleared", Toast.LENGTH_SHORT).show()
            }

            btnResetDefaults.setOnClickListener {
                prefs.edit { clear() }
                loadSettings()
                Toast.makeText(this@SettingsActivity, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSaved() {
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }
}
