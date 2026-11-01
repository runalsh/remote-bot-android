package com.remotebot.core.push

import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.TgUpdate
import com.remotebot.domain.push.PushWakeupHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

class HuaweiHmsPushService : HmsMessageService() {
    private val wakeupHandler: PushWakeupHandler by inject()
    private val logger: AppLogger by inject()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onNewToken(token: String?) {
        super.onNewToken(token)
        if (!token.isNullOrBlank()) {
            logger.i("HMS", "New HMS push token received: ${token.take(8)}...${token.takeLast(4)}")
            logger.t("HMS", "Full refreshed HMS token (len=${token.length}): $token")
        } else {
            logger.w("HMS", "Empty token received in onNewToken() callback")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        super.onMessageReceived(remoteMessage)
        if (remoteMessage == null) {
            logger.w("HMS", "Empty remoteMessage received in onMessageReceived()")
            return
        }

        val data = remoteMessage.dataOfMap
        val action = data["action"]
        val updateIdStr = data["update_id"]
        val updateJson = data["update_json"]

        logger.i("HMS", "📥 Incoming Huawei HMS Data Push: action=$action, update_id=$updateIdStr, keys: ${data.keys.joinToString(", ")}")
        logger.d("HMS", "Payload data: $data")
        logger.t(
            "HMS",
            "HMS Push details: collapseKey=${remoteMessage.collapseKey}, messageId=${remoteMessage.messageId}, sendTime=${remoteMessage.sentTime}, ttl=${remoteMessage.ttl}",
        )
        PushStatsTracker.recordDelivery()
        com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordWakeup(
            com.remotebot.domain.model.WakeupSource.PUSH_HMS,
        )

        if (action == "wakeup" || action == "update") {
            if (!updateJson.isNullOrBlank()) {
                logger.d("HMS", "Processing update_json via PushWakeupHandler (length=${updateJson.length})...")
                logger.t("HMS", "Raw update_json payload: $updateJson")
                scope.launch {
                    try {
                        val update = json.decodeFromString<TgUpdate>(updateJson)
                        logger.i("HMS", "TgUpdate decoded successfully: update_id=${update.updateId}, handing off to PushWakeupHandler")
                        wakeupHandler.processPushUpdate(update)
                    } catch (e: Exception) {
                        logger.e("HMS", "Failed to parse TgUpdate from HMS push: ${e.localizedMessage}", e)
                    }
                }
            } else if (updateIdStr != null) {
                val updateId = updateIdStr.toLongOrNull() ?: 0L
                if (updateId > 0) {
                    logger.i("HMS", "Wakeup signal: update_id=$updateId, waking service...")
                    scope.launch {
                        wakeupHandler.handlePushWakeup(updateId)
                    }
                } else {
                    logger.w("HMS", "Invalid update_id in wakeup packet: $updateIdStr")
                }
            } else {
                logger.w("HMS", "HMS push received with action=$action but without update_json and update_id")
            }
        } else {
            logger.w("HMS", "Unknown HMS push action: '$action', skipping")
        }
    }
}
