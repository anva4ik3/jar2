package com.jarvis.assistant.core

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.jarvis.assistant.features.*
import com.jarvis.assistant.services.WeatherService
import com.jarvis.assistant.services.NewsService
import com.jarvis.assistant.services.GitHubService
import com.jarvis.assistant.services.SpeedTestService
import com.jarvis.assistant.services.IPLScoreService
import java.util.*

class CommandProcessor(private val context: Context) {

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var audioManager: AudioManager
    private lateinit var weatherService: WeatherService
    private lateinit var newsService: NewsService
    private lateinit var gitHubService: GitHubService
    private lateinit var speedTestService: SpeedTestService
    private lateinit var iplScoreService: IPLScoreService

    init {
        initializeServices()
    }

    private fun initializeServices() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        weatherService = WeatherService()
        newsService = NewsService()
        gitHubService = GitHubService()
        speedTestService = SpeedTestService()
        iplScoreService = IPLScoreService()
    }

    fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // Weather Commands
    fun getWeather(command: String) {
        val location = extractLocationFromCommand(command)
        weatherService.getWeather(location) { weatherInfo ->
            speak("Weather in $location: ${weatherInfo.description}, Temperature: ${weatherInfo.temperature}°C")
        }
    }

    private fun extractLocationFromCommand(command: String): String {
        return command.replace("weather", "").replace("in", "").trim()
    }

    // Search Commands
    fun searchGoogle(command: String) {
        val query = command.replace("google", "").trim()
        val searchUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
        openUrl(searchUrl)
        speak("Searching Google for $query")
    }

    fun searchYouTube(command: String) {
        val query = command.replace("youtube", "").trim()
        val searchUrl = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        openUrl(searchUrl)
        speak("Searching YouTube for $query")
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // News Commands
    fun getNews() {
        newsService.getLatestNews { newsList ->
            val newsText = newsList.take(3).joinToString(". ") { it.title }
            speak("Latest news: $newsText")
        }
    }

    // Calculator Commands
    fun calculate(command: String) {
        val expression = command.replace("calculate", "").trim()
        try {
            val result = evaluateExpression(expression)
            speak("The result is $result")
        } catch (e: Exception) {
            speak("Sorry, I couldn't calculate that expression")
        }
    }

    private fun evaluateExpression(expression: String): Double {
        return expression.toDoubleOrNull() ?: 0.0
    }

    // Focus Mode Commands
    fun enterFocusMode() {
        val focusManager = FocusModeManager(context)
        focusManager.startFocusSession()
        speak("Entering focus mode. I'll minimize distractions for you.")
    }

    fun showFocusAnalytics() {
        val focusManager = FocusModeManager(context)
        val analytics = focusManager.getFocusAnalytics()
        speak("Your focus analytics: Total focus time ${analytics.totalTime} minutes, Sessions: ${analytics.sessionCount}")
    }

    // Translation Commands
    fun translate(command: String) {
        val text = command.replace("translate", "").trim()
        val translator = TranslatorService()
        translator.translate(text, "en") { translatedText ->
            speak("Translation: $translatedText")
        }
    }

    // WhatsApp Commands
    fun sendWhatsAppMessage() {
        val whatsAppManager = WhatsAppManager(context)
        whatsAppManager.sendMessage { success ->
            if (success) {
                speak("WhatsApp message sent successfully")
            } else {
                speak("Failed to send WhatsApp message")
            }
        }
    }

    // Gaming Commands
    fun playGame() {
        val gameManager = GameManager()
        gameManager.startRockPaperScissors { result ->
            speak("Game result: $result")
        }
    }

    // Screenshot Commands
    fun takeScreenshot() {
        val screenshotManager = ScreenshotManager(context)
        screenshotManager.takeScreenshot { success ->
            if (success) {
                speak("Screenshot captured successfully")
            } else {
                speak("Failed to capture screenshot")
            }
        }
    }

    // Camera Commands
    fun takePhoto() {
        val cameraManager = com.jarvis.assistant.features.CameraManager(context)
        cameraManager.takePhoto { success ->
            if (success) {
                speak("Photo captured successfully")
            } else {
                speak("Failed to capture photo")
            }
        }
    }

    // Volume Control Commands
    fun volumeUp() {
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND)
        speak("Volume increased")
    }

    fun volumeDown() {
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND)
        speak("Volume decreased")
    }

    // App Management Commands
    fun openApp(command: String) {
        val appName = command.replace("open", "").trim()
        val appManager = AppManager(context)
        appManager.openApp(appName) { success ->
            if (success) {
                speak("Opening $appName")
            } else {
                speak("Could not find or open $appName")
            }
        }
    }

    fun closeApp(command: String) {
        val appName = command.replace("close", "").trim()
        val appManager = AppManager(context)
        appManager.closeApp(appName) { success ->
            if (success) {
                speak("Closing $appName")
            } else {
                speak("Could not close $appName")
            }
        }
    }

    // GitHub Commands
    fun createGitHubRepository(command: String) {
        val repoName = extractRepositoryName(command)
        gitHubService.getRepositories(repoName) { repos ->
            speak("GitHub repositories for $repoName: ${repos.joinToString()}")
        }
    }

    private fun extractRepositoryName(command: String): String {
        return command.replace("create github repository", "")
            .replace("create github repo", "")
            .replace("make github repository", "")
            .trim()
    }

    // Internet Speed Commands
    fun checkInternetSpeed() {
        speedTestService.runSpeedTest { result ->
            speak("Internet speed: $result")
        }
    }

    // IPL Score Commands
    fun getIPLScore() {
        iplScoreService.getLatestScore { score ->
            speak("IPL Score: $score")
        }
    }

    // Smart Home Commands
    fun controlSmartHome(command: String) {
        val smartHomeManager = SmartHomeManager(context)
        when {
            command.contains("turn on") -> {
                val device = extractDeviceName(command, "turn on")
                smartHomeManager.turnOnDevice(device) { success ->
                    speak(if (success) "Turned on $device" else "Failed to turn on $device")
                }
            }
            command.contains("turn off") -> {
                val device = extractDeviceName(command, "turn off")
                smartHomeManager.turnOffDevice(device) { success ->
                    speak(if (success) "Turned off $device" else "Failed to turn off $device")
                }
            }
            command.contains("set temperature") -> {
                val temperature = extractTemperature(command)
                smartHomeManager.setTemperature(temperature) { success ->
                    speak(if (success) "Temperature set to $temperature°C" else "Failed to set temperature")
                }
            }
        }
    }

    private fun extractDeviceName(command: String, action: String): String {
        return command.replace(action, "").trim()
    }

    private fun extractTemperature(command: String): Int {
        return command.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 22
    }

    // Location Commands
    fun navigateTo(command: String) {
        val destination = command.replace("navigate to", "").trim()
        val locationManager = com.jarvis.assistant.features.LocationManager(context)
        locationManager.navigateTo(destination) { success ->
            if (success) {
                speak("Starting navigation to $destination")
            } else {
                speak("Could not start navigation to $destination")
            }
        }
    }

    // SMS Commands
    fun sendSMS(command: String) {
        val smsManager = SMSManager(context)
        val contact = extractContactFromCommand(command)
        val message = extractMessageFromCommand(command)
        smsManager.sendSMS(contact, message) { success ->
            speak(if (success) "SMS sent to $contact" else "Failed to send SMS")
        }
    }

    private fun extractContactFromCommand(command: String): String {
        return command.split(" ").firstOrNull() ?: ""
    }

    private fun extractMessageFromCommand(command: String): String {
        return command.replace("send sms", "").trim()
    }

    // Emergency Commands
    fun emergencyCall() {
        val emergencyManager = EmergencyManager(context)
        emergencyManager.makeEmergencyCall { success ->
            speak(if (success) "Emergency call initiated" else "Failed to make emergency call")
        }
    }

    // Battery Commands
    fun getBatteryStatus() {
        val batteryManager = BatteryManager(context)
        val status = batteryManager.getBatteryStatus()
        speak("Battery level: ${status.level}%, Status: ${status.status}")
    }

    // Flashlight Commands
    fun toggleFlashlight() {
        val flashlightManager = FlashlightManager(context)
        flashlightManager.toggleFlashlight { isOn ->
            speak(if (isOn) "Flashlight turned on" else "Flashlight turned off")
        }
    }

    // WiFi Commands
    fun toggleWiFi() {
        val wifiManager = WiFiManager(context)
        wifiManager.toggleWiFi { isEnabled ->
            speak(if (isEnabled) "WiFi enabled" else "WiFi disabled")
        }
    }

    // Bluetooth Commands
    fun toggleBluetooth() {
        val bluetoothManager = com.jarvis.assistant.features.BluetoothManager(context)
        bluetoothManager.toggleBluetooth { isEnabled ->
            speak(if (isEnabled) "Bluetooth enabled" else "Bluetooth disabled")
        }
    }

    // Health Commands
    fun getHealthData() {
        val healthManager = HealthManager(context)
        healthManager.getStepCount { steps ->
            healthManager.getHealthTip { tip ->
                speak("Health data: Steps $steps. Tip: $tip")
            }
        }
    }

    // Music Commands
    fun playMusic() {
        val musicManager = MusicManager(context)
        musicManager.playMusic { success ->
            speak(if (success) "Playing music" else "Failed to play music")
        }
    }

    fun pauseMusic() {
        val musicManager = MusicManager(context)
        musicManager.pauseMusic { success ->
            speak(if (success) "Music paused" else "Failed to pause music")
        }
    }

    // Timer Commands
    fun setTimer(command: String) {
        val time = extractTimeFromCommand(command)
        val timerManager = TimerManager(context)
        timerManager.setTimer(time) { success ->
            speak(if (success) "Timer set for $time minutes" else "Failed to set timer")
        }
    }

    private fun extractTimeFromCommand(command: String): Int {
        return command.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
    }

    // Alarm Commands
    fun setAlarm(command: String) {
        val time = extractAlarmTime(command)
        val alarmManager = com.jarvis.assistant.features.AlarmManager(context)
        alarmManager.setAlarm(time) { success ->
            speak(if (success) "Alarm set for $time" else "Failed to set alarm")
        }
    }

    private fun extractAlarmTime(command: String): String {
        return command.replace("set alarm", "").trim()
    }
}
