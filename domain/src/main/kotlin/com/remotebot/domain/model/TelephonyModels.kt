package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    UNKNOWN,
}

@Serializable
data class CallLogEntry(
    val number: String,
    val name: String? = null,
    val type: CallType = CallType.UNKNOWN,
    val dateMs: Long = System.currentTimeMillis(),
    val durationSec: Long = 0,
)

@Serializable
data class SmsMessage(
    val address: String,
    val body: String,
    val dateMs: Long = System.currentTimeMillis(),
    val isIncoming: Boolean = true,
    val simSlot: Int? = null,
)

@Serializable
data class NotificationRule(
    val id: String,
    val packageName: String? = null,
    val titlePattern: String? = null,
    val bodyPattern: String? = null,
    val isRegex: Boolean = false,
    val isExclude: Boolean = false,
)

@Serializable
data class NotificationActionButton(
    val index: Int,
    val title: String,
)

@Serializable
data class CapturedNotification(
    val id: String,
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val timestampMs: Long = System.currentTimeMillis(),
    val hasReplyAction: Boolean = false,
    val postWhenMs: Long = 0L,
    val isGroupSummary: Boolean = false,
    val notificationCategory: String? = null,
    val actions: List<NotificationActionButton> = emptyList(),
)
