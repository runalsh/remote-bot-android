package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppBackupData(
    val version: String = "2.0.0",
    val exportedAtMs: Long = System.currentTimeMillis(),
    val botConfig: BotConfig,
    val proxyConfig: ProxyConfig,
)
