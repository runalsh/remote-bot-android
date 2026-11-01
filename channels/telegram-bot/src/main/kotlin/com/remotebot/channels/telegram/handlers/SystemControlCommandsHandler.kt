package com.remotebot.channels.telegram.handlers

import android.content.Context
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.provider.RootStatusProvider

/**
 * System control facade combining handlers:
 * - [SystemInfoCommandsHandler] (system summary, battery, network interfaces)
 * - [DeviceRadioCommandsHandler] (radio modules: Wi-Fi, Bluetooth, flashlight, airplane mode, hotspot, mobile data, GPS)
 * - [SystemSettingsCommandsHandler] (toggle switches panel, power save, auto-brightness, clipboard, sync, ADB/ADB-WiFi)
 */
class SystemControlCommandsHandler(
    context: Context,
    logger: AppLogger,
    rootStatusProvider: RootStatusProvider,
    private val botConfigRepository: com.remotebot.domain.repository.BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = com.remotebot.channels.telegram.strings.TelegramBotStrings.get(
            botConfigRepository?.config?.value?.botLanguage ?: com.remotebot.domain.model.BotLanguage.AUTO,
        )

    val infoHandler = SystemInfoCommandsHandler(context, logger, rootStatusProvider, botConfigRepository)
    val radioHandler = DeviceRadioCommandsHandler(context, logger, rootStatusProvider, botConfigRepository)
    val settingsHandler = SystemSettingsCommandsHandler(context, logger, rootStatusProvider, radioHandler, botConfigRepository)

    override val commandNames: List<String> =
        (
            infoHandler.commandNames + radioHandler.commandNames + settingsHandler.commandNames
        ).distinct()

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when {
            cmd in infoHandler.commandNames -> infoHandler.execute(command)
            cmd in radioHandler.commandNames -> radioHandler.execute(command)
            cmd in settingsHandler.commandNames -> settingsHandler.execute(command)
            else -> CommandResult.Error(strings.scUnknownCmd(cmd))
        }
    }
}
