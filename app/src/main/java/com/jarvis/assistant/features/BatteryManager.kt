package com.jarvis.assistant.features

import android.content.Context

data class BatteryStatus(val level: Int = 0, val status: String = "Unknown")

class BatteryManager(private val context: Context) {
    fun getBatteryStatus(): BatteryStatus = BatteryStatus()
}
