package com.remotebot.domain.repository

import com.remotebot.domain.model.LocationData
import com.remotebot.domain.model.ShakeDetectorConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LocationRepository {
    suspend fun getCurrentLocation(timeoutSeconds: Int = 30): Result<LocationData>

    suspend fun setGpsEnabled(enabled: Boolean): Result<Unit>

    fun isGpsEnabled(): Boolean

    // Live / periodic location tracking
    val liveTrackingStatus: StateFlow<com.remotebot.domain.model.LiveTrackingStatus>
    val liveLocationUpdates: Flow<LocationData>

    fun startLiveTracking(intervalSeconds: Long): Result<Unit>

    fun stopLiveTracking(): Result<Unit>

    fun isLiveTrackingActive(): Boolean

    val shakeDetectorConfig: StateFlow<ShakeDetectorConfig>
    val shakeEvents: Flow<Unit>

    fun setShakeDetectorEnabled(
        enabled: Boolean,
        sensitivity: Float = 13.0f,
        cooldownSeconds: Int = 10,
    )

    fun simulateShakeEvent()
}
