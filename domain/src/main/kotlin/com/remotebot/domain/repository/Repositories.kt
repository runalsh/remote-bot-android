package com.remotebot.domain.repository

import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.ProxyConfig
import kotlinx.coroutines.flow.StateFlow

interface BotConfigRepository {
    val config: StateFlow<BotConfig>

    suspend fun updateConfig(update: (BotConfig) -> BotConfig)

    fun isUserAllowed(userId: Long): Boolean

    fun isPhoneAllowed(phone: String): Boolean

    fun isUserSubscribedTo(userId: Long, eventType: com.remotebot.domain.model.SubscriptionEventType): Boolean =
        config.value.isUserSubscribedTo(userId, eventType)
}

interface ProxyConfigRepository {
    val proxyConfig: StateFlow<ProxyConfig>

    suspend fun updateProxy(config: ProxyConfig)
}
