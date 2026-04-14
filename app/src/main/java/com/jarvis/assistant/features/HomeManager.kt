package com.jarvis.assistant.features

import android.content.Context

class HomeManager(private val context: Context) {
    fun goHome(callback: (Boolean) -> Unit) { callback(true) }
}
