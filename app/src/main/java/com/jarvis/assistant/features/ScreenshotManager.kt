package com.jarvis.assistant.features

import android.content.Context

class ScreenshotManager(private val context: Context) {
    fun takeScreenshot(callback: (Boolean) -> Unit) {
        callback(false)
    }
}
