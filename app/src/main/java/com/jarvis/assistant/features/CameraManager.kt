package com.jarvis.assistant.features

import android.content.Context
import android.content.Intent
import android.provider.MediaStore

class CameraManager(private val context: Context) {
    fun takePhoto(callback: (Boolean) -> Unit) {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            callback(true)
        } catch (e: Exception) {
            callback(false)
        }
    }
}
