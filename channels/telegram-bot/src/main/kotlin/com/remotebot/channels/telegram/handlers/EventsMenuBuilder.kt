package com.remotebot.channels.telegram.handlers

import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.UserSubscription
import com.remotebot.channels.telegram.strings.BotStrings
import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.domain.model.BotLanguage

object EventsMenuBuilder {
    fun build(
        userId: Long,
        sub: UserSubscription,
        callerId: Long? = null,
        strings: BotStrings = TelegramBotStrings.get(BotLanguage.AUTO),
    ): Pair<String, InlineKeyboardMarkup> {
        val notifStatus = if (sub.receiveNotifications) strings.evStatusOn() else strings.evStatusOff()
        val smsStatus = if (sub.receiveSms) strings.evStatusOn() else strings.evStatusOff()
        val callsStatus = if (sub.receiveCalls) strings.evStatusOn() else strings.evStatusOff()
        val systemStatus = if (sub.receiveSystem) strings.evStatusOn() else strings.evStatusOff()

        val isCommandsOnly = !sub.receiveNotifications && !sub.receiveSms && !sub.receiveCalls && !sub.receiveSystem
        val roleLabel = if (isCommandsOnly) strings.evRoleCommandsOnly() else strings.evRoleRecipient()

        val isSelf = callerId == null || callerId == userId
        val userLabel = if (isSelf) strings.evUserSelf(userId) else strings.evUserOther(userId)

        val text = strings.evText(userLabel, roleLabel, notifStatus, smsStatus, callsStatus, systemStatus)

        val notifBtn = if (sub.receiveNotifications) strings.evBtnNotifOn() else strings.evBtnNotifOff()
        val smsBtn = if (sub.receiveSms) strings.evBtnSmsOn() else strings.evBtnSmsOff()
        val callsBtn = if (sub.receiveCalls) strings.evBtnCallsOn() else strings.evBtnCallsOff()
        val systemBtn = if (sub.receiveSystem) strings.evBtnSystemOn() else strings.evBtnSystemOff()

        val markup = InlineKeyboardMarkup(
            listOf(
                listOf(
                    InlineKeyboardButton(notifBtn, callbackData = "sub_toggle:$userId:notif"),
                    InlineKeyboardButton(smsBtn, callbackData = "sub_toggle:$userId:sms"),
                ),
                listOf(
                    InlineKeyboardButton(callsBtn, callbackData = "sub_toggle:$userId:calls"),
                    InlineKeyboardButton(systemBtn, callbackData = "sub_toggle:$userId:system"),
                ),
                listOf(
                    InlineKeyboardButton(strings.evBtnAllOff(), callbackData = "sub_toggle:$userId:all_off"),
                    InlineKeyboardButton(strings.evBtnAllOn(), callbackData = "sub_toggle:$userId:all_on"),
                ),
                listOf(
                    InlineKeyboardButton(strings.evBtnRefresh(), callbackData = "sub_toggle:$userId:refresh"),
                ),
            ),
        )
        return Pair(text, markup)
    }
}

