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

    private var isListening      = false
    private var isJARVISActive   = false
    private var awaitingCommand  = false
    private var isSpeaking       = false
    private val handler          = Handler(Looper.getMainLooper())
    private val russianLocale    = Locale("ru", "RU")

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val RETRY_DELAY_MS          = 1500L
        private const val AFTER_TTS_DELAY_MS      = 600L
        private const val COMMAND_TIMEOUT_MS      = 8000L
        private const val WAKE_WORD               = "джарвис"
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
                startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            }
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(u: String?) {
                    isSpeaking = true
                    handler.post { stopListening() }
                }
                override fun onDone(u: String?) {
                    isSpeaking = false
                    if (isJARVISActive) handler.postDelayed({ startListening() }, AFTER_TTS_DELAY_MS)
                }
                override fun onError(u: String?) {
                    isSpeaking = false
                    if (isJARVISActive) handler.postDelayed({ startListening() }, AFTER_TTS_DELAY_MS)
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
            override fun onReadyForSpeech(p: Bundle?) {
                isListening = true
                viewModel.setListening(true)
                viewModel.setJARVISStatus(if (awaitingCommand) "Слушаю команду..." else "Скажите «Джарвис»")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > 0)
                    binding.voiceLevelIndicator.progress = (rmsdB * 10).toInt().coerceIn(0, 100)
            }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false; viewModel.setListening(false) }
            override fun onError(error: Int) {
                isListening = false; viewModel.setListening(false)
                if (!isJARVISActive || isSpeaking) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (awaitingCommand) {
                            awaitingCommand = false
                            viewModel.setJARVISStatus("Скажите «Джарвис»")
                        }
                        handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        handler.postDelayed({ restartRecognizer() }, RETRY_DELAY_MS)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        viewModel.setJARVISStatus("Нет разрешения на микрофон")
                    else -> handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false; viewModel.setListening(false)
                if (isSpeaking) return
                val command = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.get(0)?.lowercase(russianLocale)?.trim()
                if (!command.isNullOrEmpty()) processVoiceCommand(command)
                else if (isJARVISActive) handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
            }
            override fun onPartialResults(p: Bundle?) {
                if (isSpeaking) return
                p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.get(0)?.let { binding.tvCurrentCommand.text = it }
            }
            override fun onEvent(e: Int, p: Bundle?) {}
        })
    }

    private fun startListening() {
        if (isListening || !isJARVISActive || isSpeaking || !hasAudioPermission()) return
        val silenceMs = if (awaitingCommand) 4000L else 6000L
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
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
        isListening = false; viewModel.setListening(false)
    }

    private fun restartRecognizer() {
        try { speechRecognizer.destroy() } catch (e: Exception) {}
        setupSpeechRecognizer()
        if (isJARVISActive && !isSpeaking)
            handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
    }

    // ── Активация / Деактивация ────────────────────────────────────────────

    private fun activateJARVIS() {
        if (!hasAudioPermission()) {
            requestPermissions()
            Toast.makeText(this, "Сначала разрешите доступ к микрофону", Toast.LENGTH_LONG).show()
            return
        }
        isJARVISActive = true; awaitingCommand = false
        viewModel.setJARVISStatus("Скажите «Джарвис»")
        binding.btnVoiceActivation.text = "Деактивировать ДЖАРВИС"
        binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_deactivate)
        speak("ДЖАРВИС активирован. Скажите Джарвис.")
    }

    private fun deactivateJARVIS() {
        isJARVISActive = false; awaitingCommand = false
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

        if (!awaitingCommand) {
            if (command.contains(WAKE_WORD)) {
                awaitingCommand = true
                val after = command.substringAfter(WAKE_WORD).trim()
                if (after.length > 2) {
                    speak("Выполняю.")
                    handler.postDelayed({ executeCommand(after) }, 300L)
                } else {
                    speak("Слушаю.")
                    handler.postDelayed({
                        if (awaitingCommand) {
                            awaitingCommand = false
                            viewModel.setJARVISStatus("Скажите «Джарвис»")
                        }
                    }, COMMAND_TIMEOUT_MS)
                }
            } else {
                if (isJARVISActive) handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
            }
            return
        }

        awaitingCommand = false
        handler.removeCallbacksAndMessages(null)
        executeCommand(command)
    }

    private fun executeCommand(cmd: String) {
        when {
            // Управление JARVIS
            cmd.contains("спи") || cmd.contains("стоп") -> {
                isJARVISActive = false; awaitingCommand = false
                speak("Ухожу в спящий режим.")
                handler.post {
                    binding.btnVoiceActivation.text = "Активировать ДЖАРВИС"
                    binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_activate)
                    viewModel.setJARVISStatus("ДЖАРВИС неактивен")
                }
            }
            cmd.contains("выключись") -> {
                speak("До свидания!")
                handler.postDelayed({ finish() }, 1500L)
            }

            // Время и дата
            cmd.contains("время") -> {
                val time = java.text.SimpleDateFormat("HH:mm", russianLocale).format(Date())
                speak("Сейчас $time")
            }
            cmd.contains("дата") || cmd.contains("число") || cmd.contains("день") -> {
                val date = java.text.SimpleDateFormat("d MMMM, EEEE", russianLocale).format(Date())
                speak("Сегодня $date")
            }

            // Батарея
            cmd.contains("батарея") || cmd.contains("заряд") -> {
                val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speak("Заряд батареи: $level процентов")
            }

            // Громкость
            cmd.contains("громче")             -> commandProcessor.volumeUp()
            cmd.contains("тише")               -> commandProcessor.volumeDown()
            cmd.contains("максимальная громкость") || cmd.contains("громкость максимальная") ->
                commandProcessor.setMaxVolume()
            cmd.contains("без звука") || cmd.contains("выключи звук") || cmd.contains("тихий режим") ->
                commandProcessor.setMute()
            cmd.contains("включи звук")        -> commandProcessor.setUnmute()

            // Фонарик
            cmd.contains("включи фонарик") || cmd.contains("фонарик включи") ->
                commandProcessor.flashlightOn()
            cmd.contains("выключи фонарик") || cmd.contains("фонарик выключи") ->
                commandProcessor.flashlightOff()
            cmd.contains("фонарик")            -> commandProcessor.toggleFlashlight()

            // Звонки
            cmd.contains("позвони") || cmd.contains("набери") || cmd.contains("звони") ->
                commandProcessor.makeCall(cmd)

            // SMS
            cmd.contains("отправь смс") || cmd.contains("напиши смс") ||
            cmd.contains("отправь сообщение") -> commandProcessor.sendSms(cmd)

            // WhatsApp
            cmd.contains("ватсап") || cmd.contains("whatsapp") ->
                commandProcessor.sendWhatsAppMessage(cmd)

            // Будильник и таймер
            cmd.contains("будильник")          -> commandProcessor.setAlarm(cmd)
            cmd.contains("таймер")             -> commandProcessor.setTimer(cmd)

            // Поиск
            cmd.contains("ютуб")               -> commandProcessor.searchYouTube(cmd)
            cmd.contains("гугл") || cmd.contains("найди") || cmd.contains("загугли") ->
                commandProcessor.searchGoogle(cmd)

            // Новости и расчёты
            cmd.contains("новости")            -> commandProcessor.getNews()
            cmd.contains("посчитай") || cmd.contains("сколько будет") ->
                commandProcessor.calculate(cmd)

            // Навигация
            cmd.contains("навигация") || cmd.contains("маршрут") || cmd.contains("как добраться") ->
                commandProcessor.navigateTo(cmd)
            cmd.contains("покажи на карте") || cmd.contains("где находится") ->
                commandProcessor.showOnMap(cmd)

            // WiFi, Bluetooth, Яркость
            cmd.contains("вайфай") || cmd.contains("wifi") || cmd.contains("wi-fi") ->
                commandProcessor.toggleWifi(cmd)
            cmd.contains("блютуз") || cmd.contains("bluetooth") ->
                commandProcessor.toggleBluetooth(cmd)
            cmd.contains("яркость") || cmd.contains("ярче") || cmd.contains("темнее") ->
                commandProcessor.setBrightness(cmd)

            // Режим не беспокоить
            cmd.contains("не беспокоить") || cmd.contains("тихий режим") ->
                commandProcessor.setDoNotDisturb(true)
            cmd.contains("выключи не беспокоить") ->
                commandProcessor.setDoNotDisturb(false)

            // Фокус, перевод
            cmd.contains("фокус")              -> commandProcessor.enterFocusMode()
            cmd.contains("переведи")           -> commandProcessor.translate(cmd)

            // Камера
            cmd.contains("фронталка") || cmd.contains("селфи") ->
                commandProcessor.openFrontCamera()
            cmd.contains("фото") || cmd.contains("камера") ->
                commandProcessor.takePhoto()

            // Скорость, настройки, приложения
            cmd.contains("скорость интернета") -> commandProcessor.checkInternetSpeed()
            cmd.contains("открой настройки") || cmd.contains("настройки") ->
                commandProcessor.openSettings(cmd)
            cmd.contains("открой") || cmd.contains("запусти") ->
                commandProcessor.openApp(cmd)
            cmd.contains("закрой")             -> commandProcessor.closeApp(cmd)

            // Игра
            cmd.contains("камень ножницы") || cmd.contains("сыграем") ->
                commandProcessor.playGame()

            // Скриншот
            cmd.contains("скриншот")           -> commandProcessor.takeScreenshot()

            else -> {
                speak("Не понял команду. Попробуйте ещё раз.")
                awaitingCommand = true
            }
        }
    }

    // ── Фоновый сервис ─────────────────────────────────────────────────────

    private fun startBackgroundService() {
        if (!isJARVISActive) return
        try {
            val intent = Intent(this, JARVISBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
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
        viewModel.isListening.observe(this)    { updateListeningUI(it) }
        viewModel.currentCommand.observe(this) { binding.tvCurrentCommand.text = it }
        viewModel.jarvisStatus.observe(this)   { binding.tvStatus.text = it }
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
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val toRequest = perms.filter {
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
            isJARVISActive) startListening()
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
