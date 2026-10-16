package com.remotebot.core.common.media

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.AudioRecordingParams
import com.remotebot.domain.model.AudioStreamType
import com.remotebot.domain.model.RingerMode
import com.remotebot.domain.model.SoundDetectedEvent
import com.remotebot.domain.model.SoundDetectorConfig
import com.remotebot.domain.model.VolumeInfo
import com.remotebot.domain.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class DefaultAudioRepository(
    private val context: Context,
    private val logger: AppLogger? = null,
) : AudioRepository {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var currentRecorder: MediaRecorder? = null
    private var currentRecordFile: File? = null
    private val recordMutex = Mutex()

    private var alarmPlayer: MediaPlayer? = null
    private var currentPlayer: MediaPlayer? = null
    private var ttsEngine: TextToSpeech? = null
    private var isTtsInitialized = false
    private val ttsInitMutex = Mutex()

    @Volatile
    private var isRecordingRequested = false

    override suspend fun recordAudio(params: AudioRecordingParams): Result<File> =
        withContext(Dispatchers.IO) {
            recordMutex.withLock {
                try {
                    stopAudioRecordingInternal()

                    val audioDir = File(context.cacheDir, "audio_records").apply { mkdirs() }
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outFile = File(audioDir, "rec_$timeStamp.m4a")

                    logger?.d("AudioRepository", "Подготовка MediaRecorder: AAC/44100Hz, target=${outFile.name}")

                    val recorder =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            MediaRecorder(context)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaRecorder()
                        }

                    recorder.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(128000)
                        setAudioSamplingRate(44100)
                        setOutputFile(outFile.absolutePath)
                        prepare()
                        start()
                    }

                    currentRecorder = recorder
                    currentRecordFile = outFile
                    isRecordingRequested = true

                    val duration = params.durationSeconds.coerceIn(1, 3600)
                    logger?.i("AudioRepository", "Старт аудиозаписи на $duration сек")
                    val startTime = System.currentTimeMillis()
                    val targetDurationMs = duration * 1000L

                    while (isRecordingRequested && (System.currentTimeMillis() - startTime) < targetDurationMs) {
                        delay(200L)
                    }

                    stopAudioRecordingInternal()

                    if (outFile.exists() && outFile.length() > 0) {
                        logger?.i("AudioRepository", "Аудиозапись успешно завершена: ${outFile.length()} байт")
                        Result.success(outFile)
                    } else {
                        logger?.w("AudioRepository", "Файл аудиозаписи пуст или не был создан")
                        Result.failure(RuntimeException("Файл аудиозаписи пуст или не был создан"))
                    }
                } catch (e: Exception) {
                    logger?.e("AudioRepository", "Ошибка при аудиозаписи: ${e.message}", e)
                    stopAudioRecordingInternal()
                    Result.failure(e)
                }
            }
        }

    override suspend fun stopAudioRecording(): Boolean =
        withContext(Dispatchers.IO) {
            if (!isRecordingRequested && currentRecorder == null) {
                return@withContext false
            }
            isRecordingRequested = false
            // Allow loop to finish recording gracefully
            true
        }

    private fun stopAudioRecordingInternal(): Boolean {
        isRecordingRequested = false
        return try {
            currentRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                release()
            }
            currentRecorder = null
            true
        } catch (_: Exception) {
            currentRecorder = null
            false
        }
    }

    override suspend fun speakText(
        text: String,
        lang: String?,
    ): Result<Unit> =
        withContext(Dispatchers.Main) {
            try {
                ensureTtsInitialized()
                val tts = ttsEngine ?: return@withContext Result.failure(RuntimeException("TTS движок недоступен"))

                if (!lang.isNullOrBlank()) {
                    val locale = Locale.forLanguageTag(lang)
                    tts.language = locale
                } else {
                    tts.language = Locale.getDefault()
                }

                suspendCancellableCoroutine { continuation ->
                    val utteranceId = UUID.randomUUID().toString()
                    tts.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}

                            override fun onDone(id: String?) {
                                if (id == utteranceId && continuation.isActive) {
                                    continuation.resume(Result.success(Unit))
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(id: String?) {
                                if (id == utteranceId && continuation.isActive) {
                                    continuation.resume(Result.failure(RuntimeException("Ошибка синтеза речи TTS")))
                                }
                            }
                        },
                    )

                    val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                    if (result == TextToSpeech.ERROR) {
                        logger?.w("AudioRepository", "Ошибка запуска синтеза речи TTS")
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(RuntimeException("Не удалось воспроизвести текст через TTS")))
                        }
                    } else {
                        logger?.i("AudioRepository", "Запущен синтез речи (${lang ?: "system"}): \"${text.take(30)}\"")
                    }
                }
            } catch (e: Exception) {
                logger?.e("AudioRepository", "Ошибка TTS: ${e.message}")
                Result.failure(e)
            }
        }

    private suspend fun ensureTtsInitialized() {
        if (isTtsInitialized && ttsEngine != null) return
        ttsInitMutex.withLock {
            if (isTtsInitialized && ttsEngine != null) return
            suspendCancellableCoroutine { cont ->
                ttsEngine =
                    TextToSpeech(context) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            isTtsInitialized = true
                            logger?.d("AudioRepository", "TTS Engine успешно инициализирован")
                        } else {
                            logger?.w("AudioRepository", "Сбой инициализации TTS: status=$status")
                        }
                        if (cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
            }
        }
    }

    override suspend fun playAudio(file: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                logger?.d("AudioRepository", "Воспроизведение аудио: ${file.name}")
                currentPlayer?.release()
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                player.setDataSource(file.absolutePath)
                player.prepare()

                suspendCancellableCoroutine { cont ->
                    player.setOnCompletionListener {
                        logger?.d("AudioRepository", "Воспроизведение завершено: ${file.name}")
                        player.release()
                        currentPlayer = null
                        if (cont.isActive) cont.resume(Result.success(Unit))
                    }
                    player.setOnErrorListener { _, what, extra ->
                        logger?.w("AudioRepository", "MediaPlayer ошибка: what=$what, extra=$extra")
                        player.release()
                        currentPlayer = null
                        if (cont.isActive) cont.resume(Result.failure(RuntimeException("MediaPlayer error: what=$what, extra=$extra")))
                        true
                    }
                    player.start()
                    currentPlayer = player
                }
            } catch (e: Exception) {
                logger?.e("AudioRepository", "Ошибка воспроизведения: ${e.message}")
                Result.failure(e)
            }
        }

    override fun getVolumeLevels(): List<VolumeInfo> =
        AudioStreamType.entries.map { stream ->
            val androidStream = mapStreamTypeToAndroid(stream)
            val current = audioManager.getStreamVolume(androidStream)
            val max = audioManager.getStreamMaxVolume(androidStream)
            VolumeInfo(stream = stream, currentVolume = current, maxVolume = max)
        }

    override fun setVolume(
        stream: AudioStreamType,
        level: Int,
    ): Result<Unit> =
        try {
            val androidStream = mapStreamTypeToAndroid(stream)
            val max = audioManager.getStreamMaxVolume(androidStream)
            val clamped = level.coerceIn(0, max)
            audioManager.setStreamVolume(androidStream, clamped, 0)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override fun getRingerMode(): RingerMode =
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }

    override fun setRingerMode(mode: RingerMode): Result<Unit> =
        try {
            val target =
                when (mode) {
                    RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                    RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
                    RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                }
            audioManager.ringerMode = target
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override suspend fun startDeviceSearchAlarm(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                stopDeviceSearchAlarm()

                // Set alarm volume to maximum
                val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)

                val alertUri: Uri =
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val player =
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .build(),
                        )
                        setDataSource(context, alertUri)
                        isLooping = true
                        prepare()
                        start()
                    }
                alarmPlayer = player
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun stopDeviceSearchAlarm(): Result<Unit> =
        try {
            alarmPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            alarmPlayer = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    private val detectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _soundConfig = MutableStateFlow(SoundDetectorConfig())
    override val soundDetectorConfig: StateFlow<SoundDetectorConfig> = _soundConfig.asStateFlow()

    private val _soundEvents = MutableSharedFlow<SoundDetectedEvent>(extraBufferCapacity = 16)
    override val soundEvents: Flow<SoundDetectedEvent> = _soundEvents.asSharedFlow()

    private var soundDetectorJob: Job? = null
    private var lastSoundEventTimestamp = 0L

    @SuppressLint("MissingPermission")
    override fun setSoundDetectorEnabled(
        enabled: Boolean,
        sensitivity: Int,
    ) {
        val lvl = sensitivity.coerceIn(1, 10)
        _soundConfig.value = SoundDetectorConfig(isEnabled = enabled, sensitivity = lvl)

        soundDetectorJob?.cancel()
        soundDetectorJob = null

        if (!enabled) {
            AppLogger.d("DefaultAudioRepository", "Детектор звука отключен")
            return
        }

        val thresholdDb = 90 - (lvl * 5)
        AppLogger.i("DefaultAudioRepository", "Запуск детектора звука: уровень $lvl/10 (порог: $thresholdDb dB)")

        soundDetectorJob =
            detectorScope.launch {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufSize = maxOf(minBufSize, 4096)

                var audioRecord: AudioRecord? = null
                try {
                    audioRecord =
                        AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            bufSize,
                        )

                    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        AppLogger.w("DefaultAudioRepository", "Не удалось инициализировать AudioRecord для детектора звука")
                        return@launch
                    }

                    audioRecord.startRecording()
                    val buffer = ShortArray(bufSize / 2)

                    while (isActive && _soundConfig.value.isEnabled) {
                        // Yield microphone if audio recording to file / voice message is active
                        if (currentRecorder != null) {
                            delay(500)
                            continue
                        }

                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var sumSquares = 0.0
                            for (i in 0 until read) {
                                val sample = buffer[i].toDouble()
                                sumSquares += sample * sample
                            }
                            val rms = kotlin.math.sqrt(sumSquares / read)
                            val db = if (rms > 1.0) (20 * kotlin.math.log10(rms)).toInt() else 0

                            if (db >= thresholdDb) {
                                val now = System.currentTimeMillis()
                                if (now - lastSoundEventTimestamp >= 10000L) { // 10 сек кулдаун
                                    lastSoundEventTimestamp = now
                                    AppLogger.i("DefaultAudioRepository", "🔊 Зафиксирован шум: $db dB (порог: $thresholdDb dB)")
                                    _soundEvents.emit(SoundDetectedEvent(decibels = db, timestampMs = now))
                                }
                            }
                        }
                        delay(120)
                    }
                } catch (e: Exception) {
                    AppLogger.e("DefaultAudioRepository", "Ошибка детектора звука: ${e.message}", e)
                } finally {
                    try {
                        audioRecord?.stop()
                        audioRecord?.release()
                    } catch (_: Exception) {
                    }
                }
            }
    }

    private fun mapStreamTypeToAndroid(stream: AudioStreamType): Int =
        when (stream) {
            AudioStreamType.RING -> AudioManager.STREAM_RING
            AudioStreamType.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            AudioStreamType.ALARM -> AudioManager.STREAM_ALARM
            AudioStreamType.MEDIA -> AudioManager.STREAM_MUSIC
            AudioStreamType.VOICE_CALL -> AudioManager.STREAM_VOICE_CALL
            AudioStreamType.SYSTEM -> AudioManager.STREAM_SYSTEM
        }
}
