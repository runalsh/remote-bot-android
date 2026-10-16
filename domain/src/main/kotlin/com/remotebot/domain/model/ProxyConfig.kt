package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProxyType {
    NONE,
    HTTP,
    SOCKS5,
    WEBSOCKET_WSS,
}

@Serializable
data class ProxyConfig(
    val type: ProxyType = ProxyType.NONE,
    val host: String = "",
    val port: Int = 1080,
    val username: String? = null,
    val password: String? = null,
    // WebSocket / WSS specific settings
    val wsPath: String = "/ws",
    val wsSni: String? = null,
    val wsCustomHeader: String? = null,
)
