package com.remotebot.domain.repository

import com.remotebot.domain.model.CallLogEntry
import com.remotebot.domain.model.CapturedNotification
import com.remotebot.domain.model.NotificationActionButton
import com.remotebot.domain.model.NotificationRule
import com.remotebot.domain.model.SmsMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TelephonyRepository {
    suspend fun sendSms(
        address: String,
        message: String,
        simSlot: Int? = null,
    ): Result<Unit>

    suspend fun getRecentSms(
        limit: Int = 10,
        simSlot: Int? = null,
    ): Result<List<SmsMessage>>

    suspend fun getCallHistory(limit: Int = 10): Result<List<CallLogEntry>>

    suspend fun makeCall(
        number: String,
        simSlot: Int? = null,
    ): Result<Unit>

    suspend fun sendUssd(
        code: String,
        simSlot: Int? = null,
    ): Result<String>
}

interface NotificationRepository {
    val notificationEvents: Flow<CapturedNotification>
    val rules: StateFlow<List<NotificationRule>>
    val recentNotificationsCount: Int
        get() = 0

    suspend fun addRule(rule: NotificationRule)

    suspend fun removeRule(ruleId: String)

    suspend fun getRecentNotifications(limit: Int = 10): List<CapturedNotification>

    suspend fun replyToNotification(
        notificationId: String,
        replyText: String,
    ): Result<Unit>

    suspend fun executeNotificationAction(
        notificationId: String,
        actionIndex: Int,
    ): Result<Unit>

    suspend fun cancelNotification(notificationId: String): Result<Unit>

    suspend fun postNotification(
        title: String,
        text: String,
    ): Result<Unit>

    suspend fun simulateNotification(
        packageName: String,
        appName: String,
        title: String,
        text: String,
    ): Result<Unit>

    suspend fun simulateNotificationWithFlags(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        hasRemoteInput: Boolean,
        isGroupSummary: Boolean,
        category: String?,
        actions: List<NotificationActionButton> = emptyList(),
    ): Result<Unit>

    suspend fun clearRecentNotifications()
}
