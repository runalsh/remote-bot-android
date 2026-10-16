package com.remotebot.domain.push

import com.remotebot.domain.model.TgUpdate

/**
 * Push notification dispatcher interface for communication channels (Telegram, Matrix, etc.).
 * Allows the infrastructure core:push module to be decoupled from channels:telegram-bot.
 */
interface PushWakeupHandler {
    suspend fun processPushUpdate(update: TgUpdate)

    suspend fun handlePushWakeup(targetUpdateId: Long)
}
