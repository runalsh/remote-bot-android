package com.remotebot.channels.telegram.engine

import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.TgCallbackQuery
import com.remotebot.domain.model.TgMessage
import com.remotebot.domain.repository.NotificationRepository
import com.remotebot.domain.repository.TelephonyRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for interactive replies to SMS and system notifications in Telegram:
 * 1. Inline "Reply" buttons (Callback query) that set text awaiting state.
 * 2. Free-form text reply from active awaiting state.
 * 3. Native swipe/reply (quoting) to forwarded SMS and notifications in Telegram.
 */
class TelegramInteractiveReplyHandler(
    private val apiClient: TelegramApiClient,
    private val telephonyRepository: TelephonyRepository?,
    private val notificationRepository: NotificationRepository?,
    private val configRepository: com.remotebot.domain.repository.BotConfigRepository? = null,
    private val logger: AppLogger?,
) {
    private data class PendingSmsReply(
        val targetPhone: String,
        val simSlot: Int?,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    private data class PendingNotificationReply(
        val notificationId: String,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    private val pendingSmsReplies = ConcurrentHashMap<Long, PendingSmsReply>()
    private val pendingNotifReplies = ConcurrentHashMap<Long, PendingNotificationReply>()

    fun clear() {
        pendingSmsReplies.clear()
        pendingNotifReplies.clear()
    }

    suspend fun handleCallback(
        query: TgCallbackQuery,
        data: String,
        chatId: Long,
    ): Boolean {
        if (data.startsWith("sms_reply:")) {
            val payload = data.removePrefix("sms_reply:")
            val callbackParts = payload.split(":")
            val targetPhone = callbackParts[0]
            val simSlot = callbackParts.getOrNull(1)?.toIntOrNull()
            val simText = if (simSlot != null) " (SIM $simSlot)" else ""

            pendingSmsReplies[chatId] = PendingSmsReply(targetPhone, simSlot)

            try {
                apiClient.answerCallbackQuery(query.id, text = "Введите текст SMS в поле ввода")
            } catch (_: Exception) {
            }

            val cancelKeyboard =
                InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton(text = "❌ Отмена", callbackData = "cancel_sms_reply"),
                        ),
                    ),
                )

            apiClient.sendMessage(
                chatId = chatId,
                text =
                    "✍️ <b>Введите текст сообщения для</b> <code>$targetPhone</code>$simText:\n" +
                        "<i>(Просто напишите текст в чат или нажмите «Отмена»)</i>",
                replyMarkup = cancelKeyboard,
            )
            return true
        }

        if (data == "cancel_sms_reply") {
            pendingSmsReplies.remove(chatId)
            try {
                apiClient.answerCallbackQuery(query.id, text = "Отменено")
            } catch (_: Exception) {
            }
            apiClient.sendMessage(chatId, "❌ Отправка SMS отменена.")
            return true
        }

        if (data.startsWith("notif_reply:")) {
            val notifId = data.removePrefix("notif_reply:")
            pendingNotifReplies[chatId] = PendingNotificationReply(notifId)

            try {
                apiClient.answerCallbackQuery(query.id, text = "Введите текст ответа")
            } catch (_: Exception) {
            }

            val cancelKeyboard =
                InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton(text = "❌ Отмена", callbackData = "cancel_notif_reply"),
                        ),
                    ),
                )

            apiClient.sendMessage(
                chatId = chatId,
                text =
                    "💬 <b>Ответ на уведомление #$notifId:</b>\n" +
                        "<i>(Напишите текст ответа в чат или нажмите «Отмена»)</i>",
                replyMarkup = cancelKeyboard,
            )
            return true
        }

        if (data.startsWith("notif_act:")) {
            // Format: notif_act:<notificationId>:<actionIndex>
            val parts = data.removePrefix("notif_act:").split(":")
            val notifId = parts.getOrNull(0) ?: ""
            val actionIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val is2FaEnabled = configRepository?.config?.value?.enable2FaInterception ?: false
            if (!is2FaEnabled) {
                try {
                    apiClient.answerCallbackQuery(
                        query.id,
                        text = "❌ Экспериментальный перехват 2FA выключен в настройках бота",
                        showAlert = true,
                    )
                } catch (_: Exception) {
                }
                return true
            }

            logger?.i("TelegramReplyHandler", "⚡ Нажата кнопка действия уведомления #$notifId (индекс $actionIndex)")

            val result = notificationRepository?.executeNotificationAction(notifId, actionIndex)
            val success = result?.isSuccess == true
            val errorMsg = result?.exceptionOrNull()?.message

            try {
                if (success) {
                    apiClient.answerCallbackQuery(query.id, text = "✅ Действие выполнено!")
                } else {
                    apiClient.answerCallbackQuery(query.id, text = "❌ Ошибка: ${errorMsg ?: "Не удалось выполнить"}", showAlert = true)
                }
            } catch (_: Exception) {
            }

            // Edit original message in Telegram: deactivate buttons and append status
            val msgId = query.message?.messageId
            val origText = query.message?.text ?: ""
            if (msgId != null && origText.isNotEmpty()) {
                val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val statusText = if (success) "\n\n<i>✅ Действие выполнено в $timeFormat</i>" else "\n\n<i>❌ Ошибка действия: ${errorMsg ?: "Таймаут"}</i>"
                val updatedMessage = origText + statusText
                try {
                    apiClient.editMessageText(chatId, msgId, updatedMessage, replyMarkup = null)
                } catch (e: Exception) {
                    logger?.w("TelegramReplyHandler", "Не удалось обновить сообщение уведомления: ${e.message}")
                }
            }

            return true
        }

        if (data.startsWith("sub_toggle:")) {
            val payload = data.removePrefix("sub_toggle:")
            val parts = payload.split(":")
            if (parts.size >= 2) {
                val targetUserId = parts[0].toLongOrNull() ?: return true
                val action = parts[1]
                val config = configRepository?.config?.value
                if (config != null && configRepository != null) {
                    val currentSub = config.userSubscriptions[targetUserId] ?: com.remotebot.domain.model.UserSubscription(userId = targetUserId)
                    val updatedSub =
                        when (action) {
                            "notif" -> currentSub.copy(receiveNotifications = !currentSub.receiveNotifications)
                            "sms" -> currentSub.copy(receiveSms = !currentSub.receiveSms)
                            "calls" -> currentSub.copy(receiveCalls = !currentSub.receiveCalls)
                            "system" -> currentSub.copy(receiveSystem = !currentSub.receiveSystem)
                            "all_off" ->
                                currentSub.copy(
                                    receiveNotifications = false,
                                    receiveSms = false,
                                    receiveCalls = false,
                                    receiveSystem = false,
                                )
                            "all_on" ->
                                currentSub.copy(
                                    receiveNotifications = true,
                                    receiveSms = true,
                                    receiveCalls = true,
                                    receiveSystem = false,
                                )
                            "refresh" -> currentSub
                            else -> currentSub
                        }
                    if (action != "refresh") {
                        configRepository.updateConfig { cfg ->
                            cfg.copy(userSubscriptions = cfg.userSubscriptions + (targetUserId to updatedSub))
                        }
                    }
                    try {
                        apiClient.answerCallbackQuery(query.id, text = "Настройки обновлены")
                    } catch (_: Exception) {
                    }

                    val (newText, markup) =
                        com.remotebot.channels.telegram.handlers.EventsMenuBuilder.build(
                            targetUserId,
                            updatedSub,
                            callerId = chatId,
                        )
                    val msgId = query.message?.messageId
                    if (msgId != null) {
                        try {
                            apiClient.editMessageText(chatId, msgId, newText, replyMarkup = markup)
                        } catch (e: Exception) {
                            logger?.w("TelegramReplyHandler", "Не удалось обновить меню подписок: ${e.message}")
                        }
                    }
                }
            }
            return true
        }

        return false
    }

    suspend fun handlePendingReplies(
        chatId: Long,
        text: String,
    ): Boolean {
        if (handlePendingSmsReply(chatId, text)) return true
        if (handlePendingNotifReply(chatId, text)) return true
        return false
    }

    private suspend fun handlePendingSmsReply(
        chatId: Long,
        text: String,
    ): Boolean {
        val pending = pendingSmsReplies[chatId] ?: return false
        if (System.currentTimeMillis() - pending.timestampMs >= 300_000L) {
            pendingSmsReplies.remove(chatId)
            return false
        }

        if (text.equals("/cancel", ignoreCase = true) || text.equals("отмена", ignoreCase = true)) {
            pendingSmsReplies.remove(chatId)
            apiClient.sendMessage(chatId, "❌ Отправка SMS отменена.")
            return true
        }

        if (!text.startsWith("/")) {
            pendingSmsReplies.remove(chatId)
            val simText = if (pending.simSlot != null) " (SIM ${pending.simSlot})" else ""
            val result = telephonyRepository?.sendSms(pending.targetPhone, text, pending.simSlot)
            if (result != null && result.isSuccess) {
                apiClient.sendMessage(
                    chatId,
                    "✅ <b>SMS успешно отправлено</b> на номер <code>${pending.targetPhone}</code>$simText:\n«$text»",
                )
            } else {
                val error = result?.exceptionOrNull()?.localizedMessage ?: "Не удалось отправить SMS"
                apiClient.sendMessage(
                    chatId,
                    "❌ <b>Ошибка отправки SMS</b> на номер <code>${pending.targetPhone}</code>:\n$error",
                )
            }
            return true
        }

        pendingSmsReplies.remove(chatId)
        return false
    }

    private suspend fun handlePendingNotifReply(
        chatId: Long,
        text: String,
    ): Boolean {
        val pending = pendingNotifReplies[chatId] ?: return false
        if (System.currentTimeMillis() - pending.timestampMs >= 300_000L) {
            pendingNotifReplies.remove(chatId)
            return false
        }

        if (text.equals("/cancel", ignoreCase = true) || text.equals("отмена", ignoreCase = true)) {
            pendingNotifReplies.remove(chatId)
            apiClient.sendMessage(chatId, "❌ Ответ на уведомление отменен.")
            return true
        }

        if (!text.startsWith("/")) {
            pendingNotifReplies.remove(chatId)
            logger?.i("TelegramEngine", "💬 Ответ на уведомление #${pending.notificationId} из активного ожидания: $text")
            val result = notificationRepository?.replyToNotification(pending.notificationId, text)
            if (result != null && result.isSuccess) {
                apiClient.sendMessage(
                    chatId,
                    "✅ <b>Ответ успешно отправлен в приложение:</b>\n«$text»",
                )
            } else {
                val error = result?.exceptionOrNull()?.localizedMessage ?: "Не удалось отправить ответ"
                apiClient.sendMessage(
                    chatId,
                    "❌ <b>Ошибка ответа на уведомление #${pending.notificationId}:</b>\n$error",
                )
            }
            return true
        }

        pendingNotifReplies.remove(chatId)
        return false
    }

    suspend fun handleReplyToMessage(
        message: TgMessage,
        chatId: Long,
        text: String,
    ): Boolean {
        val replyTo = message.replyToMessage ?: return false
        val replyText = replyTo.text ?: ""

        // Incoming SMS matching pattern (e.g. prompt or incoming SMS header with target phone)
        val smsRegex = """(?:Входящее SMS от|Введите текст ответа для)\s*(?:<code>)?([+0-9A-Za-z_-]+)(?:</code>)?""".toRegex()
        val smsMatch = smsRegex.find(replyText)
        if (smsMatch != null) {
            if (text.equals("/cancel", ignoreCase = true) || text.equals("отмена", ignoreCase = true)) {
                apiClient.sendMessage(chatId, "❌ Отправка SMS отменена.")
                return true
            }

            val targetPhone = smsMatch.groupValues[1]
            val simRegex = """\[SIM (\d+)\]|\(SIM (\d+)\)""".toRegex()
            val simMatch = simRegex.find(replyText)
            val simSlot = (simMatch?.groupValues?.get(1)?.ifEmpty { null } ?: simMatch?.groupValues?.get(2))?.toIntOrNull()

            val smsBody =
                if (text.startsWith("/sms", ignoreCase = true)) {
                    text.removePrefix("/sms").trim()
                } else {
                    text
                }

            if (smsBody.isNotBlank()) {
                logger?.i("TelegramEngine", "📤 Отправка SMS ответа на $targetPhone: $smsBody")
                val result = telephonyRepository?.sendSms(targetPhone, smsBody, simSlot)
                val simText = if (simSlot != null) " (SIM $simSlot)" else ""
                if (result != null && result.isSuccess) {
                    apiClient.sendMessage(
                        chatId,
                        "✅ <b>SMS успешно отправлено</b> на номер <code>$targetPhone</code>$simText:\n«$smsBody»",
                    )
                } else {
                    val error = result?.exceptionOrNull()?.localizedMessage ?: "Не удалось отправить SMS"
                    apiClient.sendMessage(
                        chatId,
                        "❌ <b>Ошибка отправки SMS</b> на номер <code>$targetPhone</code>:\n$error",
                    )
                }
                return true
            }
        }

        // Incoming system notification: match "/answer <id>"
        val answerRegex = """/answer\s+(\d+)""".toRegex()
        val notifMatch = answerRegex.find(replyText)
        if (notifMatch != null) {
            val notifId = notifMatch.groupValues[1]
            val replyBody =
                if (text.startsWith("/answer", ignoreCase = true) || text.startsWith("/resp", ignoreCase = true)) {
                    text.replaceFirst("""^/(answer|resp)\s*(\d+)?\s*""".toRegex(), "").trim()
                } else {
                    text
                }

            if (replyBody.isNotBlank()) {
                logger?.i("TelegramEngine", "💬 Быстрый ответ на уведомление [$notifId]: $replyBody")
                val result = notificationRepository?.replyToNotification(notifId, replyBody)
                if (result != null && result.isSuccess) {
                    apiClient.sendMessage(chatId, "✅ <b>Ответ отправлен в приложение:</b>\n«$replyBody»")
                } else {
                    val error = result?.exceptionOrNull()?.localizedMessage ?: "Не удалось ответить"
                    apiClient.sendMessage(chatId, "❌ <b>Ошибка ответа на уведомление:</b> $error")
                }
                return true
            }
        }

        return false
    }
}
