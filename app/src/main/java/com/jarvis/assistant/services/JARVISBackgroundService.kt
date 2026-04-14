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
import android.os.PowerManager
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

    private enum class State { IDLE, AWAITING_COMMAND, SPEAKING }

    private var state       = State.IDLE
    private var isListening = false
    private var ttsReady    = false

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var commandProcessor: CommandProcessor
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID      = "JARVIS_BG"
        private const val WAKE_WORD       = "джарвис"
        private const val RETRY_DELAY_MS  = 1500L
        private const val CMD_TIMEOUT_MS  = 8000L
        const val ACTION_STOP             = "com.jarvis.assistant.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        initTTS()
        commandProcessor = CommandProcessor(this) { text -> speak(text) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification("Жду «Джарвис»..."))
        handler.postDelayed({ initSpeechRecognizer() }, 500)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { speechRecognizer.destroy() } catch (_: Exception) {}
        try { textToSpeech.shutdown() } catch (_: Exception) {}
        wakeLock?.release()
    }

    // ── WakeLock — не даём процессору засыпать ─────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "jarvis:background"
        ).apply { acquire(10 * 60 * 1000L) } // макс 10 минут, потом авто-обновится
    }

    // ── TTS ────────────────────────────────────────────────────────────────

    private fun initTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale("ru", "RU"))
                ttsReady = true
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(u: String?) {
                        state = State.SPEAKING
                        handler.post { stopListening() }
                    }
                    override fun onDone(u: String?) {
                        handler.postDelayed({
                            if (state == State.SPEAKING) state = State.IDLE
                            startListening()
                        }, 600)
                    }
                    override fun onError(u: String?) {
                        handler.postDelayed({
                            if (state == State.SPEAKING) state = State.IDLE
                            startListening()
                        }, 600)
                    }
                })
            }
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "bg_tts")
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "bg_tts")
        updateNotification(text)
    }

    // ── SpeechRecognizer ───────────────────────────────────────────────────

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Logger.e("Speech recognition not available in background")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                isListening = true
                val notifText = if (state == State.AWAITING_COMMAND) "Слушаю команду..." else "Жду «Джарвис»..."
                updateNotification(notifText)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }

            override fun onError(error: Int) {
                isListening = false
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (state == State.AWAITING_COMMAND) {
                            state = State.IDLE
                            speak("Не расслышал команду. Скажите «Джарвис» снова.")
                        } else {
                            handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        handler.postDelayed({ restartRecognizer() }, RETRY_DELAY_MS)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        Logger.e("No audio permission in background service")
                    else ->
                        handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: run { handler.postDelayed({ startListening() }, RETRY_DELAY_MS); return }

                val text = matches[0].lowercase(Locale("ru", "RU")).trim()
                Logger.d("BG recognized: '$text' state=$state")

                when (state) {
                    State.IDLE -> {
                        if (text.contains(WAKE_WORD)) {
                            val after = text.substringAfter(WAKE_WORD).trim()
                            if (after.length > 2) {
                                // Команда уже в той же фразе
                                speak("Выполняю.")
                                handler.postDelayed({ processCommand(after) }, 1200)
                            } else {
                                state = State.AWAITING_COMMAND
                                speak("Слушаю.")
                                // Таймаут — если команды не будет
                                handler.postDelayed({
                                    if (state == State.AWAITING_COMMAND) {
                                        state = State.IDLE
                                    }
                                }, CMD_TIMEOUT_MS)
                            }
                        } else {
                            handler.postDelayed({ startListening() }, 200)
                        }
                    }
                    State.AWAITING_COMMAND -> {
                        handler.removeCallbacksAndMessages(null)
                        state = State.IDLE
                        processCommand(text)
                    }
                    State.SPEAKING -> {
                        handler.postDelayed({ startListening() }, 200)
                    }
                }
            }

            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(e: Int, p: Bundle?) {}
        })
        startListening()
    }

    private fun startListening() {
        if (isListening || state == State.SPEAKING) return

        // Обновляем WakeLock
        if (wakeLock?.isHeld == false) acquireWakeLock()

        val silenceMs = if (state == State.AWAITING_COMMAND) 4000L else 6000L
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs - 1000L)
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

    // ── Команды (те же что в MainActivity) ────────────────────────────────

    private fun processCommand(command: String) {
        Logger.d("BG processing: '$command'")
        when {
            command.contains("время") -> {
                val time = java.text.SimpleDateFormat("HH:mm", Locale("ru","RU")).format(java.util.Date())
                speak("Сейчас $time")
            }
            command.contains("дата") || command.contains("число") -> {
                val date = java.text.SimpleDateFormat("d MMMM, EEEE", Locale("ru","RU")).format(java.util.Date())
                speak("Сегодня $date")
            }
            command.contains("батарея") || command.contains("заряд") -> {
                val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speak("Заряд батареи: $level процентов")
            }
            command.contains("громче")          -> commandProcessor.volumeUp()
            command.contains("тише")            -> commandProcessor.volumeDown()
            command.contains("без звука") || command.contains("выключи звук") -> commandProcessor.setMute()
            command.contains("включи звук")     -> commandProcessor.setUnmute()
            command.contains("включи фонарик")  -> commandProcessor.flashlightOn()
            command.contains("выключи фонарик") -> commandProcessor.flashlightOff()
            command.contains("фонарик")         -> commandProcessor.toggleFlashlight()
            command.contains("позвони") || command.contains("набери") -> commandProcessor.makeCall(command)
            command.contains("отправь смс") || command.contains("напиши смс") -> commandProcessor.sendSms(command)
            command.contains("ватсап")          -> commandProcessor.sendWhatsAppMessage(command)
            command.contains("будильник")       -> commandProcessor.setAlarm(command)
            command.contains("таймер")          -> commandProcessor.setTimer(command)
            command.contains("ютуб")            -> commandProcessor.searchYouTube(command)
            command.contains("гугл") || command.contains("найди") || command.contains("загугли") ->
                commandProcessor.searchGoogle(command)
            command.contains("новости")         -> commandProcessor.getNews()
            command.contains("посчитай") || command.contains("сколько будет") ->
                commandProcessor.calculate(command)
            command.contains("навигация") || command.contains("маршрут") ->
                commandProcessor.navigateTo(command)
            command.contains("покажи на карте") -> commandProcessor.showOnMap(command)
            command.contains("вайфай") || command.contains("wifi") -> commandProcessor.toggleWifi(command)
            command.contains("блютуз") || command.contains("bluetooth") -> commandProcessor.toggleBluetooth(command)
            command.contains("яркость") || command.contains("ярче") || command.contains("темнее") ->
                commandProcessor.setBrightness(command)
            command.contains("не беспокоить")   -> commandProcessor.setDoNotDisturb(true)
            command.contains("переведи")        -> commandProcessor.translate(command)
            command.contains("селфи") || command.contains("фронталка") -> commandProcessor.openFrontCamera()
            command.contains("фото") || command.contains("камера") -> commandProcessor.takePhoto()
            command.contains("скорость")        -> commandProcessor.checkInternetSpeed()
            command.contains("настройки")       -> commandProcessor.openSettings(command)
            command.contains("открой") || command.contains("запусти") -> commandProcessor.openApp(command)
            command.contains("стоп") || command.contains("спи") -> {
                speak("Останавливаю фоновый режим.")
                stopSelf()
            }
            else -> speak("Не понял команду. Попробуйте ещё раз.")
        }
    }

    // ── Уведомление ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "JARVIS", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JARVIS фоновое прослушивание"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, JARVISBackgroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS активен")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_jarvis)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_jarvis, "Остановить", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
