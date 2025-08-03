package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.core.WakeWordDetector
import com.jarvis.assistant.utils.Logger

class JARVISBackgroundService : Service() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var wakeWordDetector: WakeWordDetector
    private var isListening = false

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "JARVIS_Background_Service"
        private const val WAKE_WORD = "arise"
    }

    override fun onCreate() {
        super.onCreate()
        initializeSpeechRecognizer()
        initializeWakeWordDetector()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Logger.d("Background service ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Logger.d("Background service beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Update voice level if needed
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Process audio buffer
            }

            override fun onEndOfSpeech() {
                Logger.d("Background service end of speech")
                isListening = false
                // Restart listening after a short delay
                restartListening()
            }

            override fun onError(error: Int) {
                Logger.e("Background service speech recognition error: $error")
                isListening = false
                restartListening()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { text ->
                    Logger.d("Background service recognized: $text")
                    processWakeWord(text.lowercase())
                }
                isListening = false
                restartListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun initializeWakeWordDetector() {
        wakeWordDetector = WakeWordDetector()
    }

    private fun startListening() {
        if (!isListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            try {
                speechRecognizer.startListening(intent)
                isListening = true
                Logger.d("Background service started listening")
            } catch (e: Exception) {
                Logger.e("Background service failed to start listening: ${e.message}")
            }
        }
    }

    private fun restartListening() {
        // Restart listening after a short delay to avoid continuous recognition
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startListening()
        }, 1000)
    }

    private fun processWakeWord(text: String) {
        if (text.contains(WAKE_WORD)) {
            Logger.d("Wake word detected: $WAKE_WORD")
            wakeWordDetector.onWakeWordDetected()
            // Send broadcast to main activity
            sendBroadcast(Intent("JARVIS_WAKE_WORD_DETECTED"))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JARVIS background voice recognition service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Assistant")
            .setContentText("Listening for wake word")
            .setSmallIcon(R.drawable.ic_jarvis)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        Logger.d("Background service destroyed")
    }
} 