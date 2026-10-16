package com.remotebot.core.common.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.CapturedNotification
import com.remotebot.domain.model.NotificationRule
import com.remotebot.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class DefaultNotificationRepository(
    private val context: Context,
) : NotificationRepository {
    private val _rules = MutableStateFlow<List<NotificationRule>>(emptyList())
    override val rules: StateFlow<List<NotificationRule>> = _rules.asStateFlow()

    override val recentNotificationsCount: Int
        get() = recentNotificationsById.size

    private val _notificationEvents = MutableSharedFlow<CapturedNotification>(extraBufferCapacity = 64)
    override val notificationEvents: Flow<CapturedNotification> = _notificationEvents.asSharedFlow()

    // Storage for recent notifications by unique shortId (FIFO capacity constrained)
    private val recentNotificationsById = ConcurrentHashMap<String, CapturedNotification>()
    private val recentSbnById = ConcurrentHashMap<String, StatusBarNotification>()
    private val recentIdsQueue = ConcurrentLinkedQueue<String>()

    // Dedicated long-lived cache for interactive 2FA/Action notifications (protected from spam eviction)
    private val active2FaSbnById = ConcurrentHashMap<String, StatusBarNotification>()
    private val active2FaIdsQueue = ConcurrentLinkedQueue<String>()

    private val idCounter = AtomicInteger(1)
    private val idMapping = ConcurrentHashMap<String, String>() // shortId -> fullKey

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        // Skip own notifications to prevent loops
        if (packageName == context.packageName) return

        // Skip ongoing system notifications and group summaries
        val notifObj = sbn.notification
        if ((notifObj.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            AppLogger.d("DefaultNotificationRepository", "🚫 Пропущено: GROUP_SUMMARY уведомление от $packageName")
            return
        }
        if ((notifObj.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            AppLogger.d("DefaultNotificationRepository", "🚫 Пропущено: ONGOING_EVENT (постоянное) уведомление от $packageName")
            return
        }

        val extras = notifObj.extras ?: Bundle()
        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        var text =
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val appName =
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }

        // 1. Check custom styles (e.g. LastPass with DecoratedCustomViewStyle)
        // If text is empty but actions exist or custom template is present, build fallback description
        val rawActions = notifObj.actions ?: emptyArray()
        val hasCustomViews =
            notifObj.contentView != null || notifObj.bigContentView != null || extras.containsKey("android.contains.customView")
        if (title.isNullOrBlank() && text.isNullOrBlank()) {
            if (rawActions.isNotEmpty() || hasCustomViews) {
                title = appName
                text = "Запрос подтверждения авторизации / действия"
                AppLogger.d("DefaultNotificationRepository", "Кастомный view пуша от $packageName: применен fallback заголовок")
            } else {
                AppLogger.d("DefaultNotificationRepository", "🚫 Пропущено: пустое уведомление (без заголовка и текста) от $packageName")
                return
            }
        }

        // Check rules
        if (shouldFilterOut(packageName, title, text)) {
            AppLogger.i(
                "DefaultNotificationRepository",
                "🚫 Отфильтровано пользовательским правилом: $appName ($packageName), заголовок: $title",
            )
            return
        }

        val shortId = idCounter.getAndIncrement().toString()
        val fullKey = sbn.key
        idMapping[shortId] = fullKey
        recentSbnById[shortId] = sbn
        recentIdsQueue.add(shortId)

        val hasReply = hasReplyAction(sbn)
        val extractedActions = extractActionButtons(sbn)

        // If notification contains interactive actions (2FA / authorization), save to dedicated cache
        if (extractedActions.isNotEmpty()) {
            active2FaSbnById[shortId] = sbn
            active2FaSbnById[fullKey] = sbn
            active2FaIdsQueue.add(shortId)
            while (active2FaIdsQueue.size > 100) {
                val oldest2FaId = active2FaIdsQueue.poll() ?: break
                active2FaSbnById.remove(oldest2FaId)
                val mappedKey = idMapping[oldest2FaId]
                if (mappedKey != null) active2FaSbnById.remove(mappedKey)
            }
            AppLogger.i(
                "DefaultNotificationRepository",
                "🛡️ Сохранено интерактивное 2FA действие для [$shortId] ($packageName): кнопок=${extractedActions.size}",
            )
        }

        // Strict FIFO rotation of general history (up to 1000 notifications)
        while (recentIdsQueue.size > 1000) {
            val oldestKey = recentIdsQueue.poll() ?: break
            // Do not remove from recentSbnById if it is an active 2FA notification
            if (!active2FaSbnById.containsKey(oldestKey)) {
                recentSbnById.remove(oldestKey)
            }
            recentNotificationsById.remove(oldestKey)
            idMapping.remove(oldestKey)
        }

        val captured =
            CapturedNotification(
                id = shortId,
                key = fullKey,
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                timestampMs = sbn.postTime,
                hasReplyAction = hasReply,
                postWhenMs = notifObj.`when`,
                actions = extractedActions,
            )
        recentNotificationsById[shortId] = captured

        AppLogger.d(
            "DefaultNotificationRepository",
            "Captured notification [$shortId] from $appName: $title (actions=${extractedActions.size})",
        )
        _notificationEvents.tryEmit(captured)
    }

    override suspend fun addRule(rule: NotificationRule) {
        _rules.value = _rules.value + rule
    }

    override suspend fun removeRule(ruleId: String) {
        _rules.value = _rules.value.filterNot { it.id == ruleId }
    }

    override suspend fun getRecentNotifications(limit: Int): List<CapturedNotification> =
        recentNotificationsById.values
            .sortedByDescending { it.timestampMs }
            .take(limit)

    private fun findSbn(notificationId: String): StatusBarNotification? {
        // 1. Direct lookup by shortId in recentSbnById
        recentSbnById[notificationId]?.let { return it }

        // 2. Search in dedicated 2FA cache
        active2FaSbnById[notificationId]?.let { return it }

        // 3. Lookup by mapped fullKey
        val fullKey = idMapping[notificationId]
        if (fullKey != null) {
            recentSbnById[fullKey]?.let { return it }
            active2FaSbnById[fullKey]?.let { return it }
        }

        // 4. Lookup by matching sbn.key among cached SBNs
        recentSbnById.values.find { it.key == notificationId || (fullKey != null && it.key == fullKey) }?.let { return it }
        active2FaSbnById.values.find { it.key == notificationId || (fullKey != null && it.key == fullKey) }?.let { return it }

        // 5. Fallback: query active system notifications from Android shade via NotificationListenerService
        val activeSbns = NotificationListenerBridge.getActiveNotifications()
        if (activeSbns != null && activeSbns.isNotEmpty()) {
            // 5.1 Exact match by key
            val byKey = activeSbns.find { it.key == notificationId || (fullKey != null && it.key == fullKey) }
            if (byKey != null) {
                AppLogger.i("DefaultNotificationRepository", "Найдено активное уведомление в шторке по ключу: ${byKey.key}")
                recentSbnById[notificationId] = byKey
                active2FaSbnById[notificationId] = byKey
                return byKey
            }

            // 5.2 Search by application package from CapturedNotification
            val captured = recentNotificationsById[notificationId]
            val targetPkg = captured?.packageName
            if (!targetPkg.isNullOrBlank()) {
                val byPkg = activeSbns.find { it.packageName == targetPkg }
                if (byPkg != null) {
                    AppLogger.i("DefaultNotificationRepository", "Найдено активное уведомление в шторке по пакету $targetPkg: ${byPkg.key}")
                    recentSbnById[notificationId] = byPkg
                    active2FaSbnById[notificationId] = byPkg
                    return byPkg
                }
            }

            // 5.3 For trusted 2FA (Multifactor, etc.): if shade contains active push with buttons
            val multifactorSbn = activeSbns.find { sbn ->
                (sbn.packageName == "ru.multifactor.app" || trusted2FaPackages.contains(sbn.packageName.lowercase())) &&
                    sbn.notification.actions?.isNotEmpty() == true
            }
            if (multifactorSbn != null) {
                AppLogger.i("DefaultNotificationRepository", "Найдено 2FA уведомление в шторке (${multifactorSbn.packageName}): ${multifactorSbn.key}")
                recentSbnById[notificationId] = multifactorSbn
                active2FaSbnById[notificationId] = multifactorSbn
                return multifactorSbn
            }
        }

        return null
    }

    override suspend fun replyToNotification(
        notificationId: String,
        replyText: String,
    ): Result<Unit> =
        runCatching {
            val sbn = findSbn(notificationId)
                ?: throw IllegalStateException("Notification $notificationId not found or expired")

            val actions = sbn.notification.actions ?: throw IllegalStateException("Notification has no action buttons")
            var replied = false

            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    val intent = Intent()
                    val bundle =
                        Bundle().apply {
                            putCharSequence(remoteInput.resultKey, replyText)
                        }
                    RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                    action.actionIntent.send(context, 0, intent)
                    replied = true
                    AppLogger.d("DefaultNotificationRepository", "Sent reply to notification $notificationId: $replyText")
                    break
                }
                if (replied) break
            }

            if (!replied) {
                throw IllegalStateException("No Quick Reply RemoteInput found for notification $notificationId")
            }
        }

    override suspend fun executeNotificationAction(
        notificationId: String,
        actionIndex: Int,
    ): Result<Unit> =
        runCatching {
            val sbn = findSbn(notificationId)
                ?: throw IllegalStateException("Notification $notificationId not found or expired")

            val actions = sbn.notification.actions ?: throw IllegalStateException("Notification has no action buttons")
            if (actionIndex < 0 || actionIndex >= actions.size) {
                throw IndexOutOfBoundsException("Action index $actionIndex out of bounds (total: ${actions.size})")
            }

            val targetAction = actions[actionIndex]
            val intent = targetAction.actionIntent ?: throw IllegalStateException("Action has no PendingIntent")
            val title = targetAction.title?.toString() ?: "Action #$actionIndex"

            AppLogger.i(
                "DefaultNotificationRepository",
                "⚡ Исполнение действия [$title] (индекс $actionIndex) для уведомления $notificationId [${sbn.packageName}]...",
            )

            // Safe PendingIntent launch (with Android 14+ background activity start support)
            val optionsBundle =
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    try {
                        val basic = android.app.ActivityOptions.makeBasic()
                        val method =
                            android.app.ActivityOptions::class.java.getMethod(
                                "setPendingIntentBackgroundActivityStartMode",
                                Int::class.javaPrimitiveType,
                            )
                        method.invoke(basic, 1) // MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        basic.toBundle()
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }

            if (optionsBundle != null) {
                intent.send(context, 0, Intent(), null, null, null, optionsBundle)
            } else {
                intent.send(context, 0, Intent())
            }

            AppLogger.i(
                "DefaultNotificationRepository",
                "✅ Действие [$title] успешно отправлено целевому приложению ${sbn.packageName}",
            )
        }

    override suspend fun cancelNotification(notificationId: String): Result<Unit> =
        runCatching {
            val fullKey = idMapping[notificationId] ?: notificationId
            recentSbnById.remove(notificationId)
            recentNotificationsById.remove(notificationId)
            active2FaSbnById.remove(notificationId)
            idMapping.remove(notificationId)
            NotificationListenerBridge.cancelNotification(fullKey)
        }

    override suspend fun postNotification(
        title: String,
        text: String,
    ): Result<Unit> =
        runCatching {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "remotebot_custom_channel"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel =
                    android.app.NotificationChannel(
                        channelId,
                        "Кастомные уведомления Remote Bot",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT,
                    )
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent =
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val contentPendingIntent =
                launchIntent?.let {
                    android.app.PendingIntent.getActivity(
                        context,
                        0,
                        it,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )
                }

            val notification =
                androidx.core.app.NotificationCompat
                    .Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .apply {
                        if (contentPendingIntent != null) {
                            setContentIntent(contentPendingIntent)
                        }
                    }.build()

            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
        }

    override suspend fun simulateNotification(
        packageName: String,
        appName: String,
        title: String,
        text: String,
    ): Result<Unit> =
        simulateNotificationWithFlags(
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            hasRemoteInput = false,
            isGroupSummary = false,
            category = null,
        )

    override suspend fun simulateNotificationWithFlags(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        hasRemoteInput: Boolean,
        isGroupSummary: Boolean,
        category: String?,
        actions: List<com.remotebot.domain.model.NotificationActionButton>,
    ): Result<Unit> =
        runCatching {
            val shortId = idCounter.getAndIncrement().toString()
            val fullKey = "simulated|$packageName|$shortId"
            idMapping[shortId] = fullKey

            val captured =
                CapturedNotification(
                    id = shortId,
                    key = fullKey,
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    timestampMs = System.currentTimeMillis(),
                    hasReplyAction = hasRemoteInput,
                    isGroupSummary = isGroupSummary,
                    notificationCategory = category,
                    actions = actions,
                )
            recentNotificationsById[shortId] = captured

            AppLogger.i(
                "DefaultNotificationRepository",
                "Emitting simulated notification [$shortId] flags(remoteInput=$hasRemoteInput, groupSummary=$isGroupSummary, category=$category, actions=${actions.size}) for $appName ($packageName)",
            )
            _notificationEvents.tryEmit(captured)
        }

    private val trusted2FaPackages =
        setOf(
            "ru.multifactor.app", // Multifactor
            "com.lastpass.authenticator", // LastPass Authenticator
            "com.duosecurity.duomobile", // Duo Mobile
            "ru.yandex.key", // Яндекс Ключ
            "com.azure.authenticator", // Microsoft Authenticator
            "com.google.android.apps.authenticator2", // Google Authenticator
        )

    private fun extractActionButtons(sbn: StatusBarNotification): List<com.remotebot.domain.model.NotificationActionButton> {
        val packageName = sbn.packageName.lowercase()
        // Strict whitelist: only trusted authenticators and 2FA apps
        val isTrusted2Fa =
            trusted2FaPackages.contains(packageName) ||
                packageName.endsWith(".authenticator") ||
                packageName.endsWith(".duomobile") ||
                packageName == "ru.yandex.key"

        if (!isTrusted2Fa) return emptyList()

        val notif = sbn.notification
        val rawActions = notif.actions ?: return emptyList()
        val result = mutableListOf<com.remotebot.domain.model.NotificationActionButton>()

        rawActions.forEachIndexed { index, action ->
            // Skip actions that are purely text input fields (RemoteInput)
            val hasFreeForm = action.remoteInputs?.any { it.allowFreeFormInput } == true
            if (!hasFreeForm && action.actionIntent != null) {
                val title = action.title?.toString()?.trim()
                if (!title.isNullOrBlank()) {
                    result.add(
                        com.remotebot.domain.model.NotificationActionButton(
                            index = index,
                            title = title,
                        ),
                    )
                }
            }
        }
        return result
    }

    private fun hasReplyAction(sbn: StatusBarNotification): Boolean {
        val notif = sbn.notification
        val actions = notif.actions ?: return false

        // Check for RemoteInput allowing free-form input (allowFreeFormInput == true)
        val hasFreeFormInput =
            actions.any { action ->
                action.remoteInputs?.any { it.allowFreeFormInput } == true
            }
        if (!hasFreeFormInput) return false

        // Check notification category: messengers and chats (CATEGORY_MESSAGE / CATEGORY_CALL)
        // or MessagingStyle in extras ("android.messagingStyleUser", "android.messages")
        val isMessageCategory =
            notif.category == Notification.CATEGORY_MESSAGE ||
                notif.category == Notification.CATEGORY_CALL ||
                notif.category == "msg" ||
                notif.extras?.containsKey(androidx.core.app.NotificationCompat.EXTRA_MESSAGING_STYLE_USER) == true ||
                notif.extras?.containsKey("android.messagingStyleUser") == true ||
                notif.extras?.containsKey("android.messages") == true

        return isMessageCategory || sbn.packageName.contains("sms", ignoreCase = true)
    }

    override suspend fun clearRecentNotifications() {
        recentSbnById.clear()
        recentNotificationsById.clear()
        recentIdsQueue.clear()
        active2FaSbnById.clear()
        active2FaIdsQueue.clear()
        idMapping.clear()
        AppLogger.i("DefaultNotificationRepository", "Очищен кэш последних уведомлений")
    }

    private fun shouldFilterOut(
        packageName: String,
        title: String?,
        text: String?,
    ): Boolean {
        val activeRules = _rules.value
        if (activeRules.isEmpty()) return false

        for (rule in activeRules) {
            val pkgMatch = rule.packageName == null || rule.packageName.equals(packageName, ignoreCase = true)
            val titlePat = rule.titlePattern
            val titleMatch =
                titlePat == null ||
                    if (rule.isRegex) {
                        title != null && Regex(titlePat).containsMatchIn(title)
                    } else {
                        title != null && title.contains(titlePat, ignoreCase = true)
                    }
            val bodyPat = rule.bodyPattern
            val textMatch =
                bodyPat == null ||
                    if (rule.isRegex) {
                        text != null && Regex(bodyPat).containsMatchIn(text)
                    } else {
                        text != null && text.contains(bodyPat, ignoreCase = true)
                    }

            if (pkgMatch && titleMatch && textMatch) {
                if (rule.isExclude) return true
            }
        }
        return false
    }
}

object NotificationListenerBridge {
    @Volatile
    var listenerInstance: NotificationListenerService? = null

    @Volatile
    var notificationConsumer: ((StatusBarNotification) -> Unit)? = null

    fun postNotification(sbn: StatusBarNotification) {
        notificationConsumer?.invoke(sbn)
    }

    fun getActiveNotifications(): Array<StatusBarNotification>? {
        val instance = listenerInstance ?: return null
        return try {
            instance.activeNotifications
        } catch (e: Exception) {
            AppLogger.e("NotificationListenerBridge", "Failed to query active notifications: ${e.message}")
            null
        }
    }

    fun cancelNotification(key: String) {
        val instance = listenerInstance ?: return
        try {
            instance.cancelNotification(key)
        } catch (e: Exception) {
            AppLogger.e("NotificationListenerBridge", "Failed to cancel notification: ${e.message}")
        }
    }
}
