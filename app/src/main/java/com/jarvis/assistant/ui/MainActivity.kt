package com.jarvis.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val WAKE_WORD = "arise"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        setupUI()
        requestPermissions()
        startBackgroundService()
    }

    private fun initializeComponents() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        voiceManager = VoiceManager(this)
        commandProcessor = CommandProcessor(this)
        textToSpeech = TextToSpeech(this, this)

        setupSpeechRecognizer()
    }

    private fun setupUI() {
        binding.apply {
            btnVoiceActivation.setOnClickListener {
                if (isJARVISActive) {
                    deactivateJARVIS()
                } else {
                    activateJARVIS()
                }
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

        viewModel.isListening.observe(this) { listening ->
            updateListeningUI(listening)
        }

        viewModel.currentCommand.observe(this) { command ->
            binding.tvCurrentCommand.text = command
        }

        viewModel.jarvisStatus.observe(this) { status ->
            binding.tvStatus.text = status
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                viewModel.setListening(true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                binding.voiceLevelIndicator.progress = (rmsdB * 10).toInt()
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                viewModel.setListening(false)
            }

            override fun onError(error: Int) {
                viewModel.setListening(false)
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> speak("I didn't catch that. Please try again.")
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> speak("Speech timeout. Please try again.")
                    else -> speak("Voice recognition error. Please try again.")
                }
            }

            override fun onResults(results: Bundle?) {
                viewModel.setListening(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { command ->
                    processVoiceCommand(command.lowercase())
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun activateJARVIS() {
        isJARVISActive = true
        viewModel.setJARVISStatus("JARVIS is now active. Say 'Arise' to wake up.")
        binding.btnVoiceActivation.text = "Deactivate JARVIS"
        binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_deactivate)

        speak("JARVIS is now active. Say Arise to wake up.")
        startListening()
    }

    private fun deactivateJARVIS() {
        isJARVISActive = false
        viewModel.setJARVISStatus("JARVIS is inactive")
        binding.btnVoiceActivation.text = "Activate JARVIS"
        binding.btnVoiceActivation.setBackgroundResource(R.drawable.btn_activate)

        stopListening()
        speak("JARVIS deactivated. Goodbye.")
    }

    private fun startListening() {
        if (!isListening && isJARVISActive) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            try {
                speechRecognizer.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Toast.makeText(this, "Error starting voice recognition", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopListening() {
        if (isListening) {
            speechRecognizer.stopListening()
            isListening = false
        }
    }

    private fun processVoiceCommand(command: String) {
        viewModel.setCurrentCommand("You said: $command")

        when {
            command.contains(WAKE_WORD) -> {
                speak("Hello! I'm JARVIS. How can I help you today?")
                startListening()
            }
            command.contains("go to sleep") -> {
                speak("Going to sleep mode.")
                stopListening()
            }
            command.contains("finally sleep") -> {
                speak("Goodbye! JARVIS signing off.")
                finish()
            }
            command.contains("the time") -> {
                val currentTime = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                speak("The current time is $currentTime")
                startListening()
            }
            command.contains("weather") -> {
                commandProcessor.getWeather(command)
                startListening()
            }
            command.contains("google") -> {
                commandProcessor.searchGoogle(command)
                startListening()
            }
            command.contains("youtube") -> {
                commandProcessor.searchYouTube(command)
                startListening()
            }
            command.contains("news") -> {
                commandProcessor.getNews()
                startListening()
            }
            command.contains("calculate") -> {
                commandProcessor.calculate(command)
                startListening()
            }
            command.contains("focus mode") -> {
                commandProcessor.enterFocusMode()
                startListening()
            }
            command.contains("show my focus") -> {
                commandProcessor.showFocusAnalytics()
                startListening()
            }
            command.contains("translate") -> {
                commandProcessor.translate(command)
                startListening()
            }
            command.contains("whatsapp") -> {
                commandProcessor.sendWhatsAppMessage()
                startListening()
            }
            command.contains("play a game") -> {
                commandProcessor.playGame()
                startListening()
            }
            command.contains("screenshot") -> {
                commandProcessor.takeScreenshot()
                startListening()
            }
            command.contains("click my photo") -> {
                commandProcessor.takePhoto()
                startListening()
            }
            command.contains("volume up") -> {
                commandProcessor.volumeUp()
                startListening()
            }
            command.contains("volume down") -> {
                commandProcessor.volumeDown()
                startListening()
            }
            command.contains("open") -> {
                commandProcessor.openApp(command)
                startListening()
            }
            command.contains("close") -> {
                commandProcessor.closeApp(command)
                startListening()
            }
            command.contains("create github repository") ||
            command.contains("create github repo") ||
            command.contains("make github repository") -> {
                commandProcessor.createGitHubRepository(command)
                startListening()
            }
            command.contains("internet speed") -> {
                commandProcessor.checkInternetSpeed()
                startListening()
            }
            command.contains("ipl score") -> {
                commandProcessor.getIPLScore()
                startListening()
            }
            else -> {
                speak("I didn't understand that command. Please try again.")
                startListening()
            }
        }
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateListeningUI(listening: Boolean) {
        binding.apply {
            if (listening) {
                voiceVisualizer.visibility = View.VISIBLE
                tvListeningStatus.text = "Listening..."
                voiceLevelIndicator.visibility = View.VISIBLE
            } else {
                voiceVisualizer.visibility = View.GONE
                tvListeningStatus.text = "Ready"
                voiceLevelIndicator.visibility = View.GONE
            }
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

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    private fun startBackgroundService() {
        val serviceIntent = Intent(this, JARVISBackgroundService::class.java)
        startForegroundService(serviceIntent)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isJARVISActive) {
            startListening()
        }
    }

    override fun onPause() {
        super.onPause()
        stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.shutdown()
    }
}
