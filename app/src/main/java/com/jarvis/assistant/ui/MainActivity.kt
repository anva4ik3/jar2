package com.jarvis.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.jarvis.assistant.R
import com.jarvis.assistant.core.CommandProcessor
import com.jarvis.assistant.core.VoiceManager
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.services.JARVISBackgroundService
import com.jarvis.assistant.viewmodels.MainViewModel
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    private var isListening = false
    private var isJARVISActive = false   // включён ли JARVIS (кнопка)
    private var awaitingCommand = false  // услышан wake word, ждём команду
    private var isSpeaking = false
    private val handler = Handler(Looper.getMainLooper())
    private val russianLocale = Locale("ru", "RU")

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val RETRY_DELAY_MS    = 1500L
        private const val AFTER_TTS_DELAY_MS = 600L

        private const val WAKE_WORD       = "джарвис"
        private const val CMD_SLEEP       = "спи"
        private const val CMD_EXIT        = "выключись"
        private const val CMD_TIME        = "время"
        private const val CMD_WEATHER     = "погода"
        private const val CMD_GOOGLE      = "гугл"
        private const val CMD_SEARCH      = "найди"
        private const val CMD_YOUTUBE     = "ютуб"
        private const val CMD_NEWS        = "новости"
        private const val CMD_CALCULATE   = "посчитай"
        private const val CMD_CALC2       = "сколько будет"
        private const val CMD_FOCUS       = "фокус"
        private const val CMD_TRANSLATE   = "переведи"
        private const val CMD_WHATSAPP    = "ватсап"
        private const val CMD_SCREENSHOT  = "скриншот"
        private const val CMD_PHOTO       = "фото"
        private const val CMD_VOL_UP      = "громче"
        private const val CMD_VOL_DOWN    = "тише"
        private const val CMD_OPEN        = "открой"
        private const val CMD_CLOSE       = "закрой"
        private const val CMD_SPEED       = "скорость"
        private const val CMD_BATTERY     = "батарея"
        private const val CMD_CHARGE      = "заряд"
        private const val CMD_CAMERA      = "камера"
        private const val CMD_ZAGOOGLI    = "загугли"

        // Сколько секунд ждём команду после wake word
        private const val COMMAND_TIMEOUT_MS = 8000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        stopBackgroundService()
        initializeComponents()
        setupUI()
        requestPermissions()
    }

    private fun initializeComponents() {
        viewModel        = ViewModelProvider(this)[MainViewModel::class.java]
        commandProcessor = CommandProcessor(this) { text -> speak(text) }
        textToSpeech     = TextToSpeech(this, this)
        setupSpeechRecognizer()
    }

    // ── TTS ────────────────────────────────────────────────────────────────

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(russianLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                startActivity(installIntent)
            }
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    handler.post { stopListening() }
                }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    if (isJARVISActive) {
                        handler.postDelayed({ startListening() }, AFTER_TTS_DELAY_MS)
                    }
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    if (isJARVISActive) {
                        handler.postDelayed({ startListening() }, AFTER_TTS_DELAY_MS)
                    }
                }
            })
        }
    }

    private fun speak(text: String) {
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "jarvis_tts")
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis_tts")
    }

    // ── SpeechRecognizer ───────────────────────────────────────────────────

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Распознавание речи недоступно", Toast.LENGTH_LONG).show()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                viewModel.setListening(true)
                val status = if (awaitingCommand) "Слушаю команду..." else "Скажите «Джарвис»"
                viewModel.setJARVISStatus(status)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > 0)
                    binding.voiceLevelIndicator.progress = (rmsdB * 10).toInt().coerceIn(0, 100)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                viewModel.setListening(false)
            }
            override fun onError(error: Int) {
                isListening = false
                viewModel.setListening(false)
                if (!isJARVISActive || isSpeaking) return

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (awaitingCommand) {
                            // Не услышали команду — возвращаемся в режим ожидания wake word
                            awaitingCommand = false
                            viewModel.setJARVISStatus("Скажите «Джарвис»")
                        }
                        handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        handler.postDelayed({ restartRecognizer() }, RETRY_DELAY_MS)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        viewModel.setJARVISStatus("Нет разрешения на микрофон")
                    }
                    else -> {
                        handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                viewModel.setListening(false)
                if (isSpeaking) return

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.get(0)?.lowercase(russianLocale)?.trim()

                if (!command.isNullOrEmpty()) {
                    processVoiceCommand(command)
                } else {
                    if (isJARVISActive) handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (isSpeaking) return
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.get(0)?.let { binding.tvCurrentCommand.text = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (isListening || !isJARVISActive || isSpeaking || !hasAudioPermission()) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Если ждём команду — пауза короче (4 сек), иначе длиннее (6 сек)
            val silenceMs = if (awaitingCommand) 4000L else 6000L
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs - 1000L)
        }
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            if (isJARVISActive) handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
        }
    }

    private fun stopListening() {
        try { speechRecognizer.stopListening() } catch (e: Exception) {}
        isListening = false
        viewModel.setListening(false)
    }

    private fun restartRecognizer() {
        try { speechRecognizer.destroy() } catch (e: Exception) {}
        setupSpeechRecognizer()
        if (isJARVISActive && !isSpeaking) {
            handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
        }
    }

    // ── Активация / Деактивация ────────────────────────────────────────────

    private fun activateJARVIS() {
        if (!hasAudioPermission()) {
            requestPermissions()
            Toast.makeText(this, "Сначала разрешите доступ к микрофону", Toast.LENGTH_LONG).show()
            return
        }
        isJARVISActive = true
        awaitingCommand = false
        viewModel.setJARVISStatus("Скажите «Джарвис»")
        binding.btnVoiceActivation.text = "Деактивировать ДЖАРВИС"
        binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_deactivate)
        speak("ДЖАРВИС активирован. Скажите Джарвис.")
    }

    private fun deactivateJARVIS() {
        isJARVISActive = false
        awaitingCommand = false
        handler.removeCallbacksAndMessages(null)
        viewModel.setJARVISStatus("ДЖАРВИС неактивен")
        binding.btnVoiceActivation.text = "Активировать ДЖАРВИС"
        binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_activate)
        stopListening()
        speak("ДЖАРВИС деактивирован.")
    }

    // ── Обработка команд ───────────────────────────────────────────────────

    private fun processVoiceCommand(command: String) {
        viewModel.setCurrentCommand("Вы: $command")

        // ── Шаг 1: не в режиме команды → ищем wake word ───────────────────
        if (!awaitingCommand) {
            if (command.contains(WAKE_WORD)) {
                awaitingCommand = true
                // Если после "джарвис" сразу идёт команда в той же фразе — обрабатываем
                val commandAfterWakeWord = command.substringAfter(WAKE_WORD).trim()
                if (commandAfterWakeWord.isNotEmpty()) {
                    speak("Слушаю.")
                    handler.postDelayed({ executeCommand(commandAfterWakeWord) }, 300L)
                } else {
                    speak("Слушаю.")
                    // Таймаут: если команды не будет 8 сек — возвращаемся в режим ожидания
                    handler.postDelayed({
                        if (awaitingCommand) {
                            awaitingCommand = false
                            viewModel.setJARVISStatus("Скажите «Джарвис»")
                        }
                    }, COMMAND_TIMEOUT_MS)
                }
            } else {
                // Wake word не услышан — продолжаем слушать молча
                if (isJARVISActive) handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
            }
            return
        }

        // ── Шаг 2: в режиме команды → выполняем ──────────────────────────
        awaitingCommand = false
        handler.removeCallbacksAndMessages(null) // убираем таймаут
        executeCommand(command)
    }

    private fun executeCommand(command: String) {
        when {
            command.contains(CMD_SLEEP) -> {
                isJARVISActive = false
                awaitingCommand = false
                speak("Ухожу в спящий режим.")
                handler.post {
                    binding.btnVoiceActivation.text = "Активировать ДЖАРВИС"
                    binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_activate)
                    viewModel.setJARVISStatus("ДЖАРВИС неактивен")
                }
            }
            command.contains(CMD_EXIT) -> {
                speak("До свидания!")
                handler.postDelayed({ finish() }, 1500L)
            }
            command.contains(CMD_TIME) -> {
                val time = java.text.SimpleDateFormat("HH:mm", russianLocale).format(Date())
                speak("Сейчас $time")
            }
            command.contains(CMD_BATTERY) || command.contains(CMD_CHARGE) -> {
                val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speak("Заряд батареи: $level процентов")
            }
            command.contains(CMD_VOL_UP)    -> commandProcessor.volumeUp()
            command.contains(CMD_VOL_DOWN)  -> commandProcessor.volumeDown()
            command.contains(CMD_WEATHER)   -> commandProcessor.getWeather(command)
            command.contains(CMD_YOUTUBE)   -> commandProcessor.searchYouTube(command)
            command.contains(CMD_GOOGLE) || command.contains(CMD_SEARCH) || command.contains(CMD_ZAGOOGLI)
                                            -> commandProcessor.searchGoogle(command)
            command.contains(CMD_NEWS)      -> commandProcessor.getNews()
            command.contains(CMD_CALCULATE) || command.contains(CMD_CALC2)
                                            -> commandProcessor.calculate(command)
            command.contains(CMD_FOCUS)     -> commandProcessor.enterFocusMode()
            command.contains(CMD_TRANSLATE) -> commandProcessor.translate(command)
            command.contains(CMD_WHATSAPP)  -> commandProcessor.sendWhatsAppMessage()
            command.contains(CMD_PHOTO) || command.contains(CMD_CAMERA)
                                            -> commandProcessor.takePhoto()
            command.contains(CMD_SCREENSHOT)-> commandProcessor.takeScreenshot()
            command.contains(CMD_OPEN)      -> commandProcessor.openApp(command)
            command.contains(CMD_CLOSE)     -> commandProcessor.closeApp(command)
            command.contains(CMD_SPEED)     -> commandProcessor.checkInternetSpeed()
            else -> {
                speak("Не понял команду. Попробуйте ещё раз.")
                // После ответа автоматически снова войдём в режим команды
                awaitingCommand = true
            }
        }
    }

    // ── Фоновый сервис ─────────────────────────────────────────────────────

    private fun startBackgroundService() {
        if (!isJARVISActive) return
        try {
            val intent = Intent(this, JARVISBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {}
    }

    private fun stopBackgroundService() {
        try { stopService(Intent(this, JARVISBackgroundService::class.java)) } catch (e: Exception) {}
    }

    // ── UI ─────────────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.apply {
            btnVoiceActivation.setOnClickListener {
                if (isJARVISActive) deactivateJARVIS() else activateJARVIS()
            }
            btnSettings.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            btnAnalytics.setOnClickListener {
                Toast.makeText(this@MainActivity, "Скоро будет доступно", Toast.LENGTH_SHORT).show()
            }
            btnVoiceTraining.setOnClickListener {
                Toast.makeText(this@MainActivity, "Скоро будет доступно", Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.isListening.observe(this)     { updateListeningUI(it) }
        viewModel.currentCommand.observe(this)  { binding.tvCurrentCommand.text = it }
        viewModel.jarvisStatus.observe(this)    { binding.tvStatus.text = it }
    }

    private fun updateListeningUI(listening: Boolean) {
        binding.voiceVisualizer.visibility     = if (listening) View.VISIBLE else View.GONE
        binding.voiceLevelIndicator.visibility = if (listening) View.VISIBLE else View.GONE
        binding.tvListeningStatus.text         = if (listening) "Слушаю..." else "Готов"
    }

    // ── Разрешения ─────────────────────────────────────────────────────────

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (toRequest.isNotEmpty())
            ActivityCompat.requestPermissions(this, toRequest, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
            isJARVISActive) {
            startListening()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        stopBackgroundService()
        if (isJARVISActive && !isListening && !isSpeaking) startListening()
    }

    override fun onPause() {
        super.onPause()
        stopListening()
        startBackgroundService()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { speechRecognizer.destroy() } catch (e: Exception) {}
        textToSpeech.shutdown()
    }
}
