package com.remotebot.channels.telegram.engine

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.telephony.IncomingSmsEvent
import com.remotebot.core.common.telephony.PhoneCallEvent
import com.remotebot.core.common.telephony.PhoneCallReceiver
import com.remotebot.core.common.telephony.SmsReceiver
import com.remotebot.core.common.telephony.TransliterationUtil
import com.remotebot.domain.dispatcher.CommandDispatcher
import com.remotebot.domain.model.CallType
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.SubscriptionEventType
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.TelephonyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Telephony event forwarder (SMS, incoming, and missed calls).
 * Responsible for:
 * 1. Command reception via SMS (rb prefix + optional PIN).
 * 2. Forwarding SMS to Telegram with inline direct reply button.
 * 3. Relaying SMS and calls to backup cellular numbers (SMS -> SMS, Calls -> SMS).
 */
class TelephonyEventForwarder(
    private val dispatcher: CommandDispatcher,
    private val configRepository: BotConfigRepository,
    private val telephonyRepository: TelephonyRepository?,
    private val broadcastMessage: suspend (String, InlineKeyboardMarkup?, SubscriptionEventType) -> Unit,
    private val isRunning: () -> Boolean,
    private val logger: AppLogger?,
) {
    // Sliding window forwarding timestamps per sender: sender -> list of timestamps
    private val recentForwardTimestampsBySender = ConcurrentHashMap<String, MutableList<Long>>()

    private fun isRateLimited(
        sender: String,
        limitPerMinute: Int,
    ): Boolean {
        if (limitPerMinute <= 0) return false
        val now = System.currentTimeMillis()
        val timestamps = recentForwardTimestampsBySender.getOrPut(sender) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { now - it > 60_000L }
            if (timestamps.size >= limitPerMinute) {
                return true
            }
            timestamps.add(now)
            return false
        }
    }

    fun start(scope: CoroutineScope) {
        // 1. Incoming SMS
        scope.launch {
            SmsReceiver.smsEvents.collect { event ->
                if (!isRunning()) return@collect
                handleIncomingSms(event)
            }
        }

        // 2. Call events
        scope.launch {
            PhoneCallReceiver.callEvents.collect { event ->
                if (!isRunning()) return@collect
                handlePhoneCallEvent(event)
            }
        }
    }

    private suspend fun handleIncomingSms(event: IncomingSmsEvent) {
        val config = configRepository.config.value
        val pin = config.smsPin?.trim()
        val text = event.body.trim()
        val isTrustedSender = configRepository.isPhoneAllowed(event.sender)

        var isBotCommand = false
        var commandStr = ""

        // SMS is treated as command ONLY if starting with "rb" prefix (case-insensitive: rb, RB, Rb, rB)
        val prefixRegex = "^(?i)rb(\\s+|/|:|$)(.*)".toRegex()
        val match = prefixRegex.find(text)
        if (match != null) {
            val delimiter = match.groupValues[1]
            val remainder = match.groupValues[2].trim()
            val content = if (delimiter == "/") "/$remainder" else remainder

            // Mode 1: Valid PIN + command from ANY number (trusted or new)
            if (!pin.isNullOrBlank() && content.startsWith(pin)) {
                val afterPin = content.removePrefix(pin).trim()
                if (afterPin.isNotEmpty()) {
                    isBotCommand = true
                    commandStr = if (afterPin.startsWith("/")) afterPin else "/$afterPin"
                } else {
                    isBotCommand = true
                    commandStr = "/status"
                }
            }
            // Mode 2: Trusted number (whitelisted) — PIN NOT required
            else if (isTrustedSender) {
                if (content.isNotEmpty()) {
                    isBotCommand = true
                    commandStr = if (content.startsWith("/")) content else "/$content"
                } else {
                    isBotCommand = true
                    commandStr = "/status"
                }
            }
            // Mode 3: Unauthorized number or invalid PIN for command with rb prefix
            else {
                com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordAuthFailure(
                    com.remotebot.domain.model.AuthChannel.SMS,
                    event.sender,
                )
                if (config.replyUnauthorizedResponseEnabled) {
                    val errorMsg =
                        if (!pin.isNullOrBlank()) {
                            "Ошибка: неверный SMS PIN или неавторизованный номер"
                        } else {
                            "Ошибка: неавторизованный номер отправителя"
                        }
                    val finalReply =
                        if (config.smsTransliterationEnabled) {
                            TransliterationUtil.transliterate(errorMsg)
                        } else {
                            errorMsg
                        }
                    telephonyRepository?.sendSms(event.sender, finalReply, event.simSlot)
                    logger?.w("TelegramEngine", "🔒 Неавторизованная попытка SMS-команды от ${event.sender}, отправлен ответ об ошибке")
                } else {
                    logger?.w("TelegramEngine", "🔒 Неавторизованная попытка SMS-команды от ${event.sender}, ответ подавлен (тихий режим)")
                }
            }
        }

        if (isBotCommand && commandStr.startsWith("/")) {
            val parts = commandStr.split("\\s+".toRegex())
            val cmdName = parts[0]
            val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()

            val incoming =
                IncomingCommand(
                    id = "sms_${System.currentTimeMillis()}",
                    name = cmdName,
                    rawText = commandStr,
                    args = args,
                    source = CommandSource.SMS,
                    senderId = event.sender,
                )

            val result = dispatcher.dispatch(incoming)
            val replyText =
                when (result) {
                    is CommandResult.Text -> result.message.replace(Regex("<[^>]*>"), "")
                    is CommandResult.Error -> "Ошибка: ${result.errorMsg}"
                    is CommandResult.Acknowledged -> "OK"
                    else -> null
                }
            if (replyText != null) {
                val finalReply =
                    if (config.smsTransliterationEnabled) {
                        TransliterationUtil.transliterate(replyText)
                    } else {
                        replyText
                    }
                telephonyRepository?.sendSms(event.sender, finalReply, event.simSlot)
            }
        } else {
            // Forward standard incoming SMS (or unauthorized) to Telegram chat
            // Check rate limit (anti-spam / per-minute flood protection)
            if (isRateLimited(event.sender, config.forwardRateLimitPerMinute)) {
                logger?.w(
                    "TelegramEngine",
                    "⚠️ Rate limit: входящее SMS от ${event.sender} превысило лимит (${config.forwardRateLimitPerMinute}/мин), пропуск пересылки",
                )
                return
            }

            val simInfo = if (event.simSlot != null) " [SIM ${event.simSlot}]" else ""
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestampMs))
            val senderClean = escapeHtml(event.sender)
            val bodyClean = escapeHtml(event.body.trim())
            val msg =
                buildString {
                    append(
                        "📨 <b>Входящее SMS от <code>",
                    ).append(senderClean).append("</code></b>").append(simInfo).append(" (<i>").append(timeStr).append("</i>):\n")
                    append("«").append(bodyClean).append("»\n\n")
                    append("<i>💬 Нажмите «Ответить» ниже или сделайте Reply на сообщение</i>")
                }

            val keyboard =
                InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton(
                                text = "💬 Ответить по SMS",
                                callbackData = "sms_reply:${event.sender}${if (event.simSlot != null) ":${event.simSlot}" else ""}",
                            ),
                        ),
                    ),
                )
            if (config.telegramBotEnabled && config.tgForwardSmsEnabled) {
                broadcastMessage(msg, keyboard, SubscriptionEventType.SMS)
            }

            // Forward incoming SMS to cellular numbers (SMS -> SMS)
            val isRbPrefix = match != null || text.startsWith("rb", ignoreCase = true)
            if (!isRbPrefix && config.smsBotEnabled && config.smsForwardSmsEnabled && config.smsForwardTargetNumbers.isNotEmpty()) {
                val rawForwardText = "SMS от ${event.sender}: ${event.body}".trim()
                val finalForwardText =
                    if (config.smsTransliterationEnabled) {
                        TransliterationUtil.transliterate(rawForwardText)
                    } else {
                        rawForwardText
                    }
                for (targetNumber in config.smsForwardTargetNumbers) {
                    // Anti-loop: do not echo SMS back to sender's own number
                    val cleanTarget = targetNumber.filter { it.isDigit() }
                    val cleanSender = event.sender.filter { it.isDigit() }
                    if (cleanTarget.isNotEmpty() && cleanSender.isNotEmpty() && cleanTarget == cleanSender) {
                        continue
                    }
                    try {
                        telephonyRepository?.sendSms(targetNumber, finalForwardText, event.simSlot)
                        logger?.i("TelegramEngine", "📤 Входящее SMS от ${event.sender} переслано по SMS на $targetNumber")
                    } catch (e: Exception) {
                        logger?.w("TelegramEngine", "Ошибка пересылки SMS на $targetNumber: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun handlePhoneCallEvent(event: PhoneCallEvent) {
        val config = configRepository.config.value
        val caller = event.number ?: "Неизвестный номер"
        val icon =
            when (event.type) {
                CallType.INCOMING -> "📞 <b>Входящий звонок</b>"
                CallType.MISSED -> "🔴 <b>Пропущенный звонок</b>"
                else -> "📞 <b>Звонок (${event.type.name})</b>"
            }
        val msg = "$icon от <code>$caller</code>"
        if (config.telegramBotEnabled && config.tgForwardCallsEnabled) {
            broadcastMessage(msg, null, SubscriptionEventType.CALLS)
        }

        // Relay missed calls via cellular SMS (Missed Call -> SMS)
        if (event.type == CallType.MISSED &&
            config.smsBotEnabled &&
            config.smsForwardMissedCallsEnabled &&
            config.smsForwardTargetNumbers.isNotEmpty()
        ) {
            val rawCallText = "Пропущенный звонок от: $caller".trim()
            val finalCallText =
                if (config.smsTransliterationEnabled) {
                    TransliterationUtil.transliterate(rawCallText)
                } else {
                    rawCallText
                }
            for (targetNumber in config.smsForwardTargetNumbers) {
                val cleanTarget = targetNumber.filter { it.isDigit() }
                val cleanCaller = (event.number ?: "").filter { it.isDigit() }
                if (cleanTarget.isNotEmpty() && cleanCaller.isNotEmpty() && cleanTarget == cleanCaller) {
                    continue
                }
                try {
                    telephonyRepository?.sendSms(targetNumber, finalCallText)
                    logger?.i("TelegramEngine", "📤 Пропущенный вызов от ${event.number} переслан по SMS на $targetNumber")
                } catch (e: Exception) {
                    logger?.w("TelegramEngine", "Ошибка пересылки инфо о пропущенном звонке по SMS на $targetNumber: ${e.message}")
                }
            }
        }
    }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
