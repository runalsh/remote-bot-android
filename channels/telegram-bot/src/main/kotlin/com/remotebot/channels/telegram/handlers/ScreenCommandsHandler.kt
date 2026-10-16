package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.ScreenRecordParams
import com.remotebot.domain.repository.ScreenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenCommandsHandler(
    private val screenRepository: ScreenRepository,
    private val logger: AppLogger,
    private val operationsTracker: com.remotebot.core.common.tracker.ActiveOperationsTracker? = null,
) : CommandHandler {
    override val commandNames: List<String> =
        listOf(
            "screenshot",
            "screenshot_send",
            "screen_rec",
            "screen_rec_send",
        )

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult =
        withContext(Dispatchers.IO) {
            val norm = command.name.lowercase().removePrefix("/")
            logger.i("ScreenHandler", "Команда захвата экрана: /$norm")
            when (norm) {
                "screenshot", "screenshot_send" -> handleScreenshot()
                "screen_rec", "screen_rec_send" -> handleScreenRec(command.args)
                else -> CommandResult.Error("Неизвестная команда экрана: /$norm")
            }
        }

    private suspend fun handleScreenshot(): CommandResult {
        val res = screenRepository.takeScreenshot()
        return res.fold(
            onSuccess = { shot ->
                CommandResult.Photo(
                    filePath = shot.file.absolutePath,
                    caption = "🖥 <b>Снимок экрана</b>\nМетод: ${shot.method}",
                    autoDelete = true,
                )
            },
            onFailure = { e ->
                CommandResult.Error(e.localizedMessage ?: e.message ?: "Сбой захвата экрана")
            },
        )
    }

    private suspend fun handleScreenRec(args: List<String>): CommandResult {
        val duration = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 60) ?: 10
        val opId = "screen_rec_${System.currentTimeMillis()}"
        operationsTracker?.register(
            id = opId,
            name = "📹 Запись экрана ($duration сек)",
            description = "Фоновая запись экрана в MP4",
        )
        try {
            val res = screenRepository.recordScreen(ScreenRecordParams(durationSeconds = duration))
            return res.fold(
                onSuccess = { file ->
                    CommandResult.Video(
                        filePath = file.absolutePath,
                        caption = "📹 <b>Видеозапись экрана</b> ($duration сек)",
                        durationSeconds = duration,
                        autoDelete = true,
                    )
                },
                onFailure = { e ->
                    CommandResult.Error(e.localizedMessage ?: e.message ?: "Сбой записи экрана")
                },
            )
        } finally {
            operationsTracker?.unregister(opId)
        }
    }
}
