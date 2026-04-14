package com.jarvis.assistant.features

import android.content.Context

class WiFiManager(private val context: Context) {
    fun toggleWiFi(callback: (Boolean) -> Unit) { callback(false) }
    fun getWiFiStatus(callback: (String) -> Unit) { callback("Unknown") }
}
