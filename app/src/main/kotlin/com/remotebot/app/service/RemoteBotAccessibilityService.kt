package com.remotebot.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteBotAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Обработка системных событий доступности (чтение экрана, USSD-диалоги, клики)
    }

    override fun onInterrupt() {
        _isRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
    }

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
