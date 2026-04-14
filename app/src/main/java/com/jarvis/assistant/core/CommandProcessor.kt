package com.jarvis.assistant.core

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import com.jarvis.assistant.features.AlarmManager
import com.jarvis.assistant.features.AppManager
import com.jarvis.assistant.features.BatteryManager
import com.jarvis.assistant.features.BluetoothManager
import com.jarvis.assistant.features.CameraManager
import com.jarvis.assistant.features.EmergencyManager
import com.jarvis.assistant.features.FlashlightManager
import com.jarvis.assistant.features.FocusModeManager
import com.jarvis.assistant.features.GameManager
import com.jarvis.assistant.features.HealthManager
import com.jarvis.assistant.features.LocationManager
import com.jarvis.assistant.features.MusicManager
import com.jarvis.assistant.features.SMSManager
import com.jarvis.assistant.features.ScreenshotManager
import com.jarvis.assistant.features.SmartHomeManager
import com.jarvis.assistant.features.TimerManager
import com.jarvis.assistant.features.TranslatorService
import com.jarvis.assistant.features.WhatsAppManager
import com.jarvis.assistant.features.WiFiManager
import com.jarvis.assistant.services.GitHubService
import com.jarvis.assistant.services.IPLScoreService
import com.jarvis.assistant.services.NewsService
import com.jarvis.assistant.services.SpeedTestService
import com.jarvis.assistant.services.WeatherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class CommandProcessor(private val context: Context) {

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var audioManager: AudioManager
    private val weatherService = WeatherService()
    private val newsService = NewsService()
    private val gitHubService = GitHubService()
    private val speedTestService = SpeedTestService()
    private val iplScoreService = IPLScoreService()
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun getWeather(command: String) {
        val location = command.replace("weather", "").replace("in", "").trim()
        scope.launch {
            weatherService.getWeather(location) { weatherInfo ->
                speak("Weather in $location: ${weatherInfo.description}, Temperature: ${weatherInfo.temperature}°C")
            }
        }
    }

    fun searchGoogle(command: String) {
        val query = command.replace("google", "").trim()
        openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
        speak("Searching Google for $query")
    }

    fun searchYouTube(command: String) {
        val query = command.replace("youtube", "").trim()
        openUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        speak("Searching YouTube for $query")
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun getNews() {
        newsService.getLatestNews { newsList ->
            val newsText = newsList.take(3).joinToString(". ") { it.title }
            speak("Latest news: $newsText")
        }
    }

    fun calculate(command: String) {
        val expression = command.replace("calculate", "").trim()
        val result = expression.toDoubleOrNull() ?: 0.0
        speak("The result is $result")
    }

    fun enterFocusMode() {
        FocusModeManager(context).startFocusSession()
        speak("Entering focus mode. I'll minimize distractions for you.")
    }

    fun showFocusAnalytics() {
        val analytics = FocusModeManager(context).getFocusAnalytics()
        speak("Your focus analytics: Total focus time ${analytics.totalTime} minutes, Sessions: ${analytics.sessionCount}")
    }

    fun translate(command: String) {
        val text = command.replace("translate", "").trim()
        TranslatorService().translate(text, "en") { translatedText ->
            speak("Translation: $translatedText")
        }
    }

    fun sendWhatsAppMessage() {
        WhatsAppManager(context).sendMessage { success ->
            speak(if (success) "WhatsApp message sent successfully" else "Failed to send WhatsApp message")
        }
    }

    fun playGame() {
        GameManager().startRockPaperScissors { result ->
            speak("Game result: $result")
        }
    }

    fun takeScreenshot() {
        ScreenshotManager(context).takeScreenshot { success ->
            speak(if (success) "Screenshot captured successfully" else "Failed to capture screenshot")
        }
    }

    fun takePhoto() {
        CameraManager(context).takePhoto { success ->
            speak(if (success) "Photo captured successfully" else "Failed to capture photo")
        }
    }

    fun volumeUp() {
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND)
        speak("Volume increased")
    }

    fun volumeDown() {
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND)
        speak("Volume decreased")
    }

    fun openApp(command: String) {
        val appName = command.replace("open", "").trim()
        AppManager(context).openApp(appName) { success ->
            speak(if (success) "Opening $appName" else "Could not find or open $appName")
        }
    }

    fun closeApp(command: String) {
        val appName = command.replace("close", "").trim()
        AppManager(context).closeApp(appName) { success ->
            speak(if (success) "Closing $appName" else "Could not close $appName")
        }
    }

    fun createGitHubRepository(command: String) {
        val repoName = command
            .replace("create github repository", "")
            .replace("create github repo", "")
            .replace("make github repository", "")
            .trim()
        gitHubService.getRepositories(repoName) { repos ->
            speak("GitHub repositories for $repoName: ${repos.joinToString()}")
        }
    }

    fun checkInternetSpeed() {
        speedTestService.runSpeedTest { result ->
            speak("Internet speed: $result")
        }
    }

    fun getIPLScore() {
        iplScoreService.getLatestScore { score ->
            speak("IPL Score: $score")
        }
    }

    fun controlSmartHome(command: String) {
        val smartHomeManager = SmartHomeManager(context)
        when {
            command.contains("turn on") -> {
                val device = command.replace("turn on", "").trim()
                smartHomeManager.turnOnDevice(device) { success ->
                    speak(if (success) "Turned on $device" else "Failed to turn on $device")
                }
            }
            command.contains("turn off") -> {
                val device = command.replace("turn off", "").trim()
                smartHomeManager.turnOffDevice(device) { success ->
                    speak(if (success) "Turned off $device" else "Failed to turn off $device")
                }
            }
            command.contains("set temperature") -> {
                val temperature = command.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 22
                smartHomeManager.setTemperature(temperature) { success ->
                    speak(if (success) "Temperature set to $temperature°C" else "Failed to set temperature")
                }
            }
        }
    }

    fun navigateTo(command: String) {
        val destination = command.replace("navigate to", "").trim()
        LocationManager(context).navigateTo(destination) { success ->
            speak(if (success) "Starting navigation to $destination" else "Could not start navigation to $destination")
        }
    }

    fun sendSMS(command: String) {
        val contact = command.split(" ").firstOrNull() ?: ""
        val message = command.replace("send sms", "").trim()
        SMSManager(context).sendSMS(contact, message) { success ->
            speak(if (success) "SMS sent to $contact" else "Failed to send SMS")
        }
    }

    fun emergencyCall() {
        EmergencyManager(context).makeEmergencyCall { success ->
            speak(if (success) "Emergency call initiated" else "Failed to make emergency call")
        }
    }

    fun getBatteryStatus() {
        val status = BatteryManager(context).getBatteryStatus()
        speak("Battery level: ${status.level}%, Status: ${status.status}")
    }

    fun toggleFlashlight() {
        FlashlightManager(context).toggleFlashlight { isOn ->
            speak(if (isOn) "Flashlight turned on" else "Flashlight turned off")
        }
    }

    fun toggleWiFi() {
        WiFiManager(context).toggleWiFi { isEnabled ->
            speak(if (isEnabled) "WiFi enabled" else "WiFi disabled")
        }
    }

    fun toggleBluetooth() {
        BluetoothManager(context).toggleBluetooth { isEnabled ->
            speak(if (isEnabled) "Bluetooth enabled" else "Bluetooth disabled")
        }
    }

    fun getHealthData() {
        val healthManager = HealthManager(context)
        healthManager.getStepCount { steps ->
            healthManager.getHealthTip { tip ->
                speak("Health data: Steps $steps. Tip: $tip")
            }
        }
    }

    fun playMusic() {
        MusicManager(context).playMusic("") { success ->
            speak(if (success) "Playing music" else "Failed to play music")
        }
    }

    fun pauseMusic() {
        MusicManager(context).pauseMusic { success ->
            speak(if (success) "Music paused" else "Failed to pause music")
        }
    }

    fun setTimer(command: String) {
        val time = command.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
        TimerManager(context).setTimer(time) { success ->
            speak(if (success) "Timer set for $time minutes" else "Failed to set timer")
        }
    }

    fun setAlarm(command: String) {
        val time = command.replace("set alarm", "").trim()
        AlarmManager(context).setAlarm(time) { success ->
            speak(if (success) "Alarm set for $time" else "Failed to set alarm")
        }
    }
}
