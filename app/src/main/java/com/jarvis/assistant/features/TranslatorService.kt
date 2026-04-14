package com.jarvis.assistant.features

class TranslatorService {
    fun translate(text: String, targetLang: String, callback: (String) -> Unit) {
        callback(text)
    }
}
