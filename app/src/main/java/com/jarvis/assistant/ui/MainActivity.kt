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

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val WAKE_WORD = "arise"
        private const val RETRY_DELAY_MS = 1500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Останавливаем фоновый сервис — он конфликтует с SpeechRecognizer в активити
        stopService(Intent(this, JARVISBackgroundService::class.java))

        initializeComponents()
        setupUI()
        requestPermissions()
    }

    private fun initializeComponents() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        voiceManager = VoiceManager(this)
        commandProcessor = CommandProcessor(this)
        textToSpeech = TextToSpeech(this, this)
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                viewModel.setListening(true)
                viewModel.setJARVISStatus("Listening...")
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
                        // Тихая ошибка — просто перезапускаем слушание
                        if (isJARVISActive) {
                            viewModel.setJARVISStatus("JARVIS is active. Say 'Arise' to wake up.")
                            retryListening()
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        // Занят — ждём и пробуем снова
                        handler.postDelayed({ restartRecognizer() }, RETRY_DELAY_MS)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        viewModel.setJARVISStatus("Microphone permission required")
                        Toast.makeText(this@MainActivity,
                            "Please grant microphone permission", Toast.LENGTH_LONG).show()
                    }
                    SpeechRecognizer.ERROR_AUDIO -> {
                        viewModel.setJARVISStatus("Audio error. Retrying...")
                        if (isJARVISActive) retryListening()
                    }
                    else -> {
                        // Остальные ошибки — тихий retry без сообщения пользователю
                        if (isJARVISActive) retryListening()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                viewModel.setListening(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { command ->
                    processVoiceCommand(command.lowercase())
                }
                // Продолжаем слушать
                if (isJARVISActive) retryListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partial?.get(0)?.let { binding.tvCurrentCommand.text = it }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun retryListening() {
        handler.postDelayed({ startListening() }, 300)
    }

    private fun restartRecognizer() {
        try {
            speechRecognizer.destroy()
        } catch (e: Exception) { /* ignore */ }
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
                Toast.makeText(this@MainActivity, "Analytics coming soon", Toast.LENGTH_SHORT).show()
            }

            btnVoiceTraining.setOnClickListener {
                Toast.makeText(this@MainActivity, "Voice training coming soon", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.isListening.observe(this) { updateListeningUI(it) }
        viewModel.currentCommand.observe(this) { binding.tvCurrentCommand.text = it }
        viewModel.jarvisStatus.observe(this) { binding.tvStatus.text = it }
    }

    private fun activateJARVIS() {
        if (!hasAudioPermission()) {
            requestPermissions()
            Toast.makeText(this, "Please grant microphone permission first", Toast.LENGTH_LONG).show()
            return
        }
        isJARVISActive = true
        viewModel.setJARVISStatus("JARVIS is now active. Say 'Arise' to wake up.")
        binding.btnVoiceActivation.text = "Deactivate JARVIS"
        binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_deactivate)
        speak("JARVIS is now active. Say Arise to wake up.")
        startListening()
    }

    private fun deactivateJARVIS() {
        isJARVISActive = false
        handler.removeCallbacksAndMessages(null)
        viewModel.setJARVISStatus("JARVIS is inactive")
        binding.btnVoiceActivation.text = "Activate JARVIS"
        binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_activate)
        stopListening()
        speak("JARVIS deactivated. Goodbye.")
    }

    private fun startListening() {
        if (isListening || !isJARVISActive) return
        if (!hasAudioPermission()) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
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
        try {
            speechRecognizer.stopListening()
        } catch (e: Exception) { /* ignore */ }
        isListening = false
        viewModel.setListening(false)
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun processVoiceCommand(command: String) {
        viewModel.setCurrentCommand("You said: $command")

        when {
            command.contains(WAKE_WORD) -> speak("Hello! I'm JARVIS. How can I help you today?")
            command.contains("go to sleep") -> { speak("Going to sleep mode."); stopListening(); isJARVISActive = false }
            command.contains("finally sleep") -> { speak("Goodbye! JARVIS signing off."); finish() }
            command.contains("the time") -> {
                val time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                speak("The current time is $time")
            }
            command.contains("weather")          -> commandProcessor.getWeather(command)
            command.contains("google")           -> commandProcessor.searchGoogle(command)
            command.contains("youtube")          -> commandProcessor.searchYouTube(command)
            command.contains("news")             -> commandProcessor.getNews()
            command.contains("calculate")        -> commandProcessor.calculate(command)
            command.contains("focus mode")       -> commandProcessor.enterFocusMode()
            command.contains("show my focus")    -> commandProcessor.showFocusAnalytics()
            command.contains("translate")        -> commandProcessor.translate(command)
            command.contains("whatsapp")         -> commandProcessor.sendWhatsAppMessage()
            command.contains("play a game")      -> commandProcessor.playGame()
            command.contains("screenshot")       -> commandProcessor.takeScreenshot()
            command.contains("click my photo")   -> commandProcessor.takePhoto()
            command.contains("volume up")        -> commandProcessor.volumeUp()
            command.contains("volume down")      -> commandProcessor.volumeDown()
            command.contains("open")             -> commandProcessor.openApp(command)
            command.contains("close")            -> commandProcessor.closeApp(command)
            command.contains("create github")    -> commandProcessor.createGitHubRepository(command)
            command.contains("internet speed")   -> commandProcessor.checkInternetSpeed()
            command.contains("ipl score")        -> commandProcessor.getIPLScore()
            else -> speak("I didn't understand that. Please try again.")
        }
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateListeningUI(listening: Boolean) {
        binding.apply {
            voiceVisualizer.visibility       = if (listening) View.VISIBLE else View.GONE
            voiceLevelIndicator.visibility   = if (listening) View.VISIBLE else View.GONE
            tvListeningStatus.text           = if (listening) "Listening..." else "Ready"
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
                // RECORD_AUDIO выдан — если JARVIS активен, запускаем слушание
                if (isJARVISActive) startListening()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech.setLanguage(Locale.ENGLISH)
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
        try { speechRecognizer.destroy() } catch (e: Exception) { /* ignore */ }
        textToSpeech.shutdown()
    }
}
