package com.remotebot.channels.telegram.engine

import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.channels.telegram.handlers.LocationCommandsHandler
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.telephony.TransliterationUtil
import com.remotebot.domain.dispatcher.CommandDispatcher
import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.SubscriptionEventType
import com.remotebot.domain.repository.AutomationRepository
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.CameraRepository
import com.remotebot.domain.repository.LocationRepository
import com.remotebot.domain.repository.NotificationRepository
import com.remotebot.domain.repository.TelephonyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TelegramEventForwarder(
    private val apiClient: TelegramApiClient,
    private val dispatcher: CommandDispatcher,
    private val configRepository: BotConfigRepository,
    private val resultSender: TelegramResultSender,
    private val notificationRepository: NotificationRepository? = null,
    private val telephonyRepository: TelephonyRepository? = null,
    private val locationRepository: LocationRepository? = null,
    private val automationRepository: AutomationRepository? = null,
    private val cameraRepository: CameraRepository? = null,
    private val audioRepository: com.remotebot.domain.repository.AudioRepository? = null,
    private val getActiveChatIds: () -> Set<Long>,
    private val isRunning: () -> Boolean,
    private val logger: AppLogger? = null,
) {
    private val recentForwardedNotifications = ConcurrentHashMap<String, Long>()
    private val recentSentBotMessages = ConcurrentHashMap<String, Long>()

    init {
        activeInstance = this
    }

    private val telephonyForwarder =
        TelephonyEventForwarder(
            dispatcher = dispatcher,
            configRepository = configRepository,
            telephonyRepository = telephonyRepository,
            broadcastMessage = { text, markup, type -> broadcastMessage(text, markup, eventType = type) },
            isRunning = isRunning,
            logger = logger,
        )

    fun startObservers(scope: CoroutineScope): Job {
        return scope.launch(Dispatchers.IO) {
            // 1. Уведомления от приложений
            launch {
                notificationRepository?.notificationEvents?.collect { notif ->
                    if (!isRunning()) return@collect
                    val config = configRepository.config.value

                    // Фильтр 1: Только выбранные приложения (whitelist)
                    if (!config.notificationForwardingApps.contains(notif.packageName)) {
                        return@collect
                    }

                    // Фильтр 2: Стоп-слова / ключ для текста (черный список фраз)
                    val title = notif.title ?: ""
                    val text = notif.text ?: ""
                    val combinedText = "$title $text"
                    val matchesStopWord =
                        config.notificationStopWords.any { keyword ->
                            keyword.isNotBlank() && combinedText.contains(keyword.trim(), ignoreCase = true)
                        }
                    if (matchesStopWord) {
                        logger?.d("TelegramEngine", "Уведомление содержит стоп-слово, пропуск (${notif.appName})")
                        return@collect
                    }

                    // Фильтр 2.1: Защита от эхо — не пересылать то, что сам бот только что отправил
                    val isOwnEcho =
                        recentSentBotMessages.keys().asSequence().any { sentText ->
                            sentText.isNotBlank() && (combinedText.contains(sentText) || sentText.contains(combinedText))
                        }
                    if (isOwnEcho) {
                        logger?.d("TelegramEngine", "Эхо-защита: пропуск собственного отправленного сообщения в шторке")
                        return@collect
                    }

                    // Фильтр 3: Защита от дубликатов (анти-спам таймаут из настроек)
                    val dedupTimeoutMs = config.notificationDedupTimeoutSeconds * 1000L
                    if (dedupTimeoutMs > 0) {
                        val dedupKey = "${notif.packageName}|$title|$text"
                        val now = System.currentTimeMillis()
                        val lastSeen = recentForwardedNotifications[dedupKey]
                        if (lastSeen != null && (now - lastSeen) < dedupTimeoutMs) {
                            logger?.d(
                                "TelegramEngine",
                                "Дедупликация: пропуск повторного уведомления от ${notif.appName} (таймаут ${config.notificationDedupTimeoutSeconds}с)",
                            )
                            return@collect
                        }
                        recentForwardedNotifications[dedupKey] = now
                    }

                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(notif.timestampMs))
                    val appNameClean = escapeHtml(notif.appName.trim())
                    val titleClean =
                        notif.title
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { escapeHtml(it) }
                    val textClean =
                        notif.text
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { escapeHtml(it) }

                    val msg =
                        buildString {
                            append("🔔 <b>")
                                .append(appNameClean)
                                .append("</b> (<i>")
                                .append(timeStr)
                                .append("</i>)")
                            if (!titleClean.isNullOrBlank()) {
                                append("\n<b>").append(titleClean).append("</b>")
                            }
                            if (!textClean.isNullOrBlank()) {
                                append("\n").append(textClean)
                            }
                        }

                    val keyboardRows = mutableListOf<List<InlineKeyboardButton>>()

                    // 1. Кнопки действий уведомления (например 2FA: Подтвердить / Отклонить, Accept / Reject)
                    // Доступно только при включенной экспериментальной функции перехвата 2FA
                    if (config.enable2FaInterception && notif.actions.isNotEmpty()) {
                        val actionButtons =
                            notif.actions.map { act ->
                                val label =
                                    when (act.title.lowercase()) {
                                        "подтвердить", "approve", "accept", "yes", "да", "разрешить" -> "✅ ${act.title}"
                                        "отклонить", "reject", "deny", "no", "нет", "запретить" -> "❌ ${act.title}"
                                        else -> act.title
                                    }
                                InlineKeyboardButton(label, callbackData = "notif_act:${notif.id}:${act.index}")
                            }
                        // Размещаем до 3 кнопок в ряд
                        actionButtons.chunked(3).forEach { row ->
                            keyboardRows.add(row)
                        }
                    }

                    // 2. Кнопка быстрого ответа текстом для чатов/мессенджеров
                    if (notif.hasReplyAction) {
                        keyboardRows.add(
                            listOf(
                                InlineKeyboardButton("💬 Ответить", callbackData = "notif_reply:${notif.id}"),
                            ),
                        )
                    }

                    val keyboard =
                        if (keyboardRows.isNotEmpty()) {
                            InlineKeyboardMarkup(keyboardRows)
                        } else {
                            null
                        }

                    if (config.telegramBotEnabled && config.tgForwardNotificationsEnabled) {
                        broadcastMessage(msg, replyMarkup = keyboard, eventType = SubscriptionEventType.NOTIFICATIONS)
                    }

                    // Пересылка push-уведомления по сотовому SMS (Push ➔ SMS)
                    if (config.smsBotEnabled && config.smsForwardNotificationsEnabled && config.smsForwardTargetNumbers.isNotEmpty()) {
                        val rawSmsText = "[${notif.appName}] ${notif.title ?: ""}: ${notif.text ?: ""}".trim()
                        val finalSmsText =
                            if (config.smsTransliterationEnabled) {
                                TransliterationUtil.transliterate(rawSmsText)
                            } else {
                                rawSmsText
                            }
                        for (targetNumber in config.smsForwardTargetNumbers) {
                            try {
                                telephonyRepository?.sendSms(targetNumber, finalSmsText)
                                logger?.i("TelegramEngine", "📤 Уведомление [${notif.appName}] продублировано по SMS на $targetNumber")
                            } catch (e: Exception) {
                                logger?.w("TelegramEngine", "Ошибка дублирования уведомления по SMS на $targetNumber: ${e.message}")
                            }
                        }
                    }
                }
            }

            // 2. Телефонные события (SMS и звонки)
            telephonyForwarder.start(this)

            // 4. Датчик тряски (Shake detector)
            launch {
                var lastShakeAlertTime = 0L
                locationRepository?.shakeEvents?.collect {
                    if (!isRunning()) return@collect
                    val now = System.currentTimeMillis()
                    if (now - lastShakeAlertTime < 10000L) {
                        return@collect
                    }
                    lastShakeAlertTime = now
                    val alert = "🚨 <b>ВНИМАНИЕ: Сработал датчик тряски!</b>\nУстройство подверглось резкому ускорению/встряхиванию."
                    logger?.i("TelegramEngine", "📳 Получено событие тряски, рассылка в чаты...")
                    broadcastMessage(alert, eventType = SubscriptionEventType.SYSTEM)
                }
            }

            // 4.1. Периодическое отслеживание местоположения (Live location)
            launch {
                locationRepository?.liveLocationUpdates?.collect { loc ->
                    if (!isRunning()) return@collect
                    val report = LocationCommandsHandler.formatLocationReport(loc, isLive = true)
                    val keyboard = LocationCommandsHandler.buildLocationKeyboard(isTrackingActive = true)
                    logger?.i("TelegramEngine", "🛰️ Отправка точки периодического трекинга #${loc.trackingSequenceNumber}...")
                    broadcastMessage(report, replyMarkup = keyboard, eventType = SubscriptionEventType.SYSTEM)
                }
            }

            // 4.2. Детектор звука (Sound detector)
            launch {
                var lastSoundAlertTime = 0L
                audioRepository?.soundEvents?.collect { event ->
                    if (!isRunning()) return@collect
                    val now = System.currentTimeMillis()
                    if (now - lastSoundAlertTime < 10000L) {
                        return@collect
                    }
                    lastSoundAlertTime = now
                    val cfg = audioRepository.soundDetectorConfig.value
                    val thresholdDb = 90 - (cfg.sensitivity.coerceIn(1, 10) * 5)
                    val alert = "🚨 <b>ВНИМАНИЕ: Сработал детектор звука!</b>\nЗафиксирован шум громкостью <b>${event.decibels} dB</b> (порог: <b>$thresholdDb dB</b>, уровень ${cfg.sensitivity}/10)."
                    logger?.i("TelegramEngine", "🔊 Получено событие детектора звука (${event.decibels} dB), рассылка в чаты...")
                    broadcastMessage(alert, eventType = SubscriptionEventType.SYSTEM)
                }
            }

            // 5. Детектор движения (Камера)
            launch {
                cameraRepository?.motionEvents?.collect { event ->
                    if (!isRunning()) return@collect
                    val alert = "🚨 <b>ВНИМАНИЕ: Детектор движения зафиксировал активность!</b>"
                    val config = configRepository.config.value
                    val photo = event.photoFile
                    val targets = getTargetUserIds(SubscriptionEventType.SYSTEM)
                    for (userId in targets) {
                        try {
                            if (photo != null && photo.exists()) {
                                apiClient.sendPhoto(userId, photo, alert)
                            } else {
                                apiClient.sendMessage(userId, alert)
                            }
                        } catch (e: Exception) {
                            logger?.w("TelegramEngine", "Ошибка отправки тревоги движения в $userId: ${e.message}")
                        }
                    }
                    if (photo != null && photo.exists()) {
                        try {
                            photo.delete()
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            // 6. Запланированные таймеры
            launch {
                automationRepository?.timerTriggers?.collect { timer ->
                    if (!isRunning()) return@collect
                    logger?.i("TelegramEngine", "⏱ Выполнение отложенной команды таймера: ${timer.commandText}")
                    val config = configRepository.config.value
                    val primaryUserId = config.allowedUserIds.firstOrNull() ?: getActiveChatIds().firstOrNull() ?: 0L

                    val parts = timer.commandText.trim().split("\\s+".toRegex())
                    val commandName = parts[0]
                    val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()

                    val incoming =
                        IncomingCommand(
                            id = "timer_${timer.id}_${System.currentTimeMillis()}",
                            name = commandName,
                            rawText = timer.commandText,
                            args = args,
                            source = CommandSource.TELEGRAM,
                            senderId = primaryUserId.toString(),
                            chatId = primaryUserId,
                        )

                    val result = dispatcher.dispatch(incoming)
                    if (primaryUserId != 0L) {
                        resultSender.sendResultToChat(primaryUserId, result)
                    }
                }
            }

            // 7. События батареи и питания
            launch {
                com.remotebot.core.common.battery.BatteryReceiver.batteryEvents.collect { event ->
                    if (!isRunning()) return@collect
                    handleBatteryEvent(event)
                }
            }
        }
    }

    fun getTargetUserIds(eventType: SubscriptionEventType): Set<Long> {
        val config = configRepository.config.value
        val candidateTargets =
            if (config.allowedUserIds.isNotEmpty()) {
                config.allowedUserIds
            } else {
                getActiveChatIds().toSet()
            }
        return candidateTargets.filter { it != 0L && config.isUserSubscribedTo(it, eventType) }.toSet()
    }

    suspend fun broadcastMessage(
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null,
        disableWebPagePreview: Boolean = true,
        eventType: SubscriptionEventType = SubscriptionEventType.SYSTEM,
    ) {
        val targets = getTargetUserIds(eventType)
        if (targets.isEmpty()) {
            logger?.d("TelegramEngine", "Нет активных подписчиков для broadcast [$eventType]")
            return
        }
        val plainText = text.replace("<[^>]*>".toRegex(), "").trim()
        val now = System.currentTimeMillis()
        recentSentBotMessages[plainText] = now
        if (recentSentBotMessages.size > 200) {
            recentSentBotMessages.entries.removeIf { (now - it.value) > 60_000L }
        }

        for (userId in targets) {
            try {
                apiClient.sendMessage(
                    chatId = userId,
                    text = text,
                    replyMarkup = replyMarkup,
                    disableWebPagePreview = disableWebPagePreview,
                )
            } catch (e: Exception) {
                logger?.w("TelegramEngine", "Ошибка отправки push в $userId: ${e.message}")
            }
        }
    }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private suspend fun handleBatteryEvent(event: com.remotebot.core.common.battery.BatteryEvent) {
        val config = configRepository.config.value
        when (event) {
            is com.remotebot.core.common.battery.BatteryEvent.ChargingChanged -> {
                val emoji = if (event.isCharging) "⚡" else "🔌"
                val title =
                    if (event.isCharging) {
                        "<b>Подключено к зарядному устройству</b>"
                    } else {
                        "<b>Отключено от зарядного устройства</b>"
                    }
                val details =
                    buildString {
                        appendLine("$emoji $title")
                        appendLine("• Уровень заряда: <b>${event.level}%</b>")
                        if (event.isCharging) {
                            appendLine("• Источник: <b>${event.plugType}</b>")
                            if (event.voltageMv > 0) appendLine("• Напряжение: ${event.voltageMv} mV")
                        } else {
                            appendLine("• Режим: <b>Работа от аккумулятора (разрядка)</b>")
                        }
                        if (event.temperatureC > 0) {
                            appendLine("• Температура: ${String.format(Locale.US, "%.1f", event.temperatureC)}°C")
                        }
                    }.trimEnd()

                if (config.telegramBotEnabled && config.tgForwardBatteryChargingEnabled) {
                    logger?.i(
                        "TelegramEngine",
                        "🔋 Отправка уведомления о статусе зарядки (${event.level}%, isCharging=${event.isCharging}) в Telegram",
                    )
                    broadcastMessage(details, eventType = SubscriptionEventType.SYSTEM)
                }

                if (config.smsBotEnabled && config.smsForwardBatteryChargingEnabled && config.smsForwardTargetNumbers.isNotEmpty()) {
                    val rawSmsText =
                        if (event.isCharging) {
                            "⚡ Zaryadka podklyuchena: ${event.level}%, ${event.plugType}"
                        } else {
                            "🔌 Zaryadka otklyuchena: ${event.level}%"
                        }
                    sendBatterySmsToTargets(rawSmsText)
                }
            }

            is com.remotebot.core.common.battery.BatteryEvent.LowBattery -> {
                val alert =
                    """
                    🪫 <b>ВНИМАНИЕ: Низкий заряд аккумулятора!</b>
                    
                    • Текущий уровень: <b>${event.level}%</b>
                    • Статус: <b>Разряжается</b>
                    • Рекомендация: <i>Подключите устройство к зарядному устройству!</i>
                    """.trimIndent()

                if (config.telegramBotEnabled && config.tgForwardBatteryLowEnabled) {
                    logger?.i("TelegramEngine", "🪫 Отправка тревоги низкого заряда (${event.level}%) в Telegram")
                    broadcastMessage(alert, eventType = SubscriptionEventType.SYSTEM)
                }

                if (config.smsBotEnabled && config.smsForwardBatteryLowEnabled && config.smsForwardTargetNumbers.isNotEmpty()) {
                    val rawSmsText = "🪫 Vnimanie: Nizkiy zaryad batarei ${event.level}%!"
                    sendBatterySmsToTargets(rawSmsText)
                }
            }
        }
    }

    private suspend fun sendBatterySmsToTargets(rawText: String) {
        val config = configRepository.config.value
        val finalSmsText =
            if (config.smsTransliterationEnabled) {
                TransliterationUtil.transliterate(rawText)
            } else {
                rawText
            }
        for (targetNumber in config.smsForwardTargetNumbers) {
            try {
                telephonyRepository?.sendSms(targetNumber, finalSmsText)
                logger?.i("TelegramEngine", "📤 SMS о статусе батареи отправлено на $targetNumber")
            } catch (e: Exception) {
                logger?.w("TelegramEngine", "Ошибка отправки SMS о батарее на $targetNumber: ${e.message}")
            }
        }
    }

    fun clearCache() {
        recentForwardedNotifications.clear()
        recentSentBotMessages.clear()
    }

    companion object {
        @Volatile
        var activeInstance: TelegramEventForwarder? = null

        fun clearAllCaches() {
            activeInstance?.clearCache()
        }

        fun getEchoCacheSize(): Int = activeInstance?.recentSentBotMessages?.size ?: 0

        fun getDedupCacheSize(): Int = activeInstance?.recentForwardedNotifications?.size ?: 0
    }
}
