package com.jarvis.assistant.features

import android.content.Context

class BluetoothManager(private val context: Context) {
    fun toggleBluetooth(callback: (Boolean) -> Unit) { callback(false) }
    fun getBluetoothStatus(callback: (String) -> Unit) { callback("Unknown") }
}
