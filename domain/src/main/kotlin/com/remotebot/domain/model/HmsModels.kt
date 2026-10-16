package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PushProvider {
    FCM,
    HMS,
    HYBRID,
}

@Serializable
data class ParsedAgconnectConfig(
    val appId: String,
    val clientId: String,
    val clientSecret: String? = null,
    val apiKey: String? = null,
    val packageName: String? = null,
    val rawJson: String? = null,
)
