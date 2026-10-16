package com.remotebot.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.remotebot.core.common.notifications.NotificationListenerBridge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteBotNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        NotificationListenerBridge.listenerInstance = this
        com.remotebot.core.common.logger.AppLogger.i(
            "NotificationListenerService",
            "NotificationListenerService connected to Android system",
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
        NotificationListenerBridge.listenerInstance = null
        com.remotebot.core.common.logger.AppLogger
            .w("NotificationListenerService", "NotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        incrementEventCounter()
        com.remotebot.core.common.logger.AppLogger.d(
            "NotificationListenerService",
            "Intercepted notification: pkg=${sbn.packageName}, id=${sbn.id}, tag=${sbn.tag}, key=${sbn.key}",
        )
        _notificationsFlow.tryEmit(sbn)
        NotificationListenerBridge.postNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null) {
            com.remotebot.core.common.logger.AppLogger.d(
                "NotificationListenerService",
                "Notification removed: pkg=${sbn.packageName}, key=${sbn.key}",
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isConnected.value = false
        NotificationListenerBridge.listenerInstance = null
        com.remotebot.core.common.logger.AppLogger
            .i("NotificationListenerService", "NotificationListenerService destroyed")
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _notificationsFlow = MutableSharedFlow<StatusBarNotification>(extraBufferCapacity = 64)
        val notificationsFlow: SharedFlow<StatusBarNotification> = _notificationsFlow.asSharedFlow()

        private val _eventsIntercepted = MutableStateFlow(0L)
        val eventsIntercepted: StateFlow<Long> = _eventsIntercepted.asStateFlow()

        fun incrementEventCounter() {
            _eventsIntercepted.value = _eventsIntercepted.value + 1
        }

        fun resetEventCounter() {
            _eventsIntercepted.value = 0L
        }
    }
}
