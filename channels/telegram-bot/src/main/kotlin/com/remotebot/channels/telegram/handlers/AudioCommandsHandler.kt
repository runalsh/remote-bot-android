package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.tracker.ActiveOperationsTracker
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.model.BotLanguage
import com.remotebot.channels.telegram.strings.TelegramBotStrings
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
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

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
            logger.i("AudioHandler", "Audio command: /$norm (args=${command.args})")
            when (norm) {
                "rec", "rec_send" -> handleAudioRec(command.args, defaultAsFile = false)
                "rec_file" -> handleAudioRec(command.args, defaultAsFile = true)
                "tts" -> handleTts(command.args)
                "playback", "player" -> handlePlayer(command.args)
                "volume" -> handleVolume(command.args)
                "ring_mode" -> handleRingMode(command.args)
                "device_search" -> handleDeviceSearch(command.args)
                "sound_detector" -> handleSoundDetector(command.args)
                else -> CommandResult.Error(strings.auUnknownCmd(norm))
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
                CommandResult.Text(strings.auRecStopped())
            } else {
                CommandResult.Text(
                    strings.auNoActiveRec(),
                    keyboard = buildRecKeyboard(defaultAsFile),
                )
            }
        }

        // 2. Interactive panel without arguments: /rec or /rec panel
        if (args.isEmpty() || firstArg == "panel") {
            val activeOp = operationsTracker?.list()?.firstOrNull { it.id.startsWith("audio_rec_") }
            val statusText =
                if (activeOp != null) {
                    strings.auRecGoing(activeOp.name)
                } else {
                    strings.auRecIdle()
                }

            return CommandResult.Text(
                strings.auRecPanel(statusText),
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
            name = strings.auRecOpName(readableDur),
            description = if (asFile) strings.auRecDescFile(readableDur) else strings.auRecDescVoice(readableDur),
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
                        caption = strings.auRecCaptionFile(actualSeconds),
                        durationSeconds = actualSeconds,
                        title = "AudioRecord_${System.currentTimeMillis()}",
                        performer = "RemoteBot",
                        autoDelete = true,
                    )
                } else {
                    CommandResult.Voice(
                        filePath = file.absolutePath,
                        caption = strings.auRecCaptionVoice(actualSeconds),
                        durationSeconds = actualSeconds,
                        autoDelete = true,
                    )
                }
            },
            onFailure = { e ->
                CommandResult.Error(strings.auRecError(e.localizedMessage ?: e.message ?: ""))
            },
        )
    }

    private fun formatAudioDurationReadable(seconds: Int): String =
        when {
            seconds < 60 -> strings.auDurSec(seconds)
            seconds % 60 == 0 -> strings.auDurMin(seconds / 60)
            else -> strings.auDurMinSec(seconds / 60, seconds % 60)
        }

    private fun buildRecKeyboard(
        asFile: Boolean,
        isRecording: Boolean = false,
    ): InlineKeyboardMarkup {
        val formatSuffix = if (asFile) " file" else ""
        val row0 =
            listOf(
                InlineKeyboardButton(strings.auBtnSec("10"), callbackData = "/rec 10$formatSuffix"),
                InlineKeyboardButton(strings.auBtnSec("30"), callbackData = "/rec 30$formatSuffix"),
            )
        val row1 =
            listOf(
                InlineKeyboardButton(strings.auB60(), callbackData = "/rec 60$formatSuffix"),
                InlineKeyboardButton(strings.auB300(), callbackData = "/rec 300$formatSuffix"),
            )
        val row2 =
            listOf(
                InlineKeyboardButton(strings.auB600(), callbackData = "/rec 600$formatSuffix"),
                InlineKeyboardButton(strings.auB1800(), callbackData = "/rec 1800$formatSuffix"),
            )
        val row3 =
            listOf(
                InlineKeyboardButton(strings.auB3600(), callbackData = "/rec 3600$formatSuffix"),
                InlineKeyboardButton(strings.auBtnRecUntilStop(), callbackData = "/rec 3600$formatSuffix"),
            )
        val row4 =
            if (isRecording) {
                listOf(
                    InlineKeyboardButton(strings.auBtnStopRec(), callbackData = "/rec stop"),
                    InlineKeyboardButton(strings.auBtnRefreshPanel(), callbackData = "/rec panel"),
                )
            } else {
                listOf(
                    InlineKeyboardButton(
                        if (asFile) strings.auBtnToVoice() else strings.auBtnToFile(),
                        callbackData = if (asFile) "/rec panel" else "/rec_file panel",
                    ),
                    InlineKeyboardButton(strings.auBtnStopRec(), callbackData = "/rec stop"),
                )
            }
        return InlineKeyboardMarkup(listOf(row0, row1, row2, row3, row4))
    }

    private suspend fun handleTts(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Text(
                strings.auTtsUsage(),
            )
        }

        val lang = if (args.size > 1 && args[0].length == 2) args[0] else null
        val text = if (lang != null) args.drop(1).joinToString(" ") else args.joinToString(" ")

        val result = audioRepository.speakText(text, lang)
        return result.fold(
            onSuccess = {
                CommandResult.Text(strings.auTtsPlayed(text))
            },
            onFailure = { e ->
                CommandResult.Error(strings.auTtsError(e.localizedMessage ?: e.message ?: ""))
            },
        )
    }

    private fun handlePlayer(args: List<String>): CommandResult {
        val keyboard =
            InlineKeyboardMarkup(
                inlineKeyboard =
                    listOf(
                        listOf(
                            InlineKeyboardButton(strings.auBtnSirenOn(), callbackData = "/device_search start"),
                            InlineKeyboardButton(strings.auBtnSirenOff(), callbackData = "/device_search stop"),
                        ),
                        listOf(
                            InlineKeyboardButton(strings.auBtnRingNormal(), callbackData = "/ring_mode normal"),
                            InlineKeyboardButton(strings.auBtnSilent(), callbackData = "/ring_mode silent"),
                            InlineKeyboardButton(strings.auBtnVibrate(), callbackData = "/ring_mode vibrate"),
                        ),
                        listOf(
                            InlineKeyboardButton(strings.auBtnVolumeLevels(), callbackData = "/volume"),
                            InlineKeyboardButton(strings.auBtnRefresh(), callbackData = "/player"),
                        ),
                    ),
            )

        val currentMode = audioRepository.getRingerMode().name
        return CommandResult.Text(
            strings.auPlayerHead(currentMode),
            keyboard = keyboard,
        )
    }

    private fun handleVolume(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val levels = audioRepository.getVolumeLevels()
            val text =
                buildString {
                    appendLine(strings.auVolHead())
                    for (info in levels) {
                        val streamName =
                            when (info.stream) {
                                AudioStreamType.RING -> strings.auStreamRing()
                                AudioStreamType.NOTIFICATION -> strings.auStreamNotif()
                                AudioStreamType.ALARM -> strings.auStreamAlarm()
                                AudioStreamType.MEDIA -> strings.auStreamMedia()
                                AudioStreamType.VOICE_CALL -> strings.auStreamCall()
                                AudioStreamType.SYSTEM -> strings.auStreamSystem()
                            }
                        val percent = if (info.maxVolume > 0) (info.currentVolume * 100) / info.maxVolume else 0
                        appendLine("• <b>$streamName</b>: <code>${info.currentVolume}/${info.maxVolume}</code> ($percent%)")
                    }
                    appendLine(strings.auVolHint())
                }

            val keyboard =
                InlineKeyboardMarkup(
                    inlineKeyboard =
                        listOf(
                            listOf(
                                InlineKeyboardButton(strings.auBtnMedia50(), callbackData = "/volume media 50%"),
                                InlineKeyboardButton(strings.auBtnMedia100(), callbackData = "/volume media 100%"),
                            ),
                            listOf(
                                InlineKeyboardButton(strings.auBtnRing100(), callbackData = "/volume ring 100%"),
                                InlineKeyboardButton(strings.auBtnAlarm100(), callbackData = "/volume alarm 100%"),
                            ),
                            listOf(
                                InlineKeyboardButton(strings.auBtnRefresh(), callbackData = "/volume"),
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
                CommandResult.Text(strings.auVolSet(targetStream.name, targetLevel.toString(), maxVol.toString()))
            },
            onFailure = { e ->
                CommandResult.Error(strings.auVolError(e.localizedMessage ?: e.message ?: ""))
            },
        )
    }

    private fun handleRingMode(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val current = audioRepository.getRingerMode()
            val text =
                strings.auRingHead(current.name)

            val keyboard =
                InlineKeyboardMarkup(
                    inlineKeyboard =
                        listOf(
                            listOf(
                                InlineKeyboardButton(strings.auBtnNormal(), callbackData = "/ring_mode normal"),
                                InlineKeyboardButton(strings.auBtnSilent(), callbackData = "/ring_mode silent"),
                                InlineKeyboardButton(strings.auBtnVibrate(), callbackData = "/ring_mode vibrate"),
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
                CommandResult.Text(strings.auRingToggled(icon, targetMode.name))
            },
            onFailure = { e ->
                CommandResult.Error(
                    strings.auRingError(e.localizedMessage ?: e.message ?: ""),
                )
            },
        )
    }

    private suspend fun handleDeviceSearch(args: List<String>): CommandResult {
        val action = args.firstOrNull()?.lowercase() ?: "start"
        return if (action == "stop" || action == "off") {
            audioRepository.stopDeviceSearchAlarm()
            operationsTracker?.unregister("device_search")
            CommandResult.Text(strings.auSirenOff())
        } else {
            val res = audioRepository.startDeviceSearchAlarm()
            res.fold(
                onSuccess = {
                    operationsTracker?.register(
                        id = "device_search",
                        name = strings.auSirenOp(),
                        description = strings.auSirenOpDesc(),
                        onCancel = { audioRepository.stopDeviceSearchAlarm() },
                    )
                    val keyboard =
                        InlineKeyboardMarkup(
                            inlineKeyboard =
                                listOf(
                                    listOf(
                                        InlineKeyboardButton(strings.auBtnSirenOff(), callbackData = "/device_search stop"),
                                    ),
                                ),
                        )
                    CommandResult.Text(
                        strings.auSirenOn(),
                        keyboard = keyboard,
                    )
                },
                onFailure = { e ->
                    CommandResult.Error(strings.auSirenError(e.localizedMessage ?: e.message ?: ""))
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
                        strings.auSdUsage(),
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
            name = strings.auSdOpName(lvl.toString()),
            description = strings.auSdOpDesc(thresholdDb.toString()),
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
                strings.auSdActive(thresholdDb.toString())
            } else {
                strings.auSdOff()
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
            row3.add(InlineKeyboardButton(strings.auBtnSdOff(), callbackData = "/sound_detector off"))
        } else {
            row3.add(InlineKeyboardButton(strings.auBtnSdOn(currentLevel.toString()), callbackData = "/sound_detector set $currentLevel"))
        }
        row3.add(InlineKeyboardButton(strings.auBtnRefresh(), callbackData = "/sound_detector panel"))

        val text =
            strings.auSdPanel(statusText, currentLevel.toString(), thresholdDb.toString())

        return CommandResult.Text(text, keyboard = InlineKeyboardMarkup(listOf(row1, row2, row3)))
    }
}
