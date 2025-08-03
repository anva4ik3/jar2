package com.jarvis.assistant.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    private val _isListening = MutableLiveData<Boolean>()
    val isListening: LiveData<Boolean> = _isListening

    private val _currentCommand = MutableLiveData<String>()
    val currentCommand: LiveData<String> = _currentCommand

    private val _jarvisStatus = MutableLiveData<String>()
    val jarvisStatus: LiveData<String> = _jarvisStatus

    private val _voiceLevel = MutableLiveData<Int>()
    val voiceLevel: LiveData<Int> = _voiceLevel

    private val _isJARVISActive = MutableLiveData<Boolean>()
    val isJARVISActive: LiveData<Boolean> = _isJARVISActive

    private val _commandHistory = MutableLiveData<List<String>>()
    val commandHistory: LiveData<List<String>> = _commandHistory

    private val _focusSessionData = MutableLiveData<FocusSessionData>()
    val focusSessionData: LiveData<FocusSessionData> = _focusSessionData

    init {
        _isListening.value = false
        _currentCommand.value = ""
        _jarvisStatus.value = "JARVIS is inactive"
        _voiceLevel.value = 0
        _isJARVISActive.value = false
        _commandHistory.value = emptyList()
        _focusSessionData.value = FocusSessionData()
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun setCurrentCommand(command: String) {
        _currentCommand.value = command
        addToCommandHistory(command)
    }

    fun setJARVISStatus(status: String) {
        _jarvisStatus.value = status
    }

    fun setVoiceLevel(level: Int) {
        _voiceLevel.value = level
    }

    fun setJARVISActive(active: Boolean) {
        _isJARVISActive.value = active
    }

    private fun addToCommandHistory(command: String) {
        val currentHistory = _commandHistory.value?.toMutableList() ?: mutableListOf()
        currentHistory.add(command)
        if (currentHistory.size > 50) { // Keep only last 50 commands
            currentHistory.removeAt(0)
        }
        _commandHistory.value = currentHistory
    }

    fun updateFocusSessionData(data: FocusSessionData) {
        _focusSessionData.value = data
    }

    fun clearCommandHistory() {
        _commandHistory.value = emptyList()
    }

    fun getCommandHistory(): List<String> {
        return _commandHistory.value ?: emptyList()
    }

    fun isJARVISListening(): Boolean {
        return _isListening.value ?: false
    }

    fun isJARVISActive(): Boolean {
        return _isJARVISActive.value ?: false
    }
}

data class FocusSessionData(
    val totalFocusTime: Long = 0,
    val sessionCount: Int = 0,
    val averageSessionLength: Long = 0,
    val lastSessionDate: String = "",
    val currentSessionStart: Long = 0,
    val isInFocusMode: Boolean = false
) 