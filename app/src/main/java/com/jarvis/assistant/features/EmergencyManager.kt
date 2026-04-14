package com.jarvis.assistant.features

import android.content.Context

class EmergencyManager(private val context: Context) {
    fun makeEmergencyCall(callback: (Boolean) -> Unit) {
        callback(false)
    }
}
