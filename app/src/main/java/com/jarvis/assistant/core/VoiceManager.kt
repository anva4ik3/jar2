package com.jarvis.assistant.core

import android.content.Context
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jarvis.assistant.utils.Logger
import java.util.*

class VoiceManager(private val context: Context) {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private var isListening = false
    private var onVoiceRecognized: ((String) -> Unit)? = null
    private var onSpeechComplete: (() -> Unit)? = null

    init {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Logger.d("Voice manager ready for speech")
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                Logger.d("Voice manager beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Handle voice level changes
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Handle audio buffer
            }

            override fun onEndOfSpeech() {
                Logger.d("Voice manager end of speech")
                isListening = false
            }

            override fun onError(error: Int) {
                Logger.e("Voice manager speech recognition error: $error")
                isListening = false
                onVoiceRecognized?.invoke("")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { text ->
                    Logger.d("Voice manager recognized: $text")
                    onVoiceRecognized?.invoke(text)
                }
                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Logger.e("Language not supported for TTS")
                } else {
                    Logger.d("TTS initialized successfully")
                }
            } else {
                Logger.e("TTS initialization failed")
            }
        }

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Logger.d("TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Logger.d("TTS completed: $utteranceId")
                onSpeechComplete?.invoke()
            }

            override fun onError(utteranceId: String?) {
                Logger.e("TTS error: $utteranceId")
            }
        })
    }

    fun startListening(callback: (String) -> Unit) {
        if (!isListening) {
            onVoiceRecognized = callback
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            try {
                speechRecognizer.startListening(intent)
                Logger.d("Voice manager started listening")
            } catch (e: Exception) {
                Logger.e("Voice manager failed to start listening: ${e.message}")
            }
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer.stopListening()
            isListening = false
            Logger.d("Voice manager stopped listening")
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        onSpeechComplete = onComplete
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_id")
        Logger.d("Voice manager speaking: $text")
    }

    fun speak(text: String) {
        speak(text, null)
    }

    fun setSpeechRate(rate: Float) {
        textToSpeech.setSpeechRate(rate)
        Logger.d("Voice manager speech rate set to: $rate")
    }

    fun setPitch(pitch: Float) {
        textToSpeech.setPitch(pitch)
        Logger.d("Voice manager pitch set to: $pitch")
    }

    fun setLanguage(locale: Locale) {
        val result = textToSpeech.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Logger.e("Language not supported: ${locale.displayLanguage}")
        } else {
            Logger.d("Voice manager language set to: ${locale.displayLanguage}")
        }
    }

    fun isListening(): Boolean {
        return isListening
    }

    fun isSpeaking(): Boolean {
        return textToSpeech.isSpeaking
    }

    fun stopSpeaking() {
        textToSpeech.stop()
        Logger.d("Voice manager stopped speaking")
    }

    fun shutdown() {
        speechRecognizer.destroy()
        textToSpeech.shutdown()
        Logger.d("Voice manager shutdown")
    }
} 