package com.remotebot.domain.model

import kotlinx.serialization.Serializable

/**
 * Application wakeup source type
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
 * Network connection / service type
 */
@Serializable
enum class ServerType {
    PUSH_GATEWAY,
    TELEGRAM_API,
}

/**
 * Network connection outcome
 */
@Serializable
enum class ConnectionOutcome {
    SUCCESS,
    FAILURE,
    TIMEOUT,
}

/**
 * Authorization channel
 */
@Serializable
enum class AuthChannel {
    TELEGRAM,
    SMS,
}

/**
 * Connection statistics for a specific server within a given window
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
 * Summary statistics for a specific time window (5m, 15m, 30m, 1h, since startup)
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
 * Complete analytics snapshot for display in DebugTab
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
