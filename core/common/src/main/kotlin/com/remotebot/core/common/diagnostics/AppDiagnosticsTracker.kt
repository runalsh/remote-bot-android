package com.remotebot.core.common.diagnostics

import com.remotebot.domain.model.AuthChannel
import com.remotebot.domain.model.ConnectionOutcome
import com.remotebot.domain.model.DebugMetricsSnapshot
import com.remotebot.domain.model.ServerConnectionStats
import com.remotebot.domain.model.ServerType
import com.remotebot.domain.model.WakeupSource
import com.remotebot.domain.model.WindowMetricsSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Global application runtime metrics tracker for DebugTab engineering menu.
 * Metrics are collected ONLY when debug mode is enabled (isDebugModeEnabled == true).
 * When debug mode is disabled, record* calls return immediately without allocations.
 */
object AppDiagnosticsTracker {
    private val appStartTimeMs = System.currentTimeMillis()

    @Volatile
    private var isTrackingActive: Boolean = false

    private data class WakeupEvent(
        val timestamp: Long,
        val source: WakeupSource,
    )

    private data class NetworkEvent(
        val timestamp: Long,
        val serverId: String,
        val serverName: String,
        val serverType: ServerType,
        val outcome: ConnectionOutcome,
    )

    private data class AuthFailureEvent(
        val timestamp: Long,
        val channel: AuthChannel,
        val senderIdentifier: String,
    )

    private val wakeupEvents = ConcurrentLinkedQueue<WakeupEvent>()
    private val networkEvents = ConcurrentLinkedQueue<NetworkEvent>()
    private val authFailureEvents = ConcurrentLinkedQueue<AuthFailureEvent>()

    private const val RETENTION_WINDOW_MS = 3600_000L // 1 час максимальная история

    private val _metricsSnapshot = MutableStateFlow(buildSnapshot())
    val metricsSnapshot: StateFlow<DebugMetricsSnapshot> = _metricsSnapshot.asStateFlow()

    /**
     * Set debug mode active status (invoked from UI / config repository)
     */
    fun setTrackingEnabled(enabled: Boolean) {
        isTrackingActive = enabled
        if (!enabled) {
            clearHistory()
        }
        updateSnapshot()
    }

    fun isTracking(): Boolean = isTrackingActive

    /**
     * Record application wakeup event
     */
    fun recordWakeup(source: WakeupSource) {
        if (!isTrackingActive) return
        val now = System.currentTimeMillis()
        wakeupEvents.add(WakeupEvent(now, source))
        pruneOldEvents(now)
        updateSnapshot()
    }

    /**
     * Record network request to Push Gateway or Telegram API
     */
    fun recordConnection(
        serverId: String,
        serverName: String,
        serverType: ServerType,
        outcome: ConnectionOutcome,
    ) {
        if (!isTrackingActive) return
        val now = System.currentTimeMillis()
        networkEvents.add(NetworkEvent(now, serverId, serverName, serverType, outcome))
        pruneOldEvents(now)
        updateSnapshot()
    }

    /**
     * Record failed authentication attempt
     */
    fun recordAuthFailure(
        channel: AuthChannel,
        senderIdentifier: String,
    ) {
        if (!isTrackingActive) return
        val now = System.currentTimeMillis()
        authFailureEvents.add(AuthFailureEvent(now, channel, senderIdentifier))
        pruneOldEvents(now)
        updateSnapshot()
    }

    /**
     * Clear event history
     */
    fun clearHistory() {
        wakeupEvents.clear()
        networkEvents.clear()
        authFailureEvents.clear()
        updateSnapshot()
    }

    /**
     * Remove events older than 1 hour to prevent memory leaks
     */
    private fun pruneOldEvents(now: Long) {
        val cutoff = now - RETENTION_WINDOW_MS
        while (wakeupEvents.peek()?.let { it.timestamp < cutoff } == true) {
            wakeupEvents.poll()
        }
        while (networkEvents.peek()?.let { it.timestamp < cutoff } == true) {
            networkEvents.poll()
        }
        while (authFailureEvents.peek()?.let { it.timestamp < cutoff } == true) {
            authFailureEvents.poll()
        }
    }

    fun updateSnapshot() {
        _metricsSnapshot.value = buildSnapshot()
    }

    private fun buildSnapshot(): DebugMetricsSnapshot {
        val now = System.currentTimeMillis()
        val uptime = now - appStartTimeMs

        val w5m = aggregateWindow("5 минут", 5 * 60_000L, now)
        val w15m = aggregateWindow("15 минут", 15 * 60_000L, now)
        val w30m = aggregateWindow("30 минут", 30 * 60_000L, now)
        val w1h = aggregateWindow("1 час", 60 * 60_000L, now)
        val wSinceStart = aggregateWindow("С запуска", null, now)

        return DebugMetricsSnapshot(
            isTrackingEnabled = isTrackingActive,
            appUptimeMs = uptime,
            last5Min = w5m,
            last15Min = w15m,
            last30Min = w30m,
            last1Hour = w1h,
            sinceStart = wSinceStart,
        )
    }

    private fun aggregateWindow(
        windowName: String,
        durationMs: Long?,
        now: Long,
    ): WindowMetricsSummary {
        val cutoff = if (durationMs != null) now - durationMs else 0L

        // Wakeups
        val filteredWakeups = wakeupEvents.filter { it.timestamp >= cutoff }
        val wakeupsTotal = filteredWakeups.size
        val wakeupsBySource = filteredWakeups.groupBy { it.source }.mapValues { it.value.size }

        // Network connections grouped by server
        val filteredNet = networkEvents.filter { it.timestamp >= cutoff }
        val serverGroups = filteredNet.groupBy { it.serverId }

        val serverStatsList =
            serverGroups
                .map { (srvId, events) ->
                    val first = events.first()
                    val successes = events.count { it.outcome == ConnectionOutcome.SUCCESS }
                    val failures = events.count { it.outcome == ConnectionOutcome.FAILURE }
                    val timeouts = events.count { it.outcome == ConnectionOutcome.TIMEOUT }
                    ServerConnectionStats(
                        serverId = srvId,
                        serverName = first.serverName,
                        serverType = first.serverType,
                        successCount = successes,
                        failureCount = failures,
                        timeoutCount = timeouts,
                        totalCount = events.size,
                    )
                }.sortedWith(compareBy({ it.serverType.name }, { it.serverName }))

        // Auth failures
        val filteredAuth = authFailureEvents.filter { it.timestamp >= cutoff }
        val tgFails = filteredAuth.count { it.channel == AuthChannel.TELEGRAM }
        val smsFails = filteredAuth.count { it.channel == AuthChannel.SMS }

        return WindowMetricsSummary(
            windowName = windowName,
            durationMs = durationMs,
            wakeupsTotal = wakeupsTotal,
            wakeupsBySource = wakeupsBySource,
            serverStats = serverStatsList,
            tgAuthFailures = tgFails,
            smsAuthFailures = smsFails,
        )
    }
}
