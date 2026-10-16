package com.remotebot.core.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class PushStats(
    val deliveredToday: Int = 0,
    val failedToday: Int = 0,
    val totalDelivered: Long = 0L,
    val totalFailed: Long = 0L,
    val lastDeliveryTimeMs: Long = 0L,
    val avgDeliveryLatencyMs: Long = 0L,
    val lastError: String? = null,
)

/**
 * Синглтон для трекинга метрик Push-уведомлений и шлюза.
 */
object PushStatsTracker {
    private val _stats = MutableStateFlow(PushStats())
    val stats: StateFlow<PushStats> = _stats.asStateFlow()

    private val deliveredCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)
    private val totalDeliveredCount = AtomicLong(0)
    private val totalFailedCount = AtomicLong(0)
    private val totalLatencyMs = AtomicLong(0)
    private val latencySamplesCount = AtomicLong(0)

    fun recordDelivery(latencyMs: Long? = null) {
        val today = deliveredCount.incrementAndGet()
        val total = totalDeliveredCount.incrementAndGet()
        val now = System.currentTimeMillis()

        var avg = _stats.value.avgDeliveryLatencyMs
        if (latencyMs != null && latencyMs > 0) {
            val totalLat = totalLatencyMs.addAndGet(latencyMs)
            val samples = latencySamplesCount.incrementAndGet()
            avg = totalLat / samples
        }

        _stats.value =
            _stats.value.copy(
                deliveredToday = today,
                totalDelivered = total,
                lastDeliveryTimeMs = now,
                avgDeliveryLatencyMs = avg,
            )
    }

    fun recordFailure(error: String) {
        val today = failedCount.incrementAndGet()
        val total = totalFailedCount.incrementAndGet()

        _stats.value =
            _stats.value.copy(
                failedToday = today,
                totalFailed = total,
                lastError = error,
            )
    }

    fun resetDailyStats() {
        deliveredCount.set(0)
        failedCount.set(0)
        _stats.value =
            _stats.value.copy(
                deliveredToday = 0,
                failedToday = 0,
            )
    }
}
