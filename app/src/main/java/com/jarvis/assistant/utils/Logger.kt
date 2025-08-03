package com.jarvis.assistant.utils

import android.util.Log

object Logger {
    private const val TAG = "JARVIS"
    private const val MAX_TAG_LENGTH = 23

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun e(message: String) {
        Log.e(TAG, message)
    }

    fun e(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
    }

    fun v(message: String) {
        Log.v(TAG, message)
    }

    fun d(tag: String, message: String) {
        Log.d(truncateTag(tag), message)
    }

    fun i(tag: String, message: String) {
        Log.i(truncateTag(tag), message)
    }

    fun w(tag: String, message: String) {
        Log.w(truncateTag(tag), message)
    }

    fun e(tag: String, message: String) {
        Log.e(truncateTag(tag), message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(truncateTag(tag), message, throwable)
    }

    fun v(tag: String, message: String) {
        Log.v(truncateTag(tag), message)
    }

    private fun truncateTag(tag: String): String {
        return if (tag.length > MAX_TAG_LENGTH) {
            tag.substring(0, MAX_TAG_LENGTH)
        } else {
            tag
        }
    }
} 