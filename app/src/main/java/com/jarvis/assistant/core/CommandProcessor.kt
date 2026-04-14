package com.jarvis.assistant.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
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

    companion object {
        private const val NOTIF_CHANNEL_ID = "jarvis_launch"
        private const val NOTIF_CHANNEL_NAME = "JARVIS Запуск"
        private const val NOTIF_ID = 2001
    }

    // ── Поиск ──────────────────────────────────────────────────────────────

    fun searchGoogle(command: String) {
        val query = command
            .replace("найди в гугле", "")
            .replace("найди в google", "")
            .replace("загугли", "")
            .replace("поищи в гугле", "")
            .replace("найди", "")
            .replace("гугл", "")
            .replace("поищи", "")
            .trim()
            .ifEmpty { command }

        // Пробуем Intent ACTION_WEB_SEARCH (откроет браузер по умолчанию)
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (context.packageManager.resolveActivity(searchIntent, 0) != null) {
            launchActivity(searchIntent, "Ищу в Google: $query")
        } else {
            openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
            speak("Ищу в Google: $query")
        }
    }

    fun searchYouTube(command: String) {
        val query = command
            .replace("найди на ютубе", "")
            .replace("найди на youtube", "")
            .replace("поищи на ютубе", "")
            .replace("ютуб", "")
            .replace("youtube", "")
            .replace("поищи", "")
            .replace("найди", "")
            .trim()
            .ifEmpty { command }

        // Сначала пробуем открыть приложение YouTube с поиском
        val ytAppIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (context.packageManager.resolveActivity(ytAppIntent, 0) != null) {
            launchActivity(ytAppIntent, "Ищу на YouTube: $query")
        } else {
            // Если YouTube не установлен — открываем в браузере
            openUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            speak("Ищу на YouTube: $query")
        }
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
            val result = evaluateExpression(expression)
            speak("Результат: $result")
        } catch (e: Exception) {
            speak("Не могу посчитать это выражение")
        }
    }

    private fun evaluateExpression(expr: String): String {
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
            .replace("запусти", "")
            .replace("запустить", "")
            .trim()

        val pm = context.packageManager

        // 1. Пробуем по известным пакетам
        val resolvedPackage = resolvePackageName(appName)
        if (resolvedPackage != appName) {
            val intent = pm.getLaunchIntentForPackage(resolvedPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchActivity(intent, "Открываю $appName")
                return
            }
        }

        // 2. Ищем по лаунчер-приложениям (работает на Android 11+ с <queries> в манифесте)
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val allApps = pm.queryIntentActivities(launcherIntent, 0)
        val match = allApps.firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.getDefault())
                .contains(appName.lowercase(Locale.getDefault()))
        }

        if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchActivity(launchIntent, "Открываю ${match.loadLabel(pm)}")
                return
            }
        }

        speak("Приложение $appName не найдено")
    }

    fun closeApp(command: String) {
        speak("Закрыть приложение можно свайпом из списка задач")
    }

    private fun resolvePackageName(name: String): String {
        return when (name.lowercase(Locale.getDefault()).trim()) {
            "ютуб", "youtube", "ютубе"           -> "com.google.android.youtube"
            "ватсап", "whatsapp", "вотсап"        -> "com.whatsapp"
            "телеграм", "telegram", "телеграмм"   -> "org.telegram.messenger"
            "хром", "chrome", "хроме"             -> "com.android.chrome"
            "гугл", "google"                      -> "com.google.android.googlequicksearchbox"
            "камера", "camera"                    -> "com.android.camera2"
            "галерея", "gallery", "фотографии"    -> "com.google.android.apps.photos"
            "настройки", "settings"               -> "com.android.settings"
            "карты", "maps", "гугл карты"         -> "com.google.android.apps.maps"
            "вконтакте", "вк", "vk"               -> "com.vkontakte.android"
            "инстаграм", "instagram", "инста"     -> "com.instagram.android"
            "тикток", "tiktok"                    -> "com.zhiliaoapp.musically"
            "спотифай", "spotify"                 -> "com.spotify.music"
            "нетфликс", "netflix"                 -> "com.netflix.mediaclient"
            "телефон", "звонки", "набор"          -> "com.google.android.dialer"
            "контакты"                            -> "com.google.android.contacts"
            "смс", "сообщения", "messages"        -> "com.google.android.apps.messaging"
            "калькулятор", "calculator"           -> "com.google.android.calculator"
            "почта", "gmail"                      -> "com.google.android.gm"
            "музыка", "music"                     -> "com.google.android.music"
            "файлы", "файловый менеджер"          -> "com.google.android.apps.nbu.files"
            "браузер", "browser"                  -> "com.android.chrome"
            else                                  -> name
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
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchActivity(intent, "Открываю WhatsApp")
    }

    // ── Игра ───────────────────────────────────────────────────────────────

    fun playGame() {
        val options = listOf("камень", "ножницы", "бумага")
        val choice = options.random()
        speak("Я выбрал: $choice. Что выберете вы?")
    }

    // ── Скриншот ───────────────────────────────────────────────────────────

    fun takeScreenshot() {
        speak("Скриншот можно сделать кнопками питания и уменьшения громкости одновременно")
    }

    // ── Камера ─────────────────────────────────────────────────────────────

    fun takePhoto() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchActivity(intent, "Открываю камеру")
    }

    // ── Скорость интернета ─────────────────────────────────────────────────

    fun checkInternetSpeed() {
        openUrl("https://www.speedtest.net")
        speak("Открываю тест скорости интернета")
    }

    // ── Заглушки ───────────────────────────────────────────────────────────

    fun getIPLScore() {
        speak("IPL статистика недоступна")
    }

    fun createGitHubRepository(command: String) {
        speak("Создание репозитория GitHub пока не поддерживается")
    }

    // ── Запуск Activity (безопасно из фона) ────────────────────────────────
    // На Android 10+ нельзя запустить Activity из фона напрямую.
    // Если startActivity бросает исключение — показываем уведомление,
    // по нажатию на которое пользователь откроет нужное приложение.

    private fun launchActivity(intent: Intent, message: String) {
        speak(message)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                showLaunchNotification(message, intent)
            } catch (ne: Exception) {
                speak("Не удалось запустить. Откройте приложение вручную.")
            }
        }
    }

    private fun showLaunchNotification(text: String, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "JARVIS запуск приложений" }
            nm.createNotificationChannel(channel)
        }

        val pi = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarvis)
            .setContentTitle("JARVIS")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(NOTIF_ID, notif)
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
