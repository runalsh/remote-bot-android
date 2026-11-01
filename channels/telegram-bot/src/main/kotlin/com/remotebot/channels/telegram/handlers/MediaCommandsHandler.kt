package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.tracker.ActiveOperationsTracker
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.model.BotLanguage
import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CameraLens
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.FlashMode
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.PhotoParams
import com.remotebot.domain.model.VideoParams
import com.remotebot.domain.repository.AudioRepository
import com.remotebot.domain.repository.CameraRepository
import com.remotebot.domain.repository.ScreenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaCommandsHandler(
    private val cameraRepository: CameraRepository,
    audioRepository: AudioRepository,
    screenRepository: ScreenRepository,
    private val logger: AppLogger,
    private val operationsTracker: ActiveOperationsTracker? = null,
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

    val audioHandler = AudioCommandsHandler(audioRepository, logger, operationsTracker, botConfigRepository)
    val screenHandler = ScreenCommandsHandler(screenRepository, logger, operationsTracker, botConfigRepository)

    override val commandNames: List<String> =
        (
            listOf(
                "photo",
                "photo_send",
                "videorec",
                "videorec_send",
                "detectmotion",
                "gallery",
            ) + audioHandler.commandNames + screenHandler.commandNames
        ).distinct()

    override val isDestructive: Boolean = false

    private var selectedMotionSensitivityLevel: Int = 5
    private var selectedMotionCooldownSeconds: Int = 5

    override suspend fun execute(command: IncomingCommand): CommandResult =
        withContext(Dispatchers.IO) {
            val norm = command.name.lowercase().removePrefix("/")
            logger.i("MediaHandler", "Media command: /$norm (args=${command.args})")

            when {
                norm in audioHandler.commandNames -> audioHandler.execute(command)
                norm in screenHandler.commandNames -> screenHandler.execute(command)
                norm in listOf("photo", "photo_send") -> handlePhoto(command.args)
                norm in listOf("videorec", "videorec_send") -> handleVideoRec(command.args)
                norm == "detectmotion" -> handleDetectMotion(command.args)
                norm == "gallery" -> handleGallery(command.args)
                else -> CommandResult.Error(strings.mdUnknownCmd(norm))
            }
        }

    private suspend fun handlePhoto(args: List<String>): CommandResult {
        var lens = CameraLens.BACK
        var flash = FlashMode.OFF
        var rotation = 0

        for (arg in args) {
            when (arg.lowercase()) {
                "front", "front_cam", "f" -> lens = CameraLens.FRONT
                "back", "back_cam", "b" -> lens = CameraLens.BACK
                "flash_on", "flash", "torch" -> flash = FlashMode.ON
                "flash_off", "noflash" -> flash = FlashMode.OFF
                "flash_auto", "auto" -> flash = FlashMode.AUTO
                "90", "rot_90", "r90" -> rotation = 90
                "180", "rot_180", "r180" -> rotation = 180
                "270", "rot_270", "r270" -> rotation = 270
            }
        }

        val result =
            cameraRepository.takePhoto(
                PhotoParams(lens = lens, flash = flash, rotationDegrees = rotation),
            )

        return result.fold(
            onSuccess = { file ->
                val lensDesc = if (lens == CameraLens.FRONT) strings.mdLensFront() else strings.mdLensBack()
                val flashDesc = if (flash == FlashMode.ON) strings.mdFlashOn() else strings.mdFlashOff()
                CommandResult.Photo(
                    filePath = file.absolutePath,
                    caption = strings.mdPhotoCaption(lensDesc, flashDesc, rotation.toString()),
                    autoDelete = true,
                )
            },
            onFailure = { e ->
                CommandResult.Error(strings.mdPhotoError(e.localizedMessage ?: e.message ?: ""))
            },
        )
    }

    private suspend fun handleVideoRec(args: List<String>): CommandResult {
        val firstArg = args.firstOrNull()?.lowercase()

        // 1. Stop video recording: /videorec stop
        if (firstArg in listOf("stop", "off", "cancel")) {
            val stopped = cameraRepository.stopVideoRecording()
            operationsTracker?.list()?.filter { it.id.startsWith("videorec_") }?.forEach {
                operationsTracker.unregister(it.id)
            }
            return if (stopped) {
                CommandResult.Text(strings.mdRecStopped())
            } else {
                CommandResult.Text(
                    strings.mdNoActiveRec(),
                    keyboard = buildVideoRecKeyboard(CameraLens.BACK),
                )
            }
        }

        // 2. Interactive panel without arguments: /videorec or /videorec panel
        if (args.isEmpty() || firstArg == "panel") {
            val selectedLens =
                if (args.any {
                        it.equals("front", ignoreCase = true) || it.equals("f", ignoreCase = true)
                    }
                ) {
                    CameraLens.FRONT
                } else {
                    CameraLens.BACK
                }
            val activeOp = operationsTracker?.list()?.firstOrNull { it.id.startsWith("videorec_") }
            val statusText =
                if (activeOp != null) {
                    strings.mdRecActive(activeOp.name)
                } else {
                    strings.mdRecIdle()
                }

            val lensDesc = if (selectedLens == CameraLens.FRONT) strings.mdLensFrontSelfie() else strings.mdLensBackRear()
            return CommandResult.Text(
                strings.mdRecPanel(statusText, lensDesc),
                keyboard = buildVideoRecKeyboard(selectedLens, isRecording = activeOp != null),
            )
        }

        var lens = CameraLens.BACK
        var duration = 10

        for (arg in args) {
            when (arg.lowercase()) {
                "front", "front_cam", "f" -> lens = CameraLens.FRONT
                "back", "back_cam", "b" -> lens = CameraLens.BACK
                "max" -> duration = 3600
                else -> arg.toIntOrNull()?.let { duration = it.coerceIn(1, 3600) }
            }
        }

        val opId = "videorec_${System.currentTimeMillis()}"
        val lensDesc = if (lens == CameraLens.FRONT) strings.mdLensFrontLower() else strings.mdLensBackLower()
        val readableDur = formatVideoDurationReadable(duration)
        operationsTracker?.register(
            id = opId,
            name = strings.mdRecOpName(lensDesc, readableDur),
            description = strings.mdRecOpDesc(lensDesc, readableDur),
            onCancel = {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    cameraRepository.stopVideoRecording()
                }
            },
        )

        val result =
            try {
                cameraRepository.recordVideo(VideoParams(lens = lens, durationSeconds = duration))
            } finally {
                operationsTracker?.unregister(opId)
            }
        return result.fold(
            onSuccess = { file ->
                CommandResult.Video(
                    filePath = file.absolutePath,
                    caption = strings.mdRecCaption(lensDesc, duration),
                    durationSeconds = duration,
                    autoDelete = true,
                )
            },
            onFailure = { e ->
                CommandResult.Error(strings.mdRecError(e.localizedMessage ?: e.message ?: ""))
            },
        )
    }

    private fun formatVideoDurationReadable(seconds: Int): String =
        when {
            seconds < 60 -> strings.mdDurSec(seconds)
            seconds % 60 == 0 -> strings.mdDurMin(seconds / 60)
            else -> strings.mdDurMinSec(seconds / 60, seconds % 60)
        }

    private fun buildVideoRecKeyboard(
        lens: CameraLens,
        isRecording: Boolean = false,
    ): InlineKeyboardMarkup {
        val lensArg = if (lens == CameraLens.FRONT) "front" else "back"
        val toggleLens = if (lens == CameraLens.FRONT) "back" else "front"
        val toggleLabel = if (lens == CameraLens.FRONT) strings.mdBtnToggleToBack() else strings.mdBtnToggleToFront()

        val row0 =
            listOf(
                InlineKeyboardButton(strings.mdBtnDur(10), callbackData = "/videorec $lensArg 10"),
                InlineKeyboardButton(strings.mdBtnDur(30), callbackData = "/videorec $lensArg 30"),
            )
        val row1 =
            listOf(
                InlineKeyboardButton(strings.mdBtnDurMin(60, 1), callbackData = "/videorec $lensArg 60"),
                InlineKeyboardButton(strings.mdBtnDurMin(300, 5), callbackData = "/videorec $lensArg 300"),
            )
        val row2 =
            listOf(
                InlineKeyboardButton(strings.mdBtnDurMin(600, 10), callbackData = "/videorec $lensArg 600"),
                InlineKeyboardButton(strings.mdBtnDurMin(1800, 30), callbackData = "/videorec $lensArg 1800"),
            )
        val row3 =
            listOf(
                InlineKeyboardButton(strings.mdBtnDurHour(3600), callbackData = "/videorec $lensArg 3600"),
                InlineKeyboardButton(strings.mdBtnRecUntilStop(), callbackData = "/videorec $lensArg 3600"),
            )
        val row4 =
            if (isRecording) {
                listOf(
                    InlineKeyboardButton(strings.mdBtnStopRec(), callbackData = "/videorec stop"),
                    InlineKeyboardButton(strings.mdBtnRefreshPanel(), callbackData = "/videorec panel $lensArg"),
                )
            } else {
                listOf(
                    InlineKeyboardButton(toggleLabel, callbackData = "/videorec panel $toggleLens"),
                    InlineKeyboardButton(strings.mdBtnStop(), callbackData = "/videorec stop"),
                )
            }
        return InlineKeyboardMarkup(listOf(row0, row1, row2, row3, row4))
    }

    private fun buildDetectMotionPanel(
        selectedLevel: Int,
        selectedCooldown: Int = selectedMotionCooldownSeconds,
    ): CommandResult.Text {
        val cfg = cameraRepository.motionDetectorConfig.value
        val activeLevel = if (cfg.isEnabled) (cfg.sensitivity / 10).coerceIn(1, 10) else selectedLevel
        val activeCooldown = if (cfg.isEnabled) cfg.cooldownSeconds else selectedCooldown

        val row1 =
            (1..5).map { lvl ->
                val label = if (activeLevel == lvl) "$lvl ✅" else "$lvl"
                InlineKeyboardButton(label, callbackData = "/detectmotion set $lvl $activeCooldown")
            }
        val row2 =
            (6..10).map { lvl ->
                val label = if (activeLevel == lvl) "$lvl ✅" else "$lvl"
                InlineKeyboardButton(label, callbackData = "/detectmotion set $lvl $activeCooldown")
            }

        val cooldownIntervals = listOf(1, 2, 5, 10, 15, 30, 60)
        val rowCooldown =
            cooldownIntervals.map { sec ->
                val label = if (activeCooldown == sec) strings.mdSecActive(sec) else strings.mdSec(sec)
                InlineKeyboardButton(label, callbackData = "/detectmotion cd $sec")
            }

        val percent = activeLevel * 10
        val rowActions = mutableListOf<InlineKeyboardButton>()
        if (cfg.isEnabled) {
            rowActions.add(InlineKeyboardButton(strings.mdBtnDisable(), callbackData = "/detectmotion off"))
        } else {
            rowActions.add(
                InlineKeyboardButton(
                    strings.mdBtnEnable(activeLevel, activeCooldown),
                    callbackData = "/detectmotion on $activeLevel $activeCooldown",
                ),
            )
        }
        rowActions.add(InlineKeyboardButton(strings.mdBtnRefresh(), callbackData = "/detectmotion panel $activeLevel $activeCooldown"))

        val statusText =
            if (cfg.isEnabled) {
                strings.mdMotionActive(percent, activeCooldown)
            } else {
                strings.mdMotionInactive()
            }

        val text =
            strings.mdMotionPanel(statusText, activeLevel, percent, activeCooldown)

        return CommandResult.Text(
            message = text,
            keyboard = InlineKeyboardMarkup(listOf(row1, row2, rowCooldown, rowActions)),
        )
    }

    private suspend fun handleDetectMotion(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return buildDetectMotionPanel(selectedMotionSensitivityLevel, selectedMotionCooldownSeconds)
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "set" -> {
                val lvl = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: selectedMotionSensitivityLevel
                val cd = args.getOrNull(2)?.toIntOrNull() ?: selectedMotionCooldownSeconds
                selectedMotionSensitivityLevel = lvl
                selectedMotionCooldownSeconds = cd
                enableMotion(lvl, cd)
                buildDetectMotionPanel(lvl, cd)
            }
            "cd", "cooldown", "interval" -> {
                val cd = args.getOrNull(1)?.toIntOrNull() ?: 5
                selectedMotionCooldownSeconds = cd
                val cfg = cameraRepository.motionDetectorConfig.value
                if (cfg.isEnabled) {
                    enableMotion(cfg.sensitivity / 10, cd)
                }
                buildDetectMotionPanel(selectedMotionSensitivityLevel, cd)
            }
            "panel" -> {
                val lvl = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: selectedMotionSensitivityLevel
                val cd = args.getOrNull(2)?.toIntOrNull() ?: selectedMotionCooldownSeconds
                buildDetectMotionPanel(lvl, cd)
            }
            "on", "start", "run" -> {
                val lvl = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: selectedMotionSensitivityLevel
                val cd = args.getOrNull(2)?.toIntOrNull() ?: selectedMotionCooldownSeconds
                selectedMotionSensitivityLevel = lvl
                selectedMotionCooldownSeconds = cd
                enableMotion(lvl, cd)
                buildDetectMotionPanel(lvl, cd)
            }
            "off", "stop" -> {
                disableMotion()
                buildDetectMotionPanel(selectedMotionSensitivityLevel, selectedMotionCooldownSeconds)
            }
            else -> {
                val directLvl = sub.toIntOrNull()
                if (directLvl != null) {
                    val clamped = directLvl.coerceIn(1, 10)
                    val cd = args.getOrNull(1)?.toIntOrNull() ?: selectedMotionCooldownSeconds
                    selectedMotionSensitivityLevel = clamped
                    selectedMotionCooldownSeconds = cd
                    enableMotion(clamped, cd)
                    buildDetectMotionPanel(clamped, cd)
                } else {
                    buildDetectMotionPanel(selectedMotionSensitivityLevel, selectedMotionCooldownSeconds)
                }
            }
        }
    }

    private fun enableMotion(
        lvl: Int,
        cd: Int,
    ) {
        val percent = lvl * 10
        cameraRepository.setMotionDetectorEnabled(true, percent, cd)
        operationsTracker?.register(
            id = "motion_detector",
            name = strings.mdMotionOpName(lvl, cd),
            description = strings.mdMotionOpDesc(percent, cd),
            onCancel = { cameraRepository.setMotionDetectorEnabled(false) },
        )
    }

    private fun disableMotion() {
        cameraRepository.setMotionDetectorEnabled(false)
        operationsTracker?.unregister("motion_detector")
    }

    private suspend fun handleGallery(args: List<String>): CommandResult {
        val limit = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 10) ?: 5
        val photos = cameraRepository.getGalleryPhotos(limit)

        if (photos.isEmpty()) {
            return CommandResult.Text(strings.mdGalleryEmpty())
        }

        val files = photos.map { it.file }
        return CommandResult.MediaGroup(
            filePaths = files.map { it.absolutePath },
            caption = strings.mdGalleryCaption(files.size),
        )
    }
}
