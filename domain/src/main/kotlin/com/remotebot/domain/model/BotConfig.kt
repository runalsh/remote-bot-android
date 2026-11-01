package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionMode {
    LONG_POLLING,
    PUSH_WAKEUP,
}

@Serializable
enum class BotLanguage {
    AUTO, // System language
    EN, // English
    RU, // Russian
}

@Serializable
enum class EndpointSelectionStrategy {
    FAILOVER, // In order: if the first one fails -> try the second, etc.
    ROUND_ROBIN, // Round-robin balancing
    LOWEST_LATENCY, // By the lowest latency (ping)
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
    val telegramBotEnabled: Boolean = true, // Enabled by default
    val botToken: String = "",
    val allowedUserIds: Set<Long> = emptySet(),
    val allowedPhoneNumbers: Set<String> = emptySet(),
    val connectionMode: ConnectionMode = ConnectionMode.LONG_POLLING,
    val customApiRoot: String? = null, // e.g. http://192.168.1.50:8081/bot for 2GB uploads
    val apiEndpoints: List<String> = emptyList(), // List of alternative/mirror API endpoints
    val endpointSelectionStrategy: EndpointSelectionStrategy = EndpointSelectionStrategy.FAILOVER,
    val pushGatewayUrl: String? = null,
    val pushSecretKey: String? = null,
    val pushServers: List<PushServerConfig> = emptyList(), // List of independent push servers/gateways
    val pushServerSelectionStrategy: EndpointSelectionStrategy = EndpointSelectionStrategy.FAILOVER,
    val pushRetryMaxAttempts: Int = 3, // Max send/wakeup attempts via the gateway (1-10)
    val pushRetryDelaySeconds: Int = 5, // Delay between attempts in seconds (1-60)
    val smsPin: String? = null,
    val smsPinRequiredAlways: Boolean = false, // If false, required only for destructive
    val replyUnauthorizedResponseEnabled: Boolean = true, // Reply "Not authorized"; if disabled — ignore silently
    val storageDirectory: String? = null,
    val notificationForwardingApps: Set<String> = emptySet(),
    val notificationStopWords: List<String> = emptyList(),
    val notificationDedupTimeoutSeconds: Int = 15,
    val forwardRateLimitPerMinute: Int = 30, // Limit of forwarded incoming SMS per minute per number (0 = unlimited)
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
    // Telegram forwarding channel settings
    val tgForwardSmsEnabled: Boolean = true, // Forward incoming SMS to Telegram
    val tgForwardCallsEnabled: Boolean = true, // Forward calls/missed calls to Telegram
    val tgForwardNotificationsEnabled: Boolean = true, // Forward push notifications to Telegram
    val tgForwardBatteryChargingEnabled: Boolean = true, // Charging/discharging status alerts in Telegram
    val tgForwardBatteryLowEnabled: Boolean = true, // Low battery alerts (<=20% every 3%) in Telegram
    // Independent SMS bot and forwarding settings
    val smsBotEnabled: Boolean = false, // Disabled by default
    val smsForwardTargetNumbers: Set<String> = emptySet(), // List of destination numbers for forwarding
    val smsForwardSmsEnabled: Boolean = false, // Forward incoming SMS to the target numbers
    val smsForwardMissedCallsEnabled: Boolean = false, // Forward missed calls via SMS
    val smsForwardNotificationsEnabled: Boolean = false, // Forward push notifications via SMS
    val smsForwardBatteryChargingEnabled: Boolean = false, // Charging/discharging status alerts via SMS
    val smsForwardBatteryLowEnabled: Boolean = false, // Low battery alerts via SMS
    val smsTransliterationEnabled: Boolean = false, // Cyrillic-to-Latin transliteration to save SMS length
    val isDebugModeEnabled: Boolean = false, // Hidden developer engineering menu (Phase 13)
    val enable2FaInterception: Boolean = false, // Experimental 2FA interception and confirmation (Multifactor etc.)
    val userSubscriptions: Map<Long, UserSubscription> = emptyMap(), // Per-user subscriptions to event types
    val botLanguage: BotLanguage = BotLanguage.AUTO, // Bot UI language (AUTO / EN / RU)
) {
    fun isUserSubscribedTo(
        userId: Long,
        eventType: SubscriptionEventType,
    ): Boolean {
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
