package com.jarvis.assistant.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.features.FlashlightManager
import com.jarvis.assistant.features.FocusModeManager
import com.jarvis.assistant.services.WeatherService
import com.jarvis.assistant.services.NewsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class CommandProcessor(
    private val context: Context,
    private val speak: (String) -> Unit
) {

    private val audioManager   = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val weatherService = WeatherService()
    private val newsService    = NewsService()
    private val flashlight     = FlashlightManager(context)
    private val scope          = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val NOTIF_CHANNEL_ID   = "jarvis_launch"
        private const val NOTIF_CHANNEL_NAME = "JARVIS Запуск"
        private const val NOTIF_ID           = 2001
    }

    // ── Поиск ──────────────────────────────────────────────────────────────

    fun searchGoogle(command: String) {
        val query = command
            .replace("найди в гугле", "").replace("найди в google", "")
            .replace("загугли", "").replace("поищи в гугле", "")
            .replace("найди", "").replace("гугл", "").replace("поищи", "")
            .trim().ifEmpty { command }

        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.packageManager.resolveActivity(intent, 0) != null) {
            launchActivity(intent, "Ищу в Google: $query")
        } else {
            openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
            speak("Ищу в Google: $query")
        }
    }

    fun searchYouTube(command: String) {
        val query = command
            .replace("найди на ютубе", "").replace("найди на youtube", "")
            .replace("поищи на ютубе", "").replace("ютуб", "")
            .replace("youtube", "").replace("поищи", "").replace("найди", "")
            .trim().ifEmpty { command }

        val ytIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.packageManager.resolveActivity(ytIntent, 0) != null) {
            launchActivity(ytIntent, "Ищу на YouTube: $query")
        } else {
            openUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            speak("Ищу на YouTube: $query")
        }
    }

    // ── Новости ─────────────────────────────────────────────────────────────

    fun getNews() {
        scope.launch {
            try {
                newsService.getLatestNews { list ->
                    if (list.isEmpty()) speak("Новости недоступны")
                    else speak("Последние новости: ${list.take(3).joinToString(". ") { it.title }}")
                }
            } catch (e: Exception) {
                speak("Не удалось загрузить новости")
            }
        }
    }

    // ── Калькулятор ────────────────────────────────────────────────────────

    fun calculate(command: String) {
        val expr = command
            .replace("посчитай", "").replace("сколько будет", "")
            .trim()
            .replace("плюс", "+").replace("минус", "-")
            .replace("умножить на", "*").replace("разделить на", "/")
            .replace(",", ".")
        try {
            speak("Результат: ${evalExpr(expr)}")
        } catch (e: Exception) {
            speak("Не могу посчитать это выражение")
        }
    }

    private fun evalExpr(expr: String): String {
        val tokens = expr.trim().split(Regex("(?<=[+\\-*/])|(?=[+\\-*/])"))
        if (tokens.size == 3) {
            val a = tokens[0].trim().toDouble()
            val op = tokens[1].trim()
            val b = tokens[2].trim().toDouble()
            val r = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b != 0.0) a / b else throw ArithmeticException()
                else -> throw IllegalArgumentException()
            }
            return if (r == r.toLong().toDouble()) r.toLong().toString()
            else String.format("%.2f", r)
        }
        throw IllegalArgumentException()
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

    fun setMaxVolume() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_PLAY_SOUND)
        speak("Громкость максимальная")
    }

    fun setMute() {
        audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
        speak("Звук выключен")
    }

    fun setUnmute() {
        audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_PLAY_SOUND)
        speak("Звук включён")
    }

    // ── Фонарик ────────────────────────────────────────────────────────────

    fun toggleFlashlight() {
        flashlight.toggleFlashlight { success ->
            if (success) {
                val state = if (flashlight.isOn()) "включён" else "выключен"
                speak("Фонарик $state")
            } else {
                speak("Не удалось управлять фонариком")
            }
        }
    }

    fun flashlightOn() {
        if (!flashlight.isOn()) toggleFlashlight()
        else speak("Фонарик уже включён")
    }

    fun flashlightOff() {
        if (flashlight.isOn()) toggleFlashlight()
        else speak("Фонарик уже выключен")
    }

    // ── Приложения ─────────────────────────────────────────────────────────

    fun openApp(command: String) {
        val appName = command
            .replace("открой", "").replace("запусти", "").replace("запустить", "")
            .trim()

        val pm = context.packageManager
        val resolved = resolvePackageName(appName)

        if (resolved != appName) {
            pm.getLaunchIntentForPackage(resolved)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchActivity(it, "Открываю $appName")
                return
            }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(launcherIntent, 0).firstOrNull {
            it.loadLabel(pm).toString().lowercase()
                .contains(appName.lowercase())
        }

        if (match != null) {
            pm.getLaunchIntentForPackage(match.activityInfo.packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchActivity(it, "Открываю ${match.loadLabel(pm)}")
                return
            }
        }

        speak("Приложение $appName не найдено")
    }

    fun closeApp(command: String) {
        speak("Закрыть приложение можно свайпом из списка задач")
    }

    private fun resolvePackageName(name: String): String = when (name.lowercase().trim()) {
        "ютуб", "youtube", "ютубе"           -> "com.google.android.youtube"
        "ватсап", "whatsapp", "вотсап"        -> "com.whatsapp"
        "телеграм", "telegram", "телеграмм"   -> "org.telegram.messenger"
        "хром", "chrome"                      -> "com.android.chrome"
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
        "часы", "clock"                       -> "com.google.android.deskclock"
        "заметки", "keep"                     -> "com.google.android.keep"
        "диск", "google drive", "drive"       -> "com.google.android.apps.docs"
        "переводчик", "translate"             -> "com.google.android.apps.translate"
        else                                  -> name
    }

    // ── Звонок ─────────────────────────────────────────────────────────────

    fun makeCall(command: String) {
        val name = command
            .replace("позвони", "").replace("позвоните", "")
            .replace("набери", "").replace("звони", "")
            .trim()

        if (name.isEmpty()) {
            speak("Кому позвонить?")
            return
        }

        // Ищем в контактах
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        if (cursor != null && cursor.moveToFirst()) {
            val number = cursor.getString(
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            )
            val displayName = cursor.getString(
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            )
            cursor.close()
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                speak("Звоню $displayName")
            } catch (e: Exception) {
                speak("Нет разрешения на звонок. Разрешите доступ к телефону.")
            }
        } else {
            cursor?.close()
            // Пробуем набрать напрямую если передан номер
            if (name.any { it.isDigit() }) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${name.filter { it.isDigit() || it == '+' }}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launchActivity(intent, "Открываю набор номера")
            } else {
                speak("Контакт $name не найден")
            }
        }
    }

    // ── SMS ────────────────────────────────────────────────────────────────

    fun sendSms(command: String) {
        // "отправь смс маме привет как дела"
        val parts = command
            .replace("отправь смс", "").replace("напиши смс", "")
            .replace("отправь сообщение", "").trim().split(" ", limit = 2)

        val contactName = parts.getOrNull(0)?.trim() ?: ""
        val message = parts.getOrNull(1)?.trim() ?: ""

        if (contactName.isEmpty()) {
            speak("Кому отправить сообщение?")
            return
        }

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$contactName%"),
            null
        )

        if (cursor != null && cursor.moveToFirst()) {
            val number = cursor.getString(
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            )
            cursor.close()
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchActivity(intent, "Открываю сообщение для $contactName")
        } else {
            cursor?.close()
            speak("Контакт $contactName не найден")
        }
    }

    // ── Будильник ──────────────────────────────────────────────────────────

    fun setAlarm(command: String) {
        // Парсим "поставь будильник на 7 утра" / "на 19:30"
        val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(утра|вечера|ночи|дня)?")
        val match = timeRegex.find(command)

        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: 7
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val period = match.groupValues[3]

            when (period) {
                "вечера", "ночи" -> if (hour < 12) hour += 12
                "утра", "дня"   -> if (hour == 12) hour = 0
            }

            try {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                speak("Будильник установлен на ${hour}:${minute.toString().padStart(2,'0')}")
            } catch (e: Exception) {
                speak("Не удалось установить будильник")
            }
        } else {
            speak("Скажите время. Например: поставь будильник на 7 утра")
        }
    }

    fun setTimer(command: String) {
        val minuteRegex = Regex("(\\d+)\\s*(минут|минуты|минуту|секунд|секунды|секунду|час|часа|часов)")
        val match = minuteRegex.find(command)

        if (match != null) {
            val value = match.groupValues[1].toIntOrNull() ?: 1
            val unit = match.groupValues[2]
            val seconds = when {
                unit.startsWith("минут") -> value * 60
                unit.startsWith("секунд") -> value
                unit.startsWith("час") -> value * 3600
                else -> value * 60
            }
            try {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS Timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                speak("Таймер на $value $unit установлен")
            } catch (e: Exception) {
                speak("Не удалось установить таймер")
            }
        } else {
            speak("Скажите время. Например: таймер на 5 минут")
        }
    }

    // ── WiFi ───────────────────────────────────────────────────────────────

    fun toggleWifi(command: String) {
        // Android 10+ не позволяет включать WiFi программно → открываем настройки
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        speak("Открываю настройки WiFi")
    }

    // ── Bluetooth ──────────────────────────────────────────────────────────

    fun toggleBluetooth(command: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        when {
            adapter == null -> speak("Bluetooth не поддерживается на этом устройстве")
            command.contains("включ") -> {
                if (!adapter.isEnabled) {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Запрашиваю включение Bluetooth")
                } else {
                    speak("Bluetooth уже включён")
                }
            }
            command.contains("выключ") -> {
                // Android 13+ не позволяет выключать напрямую
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                speak("Открываю настройки Bluetooth")
            }
            else -> {
                val status = if (adapter.isEnabled) "включён" else "выключен"
                speak("Bluetooth $status")
            }
        }
    }

    // ── Яркость ────────────────────────────────────────────────────────────

    fun setBrightness(command: String) {
        // Требует WRITE_SETTINGS — открываем настройки яркости
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            speak("Разрешите JARVIS изменять системные настройки, затем повторите команду")
            return
        }

        val level = when {
            command.contains("максимальн") || command.contains("ярче всего") -> 255
            command.contains("минимальн") || command.contains("темнее всего") -> 10
            command.contains("средн") || command.contains("половин") -> 128
            command.contains("ярче") -> {
                val cur = Settings.System.getInt(context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS, 128)
                (cur + 50).coerceIn(10, 255)
            }
            command.contains("темнее") || command.contains("тусклее") -> {
                val cur = Settings.System.getInt(context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS, 128)
                (cur - 50).coerceIn(10, 255)
            }
            else -> -1
        }

        if (level != -1) {
            try {
                Settings.System.putInt(context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS, level)
                speak("Яркость изменена")
            } catch (e: Exception) {
                speak("Не удалось изменить яркость")
            }
        } else {
            speak("Скажите: ярче, темнее, максимальная или минимальная яркость")
        }
    }

    // ── Режим не беспокоить ────────────────────────────────────────────────

    fun setDoNotDisturb(enable: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(
                    if (enable) NotificationManager.INTERRUPTION_FILTER_NONE
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
                speak(if (enable) "Режим не беспокоить включён" else "Режим не беспокоить выключен")
            } else {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                speak("Разрешите JARVIS управлять режимом не беспокоить")
            }
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

    // ── Перевод ────────────────────────────────────────────────────────────

    fun translate(command: String) {
        val text = command.replace("переведи", "").trim()
        openUrl("https://translate.google.com/?sl=auto&tl=en&text=${Uri.encode(text)}")
        speak("Открываю перевод")
    }

    // ── WhatsApp ───────────────────────────────────────────────────────────

    fun sendWhatsAppMessage(command: String = "") {
        // "напиши в ватсап маме привет"
        val parts = command
            .replace("напиши в ватсап", "").replace("отправь в ватсап", "")
            .replace("ватсап", "").trim().split(" ", limit = 2)

        val contactName = parts.getOrNull(0)?.trim() ?: ""
        val message = parts.getOrNull(1)?.trim() ?: ""

        if (contactName.isNotEmpty()) {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$contactName%"), null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val number = cursor.getString(0).filter { it.isDigit() || it == '+' }
                cursor.close()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launchActivity(intent, "Открываю WhatsApp")
                return
            }
            cursor?.close()
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchActivity(intent, "Открываю WhatsApp")
    }

    // ── Игра ───────────────────────────────────────────────────────────────

    fun playGame() {
        val options = listOf("камень", "ножницы", "бумага")
        speak("Я выбрал: ${options.random()}. Что выберете вы?")
    }

    // ── Камера ─────────────────────────────────────────────────────────────

    fun takePhoto() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchActivity(intent, "Открываю камеру")
    }

    fun openFrontCamera() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra("android.intent.extras.CAMERA_FACING",
                android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchActivity(intent, "Открываю фронтальную камеру")
    }

    // ── Скорость интернета ─────────────────────────────────────────────────

    fun checkInternetSpeed() {
        openUrl("https://www.speedtest.net")
        speak("Открываю тест скорости интернета")
    }

    // ── Навигация ──────────────────────────────────────────────────────────

    fun navigateTo(command: String) {
        val destination = command
            .replace("навигация до", "").replace("проложи маршрут до", "")
            .replace("как добраться до", "").replace("маршрут до", "")
            .trim()

        if (destination.isEmpty()) {
            speak("Куда проложить маршрут?")
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (context.packageManager.resolveActivity(intent, 0) != null) {
            launchActivity(intent, "Прокладываю маршрут до $destination")
        } else {
            openUrl("https://maps.google.com/?daddr=${Uri.encode(destination)}")
            speak("Открываю карты для $destination")
        }
    }

    fun showOnMap(command: String) {
        val place = command
            .replace("покажи на карте", "").replace("найди на карте", "")
            .replace("где находится", "").trim()

        openUrl("https://maps.google.com/?q=${Uri.encode(place)}")
        speak("Показываю $place на карте")
    }

    // ── Настройки ──────────────────────────────────────────────────────────

    fun openSettings(command: String) {
        val intent = when {
            command.contains("вайфай") || command.contains("wifi") || command.contains("wi-fi") ->
                Intent(Settings.ACTION_WIFI_SETTINGS)
            command.contains("bluetooth") || command.contains("блютуз") ->
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            command.contains("звук") || command.contains("звука") ->
                Intent(Settings.ACTION_SOUND_SETTINGS)
            command.contains("дисплей") || command.contains("экран") || command.contains("яркость") ->
                Intent(Settings.ACTION_DISPLAY_SETTINGS)
            command.contains("батарея") || command.contains("заряд") ->
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            command.contains("уведомления") ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            command.contains("разрешения") || command.contains("приложение") ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            command.contains("язык") ->
                Intent(Settings.ACTION_LOCALE_SETTINGS)
            command.contains("дату") || command.contains("время") ->
                Intent(Settings.ACTION_DATE_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchActivity(intent, "Открываю настройки")
    }

    // ── Заглушки ───────────────────────────────────────────────────────────

    fun getIPLScore() = speak("IPL статистика недоступна")
    fun createGitHubRepository(command: String) = speak("Создание репозитория GitHub пока не поддерживается")
    fun takeScreenshot() = speak("Нажмите кнопки питания и уменьшения громкости одновременно")

    // ── Вспомогательные ────────────────────────────────────────────────────

    private fun launchActivity(intent: Intent, message: String) {
        speak(message)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try { showLaunchNotification(message, intent) }
            catch (ne: Exception) { speak("Не удалось запустить. Откройте вручную.") }
        }
    }

    private fun showLaunchNotification(text: String, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL_ID, NOTIF_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "JARVIS запуск приложений"
                }
            )
        }
        val pi = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(NOTIF_ID, NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarvis)
            .setContentTitle("JARVIS").setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(pi).build())
    }

    private fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) { speak("Не удалось открыть ссылку") }
    }
}
