package com.jarvis.assistant.features

import android.content.Context

class SMSManager(private val context: Context) {
    fun sendSMS(contact: String, message: String, callback: (Boolean) -> Unit) {
        callback(false)
    }
}
