package com.remotebot.domain.repository

import com.remotebot.domain.model.AudioRecordingParams
import com.remotebot.domain.model.AudioStreamType
import com.remotebot.domain.model.GalleryPhoto
import com.remotebot.domain.model.MotionDetectedEvent
import com.remotebot.domain.model.MotionDetectionConfig
import com.remotebot.domain.model.MotionDetectionParams
import com.remotebot.domain.model.PhotoParams
import com.remotebot.domain.model.RingerMode
import com.remotebot.domain.model.ScreenRecordParams
import com.remotebot.domain.model.ScreenShotResult
import com.remotebot.domain.model.VideoParams
import com.remotebot.domain.model.VolumeInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface CameraRepository {
    suspend fun takePhoto(params: PhotoParams): Result<File>

    suspend fun recordVideo(
        params: VideoParams,
        onProgress: ((Int) -> Unit)? = null,
    ): Result<File>

    suspend fun stopVideoRecording(): Boolean

    suspend fun detectMotion(
        params: MotionDetectionParams,
        onMotionDetected: suspend (File) -> Unit,
    ): Result<Int>

    fun setMotionDetectorEnabled(
        enabled: Boolean,
        sensitivity: Int = 50,
        cooldownSeconds: Int = 5,
    )

    val motionDetectorConfig: StateFlow<MotionDetectionConfig>
    val motionEvents: Flow<MotionDetectedEvent>

    suspend fun getGalleryPhotos(limit: Int): List<GalleryPhoto>
}

interface AudioRepository {
    suspend fun recordAudio(params: AudioRecordingParams): Result<File>

    suspend fun stopAudioRecording(): Boolean

    suspend fun speakText(
        text: String,
        lang: String? = null,
    ): Result<Unit>

    suspend fun playAudio(file: File): Result<Unit>

    fun getVolumeLevels(): List<VolumeInfo>

    fun setVolume(
        stream: AudioStreamType,
        level: Int,
    ): Result<Unit>

    fun getRingerMode(): RingerMode

    fun setRingerMode(mode: RingerMode): Result<Unit>

    suspend fun startDeviceSearchAlarm(): Result<Unit>

    fun stopDeviceSearchAlarm(): Result<Unit>

    val soundDetectorConfig: StateFlow<com.remotebot.domain.model.SoundDetectorConfig>
    val soundEvents: Flow<com.remotebot.domain.model.SoundDetectedEvent>

    fun setSoundDetectorEnabled(
        enabled: Boolean,
        sensitivity: Int = 5,
    )
}

interface ScreenRepository {
    suspend fun takeScreenshot(): Result<ScreenShotResult>

    suspend fun recordScreen(params: ScreenRecordParams): Result<File>
}
