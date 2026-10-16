package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
    val altitudeMeters: Double = 0.0,
    val speedMps: Float = 0f,
    val address: String? = null,
    val provider: String = "gps",
    val timestampMs: Long = System.currentTimeMillis(),
    val isDirectGps: Boolean = false,
    val determinationMethod: String = "",
    val satellitesUsed: Int? = null,
    val satellitesVisible: Int? = null,
    val signalStrengthLevel: String? = null,
    val cn0DbHz: Float? = null,
    val trackingSequenceNumber: Long? = null,
)

@Serializable
data class LiveTrackingStatus(
    val isActive: Boolean = false,
    val intervalSeconds: Long = 60L,
    val startedAtMs: Long = 0L,
    val pointsSent: Int = 0,
    val lastLocation: LocationData? = null,
)

@Serializable
data class ShakeDetectorConfig(
    val isEnabled: Boolean = false,
    val sensitivity: Float = 13.0f, // m/s^2 threshold
    val cooldownSeconds: Int = 10,
)
