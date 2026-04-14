package com.jarvis.assistant.features

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

class AlarmManager(private val context: Context) {
    fun setAlarm(time: String, callback: (Boolean) -> Unit) {
        try {
            val parts = time.trim().split(":")
            val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 8
            val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            callback(true)
        } catch (e: Exception) {
            callback(false)
        }
    }
}
