package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionMode {
    LONG_POLLING,
    PUSH_WAKEUP,
}

@Serializable
enum class EndpointSelectionStrategy {
    FAILOVER, // По порядку: первый упал -> пробуем второй и т.д.
    ROUND_ROBIN, // Балансировка по кругу
    LOWEST_LATENCY, // По минимальной задержке (пингу)
}

@Serializable
data class PushServerConfig(
    val id: String = "",
    val name: String = "",
    val gatewayUrl: String = "",
    val secretKey: String = "",
    val provider: PushProvider = PushProvider.FCM,
    val googleServicesJson: String? = null,
    val agconnectServicesJson: String? = null,
)

@Serializable
data class BotConfig(
    val telegramBotEnabled: Boolean = true, // По умолчанию включен
    val botToken: String = "",
    val allowedUserIds: Set<Long> = emptySet(),
    val allowedPhoneNumbers: Set<String> = emptySet(),
    val connectionMode: ConnectionMode = ConnectionMode.LONG_POLLING,
    val customApiRoot: String? = null, // e.g. http://192.168.1.50:8081/bot for 2GB uploads
    val apiEndpoints: List<String> = emptyList(), // Список альтернативных/зеркальных эндпоинтов API
    val endpointSelectionStrategy: EndpointSelectionStrategy = EndpointSelectionStrategy.FAILOVER,
    val pushGatewayUrl: String? = null,
    val pushSecretKey: String? = null,
    val pushServers: List<PushServerConfig> = emptyList(), // Список независимых Push-серверов/шлюзов
    val pushServerSelectionStrategy: EndpointSelectionStrategy = EndpointSelectionStrategy.FAILOVER,
    val pushRetryMaxAttempts: Int = 3, // Максимум попыток отправки/пробуждения через шлюз (1-10)
    val pushRetryDelaySeconds: Int = 5, // Задержка между попытками в секундах (1-60)
    val smsPin: String? = null,
    val smsPinRequiredAlways: Boolean = false, // If false, required only for destructive
    val replyUnauthorizedResponseEnabled: Boolean = true, // Отвечать «Не авторизован», если выключено — игнорировать (молчать)
    val storageDirectory: String? = null,
    val notificationForwardingApps: Set<String> = emptySet(),
    val notificationStopWords: List<String> = emptyList(),
    val notificationDedupTimeoutSeconds: Int = 15,
    val forwardRateLimitPerMinute: Int = 30, // Лимит пересылаемых входящих SMS в минуту от одного номера (0 = без ограничений)
    val googleServicesJson: String? = null,
    val fcmAppId: String? = null,
    val fcmApiKey: String? = null,
    val fcmProjectId: String? = null,
    val fcmSenderId: String? = null,
    val pushProvider: PushProvider = PushProvider.FCM,
    val agconnectServicesJson: String? = null,
    val hmsAppId: String? = null,
    val hmsClientId: String? = null,
    val hmsClientSecret: String? = null,
    val hmsApiKey: String? = null,
    // Настройки каналов пересылки в Telegram
    val tgForwardSmsEnabled: Boolean = true, // Пересылка входящих SMS в Telegram
    val tgForwardCallsEnabled: Boolean = true, // Пересылка звонков/пропущенных в Telegram
    val tgForwardNotificationsEnabled: Boolean = true, // Пересылка push-уведомлений в Telegram
    val tgForwardBatteryChargingEnabled: Boolean = true, // Оповещение о статусе зарядки/разрядки в Telegram
    val tgForwardBatteryLowEnabled: Boolean = true, // Оповещение о низком заряде (≤20% каждые 3%) в Telegram
    // Настройки независимого SMS-бота и пересылки
    val smsBotEnabled: Boolean = false, // По умолчанию выключен
    val smsForwardTargetNumbers: Set<String> = emptySet(), // Список номеров назначения для пересылки
    val smsForwardSmsEnabled: Boolean = false, // Пересылка входящих SMS на целевые номера
    val smsForwardMissedCallsEnabled: Boolean = false, // Пересылка пропущенных звонков по SMS
    val smsForwardNotificationsEnabled: Boolean = false, // Пересылка push-уведомлений по SMS
    val smsForwardBatteryChargingEnabled: Boolean = false, // Оповещение о статусе зарядки/разрядки по SMS
    val smsForwardBatteryLowEnabled: Boolean = false, // Оповещение о низком заряде по SMS
    val smsTransliterationEnabled: Boolean = false, // Транслитерация кириллицы в латиницу для экономии SMS
    val isDebugModeEnabled: Boolean = false, // Скрытое инженерное меню отладки (Phase 13)
    val enable2FaInterception: Boolean = false, // Экспериментальный перехват и подтверждение 2FA (Multifactor и др.)
    val userSubscriptions: Map<Long, UserSubscription> = emptyMap(), // Индивидуальные подписки пользователей на типы событий
) {
    fun isUserSubscribedTo(userId: Long, eventType: SubscriptionEventType): Boolean {
        if (allowedUserIds.isNotEmpty() && !allowedUserIds.contains(userId)) {
            return false
        }
        val sub = userSubscriptions[userId]
        return if (sub != null) {
            sub.isSubscribedTo(eventType)
        } else {
            eventType != SubscriptionEventType.SYSTEM
        }
    }
}
