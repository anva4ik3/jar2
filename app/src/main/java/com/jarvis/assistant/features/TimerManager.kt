package com.jarvis.assistant.features

import android.content.Context

class TimerManager(private val context: Context) {
    fun setTimer(minutes: Int, callback: (Boolean) -> Unit) { callback(true) }
    fun cancelTimer(callback: (Boolean) -> Unit) { callback(true) }
}
