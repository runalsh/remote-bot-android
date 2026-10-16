package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

@Serializable
data class LogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String,
    val message: String,
)
