package com.jarvis.assistant.core

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import com.jarvis.assistant.features.AppManager
import com.jarvis.assistant.features.FocusModeManager
import com.jarvis.assistant.services.WeatherService
import com.jarvis.assistant.services.NewsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class CommandProcessor(
    private val context: Context,
    // speak-функция из MainActivity — избегаем неинициализированного TTS
    private val speak: (String) -> Unit
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val weatherService = WeatherService()
    private val newsService = NewsService()
    private val scope = CoroutineScope(Dispatchers.Main)

    // ── Поиск ──────────────────────────────────────────────────────────────

    fun searchGoogle(command: String) {
        val query = command
            .replace("найди в гугле", "")
            .replace("найди", "")
            .trim()
        openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
        speak("Ищу в Google: $query")
    }

    fun searchYouTube(command: String) {
        val query = command
            .replace("найди на ютубе", "")
            .replace("найди на youtube", "")
            .trim()
        openUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        speak("Ищу на YouTube: $query")
    }

    // ── Погода ─────────────────────────────────────────────────────────────

    fun getWeather(command: String) {
        val location = command
            .replace("погода", "")
            .replace("в", "")
            .trim()
            .ifEmpty { "текущее местоположение" }
        scope.launch {
            try {
                weatherService.getWeather(location) { info ->
                    speak("Погода: ${info.description}, температура ${info.temperature.toInt()} градусов")
                }
            } catch (e: Exception) {
                speak("Не удалось получить данные о погоде")
            }
        }
    }

    // ── Новости ────────────────────────────────────────────────────────────

    fun getNews() {
        scope.launch {
            try {
                newsService.getLatestNews { list ->
                    if (list.isEmpty()) {
                        speak("Новости недоступны")
                    } else {
                        val text = list.take(3).joinToString(". ") { it.title }
                        speak("Последние новости: $text")
                    }
                }
            } catch (e: Exception) {
                speak("Не удалось загрузить новости")
            }
        }
    }

    // ── Калькулятор ────────────────────────────────────────────────────────

    fun calculate(command: String) {
        val expression = command
            .replace("посчитай", "")
            .replace("сколько будет", "")
            .trim()
            .replace("плюс", "+")
            .replace("минус", "-")
            .replace("умножить на", "*")
            .replace("разделить на", "/")
            .replace(",", ".")

        try {
            // Простые вычисления через движок скриптов
            val result = evaluateExpression(expression)
            speak("Результат: $result")
        } catch (e: Exception) {
            speak("Не могу посчитать это выражение")
        }
    }

    private fun evaluateExpression(expr: String): String {
        // Простая оценка без eval — только +, -, *, /
        val tokens = expr.trim().split(Regex("(?<=[+\\-*/])|(?=[+\\-*/])"))
        if (tokens.size == 3) {
            val a = tokens[0].trim().toDouble()
            val op = tokens[1].trim()
            val b = tokens[2].trim().toDouble()
            val result = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b != 0.0) a / b else throw ArithmeticException("div by zero")
                else -> throw IllegalArgumentException()
            }
            return if (result == result.toLong().toDouble())
                result.toLong().toString()
            else
                String.format("%.2f", result)
        }
        throw IllegalArgumentException("unsupported expression")
    }

    // ── Громкость ──────────────────────────────────────────────────────────

    fun volumeUp() {
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND)
        speak("Громкость увеличена")
    }

    fun volumeDown() {
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND)
        speak("Громкость уменьшена")
    }

    // ── Приложения ─────────────────────────────────────────────────────────

    fun openApp(command: String) {
        val appName = command
            .replace("открой", "")
            .trim()
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(resolvePackageName(appName))
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            speak("Открываю $appName")
        } else {
            // Ищем по имени в установленных приложениях
            val apps = pm.getInstalledApplications(0)
            val found = apps.firstOrNull {
                pm.getApplicationLabel(it).toString().lowercase().contains(appName.lowercase())
            }
            if (found != null) {
                val launchIntent = pm.getLaunchIntentForPackage(found.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    speak("Открываю ${pm.getApplicationLabel(found)}")
                    return
                }
            }
            speak("Приложение $appName не найдено")
        }
    }

    fun closeApp(command: String) {
        speak("Закрыть приложение можно свайпом из списка задач")
    }

    private fun resolvePackageName(name: String): String {
        return when (name.lowercase()) {
            "ютуб", "youtube"           -> "com.google.android.youtube"
            "ватсап", "whatsapp"        -> "com.whatsapp"
            "телеграм", "telegram"      -> "org.telegram.messenger"
            "хром", "chrome"            -> "com.android.chrome"
            "гугл", "google"            -> "com.google.android.googlequicksearchbox"
            "камера", "camera"          -> "com.android.camera2"
            "галерея", "gallery"        -> "com.google.android.apps.photos"
            "настройки", "settings"     -> "com.android.settings"
            "музыка", "music"           -> "com.google.android.music"
            "карты", "maps"             -> "com.google.android.apps.maps"
            "вконтакте", "vk"           -> "com.vkontakte.android"
            "инстаграм", "instagram"    -> "com.instagram.android"
            "тикток", "tiktok"          -> "com.zhiliaoapp.musically"
            else                        -> name
        }
    }

    // ── Фокус режим ────────────────────────────────────────────────────────

    fun enterFocusMode() {
        try {
            FocusModeManager(context).startFocusSession()
            speak("Режим фокуса включён. Сосредоточьтесь на работе.")
        } catch (e: Exception) {
            speak("Не удалось включить режим фокуса")
        }
    }

    fun showFocusAnalytics() {
        try {
            val analytics = FocusModeManager(context).getFocusAnalytics()
            speak("Статистика фокуса: ${analytics.totalTime} минут, сессий: ${analytics.sessionCount}")
        } catch (e: Exception) {
            speak("Статистика недоступна")
        }
    }

    // ── Перевод ────────────────────────────────────────────────────────────

    fun translate(command: String) {
        val text = command.replace("переведи", "").trim()
        openUrl("https://translate.google.com/?sl=auto&tl=en&text=${Uri.encode(text)}")
        speak("Открываю перевод для: $text")
    }

    // ── WhatsApp ───────────────────────────────────────────────────────────

    fun sendWhatsAppMessage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            speak("Открываю WhatsApp")
        } catch (e: Exception) {
            speak("WhatsApp не установлен")
        }
    }

    // ── Игра ───────────────────────────────────────────────────────────────

    fun playGame() {
        val options = listOf("камень", "ножницы", "бумага")
        val choice = options.random()
        speak("Я выбрал: $choice. Что выберете вы?")
    }

    // ── Скриншот ───────────────────────────────────────────────────────────

    fun takeScreenshot() {
        speak("Скриншот можно сделать кнопками питания и уменьшения громкости")
    }

    // ── Камера ─────────────────────────────────────────────────────────────

    fun takePhoto() {
        try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            speak("Открываю камеру")
        } catch (e: Exception) {
            speak("Не удалось открыть камеру")
        }
    }

    // ── Скорость интернета ─────────────────────────────────────────────────

    fun checkInternetSpeed() {
        openUrl("https://www.speedtest.net")
        speak("Открываю тест скорости интернета")
    }

    // ── Заглушки (не реализованы) ──────────────────────────────────────────

    fun getIPLScore() {
        speak("IPL статистика недоступна")
    }

    fun createGitHubRepository(command: String) {
        speak("Создание репозитория GitHub пока не поддерживается")
    }

    // ── Утилиты ────────────────────────────────────────────────────────────

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            speak("Не удалось открыть ссылку")
        }
    }
}
