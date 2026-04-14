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

    // ── Состояния ──────────────────────────────────────────────────────────
    private enum class State {
        IDLE,               // Ждём wake word "джарвис"
        LISTENING_COMMAND,  // Wake word услышан, ждём команду
        SPEAKING            // TTS говорит — микрофон выключен
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
        private const val WAKE_WORD      = "джарвис"   // ИСПРАВЛЕНО: было "arise"
        private const val RETRY_DELAY_MS = 1500L
        private const val CMD_TIMEOUT_MS = 6000L       // 6 сек ждём команду после wake word
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initTTS()
        // CommandProcessor использует наш speak() — TTS из этого сервиса
        commandProcessor = CommandProcessor(this) { text -> speak(text) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Жду «Джарвис»..."))
        // Запускаем распознавание после инициализации TTS
        handler.postDelayed({ initSpeechRecognizer() }, 500)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { speechRecognizer.destroy() } catch (e: Exception) {}
        try { textToSpeech.shutdown() } catch (e: Exception) {}
        Logger.d("Background service destroyed")
    }

    // ── TTS ────────────────────────────────────────────────────────────────

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
                        // После TTS возвращаемся в IDLE и слушаем снова
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

    // ── SpeechRecognizer ───────────────────────────────────────────────────

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Logger.e("Speech recognition not available")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                Logger.d("BG service ready: state=$state")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                Logger.e("BG service error: $error state=$state")

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (state == State.LISTENING_COMMAND) {
                            // Не услышали команду — возвращаемся к ожиданию wake word
                            state = State.IDLE
                            speak("Не расслышал команду")
                        } else {
                            // Тихо перезапускаем ожидание wake word
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

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: return handler.postDelayed({ startListening() }, RETRY_DELAY_MS)

                val text = matches[0].lowercase(Locale.getDefault()).trim()
                Logger.d("BG service recognized: '$text' state=$state")

                when (state) {
                    State.IDLE -> {
                        if (text.contains(WAKE_WORD)) {
                            // Проверяем: может быть wake word + команда в одной фразе
                            // Например: "джарвис открой ютуб"
                            val commandAfterWake = text
                                .substringAfter(WAKE_WORD)
                                .trim()

                            if (commandAfterWake.length > 2) {
                                // Команда уже есть в этой же фразе
                                speak("Выполняю")
                                handler.postDelayed({
                                    processCommand(commandAfterWake)
                                }, 1200)
                            } else {
                                // Только wake word — ждём следующую фразу с командой
                                state = State.LISTENING_COMMAND
                                speak("Слушаю")
                                // Таймаут: если команда не пришла — возвращаемся в IDLE
                                handler.postDelayed({
                                    if (state == State.LISTENING_COMMAND) {
                                        state = State.IDLE
                                    }
                                }, CMD_TIMEOUT_MS)
                            }
                        } else {
                            // Wake word не услышан — продолжаем слушать
                            handler.postDelayed({ startListening() }, 200)
                        }
                    }

                    State.LISTENING_COMMAND -> {
                        // Получили команду
                        handler.removeCallbacksAndMessages(null)
                        processCommand(text)
                    }

                    State.SPEAKING -> {
                        // Игнорируем — TTS говорит
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

        val lang = if (state == State.IDLE) "ru-RU" else "ru-RU"

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // В режиме IDLE слушаем дольше (ждём wake word)
            // В режиме LISTENING_COMMAND — стандартный таймаут
            val silence = if (state == State.IDLE) 5000L else 4000L
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
        }

        try {
            speechRecognizer.startListening(intent)
            Logger.d("BG service startListening state=$state")
        } catch (e: Exception) {
            isListening = false
            Logger.e("BG service startListening failed: ${e.message}")
            handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
        }
    }

    private fun stopListening() {
        try { speechRecognizer.stopListening() } catch (e: Exception) {}
        isListening = false
    }

    private fun restartRecognizer() {
        try { speechRecognizer.destroy() } catch (e: Exception) {}
        handler.postDelayed({ initSpeechRecognizer() }, 1000)
    }

    // ── Обработка команд ───────────────────────────────────────────────────

    private fun processCommand(command: String) {
        state = State.IDLE
        Logger.d("BG processing command: '$command'")
        updateNotification("Команда: $command")

        when {
            command.contains("спи") || command.contains("стоп") -> {
                speak("Ухожу в спящий режим")
                stopSelf()
            }
            command.contains("выключись") -> {
                speak("До свидания!")
                handler.postDelayed({ stopSelf() }, 2000)
            }
            command.contains("время") -> {
                val time = java.text.SimpleDateFormat("HH:mm", Locale("ru", "RU"))
                    .format(java.util.Date())
                speak("Сейчас $time")
            }
            command.contains("батарея") || command.contains("заряд") -> {
                val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speak("Заряд батареи: $level процентов")
            }
            command.contains("громче")   -> commandProcessor.volumeUp()
            command.contains("тише")    -> commandProcessor.volumeDown()
            command.contains("погода")  -> commandProcessor.getWeather(command)
            command.contains("гугл") || command.contains("найди") || command.contains("загугли")
                                        -> commandProcessor.searchGoogle(command)
            command.contains("ютуб")    -> commandProcessor.searchYouTube(command)
            command.contains("новости") -> commandProcessor.getNews()
            command.contains("посчитай") || command.contains("сколько будет")
                                        -> commandProcessor.calculate(command)
            command.contains("переведи") -> commandProcessor.translate(command)
            command.contains("ватсап") || command.contains("whatsapp")
                                        -> commandProcessor.sendWhatsAppMessage()
            command.contains("фото") || command.contains("камера")
                                        -> commandProcessor.takePhoto()
            command.contains("скриншот") -> commandProcessor.takeScreenshot()
            command.contains("открой")  -> commandProcessor.openApp(command)
            command.contains("закрой")  -> commandProcessor.closeApp(command)
            command.contains("скорость") -> commandProcessor.checkInternetSpeed()
            else -> {
                speak("Не понял команду: $command")
            }
        }
    }

    // ── Уведомление ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JARVIS фоновое распознавание голоса"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String = "Жду «Джарвис»..."): Notification {
        // По нажатию на уведомление — открываем MainActivity
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Assistant")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_jarvis)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification(text))
    }
}
