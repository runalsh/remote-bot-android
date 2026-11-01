package com.remotebot.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.TgUpdate
import com.remotebot.domain.push.PushWakeupHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

class FirebasePushService : FirebaseMessagingService() {
    private val wakeupHandler: PushWakeupHandler by inject()
    private val logger: AppLogger by inject()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    @Deprecated("Deprecated in FirebaseMessagingService")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        logger.i("FCM", "New FCM push token received: ${token.take(8)}...${token.takeLast(4)}")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val action = data["action"]
        val updateIdStr = data["update_id"]
        val updateJson = data["update_json"]

        logger.i("FCM", "📥 Incoming FCM Data Push: action=$action, update_id=$updateIdStr, keys: ${data.keys.joinToString(", ")}")
        logger.d("FCM", "Payload data: $data")
        PushStatsTracker.recordDelivery()
        com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordWakeup(
            com.remotebot.domain.model.WakeupSource.PUSH_FCM,
        )

        if (action == "wakeup" || action == "update") {
            if (!updateJson.isNullOrBlank()) {
                scope.launch {
                    try {
                        val update = json.decodeFromString<TgUpdate>(updateJson)
                        wakeupHandler.processPushUpdate(update)
                    } catch (e: Exception) {
                        logger.e("FCM", "Failed to parse TgUpdate from push: ${e.localizedMessage}", e)
                    }
                }
            } else if (updateIdStr != null) {
                val updateId = updateIdStr.toLongOrNull() ?: 0L
                if (updateId > 0) {
                    scope.launch {
                        wakeupHandler.handlePushWakeup(updateId)
                    }
                }
            }
        }
    }
}
