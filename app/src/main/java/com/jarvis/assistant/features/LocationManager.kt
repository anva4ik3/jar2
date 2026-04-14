package com.jarvis.assistant.features

import android.content.Context
import android.content.Intent
import android.net.Uri

class LocationManager(private val context: Context) {
    fun navigateTo(destination: String, callback: (Boolean) -> Unit) {
        try {
            val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            callback(true)
        } catch (e: Exception) {
            callback(false)
        }
    }
}
