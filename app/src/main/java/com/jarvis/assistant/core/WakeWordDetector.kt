package com.jarvis.assistant.core

import com.jarvis.assistant.utils.Logger

class WakeWordDetector {
    
    companion object {
        private const val WAKE_WORD = "arise"
        private const val CONFIDENCE_THRESHOLD = 0.8f
    }

    private var isListening = false
    private var onWakeWordDetected: (() -> Unit)? = null

    fun startListening(callback: () -> Unit) {
        isListening = true
        onWakeWordDetected = callback
        Logger.d("Wake word detector started listening")
    }

    fun stopListening() {
        isListening = false
        onWakeWordDetected = null
        Logger.d("Wake word detector stopped listening")
    }

    fun onWakeWordDetected() {
        if (isListening) {
            Logger.d("Wake word '$WAKE_WORD' detected")
            onWakeWordDetected?.invoke()
        }
    }

    fun processAudioInput(text: String, confidence: Float = 1.0f): Boolean {
        if (!isListening) return false

        val lowerText = text.lowercase()
        val containsWakeWord = lowerText.contains(WAKE_WORD)
        val meetsConfidence = confidence >= CONFIDENCE_THRESHOLD

        if (containsWakeWord && meetsConfidence) {
            onWakeWordDetected()
            return true
        }

        return false
    }

    fun isWakeWord(text: String): Boolean {
        return text.lowercase().contains(WAKE_WORD)
    }

    fun getWakeWord(): String {
        return WAKE_WORD
    }

    fun setConfidenceThreshold(threshold: Float) {
        // In a real implementation, this would update the confidence threshold
        Logger.d("Confidence threshold updated to: $threshold")
    }

    fun isListening(): Boolean {
        return isListening
    }
} 