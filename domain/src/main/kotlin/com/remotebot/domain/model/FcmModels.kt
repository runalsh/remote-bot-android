package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ParsedFirebaseConfig(
    val appId: String,
    val apiKey: String,
    val projectId: String,
    val gcmSenderId: String,
)

@Serializable
data class FcmPushPayload(
    val action: String? = null,
    val updateId: Long? = null,
    val updateJson: String? = null,
)
