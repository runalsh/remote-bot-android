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
            logger.i("HMS", "Получен новый HMS push token: ${token.take(8)}...${token.takeLast(4)}")
            logger.t("HMS", "Полный обновленный HMS токен (len=${token.length}): $token")
        } else {
            logger.w("HMS", "Получен пустой токен в обратном вызове onNewToken()")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        super.onMessageReceived(remoteMessage)
        if (remoteMessage == null) {
            logger.w("HMS", "Получено пустое сообщение remoteMessage в onMessageReceived()")
            return
        }

        val data = remoteMessage.dataOfMap
        val action = data["action"]
        val updateIdStr = data["update_id"]
        val updateJson = data["update_json"]

        logger.i("HMS", "📥 Входящий Huawei HMS Data Push: action=$action, update_id=$updateIdStr, ключи: ${data.keys.joinToString(", ")}")
        logger.d("HMS", "Payload data: $data")
        logger.t(
            "HMS",
            "Детали HMS Push: collapseKey=${remoteMessage.collapseKey}, messageId=${remoteMessage.messageId}, sendTime=${remoteMessage.sentTime}, ttl=${remoteMessage.ttl}",
        )
        PushStatsTracker.recordDelivery()
        com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordWakeup(
            com.remotebot.domain.model.WakeupSource.PUSH_HMS,
        )

        if (action == "wakeup" || action == "update") {
            if (!updateJson.isNullOrBlank()) {
                logger.d("HMS", "Обработка update_json через PushWakeupHandler (длина=${updateJson.length})...")
                logger.t("HMS", "Сырой payload update_json: $updateJson")
                scope.launch {
                    try {
                        val update = json.decodeFromString<TgUpdate>(updateJson)
                        logger.i("HMS", "TgUpdate успешно декодирован: update_id=${update.updateId}, передача в PushWakeupHandler")
                        wakeupHandler.processPushUpdate(update)
                    } catch (e: Exception) {
                        logger.e("HMS", "Сбой парсинга TgUpdate из HMS push: ${e.localizedMessage}", e)
                    }
                }
            } else if (updateIdStr != null) {
                val updateId = updateIdStr.toLongOrNull() ?: 0L
                if (updateId > 0) {
                    logger.i("HMS", "Wakeup сигнал: update_id=$updateId, пробуждение службы...")
                    scope.launch {
                        wakeupHandler.handlePushWakeup(updateId)
                    }
                } else {
                    logger.w("HMS", "Некорректный update_id в пакете wakeup: $updateIdStr")
                }
            } else {
                logger.w("HMS", "Получен HMS push c action=$action, но без update_json и update_id")
            }
        } else {
            logger.w("HMS", "Неизвестное действие HMS push action: '$action', пропуск")
        }
    }
}
