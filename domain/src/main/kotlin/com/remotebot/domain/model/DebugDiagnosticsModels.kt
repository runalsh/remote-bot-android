package com.remotebot.domain.model

import kotlinx.serialization.Serializable

/**
 * Тип источника пробуждения приложения
 */
@Serializable
enum class WakeupSource {
    PUSH_FCM,
    PUSH_HMS,
    SMS,
    PHONE_CALL,
    BATTERY_EVENT,
    BOOT_COMPLETED,
}

/**
 * Тип сетевого соединения / сервиса
 */
@Serializable
enum class ServerType {
    PUSH_GATEWAY,
    TELEGRAM_API,
}

/**
 * Исход сетевого подключения
 */
@Serializable
enum class ConnectionOutcome {
    SUCCESS,
    FAILURE,
    TIMEOUT,
}

/**
 * Канал авторизации
 */
@Serializable
enum class AuthChannel {
    TELEGRAM,
    SMS,
}

/**
 * Статистика подключений для конкретного сервера в заданном окне
 */
data class ServerConnectionStats(
    val serverId: String,
    val serverName: String,
    val serverType: ServerType,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val timeoutCount: Int = 0,
    val totalCount: Int = 0,
)

/**
 * Сводная статистика за конкретное временное окно (5м, 15м, 30м, 1ч, с запуска)
 */
data class WindowMetricsSummary(
    val windowName: String,
    val durationMs: Long?, // null для "с момента запуска"
    val wakeupsTotal: Int = 0,
    val wakeupsBySource: Map<WakeupSource, Int> = emptyMap(),
    val serverStats: List<ServerConnectionStats> = emptyList(),
    val tgAuthFailures: Int = 0,
    val smsAuthFailures: Int = 0,
)

/**
 * Полный снимок аналитики для отображения в DebugTab
 */
data class DebugMetricsSnapshot(
    val isTrackingEnabled: Boolean,
    val appUptimeMs: Long,
    val last5Min: WindowMetricsSummary,
    val last15Min: WindowMetricsSummary,
    val last30Min: WindowMetricsSummary,
    val last1Hour: WindowMetricsSummary,
    val sinceStart: WindowMetricsSummary,
)
