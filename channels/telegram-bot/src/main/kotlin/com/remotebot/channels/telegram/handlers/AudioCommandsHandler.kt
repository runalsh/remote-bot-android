package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.tracker.ActiveOperationsTracker
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.AudioRecordingParams
import com.remotebot.domain.model.AudioStreamType
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.RingerMode
import com.remotebot.domain.repository.AudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioCommandsHandler(
    private val audioRepository: AudioRepository,
    private val logger: AppLogger,
    private val operationsTracker: ActiveOperationsTracker? = null,
) : CommandHandler {
    override val commandNames: List<String> =
        listOf(
            "rec",
            "rec_send",
            "rec_file",
            "tts",
            "playback",
            "player",
            "volume",
            "ring_mode",
            "device_search",
            "sound_detector",
        )

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult =
        withContext(Dispatchers.IO) {
            val norm = command.name.lowercase().removePrefix("/")
            logger.i("AudioHandler", "Аудио-команда: /$norm (args=${command.args})")
            when (norm) {
                "rec", "rec_send" -> handleAudioRec(command.args, defaultAsFile = false)
                "rec_file" -> handleAudioRec(command.args, defaultAsFile = true)
                "tts" -> handleTts(command.args)
                "playback", "player" -> handlePlayer(command.args)
                "volume" -> handleVolume(command.args)
                "ring_mode" -> handleRingMode(command.args)
                "device_search" -> handleDeviceSearch(command.args)
                "sound_detector" -> handleSoundDetector(command.args)
                else -> CommandResult.Error("Неизвестная аудио-команда: /$norm")
            }
        }

    private suspend fun handleAudioRec(
        args: List<String>,
        defaultAsFile: Boolean,
    ): CommandResult {
        val firstArg = args.firstOrNull()?.lowercase()

        // 1. Stop recording
        if (firstArg in listOf("stop", "off", "cancel")) {
            val stopped = audioRepository.stopAudioRecording()
            operationsTracker?.list()?.filter { it.id.startsWith("audio_rec_") }?.forEach {
                operationsTracker.unregister(it.id)
            }
            return if (stopped) {
                CommandResult.Text("⏹ Запись микрофона остановлена. Сохраненное аудио отправляется...")
            } else {
                CommandResult.Text(
                    "⚠️ Нет активной записи микрофона для остановки.",
                    keyboard = buildRecKeyboard(defaultAsFile),
                )
            }
        }

        // 2. Interactive panel without arguments: /rec or /rec panel
        if (args.isEmpty() || firstArg == "panel") {
            val activeOp = operationsTracker?.list()?.firstOrNull { it.id.startsWith("audio_rec_") }
            val statusText =
                if (activeOp != null) {
                    "🔴 <b>Идёт запись:</b> ${activeOp.name}"
                } else {
                    "⏹ <b>В ожидании</b> (нет активной записи)"
                }

            return CommandResult.Text(
                """
                🎙 <b>Скрытая аудиозапись с микрофона (/rec):</b>
                
                • Статус: $statusText
                • Выберите длительность записи кнопками ниже (до 1 часа) или запустите длительную запись с возможностью остановки в любой момент:
                """.trimIndent(),
                keyboard = buildRecKeyboard(defaultAsFile, isRecording = activeOp != null),
            )
        }

        var duration = 10
        var asFile = defaultAsFile

        for (arg in args) {
            when (arg.lowercase()) {
                "file", "doc", "f" -> asFile = true
                "voice", "v" -> asFile = false
                "max" -> duration = 3600
                else -> arg.toIntOrNull()?.let { duration = it.coerceIn(1, 3600) }
            }
        }

        val opId = "audio_rec_${System.currentTimeMillis()}"
        val readableDur = formatAudioDurationReadable(duration)
        operationsTracker?.register(
            id = opId,
            name = "🎙 Аудиозапись микрофона ($readableDur)",
            description = if (asFile) "В виде файла (до $readableDur)" else "Голосовое сообщение (до $readableDur)",
            onCancel = {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    audioRepository.stopAudioRecording()
                }
            },
        )

        val result =
            try {
                audioRepository.recordAudio(AudioRecordingParams(durationSeconds = duration))
            } finally {
                operationsTracker?.unregister(opId)
            }
        return result.fold(
            onSuccess = { file ->
                val actualSeconds = (file.length() / (128000 / 8)).coerceIn(1, duration.toLong()).toInt()
                if (asFile) {
                    CommandResult.Audio(
                        filePath = file.absolutePath,
                        caption = "🎙 <b>Аудиозапись с микрофона</b> ($actualSeconds сек)",
                        durationSeconds = actualSeconds,
                        title = "AudioRecord_${System.currentTimeMillis()}",
                        performer = "RemoteBot",
                        autoDelete = true,
                    )
                } else {
                    CommandResult.Voice(
                        filePath = file.absolutePath,
                        caption = "🎙 <b>Запись с микрофона</b> ($actualSeconds сек)",
                        durationSeconds = actualSeconds,
                        autoDelete = true,
                    )
                }
            },
            onFailure = { e ->
                CommandResult.Error("Ошибка аудиозаписи: ${e.localizedMessage ?: e.message}")
            },
        )
    }

    private fun formatAudioDurationReadable(seconds: Int): String =
        when {
            seconds < 60 -> "$seconds сек"
            seconds % 60 == 0 -> "${seconds / 60} мин"
            else -> "${seconds / 60} мин ${seconds % 60} сек"
        }

    private fun buildRecKeyboard(
        asFile: Boolean,
        isRecording: Boolean = false,
    ): InlineKeyboardMarkup {
        val formatSuffix = if (asFile) " file" else ""
        val row0 =
            listOf(
                InlineKeyboardButton("⏱ 10 сек", callbackData = "/rec 10$formatSuffix"),
                InlineKeyboardButton("⏱ 30 сек", callbackData = "/rec 30$formatSuffix"),
            )
        val row1 =
            listOf(
                InlineKeyboardButton("⏱ 60 сек (1м)", callbackData = "/rec 60$formatSuffix"),
                InlineKeyboardButton("⏱ 300 сек (5м)", callbackData = "/rec 300$formatSuffix"),
            )
        val row2 =
            listOf(
                InlineKeyboardButton("⏱ 600 сек (10м)", callbackData = "/rec 600$formatSuffix"),
                InlineKeyboardButton("⏱ 1800 сек (30м)", callbackData = "/rec 1800$formatSuffix"),
            )
        val row3 =
            listOf(
                InlineKeyboardButton("⏱ 3600 сек (1 час)", callbackData = "/rec 3600$formatSuffix"),
                InlineKeyboardButton("🔴 Запись до остановки", callbackData = "/rec 3600$formatSuffix"),
            )
        val row4 =
            if (isRecording) {
                listOf(
                    InlineKeyboardButton("⏹ Остановить запись", callbackData = "/rec stop"),
                    InlineKeyboardButton("🔄 Обновить панель", callbackData = "/rec panel"),
                )
            } else {
                listOf(
                    InlineKeyboardButton(
                        if (asFile) "🔄 Переключить на Voice" else "🔄 Переключить на Файл",
                        callbackData = if (asFile) "/rec panel" else "/rec_file panel",
                    ),
                    InlineKeyboardButton("⏹ Остановить запись", callbackData = "/rec stop"),
                )
            }
        return InlineKeyboardMarkup(listOf(row0, row1, row2, row3, row4))
    }

    private suspend fun handleTts(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Text(
                "🗣 <b>Синтез речи (TTS)</b>\n" +
                    "Использование: <code>/tts [язык] &lt;текст&gt;</code>\n" +
                    "Примеры:\n" +
                    "• <code>/tts Привет, телефон на связи!</code>\n" +
                    "• <code>/tts en Hello, I am RemoteBot!</code>",
            )
        }

        val lang = if (args.size > 1 && args[0].length == 2) args[0] else null
        val text = if (lang != null) args.drop(1).joinToString(" ") else args.joinToString(" ")

        val result = audioRepository.speakText(text, lang)
        return result.fold(
            onSuccess = {
                CommandResult.Text("🗣 Воспроизведено через динамик:\n<i>«$text»</i>")
            },
            onFailure = { e ->
                CommandResult.Error("Ошибка TTS: ${e.localizedMessage ?: e.message}")
            },
        )
    }

    private fun handlePlayer(args: List<String>): CommandResult {
        val keyboard =
            InlineKeyboardMarkup(
                inlineKeyboard =
                    listOf(
                        listOf(
                            InlineKeyboardButton("🔊 Сирена тревоги", callbackData = "/device_search start"),
                            InlineKeyboardButton("⏹ Выключить сирену", callbackData = "/device_search stop"),
                        ),
                        listOf(
                            InlineKeyboardButton("🔔 Профиль: Звонок", callbackData = "/ring_mode normal"),
                            InlineKeyboardButton("🔕 Без звука", callbackData = "/ring_mode silent"),
                            InlineKeyboardButton("📳 Вибро", callbackData = "/ring_mode vibrate"),
                        ),
                        listOf(
                            InlineKeyboardButton("📊 Уровни громкости", callbackData = "/volume"),
                            InlineKeyboardButton("🔄 Обновить", callbackData = "/player"),
                        ),
                    ),
            )

        val currentMode = audioRepository.getRingerMode().name
        return CommandResult.Text(
            "🎵 <b>Медиаплеер и Звук</b>\n" +
                "• Текущий звуковой профиль: <b>$currentMode</b>\n" +
                "• Управление громкостью: <code>/volume [stream] [level]</code>\n" +
                "• Синтез речи: <code>/tts &lt;текст&gt;</code>",
            keyboard = keyboard,
        )
    }

    private fun handleVolume(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val levels = audioRepository.getVolumeLevels()
            val text =
                buildString {
                    appendLine("🔊 <b>Уровни громкости устройства</b>:\n")
                    for (info in levels) {
                        val streamName =
                            when (info.stream) {
                                AudioStreamType.RING -> "🔔 Вызов (Ring)"
                                AudioStreamType.NOTIFICATION -> "💬 Уведомления"
                                AudioStreamType.ALARM -> "⏰ Будильник (Alarm)"
                                AudioStreamType.MEDIA -> "🎵 Медиа (Media)"
                                AudioStreamType.VOICE_CALL -> "📞 Разговор (Voice Call)"
                                AudioStreamType.SYSTEM -> "⚙️ Система"
                            }
                        val percent = if (info.maxVolume > 0) (info.currentVolume * 100) / info.maxVolume else 0
                        appendLine("• <b>$streamName</b>: <code>${info.currentVolume}/${info.maxVolume}</code> ($percent%)")
                    }
                    appendLine("\n<i>Установка: <code>/volume &lt;media|ring|alarm|notif&gt; &lt;0..15&gt;</code></i>")
                }

            val keyboard =
                InlineKeyboardMarkup(
                    inlineKeyboard =
                        listOf(
                            listOf(
                                InlineKeyboardButton("🎵 Медиа 50%", callbackData = "/volume media 50%"),
                                InlineKeyboardButton("🎵 Медиа 100%", callbackData = "/volume media 100%"),
                            ),
                            listOf(
                                InlineKeyboardButton("🔔 Вызов 100%", callbackData = "/volume ring 100%"),
                                InlineKeyboardButton("⏰ Будильник 100%", callbackData = "/volume alarm 100%"),
                            ),
                            listOf(
                                InlineKeyboardButton("🔄 Обновить", callbackData = "/volume"),
                            ),
                        ),
                )

            return CommandResult.Text(text, keyboard = keyboard)
        }

        val targetStream =
            when (args[0].lowercase()) {
                "ring", "call", "звонок" -> AudioStreamType.RING
                "notification", "notif", "уведомления" -> AudioStreamType.NOTIFICATION
                "alarm", "будильник" -> AudioStreamType.ALARM
                "media", "music", "медиа", "музыка" -> AudioStreamType.MEDIA
                "voice", "voice_call" -> AudioStreamType.VOICE_CALL
                "system", "система" -> AudioStreamType.SYSTEM
                else -> AudioStreamType.MEDIA
            }

        val levelArg = if (args.size > 1) args[1] else args[0]
        val levels = audioRepository.getVolumeLevels()
        val streamInfo = levels.firstOrNull { it.stream == targetStream }
        val maxVol = streamInfo?.maxVolume ?: 15

        val targetLevel =
            if (levelArg.endsWith("%")) {
                val pct = levelArg.removeSuffix("%").toIntOrNull() ?: 50
                (pct * maxVol) / 100
            } else {
                levelArg.toIntOrNull() ?: maxVol
            }

        val res = audioRepository.setVolume(targetStream, targetLevel)
        return res.fold(
            onSuccess = {
                CommandResult.Text("🔊 Громкость потока <b>${targetStream.name}</b> установлена: <code>$targetLevel/$maxVol</code>")
            },
            onFailure = { e ->
                CommandResult.Error("Ошибка установки громкости: ${e.localizedMessage ?: e.message}")
            },
        )
    }

    private fun handleRingMode(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val current = audioRepository.getRingerMode()
            val text =
                "🔔 <b>Звуковой профиль устройства</b>: <b>${current.name}</b>\n\n" +
                    "• <code>/ring_mode normal</code> — обычный режим со звуком\n" +
                    "• <code>/ring_mode silent</code> — без звука (DND / Тихий)\n" +
                    "• <code>/ring_mode vibrate</code> — только вибрация"

            val keyboard =
                InlineKeyboardMarkup(
                    inlineKeyboard =
                        listOf(
                            listOf(
                                InlineKeyboardButton("🔔 Обычный", callbackData = "/ring_mode normal"),
                                InlineKeyboardButton("🔕 Без звука", callbackData = "/ring_mode silent"),
                                InlineKeyboardButton("📳 Вибро", callbackData = "/ring_mode vibrate"),
                            ),
                        ),
                )
            return CommandResult.Text(text, keyboard = keyboard)
        }

        val targetMode =
            when (args[0].lowercase()) {
                "normal", "on", "звук" -> RingerMode.NORMAL
                "silent", "mute", "тихий", "без_звука" -> RingerMode.SILENT
                "vibrate", "vibro", "вибро" -> RingerMode.VIBRATE
                else -> RingerMode.NORMAL
            }

        val res = audioRepository.setRingerMode(targetMode)
        return res.fold(
            onSuccess = {
                val icon =
                    when (targetMode) {
                        RingerMode.NORMAL -> "🔔"
                        RingerMode.SILENT -> "🔕"
                        RingerMode.VIBRATE -> "📳"
                    }
                CommandResult.Text("$icon Звуковой профиль переключен на: <b>${targetMode.name}</b>")
            },
            onFailure = { e ->
                CommandResult.Error(
                    "Не удалось изменить звуковой режим (требуется разрешение «Не беспокоить» / DND): ${e.localizedMessage ?: e.message}",
                )
            },
        )
    }

    private suspend fun handleDeviceSearch(args: List<String>): CommandResult {
        val action = args.firstOrNull()?.lowercase() ?: "start"
        return if (action == "stop" || action == "off") {
            audioRepository.stopDeviceSearchAlarm()
            operationsTracker?.unregister("device_search")
            CommandResult.Text("⏹ <b>Поиск устройства</b>: сирена выключена.")
        } else {
            val res = audioRepository.startDeviceSearchAlarm()
            res.fold(
                onSuccess = {
                    operationsTracker?.register(
                        id = "device_search",
                        name = "🚨 Сирена поиска устройства",
                        description = "Громкость будильника 100%",
                        onCancel = { audioRepository.stopDeviceSearchAlarm() },
                    )
                    val keyboard =
                        InlineKeyboardMarkup(
                            inlineKeyboard =
                                listOf(
                                    listOf(
                                        InlineKeyboardButton("⏹ Выключить сирену", callbackData = "/device_search stop"),
                                    ),
                                ),
                        )
                    CommandResult.Text(
                        "🚨 <b>Включена сирена поиска устройства!</b>\nГромкость будильника установлена на 100%.",
                        keyboard = keyboard,
                    )
                },
                onFailure = { e ->
                    CommandResult.Error("Ошибка запуска сирены: ${e.localizedMessage ?: e.message}")
                },
            )
        }
    }

    private fun handleSoundDetector(args: List<String>): CommandResult {
        if (args.isEmpty() || args[0].equals("panel", ignoreCase = true)) {
            return buildSoundDetectorPanel()
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "set" -> {
                val lvl = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 5
                enableSoundDetector(lvl)
                buildSoundDetectorPanel()
            }
            "on", "start", "enable" -> {
                val lvl = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 5
                enableSoundDetector(lvl)
                buildSoundDetectorPanel()
            }
            "off", "stop", "disable" -> {
                disableSoundDetector()
                buildSoundDetectorPanel()
            }
            else -> {
                val directLvl = sub.toIntOrNull()?.coerceIn(1, 10)
                if (directLvl != null) {
                    enableSoundDetector(directLvl)
                    buildSoundDetectorPanel()
                } else {
                    CommandResult.Error(
                        "Формат: <code>/sound_detector</code> (интерактивная панель), <code>/sound_detector &lt;1..10&gt;</code> или <code>/sound_detector off</code>",
                    )
                }
            }
        }
    }

    private fun enableSoundDetector(lvl: Int) {
        audioRepository.setSoundDetectorEnabled(true, lvl)
        val thresholdDb = 90 - (lvl * 5)
        operationsTracker?.register(
            id = "sound_detector",
            name = "🔊 Детектор звука (Микрофон, $lvl/10)",
            description = "Порог $thresholdDb dB",
            onCancel = { audioRepository.setSoundDetectorEnabled(false) },
        )
    }

    private fun disableSoundDetector() {
        audioRepository.setSoundDetectorEnabled(false)
        operationsTracker?.unregister("sound_detector")
    }

    private fun buildSoundDetectorPanel(): CommandResult.Text {
        val cfg = audioRepository.soundDetectorConfig.value
        val currentLevel = if (cfg.isEnabled) cfg.sensitivity.coerceIn(1, 10) else 5
        val thresholdDb = 90 - (currentLevel * 5)
        val statusText =
            if (cfg.isEnabled) {
                "✅ <b>АКТИВЕН</b> (Порог: <b>$thresholdDb dB</b>)"
            } else {
                "❌ <b>ОТКЛЮЧЕН</b>"
            }

        val row1 =
            (1..5).map { lvl ->
                val label = if (cfg.isEnabled && lvl == currentLevel) "$lvl ✅" else "$lvl"
                InlineKeyboardButton(label, callbackData = "/sound_detector set $lvl")
            }
        val row2 =
            (6..10).map { lvl ->
                val label = if (cfg.isEnabled && lvl == currentLevel) "$lvl ✅" else "$lvl"
                InlineKeyboardButton(label, callbackData = "/sound_detector set $lvl")
            }
        val row3 = mutableListOf<InlineKeyboardButton>()
        if (cfg.isEnabled) {
            row3.add(InlineKeyboardButton("⏹ Выключить", callbackData = "/sound_detector off"))
        } else {
            row3.add(InlineKeyboardButton("▶️ Включить ($currentLevel/10)", callbackData = "/sound_detector set $currentLevel"))
        }
        row3.add(InlineKeyboardButton("🔄 Обновить", callbackData = "/sound_detector panel"))

        val text =
            """
            🔊 <b>Охранный детектор звука (Микрофон)</b>
            
            • Статус: $statusText
            • Текущий уровень: <b>$currentLevel / 10</b> (Порог: $thresholdDb dB)
            
            <i>Шкала: 1 = громкий шум (85 dB, крики/хлопки), 10 = ультрачувствительный (40 dB, тихий шепот/шорохи).</i>
            
            Выберите чувствительность кнопками ниже:
            """.trimIndent()

        return CommandResult.Text(text, keyboard = InlineKeyboardMarkup(listOf(row1, row2, row3)))
    }
}
