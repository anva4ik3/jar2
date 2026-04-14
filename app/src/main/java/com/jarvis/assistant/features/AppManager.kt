package com.jarvis.assistant.features

import android.content.Context

class AppManager(private val context: Context) {
    fun openApp(appName: String, callback: (Boolean) -> Unit) {
        callback(false)
    }
    fun closeApp(appName: String, callback: (Boolean) -> Unit) {
        callback(false)
    }
}
