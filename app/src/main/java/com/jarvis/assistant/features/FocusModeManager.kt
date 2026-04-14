package com.jarvis.assistant.features

import android.content.Context

data class FocusAnalytics(val totalTime: Int = 0, val sessionCount: Int = 0)

class FocusModeManager(private val context: Context) {
    fun startFocusSession() {}
    fun stopFocusSession() {}
    fun getFocusAnalytics(): FocusAnalytics = FocusAnalytics()
}
