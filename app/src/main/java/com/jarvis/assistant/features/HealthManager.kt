package com.jarvis.assistant.features

import android.content.Context

class HealthManager(private val context: Context) {
    fun getStepCount(callback: (Int) -> Unit) { callback(0) }
    fun getHealthTip(callback: (String) -> Unit) { callback("Stay hydrated!") }
}
