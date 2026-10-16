package com.remotebot.app.data

import android.content.Context
import com.remotebot.core.security.SecureStorageManager
import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.ConnectionMode
import com.remotebot.domain.model.EndpointSelectionStrategy
import com.remotebot.domain.model.UserSubscription
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray

class PreferencesBotConfigRepository(
    context: Context,
    private val secureStorage: SecureStorageManager? = null,
) : BotConfigRepository {
    private val prefs = context.getSharedPreferences("remotebot_config", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _config = MutableStateFlow(loadConfig())
    override val config: StateFlow<BotConfig> = _config.asStateFlow()

    private fun loadConfig(): BotConfig {
        val telegramEnabled = prefs.getBoolean("telegram_bot_enabled", true)
        val rawToken = prefs.getString("bot_token", "") ?: ""
        val token = secureStorage?.decrypt(rawToken) ?: rawToken
        val userIdsString = prefs.getString("allowed_user_ids", "") ?: ""
        val allowedIds =
            if (userIdsString.isNotBlank()) {
                userIdsString.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
            } else {
                emptySet()
            }
        val phonesString = prefs.getString("allowed_phone_numbers", "") ?: ""
        val allowedPhones =
            if (phonesString.isNotBlank()) {
                phonesString
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            } else {
                emptySet()
            }
        val customRoot = prefs.getString("custom_api_root", null)
        val endpointsString = prefs.getString("api_endpoints", "") ?: ""
        val apiEndpointsList =
            if (endpointsString.isNotBlank()) {
                endpointsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        val strategyStr = prefs.getString("endpoint_selection_strategy", EndpointSelectionStrategy.FAILOVER.name)
        val strategy =
            try {
                EndpointSelectionStrategy.valueOf(strategyStr ?: EndpointSelectionStrategy.FAILOVER.name)
            } catch (
                _: Exception,
            ) {
                EndpointSelectionStrategy.FAILOVER
            }
        val pushUrl = prefs.getString("push_gateway_url", null)
        val rawPushSecret = prefs.getString("push_secret_key", null)
        val pushSecret = secureStorage?.decrypt(rawPushSecret) ?: rawPushSecret
        val pin = prefs.getString("sms_pin", null)
        val pinRequiredAlways = prefs.getBoolean("sms_pin_required_always", false)
        val replyUnauthorized = prefs.getBoolean("reply_unauthorized_response_enabled", true)
        val storageDir = prefs.getString("storage_directory", null)
        val notifAppsString = prefs.getString("notification_forwarding_apps", "") ?: ""
        val notifApps =
            if (notifAppsString.isNotBlank()) {
                notifAppsString
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            } else {
                emptySet()
            }
        val stopWordsString = prefs.getString("notification_stop_words", "") ?: ""
        val stopWords =
            if (stopWordsString.isNotBlank()) {
                try {
                    val jsonArray = JSONArray(stopWordsString)
                    val list = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getString(i))
                    }
                    list
                } catch (_: Exception) {
                    stopWordsString.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                }
            } else {
                emptyList()
            }
        val dedupTimeout = prefs.getInt("notification_dedup_timeout_seconds", 15)
        val rateLimitPerMinute = prefs.getInt("forward_rate_limit_per_minute", 30)
        val modeStr = prefs.getString("connection_mode", ConnectionMode.LONG_POLLING.name)
        val mode =
            try {
                ConnectionMode.valueOf(modeStr ?: ConnectionMode.LONG_POLLING.name)
            } catch (
                e: Exception,
            ) {
                ConnectionMode.LONG_POLLING
            }
        val fcmJson = prefs.getString("google_services_json", null)
        val fcmAppId = prefs.getString("fcm_app_id", null)
        val rawFcmApiKey = prefs.getString("fcm_api_key", null)
        val fcmApiKey = secureStorage?.decrypt(rawFcmApiKey) ?: rawFcmApiKey
        val fcmProjectId = prefs.getString("fcm_project_id", null)
        val fcmSenderId = prefs.getString("fcm_sender_id", null)
        val tgForwardSms = prefs.getBoolean("tg_forward_sms_enabled", true)
        val tgForwardCalls = prefs.getBoolean("tg_forward_calls_enabled", true)
        val tgForwardNotifs = prefs.getBoolean("tg_forward_notifications_enabled", true)
        val tgForwardBatteryCharging = prefs.getBoolean("tg_forward_battery_charging_enabled", true)
        val tgForwardBatteryLow = prefs.getBoolean("tg_forward_battery_low_enabled", true)
        val smsBotEnabled = prefs.getBoolean("sms_bot_enabled", false)
        val targetNumbersString = prefs.getString("sms_forward_target_numbers", "") ?: ""
        val targetNumbers =
            if (targetNumbersString.isNotBlank()) {
                targetNumbersString
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            } else {
                emptySet()
            }
        val smsForwardSms = prefs.getBoolean("sms_forward_sms_enabled", false)
        val smsForwardMissedCalls = prefs.getBoolean("sms_forward_missed_calls_enabled", false)
        val smsForwardNotifs = prefs.getBoolean("sms_forward_notifications_enabled", false)
        val smsForwardBatteryCharging = prefs.getBoolean("sms_forward_battery_charging_enabled", false)
        val smsForwardBatteryLow = prefs.getBoolean("sms_forward_battery_low_enabled", false)
        val smsTranslit = prefs.getBoolean("sms_transliteration_enabled", false)
        val debugMode = prefs.getBoolean("is_debug_mode_enabled", false)
        val enable2Fa = prefs.getBoolean("enable_2fa_interception", false)
        val subsJson = prefs.getString("user_subscriptions_json", null)
        val userSubs: Map<Long, UserSubscription> =
            if (!subsJson.isNullOrBlank()) {
                try {
                    json.decodeFromString<Map<Long, UserSubscription>>(subsJson)
                } catch (_: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }

        return BotConfig(
            telegramBotEnabled = telegramEnabled,
            botToken = token,
            allowedUserIds = allowedIds,
            allowedPhoneNumbers = allowedPhones,
            customApiRoot = customRoot,
            apiEndpoints = apiEndpointsList,
            endpointSelectionStrategy = strategy,
            pushGatewayUrl = pushUrl,
            pushSecretKey = pushSecret,
            smsPin = pin,
            smsPinRequiredAlways = pinRequiredAlways,
            replyUnauthorizedResponseEnabled = replyUnauthorized,
            storageDirectory = storageDir,
            connectionMode = mode,
            notificationForwardingApps = notifApps,
            notificationStopWords = stopWords,
            notificationDedupTimeoutSeconds = dedupTimeout,
            forwardRateLimitPerMinute = rateLimitPerMinute,
            googleServicesJson = fcmJson,
            fcmAppId = fcmAppId,
            fcmApiKey = fcmApiKey,
            fcmProjectId = fcmProjectId,
            fcmSenderId = fcmSenderId,
            tgForwardSmsEnabled = tgForwardSms,
            tgForwardCallsEnabled = tgForwardCalls,
            tgForwardNotificationsEnabled = tgForwardNotifs,
            tgForwardBatteryChargingEnabled = tgForwardBatteryCharging,
            tgForwardBatteryLowEnabled = tgForwardBatteryLow,
            smsBotEnabled = smsBotEnabled,
            smsForwardTargetNumbers = targetNumbers,
            smsForwardSmsEnabled = smsForwardSms,
            smsForwardMissedCallsEnabled = smsForwardMissedCalls,
            smsForwardNotificationsEnabled = smsForwardNotifs,
            smsForwardBatteryChargingEnabled = smsForwardBatteryCharging,
            smsForwardBatteryLowEnabled = smsForwardBatteryLow,
            smsTransliterationEnabled = smsTranslit,
            isDebugModeEnabled = debugMode,
            enable2FaInterception = enable2Fa,
            userSubscriptions = userSubs,
        )
    }

    override suspend fun updateConfig(update: (BotConfig) -> BotConfig) {
        val newConfig = update(_config.value)
        _config.value = newConfig

        val subsJson =
            try {
                json.encodeToString(newConfig.userSubscriptions)
            } catch (_: Exception) {
                "{}"
            }

        prefs
            .edit()
            .putBoolean("telegram_bot_enabled", newConfig.telegramBotEnabled)
            .putString("bot_token", secureStorage?.encrypt(newConfig.botToken) ?: newConfig.botToken)
            .putString("allowed_user_ids", newConfig.allowedUserIds.joinToString(","))
            .putString("allowed_phone_numbers", newConfig.allowedPhoneNumbers.joinToString(","))
            .putString("custom_api_root", newConfig.customApiRoot)
            .putString("api_endpoints", newConfig.apiEndpoints.joinToString(","))
            .putString("endpoint_selection_strategy", newConfig.endpointSelectionStrategy.name)
            .putString("push_gateway_url", newConfig.pushGatewayUrl)
            .putString("push_secret_key", secureStorage?.encrypt(newConfig.pushSecretKey) ?: newConfig.pushSecretKey)
            .putString("sms_pin", newConfig.smsPin)
            .putBoolean("sms_pin_required_always", newConfig.smsPinRequiredAlways)
            .putBoolean("reply_unauthorized_response_enabled", newConfig.replyUnauthorizedResponseEnabled)
            .putString("storage_directory", newConfig.storageDirectory)
            .putString("notification_forwarding_apps", newConfig.notificationForwardingApps.joinToString(","))
            .putString("notification_stop_words", JSONArray(newConfig.notificationStopWords).toString())
            .putInt("notification_dedup_timeout_seconds", newConfig.notificationDedupTimeoutSeconds)
            .putInt("forward_rate_limit_per_minute", newConfig.forwardRateLimitPerMinute)
            .putString("connection_mode", newConfig.connectionMode.name)
            .putString("google_services_json", newConfig.googleServicesJson)
            .putString("fcm_app_id", newConfig.fcmAppId)
            .putString("fcm_api_key", secureStorage?.encrypt(newConfig.fcmApiKey) ?: newConfig.fcmApiKey)
            .putString("fcm_project_id", newConfig.fcmProjectId)
            .putString("fcm_sender_id", newConfig.fcmSenderId)
            .putBoolean("tg_forward_sms_enabled", newConfig.tgForwardSmsEnabled)
            .putBoolean("tg_forward_calls_enabled", newConfig.tgForwardCallsEnabled)
            .putBoolean("tg_forward_notifications_enabled", newConfig.tgForwardNotificationsEnabled)
            .putBoolean("tg_forward_battery_charging_enabled", newConfig.tgForwardBatteryChargingEnabled)
            .putBoolean("tg_forward_battery_low_enabled", newConfig.tgForwardBatteryLowEnabled)
            .putBoolean("sms_bot_enabled", newConfig.smsBotEnabled)
            .putString("sms_forward_target_numbers", newConfig.smsForwardTargetNumbers.joinToString(","))
            .putBoolean("sms_forward_sms_enabled", newConfig.smsForwardSmsEnabled)
            .putBoolean("sms_forward_missed_calls_enabled", newConfig.smsForwardMissedCallsEnabled)
            .putBoolean("sms_forward_notifications_enabled", newConfig.smsForwardNotificationsEnabled)
            .putBoolean("sms_forward_battery_charging_enabled", newConfig.smsForwardBatteryChargingEnabled)
            .putBoolean("sms_forward_battery_low_enabled", newConfig.smsForwardBatteryLowEnabled)
            .putBoolean("sms_transliteration_enabled", newConfig.smsTransliterationEnabled)
            .putBoolean("is_debug_mode_enabled", newConfig.isDebugModeEnabled)
            .putBoolean("enable_2fa_interception", newConfig.enable2FaInterception)
            .putString("user_subscriptions_json", subsJson)
            .apply()
    }

    override fun isUserAllowed(userId: Long): Boolean {
        val allowed = _config.value.allowedUserIds
        return allowed.isEmpty() || allowed.contains(userId)
    }

    override fun isPhoneAllowed(phone: String): Boolean {
        val allowed = _config.value.allowedPhoneNumbers
        if (allowed.isEmpty()) return false
        val cleanPhone = phone.filter { it.isDigit() }
        if (cleanPhone.isBlank()) return false

        return allowed.any { allowedNum ->
            val cleanAllowed = allowedNum.filter { it.isDigit() }
            cleanPhone == cleanAllowed ||
                (cleanPhone.length >= 10 && cleanAllowed.length >= 10 && cleanPhone.takeLast(10) == cleanAllowed.takeLast(10))
        }
    }
}
