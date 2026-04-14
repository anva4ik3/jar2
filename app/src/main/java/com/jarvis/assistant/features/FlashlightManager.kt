package com.jarvis.assistant.features

import android.content.Context
import android.hardware.camera2.CameraManager
import com.jarvis.assistant.utils.Logger

class FlashlightManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var isFlashlightOn = false
    private var cameraId: String? = null

    init {
        // Находим камеру со вспышкой
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            Logger.e("FlashlightManager: failed to find camera with flash: ${e.message}")
        }
    }

    fun toggleFlashlight(callback: (Boolean) -> Unit) {
        val id = cameraId
        if (id == null) {
            Logger.e("FlashlightManager: no camera with flash found")
            callback(false)
            return
        }

        try {
            isFlashlightOn = !isFlashlightOn
            cameraManager.setTorchMode(id, isFlashlightOn)
            Logger.d("FlashlightManager: torch=${isFlashlightOn}")
            callback(true)
        } catch (e: Exception) {
            Logger.e("FlashlightManager: toggleFlashlight error: ${e.message}")
            isFlashlightOn = false
            callback(false)
        }
    }

    fun turnOn(callback: (Boolean) -> Unit) {
        val id = cameraId
        if (id == null) { callback(false); return }
        try {
            cameraManager.setTorchMode(id, true)
            isFlashlightOn = true
            callback(true)
        } catch (e: Exception) {
            Logger.e("FlashlightManager: turnOn error: ${e.message}")
            callback(false)
        }
    }

    fun turnOff(callback: (Boolean) -> Unit) {
        val id = cameraId
        if (id == null) { callback(false); return }
        try {
            cameraManager.setTorchMode(id, false)
            isFlashlightOn = false
            callback(true)
        } catch (e: Exception) {
            Logger.e("FlashlightManager: turnOff error: ${e.message}")
            callback(false)
        }
    }

    fun isOn(): Boolean = isFlashlightOn
}
