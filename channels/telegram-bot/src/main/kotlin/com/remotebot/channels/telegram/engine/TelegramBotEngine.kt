package com.remotebot.channels.telegram.engine

import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandDispatcher
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.ConnectionMode
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.SubscriptionEventType
import com.remotebot.domain.model.TgUpdate
import com.remotebot.domain.push.PushWakeupHandler
import com.remotebot.domain.repository.AutomationRepository
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.CameraRepository
import com.remotebot.domain.repository.LocationRepository
import com.remotebot.domain.repository.NotificationRepository
import com.remotebot.domain.repository.TelephonyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TelegramBotEngine(
    private val apiClient: TelegramApiClient,
    private val dispatcher: CommandDispatcher,
    private val configRepository: BotConfigRepository,
    private val scope: CoroutineScope,
    private val notificationRepository: NotificationRepository? = null,
    private val telephonyRepository: TelephonyRepository? = null,
    private val locationRepository: LocationRepository? = null,
    private val automationRepository: AutomationRepository? = null,
    private val cameraRepository: CameraRepository? = null,
    private val audioRepository: com.remotebot.domain.repository.AudioRepository? = null,
    private val logger: AppLogger? = null,
) : PushWakeupHandler {
    private var pollingJob: Job? = null
    private var eventObserversJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private var lastUpdateId = 0L
    private val activeChatIds = ConcurrentHashMap.newKeySet<Long>()
    private val resultSender = TelegramResultSender(apiClient, logger)

    private val replyHandler =
        TelegramInteractiveReplyHandler(
            apiClient = apiClient,
            telephonyRepository = telephonyRepository,
            notificationRepository = notificationRepository,
            configRepository = configRepository,
            logger = logger,
        )

    private val eventForwarder =
        TelegramEventForwarder(
            apiClient = apiClient,
            dispatcher = dispatcher,
            configRepository = configRepository,
            resultSender = resultSender,
            notificationRepository = notificationRepository,
            telephonyRepository = telephonyRepository,
            locationRepository = locationRepository,
            automationRepository = automationRepository,
            cameraRepository = cameraRepository,
            audioRepository = audioRepository,
            getActiveChatIds = { activeChatIds },
            isRunning = { isRunning.get() },
            logger = logger,
        )

    private fun recordActiveUser(chatId: Long) {
        activeChatIds.add(chatId)
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        logger?.i("TelegramEngine", "Запуск движка опроса Telegram (Long Polling)")

        eventObserversJob = eventForwarder.startObservers(scope)

        pollingJob =
            scope.launch(Dispatchers.IO) {
                var errorBackoffMs = 1000L

                while (isActive && isRunning.get()) {
                    val config = configRepository.config.value
                    // If Telegram bot is toggled off or token is empty
                    if (!config.telegramBotEnabled || config.botToken.isBlank()) {
                        delay(3000)
                        continue
                    }

                    // If PUSH_WAKEUP mode is enabled, background Long Polling stays idle
                    if (config.connectionMode == ConnectionMode.PUSH_WAKEUP) {
                        delay(3000)
                        continue
                    }

                    try {
                        val updates = apiClient.getUpdates(offset = lastUpdateId + 1, timeoutSeconds = 30)
                        errorBackoffMs = 1000L // Сброс backoff после успешного запроса

                        for (update in updates) {
                            lastUpdateId = maxOf(lastUpdateId, update.updateId)
                            processUpdate(update)
                        }
                    } catch (e: Exception) {
                        logger?.e("TelegramEngine", "Сбой опроса: ${e.message}", e)
                        delay(errorBackoffMs)
                        errorBackoffMs = minOf(errorBackoffMs * 2, 30000L) // Максимум 30 сек задержки
                    }
                }
            }
    }

    suspend fun broadcastMessage(
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null,
        eventType: SubscriptionEventType = SubscriptionEventType.SYSTEM,
    ) {
        eventForwarder.broadcastMessage(text, replyMarkup, eventType = eventType)
    }

    /**
     * Direct processing of update from FCM Data Push
     */
    override suspend fun processPushUpdate(update: TgUpdate) {
        logger?.i("TelegramEngine", "⚡ Обработка Push Update id=${update.updateId}")
        processUpdate(update)
    }

    /**
     * Handle wakeup triggered by FCM Data Push signal (wake up)
     */
    override suspend fun handlePushWakeup(targetUpdateId: Long) {
        logger?.i("TelegramEngine", "Пробуждение по FCM Push update_id=$targetUpdateId")
    }

    private suspend fun processUpdate(update: TgUpdate) {
        val query = update.callbackQuery
        if (query != null) {
            handleCallbackQuery(update, query)
            return
        }

        val message = update.message ?: return
        val rawText = (message.text ?: message.caption)?.trim() ?: ""
        val senderId = message.from?.id?.toString() ?: return
        val chatId = message.chat.id

        // If document received, check file type
        val doc = message.document
        if (doc != null) {
            if (handleDocumentMessage(update, doc, rawText, senderId, chatId)) {
                return
            }
        }

        if (rawText.isBlank()) return
        val text = rawText

        recordActiveUser(chatId)
        logger?.i("TelegramEngine", "📥 [$senderId]: $text")

        // 1. Check active interactive reply modes for SMS or notification
        if (replyHandler.handlePendingReplies(chatId, text)) {
            return
        }

        // 2. Check if this is a Reply/quote to a previously forwarded SMS or notification
        if (replyHandler.handleReplyToMessage(message, chatId, text)) {
            return
        }

        // Parse command and arguments
        val parts = text.split("\\s+".toRegex())
        val commandName = parts[0]
        val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()

        val incoming =
            IncomingCommand(
                id = update.updateId.toString(),
                name = commandName,
                rawText = text,
                args = args,
                source = CommandSource.TELEGRAM,
                senderId = senderId,
                chatId = chatId,
            )

        try {
            val result = dispatcher.dispatch(incoming)
            resultSender.sendResultToChat(chatId, result)
        } catch (e: Exception) {
            logger?.e("TelegramEngine", "Ошибка обработки команды $commandName: ${e.message}", e)
            try {
                apiClient.sendMessage(chatId, "⚠️ Ошибка обработки команды: ${e.localizedMessage ?: e.message}")
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun handleCallbackQuery(
        update: TgUpdate,
        query: com.remotebot.domain.model.TgCallbackQuery,
    ) {
        val data = query.data?.trim() ?: return
        val senderId = query.from.id.toString()
        val msg = query.message ?: return
        val chatId = msg.chat.id
        val messageId = msg.messageId

        recordActiveUser(chatId)
        logger?.i("TelegramEngine", "🔘 Callback [$senderId]: $data")

        if (replyHandler.handleCallback(query, data, chatId)) {
            return
        }

        try {
            val parts = data.split("\\s+".toRegex())
            val commandName = parts[0]
            val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()

            val incoming =
                IncomingCommand(
                    id = "${update.updateId}_cb",
                    name = commandName,
                    rawText = data,
                    args = args,
                    source = CommandSource.TELEGRAM,
                    senderId = senderId,
                    chatId = chatId,
                )

            val result = dispatcher.dispatch(incoming)

            try {
                if (result is CommandResult.Error) {
                    apiClient.answerCallbackQuery(query.id, text = result.errorMsg, showAlert = true)
                } else {
                    apiClient.answerCallbackQuery(query.id)
                }
            } catch (e: Exception) {
                logger?.w("TelegramEngine", "answerCallbackQuery failed: ${e.message}")
            }

            when (result) {
                is CommandResult.Text -> {
                    val edited =
                        try {
                            apiClient.editMessageText(chatId, messageId, result.message, replyMarkup = result.keyboard)
                        } catch (e: Exception) {
                            logger?.w("TelegramEngine", "editMessageText failed: ${e.message}")
                            false
                        }
                    if (!edited) {
                        apiClient.sendMessage(chatId, result.message, replyMarkup = result.keyboard)
                    }
                }
                is CommandResult.Error -> {
                    try {
                        apiClient.sendMessage(chatId, result.errorMsg)
                    } catch (_: Exception) {
                    }
                }
                else -> resultSender.sendResultToChat(chatId, result)
            }
        } catch (e: Exception) {
            logger?.e("TelegramEngine", "Ошибка обработки callback [$data]: ${e.message}", e)
            try {
                apiClient.sendMessage(chatId, "⚠️ <b>Ошибка обработки:</b> ${e.localizedMessage ?: e.message}")
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun handleDocumentMessage(
        update: TgUpdate,
        doc: com.remotebot.domain.model.TgDocument,
        rawText: String,
        senderId: String,
        chatId: Long,
    ): Boolean {
        val fileName = doc.fileName ?: ""

        // 1. Check if received document is an APK package
        val isApkFile =
            fileName.endsWith(".apk", ignoreCase = true) ||
                rawText.startsWith("/install", ignoreCase = true)

        if (isApkFile) {
            val apkName = if (fileName.isNotBlank()) fileName else "app_${System.currentTimeMillis()}.apk"
            logger?.i("TelegramEngine", "📦 Получен APK файл: $apkName (${doc.fileSize} bytes)")

            val isCloudApi =
                configRepository.config.value.customApiRoot
                    .isNullOrBlank()
            val fileSize = doc.fileSize
            if (isCloudApi && fileSize != null && fileSize > 20 * 1024 * 1024) {
                val sizeMb = fileSize / (1024 * 1024)
                apiClient.sendMessage(
                    chatId,
                    """
                    ⚠️ <b>Файл превышает лимит Telegram Cloud API ($sizeMb МБ > 20 МБ)</b>

                    Облачный Telegram Bot API запрещает ботам скачивать файлы размером более 20 МБ через команду getFile.

                    💡 <b>Как установить этот APK:</b>
                    1. Загрузите файл на телефон вручную или через ADB:
                       <code>adb push "$apkName" /sdcard/Download/</code>
                    2. Отправьте боту команду установки:
                       <code>/install /sdcard/Download/$apkName</code>

                    <i>Либо подключите локальный Telegram Bot API Server (поддержка файлов до 2000 МБ).</i>
                    """.trimIndent(),
                )
                return true
            }

            apiClient.sendMessage(chatId, "⏳ Скачивание APK пакета <code>$apkName</code>...")
            try {
                val tgFile = apiClient.getFile(doc.fileId)
                val filePath = tgFile.filePath
                if (filePath != null) {
                    val bytes = apiClient.downloadFileBytes(filePath)
                    val targetDir =
                        File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "")
                    targetDir.mkdirs()
                    val localApk = File(targetDir, apkName)
                    localApk.writeBytes(bytes)
                    resultSender.ensureFileReadable(localApk)

                    logger?.i("TelegramEngine", "APK сохранен в: ${localApk.absolutePath}. Запуск установки...")
                    val incoming =
                        IncomingCommand(
                            id = update.updateId.toString(),
                            name = "/install",
                            rawText = "/install ${localApk.absolutePath}",
                            args = listOf(localApk.absolutePath),
                            source = CommandSource.TELEGRAM,
                            senderId = senderId,
                            chatId = chatId,
                        )
                    val result = dispatcher.dispatch(incoming)
                    resultSender.sendResultToChat(chatId, result)
                    return true
                }
            } catch (e: Exception) {
                logger?.e("TelegramEngine", "Ошибка скачивания/установки APK: ${e.message}", e)
                val isSizeError = e.message?.contains("file is too big", ignoreCase = true) == true
                if (isSizeError) {
                    apiClient.sendMessage(
                        chatId,
                        "❌ <b>Файл превышает лимит Telegram Cloud API (20 МБ).</b>\nЗагрузите APK локально и выполните: <code>/install /sdcard/Download/$apkName</code>",
                    )
                } else {
                    apiClient.sendMessage(chatId, "❌ Ошибка скачивания или установки APK: ${e.localizedMessage ?: e.message}")
                }
                return true
            }
        }

        // 2. Check if received document is a backup file (.json)
        val isBackupFile =
            !isApkFile &&
                (
                    fileName.endsWith(".json", ignoreCase = true) ||
                        rawText.startsWith("/restore_backup", ignoreCase = true)
                )

        if (isBackupFile) {
            logger?.i("TelegramEngine", "📥 Получен документ бэкапа: ${doc.fileName} (${doc.fileSize} bytes)")
            try {
                val tgFile = apiClient.getFile(doc.fileId)
                val filePath = tgFile.filePath
                if (filePath != null) {
                    val bytes = apiClient.downloadFileBytes(filePath)
                    val jsonString = String(bytes, Charsets.UTF_8)
                    val incoming =
                        IncomingCommand(
                            id = update.updateId.toString(),
                            name = "/restore_backup",
                            rawText = "/restore_backup $jsonString",
                            args = listOf(jsonString),
                            source = CommandSource.TELEGRAM,
                            senderId = senderId,
                            chatId = chatId,
                        )
                    val result = dispatcher.dispatch(incoming)
                    resultSender.sendResultToChat(chatId, result)
                    return true
                }
            } catch (e: Exception) {
                logger?.e("TelegramEngine", "Ошибка загрузки документа бэкапа: ${e.message}", e)
                apiClient.sendMessage(chatId, "❌ Ошибка загрузки файла бэкапа: ${e.localizedMessage ?: e.message}")
                return true
            }
        }

        return false
    }

    fun stop() {
        logger?.i("TelegramEngine", "Остановка движка опроса Telegram")
        isRunning.set(false)
        pollingJob?.cancel()
        eventObserversJob?.cancel()
        eventForwarder.clearCache()
        replyHandler.clear()
    }
}
