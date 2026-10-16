package com.remotebot.channels.telegram.handlers

import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.UserSubscription

object EventsMenuBuilder {
    fun build(userId: Long, sub: UserSubscription, callerId: Long? = null): Pair<String, InlineKeyboardMarkup> {
        val notifStatus = if (sub.receiveNotifications) "🟢 Вкл" else "🔴 Выкл"
        val smsStatus = if (sub.receiveSms) "🟢 Вкл" else "🔴 Выкл"
        val callsStatus = if (sub.receiveCalls) "🟢 Вкл" else "🔴 Выкл"
        val systemStatus = if (sub.receiveSystem) "🟢 Вкл" else "🔴 Выкл"

        val isCommandsOnly = !sub.receiveNotifications && !sub.receiveSms && !sub.receiveCalls && !sub.receiveSystem
        val roleLabel = if (isCommandsOnly) "🕹 <b>Только управление</b> (рассылка отключена)" else "📬 <b>Получатель событий</b>"

        val isSelf = callerId == null || callerId == userId
        val userLabel = if (isSelf) "вашего аккаунта (ID: <code>$userId</code>)" else "пользователя (ID: <code>$userId</code>)"

        val text = """
            ⚙️ <b>Рассылка событий для $userLabel</b>
            
            Роль: $roleLabel
            
            • 🔔 Push-уведомления: <b>$notifStatus</b>
            • 💬 Входящие SMS: <b>$smsStatus</b>
            • 📞 Звонки / Пропущенные: <b>$callsStatus</b>
            • ⚙️ Системные (батарея, питание, датчики): <b>$systemStatus</b>
            
            <i>Нажимайте кнопки ниже для переключения категорий:</i>
        """.trimIndent()

        val notifBtn = if (sub.receiveNotifications) "🔔 Уведомления: [ВКЛ]" else "🔕 Уведомления: [ВЫКЛ]"
        val smsBtn = if (sub.receiveSms) "💬 SMS: [ВКЛ]" else "🚫 SMS: [ВЫКЛ]"
        val callsBtn = if (sub.receiveCalls) "📞 Звонки: [ВКЛ]" else "📵 Звонки: [ВЫКЛ]"
        val systemBtn = if (sub.receiveSystem) "⚙️ Система: [ВКЛ]" else "💤 Система: [ВЫКЛ]"

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
                    InlineKeyboardButton("🔕 Только команды (Выкл всё)", callbackData = "sub_toggle:$userId:all_off"),
                    InlineKeyboardButton("🔔 Включить всё", callbackData = "sub_toggle:$userId:all_on"),
                ),
                listOf(
                    InlineKeyboardButton("🔄 Обновить", callbackData = "sub_toggle:$userId:refresh"),
                ),
            ),
        )
        return Pair(text, markup)
    }
}

