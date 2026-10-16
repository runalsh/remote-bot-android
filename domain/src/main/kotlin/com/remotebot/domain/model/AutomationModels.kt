package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskerTask(
    val taskName: String,
    val param1: String? = null,
    val param2: String? = null,
    val param3: String? = null,
)

@Serializable
data class ScheduledTimer(
    val id: String,
    val commandText: String,
    val triggerAtMs: Long,
    val intervalMs: Long? = null, // null for one-shot
    val isEnabled: Boolean = true,
)

@Serializable
enum class SystemTriggerType {
    POWER_CONNECTED,
    POWER_DISCONNECTED,
    BATTERY_LOW,
    BATTERY_OKAY,
    BOOT_COMPLETED,
    SCREEN_ON,
    SCREEN_OFF,
}

@Serializable
data class SystemTriggerRule(
    val id: String,
    val type: SystemTriggerType,
    val actionCommand: String,
    val isEnabled: Boolean = true,
)

@Serializable
data class ScriptExecutionResult(
    val isSuccess: Boolean,
    val output: String?,
    val error: String? = null,
    val executionTimeMs: Long = 0,
)
