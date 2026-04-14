package com.jarvis.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
    private lateinit var voiceManager: VoiceManager
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    private var isListening = false
    private var isJARVISActive = false
    private val handler = Handler(Looper.getMainLooper())

    // Русская локаль
    private val russianLocale = Locale("ru", "RU")

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val RETRY_DELAY_MS = 1500L

        // Команды на русском
        private const val WAKE_WORD        = "джарвис"
        private const val CMD_SLEEP        = "спи"
        private const val CMD_EXIT         = "выключись"
        private const val CMD_TIME         = "время"
        private const val CMD_WEATHER      = "погода"
        private const val CMD_GOOGLE       = "найди в гугле"
        private const val CMD_YOUTUBE      = "найди на ютубе"
        private const val CMD_NEWS         = "новости"
        private const val CMD_CALCULATE    = "посчитай"
        private const val CMD_FOCUS        = "режим фокуса"
        private const val CMD_FOCUS_STATS  = "статистика фокуса"
        private const val CMD_TRANSLATE    = "переведи"
        private const val CMD_WHATSAPP     = "ватсап"
        private const val CMD_SCREENSHOT   = "скриншот"
        private const val CMD_PHOTO        = "сделай фото"
        private const val CMD_VOL_UP       = "громче"
        private const val CMD_VOL_DOWN     = "тише"
        private const val CMD_OPEN         = "открой"
        private const val CMD_CLOSE        = "закрой"
        private const val CMD_SPEED        = "скорость интернета"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Останавливаем фоновый сервис — конфликтует с SpeechRecognizer
        stopService(Intent(this, JARVISBackgroundService::class.java))

        initializeComponents()
        setupUI()
        requestPermissions()
    }

    private fun initializeComponents() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        voiceManager = VoiceManager(this)
        commandProcessor = CommandProcessor(this) { text -> speak(text) }
        textToSpeech = TextToSpeech(this, this)
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Распознавание речи недоступно на этом устройстве", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                viewModel.setListening(true)
                viewModel.setJARVISStatus("Слушаю...")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > 0) {
                    binding.voiceLevelIndicator.progress = (rmsdB * 10).toInt().coerceIn(0, 100)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                viewModel.setListening(false)
            }

            override fun onError(error: Int) {
                isListening = false
                viewModel.setListening(false)

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (isJARVISActive) {
                            viewModel.setJARVISStatus("Скажите «Джарвис» для активации")
                            retryListening()
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        handler.postDelayed({ restartRecognizer() }, RETRY_DELAY_MS)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        viewModel.setJARVISStatus("Нет разрешения на микрофон")
                        Toast.makeText(this@MainActivity,
                            "Разрешите доступ к микрофону", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        if (isJARVISActive) retryListening()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                viewModel.setListening(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { processVoiceCommand(it.lowercase()) }
                if (isJARVISActive) retryListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partial?.get(0)?.let { binding.tvCurrentCommand.text = it }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun retryListening() {
        handler.postDelayed({ startListening() }, 300)
    }

    private fun restartRecognizer() {
        try { speechRecognizer.destroy() } catch (e: Exception) {}
        setupSpeechRecognizer()
        if (isJARVISActive) retryListening()
    }

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

        viewModel.isListening.observe(this) { updateListeningUI(it) }
        viewModel.currentCommand.observe(this) { binding.tvCurrentCommand.text = it }
        viewModel.jarvisStatus.observe(this) { binding.tvStatus.text = it }
    }

    private fun activateJARVIS() {
        if (!hasAudioPermission()) {
            requestPermissions()
            Toast.makeText(this, "Сначала разрешите доступ к микрофону", Toast.LENGTH_LONG).show()
            return
        }
        isJARVISActive = true
        viewModel.setJARVISStatus("ДЖАРВИС активен. Скажите «Джарвис»")
        binding.btnVoiceActivation.text = "Деактивировать ДЖАРВИС"
        binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_deactivate)
        speak("ДЖАРВИС активирован. Скажите Джарвис для начала.")
        startListening()
    }

    private fun deactivateJARVIS() {
        isJARVISActive = false
        handler.removeCallbacksAndMessages(null)
        viewModel.setJARVISStatus("ДЖАРВИС неактивен")
        binding.btnVoiceActivation.text = "Активировать ДЖАРВИС"
        binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_activate)
        stopListening()
        speak("ДЖАРВИС деактивирован. До свидания.")
    }

    private fun startListening() {
        if (isListening || !isJARVISActive || !hasAudioPermission()) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Русский язык для распознавания
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            if (isJARVISActive) retryListening()
        }
    }

    private fun stopListening() {
        try { speechRecognizer.stopListening() } catch (e: Exception) {}
        isListening = false
        viewModel.setListening(false)
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun processVoiceCommand(command: String) {
        viewModel.setCurrentCommand("Вы сказали: $command")

        when {
            command.contains(WAKE_WORD)       -> speak("Привет! Я ДЖАРВИС. Чем могу помочь?")
            command.contains(CMD_SLEEP)       -> { speak("Ухожу в спящий режим."); stopListening(); isJARVISActive = false }
            command.contains(CMD_EXIT)        -> { speak("До свидания! ДЖАРВИС отключается."); finish() }
            command.contains(CMD_TIME)        -> {
                val time = java.text.SimpleDateFormat("HH:mm", russianLocale).format(Date())
                speak("Сейчас $time")
            }
            command.contains(CMD_WEATHER)     -> commandProcessor.getWeather(command)
            command.contains(CMD_GOOGLE)      -> commandProcessor.searchGoogle(command)
            command.contains(CMD_YOUTUBE)     -> commandProcessor.searchYouTube(command)
            command.contains(CMD_NEWS)        -> commandProcessor.getNews()
            command.contains(CMD_CALCULATE)   -> commandProcessor.calculate(command)
            command.contains(CMD_FOCUS)       -> commandProcessor.enterFocusMode()
            command.contains(CMD_FOCUS_STATS) -> commandProcessor.showFocusAnalytics()
            command.contains(CMD_TRANSLATE)   -> commandProcessor.translate(command)
            command.contains(CMD_WHATSAPP)    -> commandProcessor.sendWhatsAppMessage()
            command.contains(CMD_SCREENSHOT)  -> commandProcessor.takeScreenshot()
            command.contains(CMD_PHOTO)       -> commandProcessor.takePhoto()
            command.contains(CMD_VOL_UP)      -> commandProcessor.volumeUp()
            command.contains(CMD_VOL_DOWN)    -> commandProcessor.volumeDown()
            command.contains(CMD_OPEN)        -> commandProcessor.openApp(command)
            command.contains(CMD_CLOSE)       -> commandProcessor.closeApp(command)
            command.contains(CMD_SPEED)       -> commandProcessor.checkInternetSpeed()
            else -> speak("Не понял команду. Попробуйте ещё раз.")
        }
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateListeningUI(listening: Boolean) {
        binding.apply {
            voiceVisualizer.visibility     = if (listening) View.VISIBLE else View.GONE
            voiceLevelIndicator.visibility = if (listening) View.VISIBLE else View.GONE
            tvListeningStatus.text         = if (listening) "Слушаю..." else "Готов"
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                if (isJARVISActive) startListening()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Устанавливаем русский язык для TTS
            val result = textToSpeech.setLanguage(russianLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Если русский TTS не установлен — предлагаем скачать
                val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                startActivity(installIntent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isJARVISActive && !isListening) startListening()
    }

    override fun onPause() {
        super.onPause()
        stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { speechRecognizer.destroy() } catch (e: Exception) {}
        textToSpeech.shutdown()
    }
}
