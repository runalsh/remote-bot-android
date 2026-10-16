package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OperationInfo(
    val id: String,
    val name: String,
    val startedAtMs: Long = System.currentTimeMillis(),
    val description: String = "",
)
