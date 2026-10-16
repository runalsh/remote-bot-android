package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SubscriptionEventType {
    NOTIFICATIONS,
    SMS,
    CALLS,
    SYSTEM,
}

@Serializable
data class UserSubscription(
    val userId: Long,
    val receiveNotifications: Boolean = true,
    val receiveSms: Boolean = true,
    val receiveCalls: Boolean = true,
    val receiveSystem: Boolean = false,
) {
    fun isSubscribedTo(type: SubscriptionEventType): Boolean =
        when (type) {
            SubscriptionEventType.NOTIFICATIONS -> receiveNotifications
            SubscriptionEventType.SMS -> receiveSms
            SubscriptionEventType.CALLS -> receiveCalls
            SubscriptionEventType.SYSTEM -> receiveSystem
        }
}

