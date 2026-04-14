package com.jarvis.assistant.features

import android.content.Context

class FlashlightManager(private val context: Context) {
    fun toggleFlashlight(callback: (Boolean) -> Unit) {
        callback(false)
    }
}
