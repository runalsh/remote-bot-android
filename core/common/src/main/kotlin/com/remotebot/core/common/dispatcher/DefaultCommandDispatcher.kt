package com.remotebot.core.common.dispatcher

import com.remotebot.domain.dispatcher.CommandDispatcher
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.repository.BotConfigRepository
import java.util.concurrent.ConcurrentHashMap

class DefaultCommandDispatcher(
    private val configRepository: BotConfigRepository,
) : CommandDispatcher {
    private val handlers = ConcurrentHashMap<String, CommandHandler>()

    override fun registerHandler(handler: CommandHandler) {
        for (name in handler.commandNames) {
            handlers[name.lowercase()] = handler
        }
    }

    override suspend fun dispatch(command: IncomingCommand): CommandResult {
        // 1. Source and whitelist verification
        when (command.source) {
            CommandSource.TELEGRAM -> {
                val userId = command.senderId.toLongOrNull()
                if (userId == null || !configRepository.isUserAllowed(userId)) {
                    com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordAuthFailure(
                        com.remotebot.domain.model.AuthChannel.TELEGRAM,
                        command.senderId,
                    )
                    val config = configRepository.config.value
                    return if (config.replyUnauthorizedResponseEnabled) {
                        CommandResult.Error("Unauthorized User ID: ${command.senderId}")
                    } else {
                        CommandResult.Acknowledged
                    }
                }
            }
            CommandSource.SMS -> {
                // SMS authorization (valid PIN from any number or trusted sender) is verified at SMS handler level
            }
        }

        // 2. Handler lookup
        val cmdName =
            command.name
                .lowercase()
                .removePrefix("/")
                .substringBefore("@")
        val handler = handlers[cmdName] ?: return CommandResult.Error("Неизвестная команда: ${command.name}. Введите /help")

        // 3. Execution
        return try {
            handler.execute(command)
        } catch (e: Exception) {
            CommandResult.Error("Ошибка исполнения ${command.name}: ${e.localizedMessage}")
        }
    }
}
