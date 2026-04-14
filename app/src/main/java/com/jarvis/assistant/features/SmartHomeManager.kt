package com.jarvis.assistant.features

import android.content.Context

class SmartHomeManager(private val context: Context) {
    fun turnOnDevice(device: String, callback: (Boolean) -> Unit) { callback(false) }
    fun turnOffDevice(device: String, callback: (Boolean) -> Unit) { callback(false) }
    fun setTemperature(temperature: Int, callback: (Boolean) -> Unit) { callback(false) }
}
