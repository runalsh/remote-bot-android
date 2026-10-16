package com.remotebot.domain.push

import com.remotebot.domain.model.TgUpdate

/**
 * Интерфейс диспетчера push-уведомлений для каналов связи (Telegram, Matrix и т.д.).
 * Позволяет инфраструктурному модулю core:push не зависеть от channels:telegram-bot.
 */
interface PushWakeupHandler {
    suspend fun processPushUpdate(update: TgUpdate)

    suspend fun handlePushWakeup(targetUpdateId: Long)
}
