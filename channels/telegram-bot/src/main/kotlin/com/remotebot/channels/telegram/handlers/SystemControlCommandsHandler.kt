package com.remotebot.channels.telegram.handlers

import android.content.Context
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.provider.RootStatusProvider

/**
 * Фасад системного контроля, объединяющий обработчики:
 * - [SystemInfoCommandsHandler] (сводка системы, батарея, сетевые интерфейсы)
 * - [DeviceRadioCommandsHandler] (радиомодули: Wi-Fi, Bluetooth, фонарик, режим полета, hotspot, мобильные данные, GPS)
 * - [SystemSettingsCommandsHandler] (панель переключателей, энергосбережение, автояркость, буфер обмена, синхронизация, ADB/ADB-WiFi)
 */
class SystemControlCommandsHandler(
    context: Context,
    logger: AppLogger,
    rootStatusProvider: RootStatusProvider,
) : CommandHandler {
    val infoHandler = SystemInfoCommandsHandler(context, logger, rootStatusProvider)
    val radioHandler = DeviceRadioCommandsHandler(context, logger, rootStatusProvider)
    val settingsHandler = SystemSettingsCommandsHandler(context, logger, rootStatusProvider, radioHandler)

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
            else -> CommandResult.Error("Неизвестная команда системного контроля: $cmd")
        }
    }
}
