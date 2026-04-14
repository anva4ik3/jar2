package com.jarvis.assistant.features

import android.content.Context

class MusicManager(private val context: Context) {
    fun playMusic(query: String, callback: (Boolean) -> Unit) { callback(false) }
    fun pauseMusic(callback: (Boolean) -> Unit) { callback(false) }
    fun nextTrack(callback: (Boolean) -> Unit) { callback(false) }
}
