package com.jarvis.assistant.features

import android.content.Context

class WhatsAppManager(private val context: Context) {
    fun sendMessage(callback: (Boolean) -> Unit) {
        callback(false)
    }
}
