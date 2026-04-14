package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.core.CommandProcessor
import com.jarvis.assistant.ui.MainActivity
import com.jarvis.assistant.utils.Logger
import java.util.Locale

class JARVISBackgroundService : Service() {

    private enum class State {
        IDLE,
        LISTENING_COMMAND,
        SPEAKING
    }

    private var state = State.IDLE
    private var isListening = false
    private var ttsReady = false

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var commandProcessor: CommandProcessor
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID     = "JARVIS_Background_Service"
        private const val WAKE_WORD      = "джарвис"
        private const val RETRY_DELAY_MS = 1500L
        private const val CMD_TIMEOUT_MS = 6000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initTTS()
        commandProcessor = CommandProcessor(this) { text -> speak(text) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Жду «Джарвис»..."))
        handler.postDelayed({ initSpeechRecognizer() }, 500)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { speechRecognizer.destroy() } catch (_: Exception) {}
        try { textToSpeech.shutdown() } catch (_: Exception) {}
        Logger.d("Background service destroyed")
    }

    // ── TTS ──

    private fun initTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale("ru", "RU"))
                ttsReady = true

                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        state = State.SPEAKING
                        handler.post { stopListening() }
                    }
                    override fun onDone(utteranceId: String?) {
                        handler.postDelayed({
                            state = State.IDLE
                            startListening()
                        }, 600)
                    }
                    override fun onError(utteranceId: String?) {
                        handler.postDelayed({
                            state = State.IDLE
                            startListening()
                        }, 600)
                    }
                })
            } else {
                Logger.e("Background TTS init failed")
            }
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "bg_tts")
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "bg_tts")
    }

    // ── SpeechRecognizer ──

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Logger.e("Speech recognition not available")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                Logger.d("BG ready: state=$state")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (state == State.LISTENING_COMMAND) {
                            state = State.IDLE
                            speak("Не расслышал команду")
                        } else {
                            handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        handler.postDelayed({ restartRecognizer() }, RETRY_DELAY_MS)
                    }
                    else -> {
                        handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                    }
                }
            }

            // ✅ ИСПРАВЛЕНО ЗДЕСЬ
            override fun onResults(results: Bundle?) {
                isListening = false

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches == null) {
                    handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                    return
                }

                val text = matches[0].lowercase(Locale.getDefault()).trim()
                Logger.d("BG recognized: '$text' state=$state")

                when (state) {
                    State.IDLE -> {
                        if (text.contains(WAKE_WORD)) {
                            val commandAfterWake = text.substringAfter(WAKE_WORD).trim()

                            if (commandAfterWake.length > 2) {
                                speak("Выполняю")
                                handler.postDelayed({
                                    processCommand(commandAfterWake)
                                }, 1200)
                            } else {
                                state = State.LISTENING_COMMAND
                                speak("Слушаю")
                                handler.postDelayed({
                                    if (state == State.LISTENING_COMMAND) {
                                        state = State.IDLE
                                    }
                                }, CMD_TIMEOUT_MS)
                            }
                        } else {
                            handler.postDelayed({ startListening() }, 200)
                        }
                    }

                    State.LISTENING_COMMAND -> {
                        handler.removeCallbacksAndMessages(null)
                        processCommand(text)
                    }

                    State.SPEAKING -> {
                        handler.postDelayed({ startListening() }, 200)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        startListening()
    }

    private fun startListening() {
        if (isListening || state == State.SPEAKING) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer.startListening(intent)
        } catch (_: Exception) {
            handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
        }
    }

    private fun stopListening() {
        try { speechRecognizer.stopListening() } catch (_: Exception) {}
        isListening = false
    }

    private fun restartRecognizer() {
        try { speechRecognizer.destroy() } catch (_: Exception) {}
        handler.postDelayed({ initSpeechRecognizer() }, 1000)
    }

    private fun processCommand(command: String) {
        state = State.IDLE
        Logger.d("Processing: '$command'")

        when {
            command.contains("время") -> {
                val time = java.text.SimpleDateFormat("HH:mm", Locale("ru", "RU"))
                    .format(java.util.Date())
                speak("Сейчас $time")
            }
            else -> speak("Не понял команду")
        }
    }

    // ── Notification ──

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }
}
