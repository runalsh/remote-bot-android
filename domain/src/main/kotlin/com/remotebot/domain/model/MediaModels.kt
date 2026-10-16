package com.remotebot.domain.model

import java.io.File

enum class CameraLens {
    FRONT,
    BACK,
}

enum class FlashMode {
    OFF,
    ON,
    AUTO,
}

data class PhotoParams(
    val lens: CameraLens = CameraLens.BACK,
    val flash: FlashMode = FlashMode.OFF,
    val rotationDegrees: Int = 0,
)

data class VideoParams(
    val lens: CameraLens = CameraLens.BACK,
    val durationSeconds: Int = 10,
)

data class MotionDetectionParams(
    val sensitivity: Int = 50, // 1..100
    val durationSeconds: Int = 30,
)

data class MotionDetectionConfig(
    val isEnabled: Boolean = false,
    val sensitivity: Int = 50, // 1..100 (10..100%)
    val cooldownSeconds: Int = 5, // 1, 2, 5, 10, 15, 30, 60
)

data class MotionDetectedEvent(
    val photoFile: File?,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class GalleryPhoto(
    val id: Long,
    val file: File,
    val dateAdded: Long,
    val sizeBytes: Long,
)

enum class AudioStreamType {
    RING,
    NOTIFICATION,
    ALARM,
    MEDIA,
    VOICE_CALL,
    SYSTEM,
}

enum class RingerMode {
    NORMAL,
    SILENT,
    VIBRATE,
}

data class VolumeInfo(
    val stream: AudioStreamType,
    val currentVolume: Int,
    val maxVolume: Int,
)

data class AudioRecordingParams(
    val durationSeconds: Int = 10,
)

data class ScreenShotResult(
    val file: File,
    val method: String,
)

data class ScreenRecordParams(
    val durationSeconds: Int = 10,
)

data class SoundDetectorConfig(
    val isEnabled: Boolean = false,
    val sensitivity: Int = 5,
)

data class SoundDetectedEvent(
    val decibels: Int,
    val timestampMs: Long = System.currentTimeMillis(),
)
