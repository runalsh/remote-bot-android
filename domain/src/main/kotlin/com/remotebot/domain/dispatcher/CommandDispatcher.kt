package com.remotebot.domain.dispatcher

import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand

interface CommandHandler {
    val commandNames: List<String>
    val isDestructive: Boolean

    suspend fun execute(command: IncomingCommand): CommandResult
}

interface CommandDispatcher {
    fun registerHandler(handler: CommandHandler)

    suspend fun dispatch(command: IncomingCommand): CommandResult
}
