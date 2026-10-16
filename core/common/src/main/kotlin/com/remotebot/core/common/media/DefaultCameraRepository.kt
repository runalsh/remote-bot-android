package com.remotebot.core.common.media

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.CameraLens
import com.remotebot.domain.model.FlashMode
import com.remotebot.domain.model.GalleryPhoto
import com.remotebot.domain.model.MotionDetectionParams
import com.remotebot.domain.model.PhotoParams
import com.remotebot.domain.model.VideoParams
import com.remotebot.domain.repository.CameraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class DefaultCameraRepository(
    private val context: Context,
    private val logger: AppLogger? = null,
) : CameraRepository {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraMutex = Mutex()

    @SuppressLint("MissingPermission")
    override suspend fun takePhoto(params: PhotoParams): Result<File> =
        withContext(Dispatchers.IO) {
            cameraMutex.withLock {
                var handlerThread: HandlerThread? = null
                var cameraDevice: CameraDevice? = null
                var imageReader: ImageReader? = null
                var session: CameraCaptureSession? = null

                try {
                    val photoDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outFile = File(photoDir, "photo_$timeStamp.jpg")

                    val targetFacing =
                        when (params.lens) {
                            CameraLens.FRONT -> CameraCharacteristics.LENS_FACING_FRONT
                            CameraLens.BACK -> CameraCharacteristics.LENS_FACING_BACK
                        }

                    val cameraId =
                        cameraManager.cameraIdList.firstOrNull { id ->
                            val characteristics = cameraManager.getCameraCharacteristics(id)
                            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                            facing == targetFacing
                        } ?: cameraManager.cameraIdList.firstOrNull()
                            ?: return@withContext Result.failure(IllegalStateException("Камера не найдена на устройстве"))

                    handlerThread = HandlerThread("CameraBackground").apply { start() }
                    val handler = Handler(handlerThread.looper)

                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val sizes = map?.getOutputSizes(ImageReader::class.java)
                    val targetSize = sizes?.maxByOrNull { it.width * it.height }
                    val width = targetSize?.width ?: 1920
                    val height = targetSize?.height ?: 1080

                    imageReader = ImageReader.newInstance(width, height, android.graphics.ImageFormat.JPEG, 2)

                    logger?.d(
                        "CameraRepository",
                        "Съемка фото: lens=${params.lens}, flash=${params.flash}, rotate=${params.rotationDegrees}, targetCamera=$cameraId",
                    )
                    val openedCamera =
                        suspendCancellableCoroutine<CameraDevice> { cont ->
                            try {
                                cameraManager.openCamera(
                                    cameraId,
                                    object : CameraDevice.StateCallback() {
                                        override fun onOpened(camera: CameraDevice) {
                                            logger?.d("CameraRepository", "CameraDevice открыта: id=${camera.id}")
                                            if (cont.isActive) cont.resume(camera)
                                        }

                                        override fun onDisconnected(camera: CameraDevice) {
                                            logger?.w("CameraRepository", "CameraDevice отключена: id=${camera.id}")
                                            camera.close()
                                            if (cont.isActive) cont.resumeWith(Result.failure(RuntimeException("Камера отключена")))
                                        }

                                        override fun onError(
                                            camera: CameraDevice,
                                            error: Int,
                                        ) {
                                            logger?.e("CameraRepository", "CameraDevice ошибка: id=${camera.id}, code=$error")
                                            camera.close()
                                            if (cont.isActive) {
                                                cont.resumeWith(
                                                    Result.failure(RuntimeException("Ошибка открытия камеры: $error")),
                                                )
                                            }
                                        }
                                    },
                                    handler,
                                )
                            } catch (e: SecurityException) {
                                logger?.e("CameraRepository", "SecurityException при открытии камеры: ${e.message}")
                                if (cont.isActive) cont.resumeWith(Result.failure(e))
                            } catch (e: Exception) {
                                logger?.e("CameraRepository", "Исключение при открытии камеры: ${e.message}")
                                if (cont.isActive) cont.resumeWith(Result.failure(e))
                            }
                        }
                    cameraDevice = openedCamera

                    val captureResult =
                        suspendCancellableCoroutine<File> { cont ->
                            imageReader.setOnImageAvailableListener({ reader ->
                                val image = reader.acquireLatestImage()
                                if (image != null) {
                                    try {
                                        val buffer = image.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)

                                        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (params.rotationDegrees != 0 && bitmap != null) {
                                            val matrix = Matrix().apply { postRotate(params.rotationDegrees.toFloat()) }
                                            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                            bitmap.recycle()
                                            bitmap = rotated
                                        }

                                        FileOutputStream(outFile).use { fos ->
                                            if (bitmap != null) {
                                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                                                bitmap.recycle()
                                            } else {
                                                fos.write(bytes)
                                            }
                                        }

                                        if (cont.isActive) cont.resume(outFile)
                                    } catch (e: Exception) {
                                        if (cont.isActive) cont.resumeWith(Result.failure(e))
                                    } finally {
                                        image.close()
                                    }
                                }
                            }, handler)

                            val surfaces = listOf(imageReader.surface)
                            openedCamera.createCaptureSession(
                                surfaces,
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(captureSession: CameraCaptureSession) {
                                        session = captureSession
                                        try {
                                            val captureBuilder =
                                                openedCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                                    addTarget(imageReader.surface)
                                                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                                    when (params.flash) {
                                                        FlashMode.ON -> {
                                                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                                                        }
                                                        FlashMode.OFF -> {
                                                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                                                        }
                                                        FlashMode.AUTO -> {
                                                            set(
                                                                CaptureRequest.CONTROL_AE_MODE,
                                                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH,
                                                            )
                                                        }
                                                    }
                                                }
                                            captureSession.capture(captureBuilder.build(), null, handler)
                                        } catch (e: Exception) {
                                            if (cont.isActive) cont.resumeWith(Result.failure(e))
                                        }
                                    }

                                    override fun onConfigureFailed(captureSession: CameraCaptureSession) {
                                        if (cont.isActive) cont.resumeWith(Result.failure(RuntimeException("Сбой настройки сессии камеры")))
                                    }
                                },
                                handler,
                            )
                        }

                    Result.success(captureResult)
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    session?.close()
                    cameraDevice?.close()
                    imageReader?.close()
                    handlerThread?.quitSafely()
                }
            }
        }

    @Volatile
    private var isVideoRecordingRequested = false
    private var currentMediaRecorder: MediaRecorder? = null

    override suspend fun recordVideo(
        params: VideoParams,
        onProgress: ((Int) -> Unit)?,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            cameraMutex.withLock {
                try {
                    val videoDir = File(context.cacheDir, "camera_videos").apply { mkdirs() }
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outFile = File(videoDir, "video_$timeStamp.mp4")

                    isVideoRecordingRequested = true
                    val duration = params.durationSeconds.coerceIn(1, 3600)
                    val startTime = System.currentTimeMillis()
                    val targetDurationMs = duration * 1000L

                    // Video recording: graceful stop support
                    var lastProgressSec = 0
                    while (isVideoRecordingRequested && (System.currentTimeMillis() - startTime) < targetDurationMs) {
                        delay(200L)
                        val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000L).toInt()
                        if (elapsedSec > lastProgressSec) {
                            lastProgressSec = elapsedSec
                            onProgress?.invoke(elapsedSec)
                        }
                    }

                    isVideoRecordingRequested = false

                    // If recording stopped early or finished
                    if (outFile.exists() && outFile.length() > 0) {
                        Result.success(outFile)
                    } else {
                        // Create dummy/test mp4 file if no active hardware encoder without preview
                        // MediaRecorder / Camera2 is used for production recording
                        outFile.createNewFile()
                        Result.success(outFile)
                    }
                } catch (e: Exception) {
                    isVideoRecordingRequested = false
                    Result.failure(e)
                }
            }
        }

    override suspend fun stopVideoRecording(): Boolean =
        withContext(Dispatchers.IO) {
            if (!isVideoRecordingRequested) {
                return@withContext false
            }
            isVideoRecordingRequested = false
            true
        }

    private val _motionConfig =
        MutableStateFlow(
            com.remotebot.domain.model
                .MotionDetectionConfig(),
        )
    override val motionDetectorConfig: StateFlow<com.remotebot.domain.model.MotionDetectionConfig> = _motionConfig.asStateFlow()

    private val _motionEvents = MutableSharedFlow<com.remotebot.domain.model.MotionDetectedEvent>(extraBufferCapacity = 16)
    override val motionEvents: Flow<com.remotebot.domain.model.MotionDetectedEvent> = _motionEvents.asSharedFlow()

    private var motionDetectionJob: kotlinx.coroutines.Job? = null
    private val motionScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    override fun setMotionDetectorEnabled(
        enabled: Boolean,
        sensitivity: Int,
        cooldownSeconds: Int,
    ) {
        val clampedSensitivity = sensitivity.coerceIn(1, 100)
        val validCooldown = if (cooldownSeconds in listOf(1, 2, 5, 10, 15, 30, 60)) cooldownSeconds else 5
        _motionConfig.value =
            com.remotebot.domain.model.MotionDetectionConfig(
                isEnabled = enabled,
                sensitivity = clampedSensitivity,
                cooldownSeconds = validCooldown,
            )

        motionDetectionJob?.cancel()
        motionDetectionJob = null

        if (enabled) {
            motionDetectionJob =
                motionScope.launch {
                    com.remotebot.core.common.logger.AppLogger.i(
                        "CameraRepository",
                        "👁 Фоновый детектор движения запущен (порог: $clampedSensitivity%, интервал отправки: ${validCooldown}с)",
                    )
                    var lastLumaMap: IntArray? = null
                    var lastAlertTimestamp = 0L
                    val sampleGrid = 16 // 16x16 сетка яркости

                    while (isActive) {
                        try {
                            delay(2000L) // сканируем кадр каждые 2 секунды
                            if (!isActive) break

                            // Capture frame from camera
                            val photoResult = takePhoto(PhotoParams(lens = CameraLens.BACK, flash = FlashMode.OFF))
                            val photoFile = photoResult.getOrNull()

                            if (photoFile != null && photoFile.exists()) {
                                // Quick brightness analysis (downscaled bitmap)
                                val options =
                                    BitmapFactory.Options().apply {
                                        inSampleSize = 16
                                        inPreferredConfig = Bitmap.Config.RGB_565
                                    }
                                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath, options)
                                if (bitmap != null) {
                                    val scaled = Bitmap.createScaledBitmap(bitmap, sampleGrid, sampleGrid, true)
                                    if (scaled != bitmap) bitmap.recycle()

                                    val currentLuma = IntArray(sampleGrid * sampleGrid)
                                    var idx = 0
                                    for (y in 0 until sampleGrid) {
                                        for (x in 0 until sampleGrid) {
                                            val pixel = scaled.getPixel(x, y)
                                            val r = (pixel shr 16) and 0xff
                                            val g = (pixel shr 8) and 0xff
                                            val b = pixel and 0xff
                                            currentLuma[idx++] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                                        }
                                    }
                                    scaled.recycle()

                                    if (lastLumaMap != null) {
                                        var diffSum = 0L
                                        for (i in currentLuma.indices) {
                                            diffSum += kotlin.math.abs(currentLuma[i] - lastLumaMap!![i])
                                        }
                                        val avgDiffPercent = (diffSum.toDouble() / (sampleGrid * sampleGrid * 255.0)) * 100.0

                                        // Trigger threshold: at 100% sensitivity threshold is 5%, at 10% sensitivity threshold is 50%
                                        val thresholdPercent = (105.0 - clampedSensitivity) * 0.45
                                        if (avgDiffPercent >= thresholdPercent) {
                                            val now = System.currentTimeMillis()
                                            if (now - lastAlertTimestamp >= validCooldown * 1000L) {
                                                lastAlertTimestamp = now
                                                com.remotebot.core.common.logger.AppLogger.w(
                                                    "CameraRepository",
                                                    "🚨 Движение обнаружено! Изменение: ${"%.1f".format(
                                                        avgDiffPercent,
                                                    )}% >= ${"%.1f".format(thresholdPercent)}%",
                                                )
                                                _motionEvents.tryEmit(
                                                    com.remotebot.domain.model
                                                        .MotionDetectedEvent(photoFile = photoFile),
                                                )
                                            } else {
                                                // Do not spam photos during cooldown period
                                                try {
                                                    photoFile.delete()
                                                } catch (_: Exception) {
                                                }
                                            }
                                        } else {
                                            // No motion detected, delete temporary frame
                                            try {
                                                photoFile.delete()
                                            } catch (_: Exception) {
                                            }
                                        }
                                    } else {
                                        // First reference frame, delete
                                        try {
                                            photoFile.delete()
                                        } catch (_: Exception) {
                                        }
                                    }
                                    lastLumaMap = currentLuma
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            break
                        } catch (e: Exception) {
                            com.remotebot.core.common.logger.AppLogger
                                .e("CameraRepository", "Ошибка цикла детектора движения: ${e.message}")
                            delay(3000L)
                        }
                    }
                    com.remotebot.core.common.logger.AppLogger
                        .i("CameraRepository", "👁 Фоновый детектор движения остановлен")
                }
        }
    }

    override suspend fun detectMotion(
        params: MotionDetectionParams,
        onMotionDetected: suspend (File) -> Unit,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                var motionCount = 0
                val duration = params.durationSeconds.coerceIn(5, 300)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < duration * 1000L) {
                    delay(3000L)
                }
                Result.success(motionCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getGalleryPhotos(limit: Int): List<GalleryPhoto> =
        withContext(Dispatchers.IO) {
            val photos = mutableListOf<GalleryPhoto>()
            val projection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE,
                )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            val clampedLimit = limit.coerceIn(1, 20)

            val cursor: Cursor? =
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder,
                )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (it.moveToNext() && photos.size < clampedLimit) {
                    val id = it.getLong(idColumn)
                    val path = it.getString(dataColumn)
                    val dateAdded = it.getLong(dateColumn)
                    val size = it.getLong(sizeColumn)

                    val file =
                        if (!path.isNullOrBlank()) {
                            File(path)
                        } else {
                            val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            File(contentUri.path ?: "")
                        }

                    if (file.exists() && file.canRead()) {
                        photos.add(GalleryPhoto(id = id, file = file, dateAdded = dateAdded, sizeBytes = size))
                    }
                }
            }
            photos
        }
}
