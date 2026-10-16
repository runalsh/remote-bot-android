package com.remotebot.channels.telegram.handlers

import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.UserSubscription
import com.remotebot.domain.repository.BotConfigRepository

class SecurityManagementCommandsHandler(
    private val botConfigRepository: BotConfigRepository,
) : CommandHandler {
    override val commandNames: List<String> =
        listOf(
            "whitelist_user",
            "whitelist_phone",
            "sms_forward",
            "add_user",
            "del_user",
            "list_users",
            "add_phone",
            "del_phone",
            "list_phones",
            "add_forward",
            "del_forward",
            "list_forwards",
            "user",
            "phone",
            "forward",
            "events",
            "subs",
        )

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "whitelist_user", "user" -> handleWhitelistUser(command, command.args)
            "whitelist_phone", "phone" -> handleWhitelistPhone(command, command.args)
            "sms_forward", "forward" -> handleSmsForward(command, command.args)
            "add_user" -> handleAddUser(command)
            "del_user" -> handleDelUser(command)
            "list_users" -> handleWhitelistUser(command, listOf("list"))
            "add_phone" -> handleAddPhone(command)
            "del_phone" -> handleDelPhone(command)
            "list_phones" -> handleWhitelistPhone(command, listOf("list"))
            "add_forward" -> handleAddForward(command)
            "del_forward" -> handleDelForward(command)
            "list_forwards" -> handleSmsForward(command, listOf("list"))
            "events", "subs" -> handleEventsCommand(command, command.args)
            else -> CommandResult.Error("Неизвестная команда безопасности: $cmd")
        }
    }

    private suspend fun handleWhitelistUser(
        command: IncomingCommand,
        args: List<String>,
    ): CommandResult {
        val currentList = botConfigRepository.config.value.allowedUserIds
        if (args.isEmpty() || args[0].equals("list", ignoreCase = true)) {
            val listStr =
                if (currentList.isEmpty()) {
                    "<i>Список пуст (доступ разрешен всем или только первому подключившемуся)</i>"
                } else {
                    currentList.mapIndexed { idx, id -> "${idx + 1}. <code>$id</code>" }.joinToString("\n")
                }
            return CommandResult.Text(
                """
                👥 <b>Белый список пользователей Telegram (User ID):</b>
                
                $listStr
                
                <i>Управление:</i>
                <code>/add_user [id]</code> — добавить пользователя (по умолч. ваш ID)
                <code>/del_user &lt;id&gt;</code> — удалить пользователя
                <code>/whitelist_user clear</code> — очистить список
                """.trimIndent(),
            )
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "add" -> {
                val id: Long =
                    args.getOrNull(1)?.toLongOrNull()
                        ?: (if (command.source == CommandSource.TELEGRAM) command.senderId.toLongOrNull() else null)
                        ?: return CommandResult.Error("Укажите числовой User ID: <code>/add_user 123456789</code>")
                if (currentList.contains(id)) {
                    return CommandResult.Text("ℹ️ Пользователь <code>$id</code> уже есть в белом списке.")
                }
                botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList + id) }
                CommandResult.Text("✅ Пользователь <code>$id</code> добавлен в белый список Telegram.")
            }
            "del", "remove" -> {
                val id =
                    args.getOrNull(1)?.toLongOrNull()
                        ?: return CommandResult.Error("Укажите числовой User ID: <code>/del_user 123456789</code>")
                if (!currentList.contains(id)) {
                    return CommandResult.Error("Пользователь <code>$id</code> не найден в списке.")
                }
                botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList - id) }
                CommandResult.Text("🗑 Пользователь <code>$id</code> удален из белого списка.")
            }
            "clear" -> {
                botConfigRepository.updateConfig { it.copy(allowedUserIds = emptySet()) }
                CommandResult.Text("⚠️ Белый список пользователей Telegram полностью очищен.")
            }
            else -> {
                val directId = sub.toLongOrNull()
                if (directId != null) {
                    botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList + directId) }
                    CommandResult.Text("✅ Пользователь <code>$directId</code> добавлен в белый список Telegram.")
                } else {
                    CommandResult.Error("Формат: <code>/add_user &lt;id&gt;</code> или <code>/del_user &lt;id&gt;</code>")
                }
            }
        }
    }

    private suspend fun handleAddUser(command: IncomingCommand): CommandResult {
        val id: Long =
            command.args.firstOrNull()?.toLongOrNull()
                ?: (if (command.source == CommandSource.TELEGRAM) command.senderId.toLongOrNull() else null)
                ?: return CommandResult.Error("Укажите числовой Telegram User ID: <code>/add_user 123456789</code>")
        val currentList = botConfigRepository.config.value.allowedUserIds
        if (currentList.contains(id)) {
            return CommandResult.Text("ℹ️ Пользователь <code>$id</code> уже есть в белом списке Telegram.")
        }
        botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList + id) }
        return CommandResult.Text("✅ Пользователь <code>$id</code> добавлен в белый список Telegram.")
    }

    private suspend fun handleDelUser(command: IncomingCommand): CommandResult {
        val id =
            command.args.firstOrNull()?.toLongOrNull()
                ?: return CommandResult.Error("Укажите числовой Telegram User ID: <code>/del_user 123456789</code>")
        val currentList = botConfigRepository.config.value.allowedUserIds
        if (!currentList.contains(id)) {
            return CommandResult.Error("Пользователь <code>$id</code> не найден в белом списке.")
        }
        botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList - id) }
        return CommandResult.Text("🗑 Пользователь <code>$id</code> удален из белого списка Telegram.")
    }

    private suspend fun handleWhitelistPhone(
        command: IncomingCommand,
        args: List<String>,
    ): CommandResult {
        val currentList = botConfigRepository.config.value.allowedPhoneNumbers
        if (args.isEmpty() || args[0].equals("list", ignoreCase = true)) {
            val listStr =
                if (currentList.isEmpty()) {
                    "<i>Список пуст (управление без PIN отключено)</i>"
                } else {
                    currentList.mapIndexed { idx, p -> "${idx + 1}. <code>$p</code>" }.joinToString("\n")
                }
            return CommandResult.Text(
                """
                📱 <b>Доверенные номера SMS-управления (без PIN):</b>
                
                $listStr
                
                <i>Управление:</i>
                <code>/add_phone [номер]</code> — добавить номер (по умолч. ваш номер при SMS)
                <code>/del_phone &lt;номер&gt;</code> — удалить номер
                <code>/whitelist_phone clear</code> — очистить список
                """.trimIndent(),
            )
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "add" -> {
                val phone: String =
                    args.getOrNull(1)?.trim()
                        ?: (if (command.source == CommandSource.SMS && command.senderId.isNotBlank()) command.senderId.trim() else null)
                        ?: return CommandResult.Error("Укажите номер: <code>/add_phone +79991234567</code>")
                if (currentList.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
                    return CommandResult.Text("ℹ️ Номер <code>$phone</code> уже есть в доверенном списке SMS.")
                }
                botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList + phone) }
                CommandResult.Text("✅ Номер <code>$phone</code> добавлен в доверенный список SMS-управления.")
            }
            "del", "remove" -> {
                val phone =
                    args.getOrNull(1)?.trim()
                        ?: return CommandResult.Error("Укажите номер: <code>/del_phone +79991234567</code>")
                val matched = currentList.firstOrNull { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }
                if (matched == null) {
                    return CommandResult.Error("Номер <code>$phone</code> не найден в доверенном списке.")
                }
                botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList - matched) }
                CommandResult.Text("🗑 Номер <code>$phone</code> удален из доверенного списка SMS.")
            }
            "clear" -> {
                botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = emptySet()) }
                CommandResult.Text("⚠️ Доверенный список SMS-управления очищен.")
            }
            else -> {
                val phone = args[0].trim()
                if (currentList.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
                    return CommandResult.Text("ℹ️ Номер <code>$phone</code> уже есть в доверенном списке SMS.")
                }
                botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList + phone) }
                CommandResult.Text("✅ Номер <code>$phone</code> добавлен в доверенный список SMS-управления.")
            }
        }
    }

    private suspend fun handleAddPhone(command: IncomingCommand): CommandResult {
        val phone: String =
            command.args.firstOrNull()?.trim()
                ?: (if (command.source == CommandSource.SMS && command.senderId.isNotBlank()) command.senderId.trim() else null)
                ?: return CommandResult.Error("Укажите номер телефона: <code>/add_phone +79991234567</code>")
        val currentList = botConfigRepository.config.value.allowedPhoneNumbers
        if (currentList.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
            return CommandResult.Text("ℹ️ Номер <code>$phone</code> уже есть в доверенном списке SMS.")
        }
        botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList + phone) }
        return CommandResult.Text("✅ Номер <code>$phone</code> добавлен в доверенный список SMS-управления (без PIN).")
    }

    private suspend fun handleDelPhone(command: IncomingCommand): CommandResult {
        val phone =
            command.args.firstOrNull()?.trim()
                ?: return CommandResult.Error("Укажите номер телефона: <code>/del_phone +79991234567</code>")
        val currentList = botConfigRepository.config.value.allowedPhoneNumbers
        val matched = currentList.firstOrNull { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }
        if (matched == null) {
            return CommandResult.Error("Номер <code>$phone</code> не найден в доверенном списке SMS.")
        }
        botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList - matched) }
        return CommandResult.Text("🗑 Номер <code>$phone</code> удален из доверенного списка SMS.")
    }

    private suspend fun handleSmsForward(
        command: IncomingCommand,
        args: List<String>,
    ): CommandResult {
        val currentTargets = botConfigRepository.config.value.smsForwardTargetNumbers
        if (args.isEmpty() || args[0].equals("list", ignoreCase = true)) {
            val listStr =
                if (currentTargets.isEmpty()) {
                    "<i>Список номеров назначения пуст (пересылка отключена)</i>"
                } else {
                    currentTargets.mapIndexed { idx, p -> "${idx + 1}. <code>$p</code>" }.joinToString("\n")
                }
            val cfg = botConfigRepository.config.value
            return CommandResult.Text(
                """
                📤 <b>Номера пересылки SMS и Уведомлений:</b>
                
                $listStr
                
                • Пересылка SMS: ${if (cfg.smsForwardSmsEnabled) "Вкл ✅" else "Выкл ❌"}
                • Пересылка пропущенных звонков: ${if (cfg.smsForwardMissedCallsEnabled) "Вкл ✅" else "Выкл ❌"}
                • Пересылка Push: ${if (cfg.smsForwardNotificationsEnabled) "Вкл ✅" else "Выкл ❌"}
                • Статус зарядки/питания: ${if (cfg.smsForwardBatteryChargingEnabled) "Вкл ✅" else "Выкл ❌"}
                • Низкий заряд (≤20% каждые 3%): ${if (cfg.smsForwardBatteryLowEnabled) "Вкл ✅" else "Выкл ❌"}
                • Транслитерация: ${if (cfg.smsTransliterationEnabled) "Вкл ✅" else "Выкл ❌"}
                
                <i>Управление:</i>
                <code>/add_forward [номер]</code> — добавить номер назначения
                <code>/del_forward &lt;номер&gt;</code> — удалить номер назначения
                <code>/sms_forward on|off</code> — вкл/выкл пересылку SMS
                <code>/sms_forward missed on|off</code> — вкл/выкл пересылку звонков
                <code>/sms_forward push on|off</code> — вкл/выкл пересылку Push
                <code>/sms_forward battery_charge on|off</code> — вкл/выкл статус зарядки
                <code>/sms_forward battery_low on|off</code> — вкл/выкл низкий заряд (≤20% каждые 3%)
                <code>/sms_forward battery on|off</code> — вкл/выкл все оповещения батареи
                <code>/sms_forward translit on|off</code> — вкл/выкл транслитерацию
                <code>/sms_forward clear</code> — очистить все номера
                """.trimIndent(),
            )
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "on", "enable" -> {
                botConfigRepository.updateConfig { it.copy(smsForwardSmsEnabled = true) }
                CommandResult.Text("✅ Пересылка входящих SMS по сотовой связи включена.")
            }
            "off", "disable" -> {
                botConfigRepository.updateConfig { it.copy(smsForwardSmsEnabled = false) }
                CommandResult.Text("⏹ Пересылка входящих SMS отключена.")
            }
            "missed" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsForwardMissedCallsEnabled = enable) }
                CommandResult.Text("Пересылка пропущенных вызовов по SMS: ${if (enable) "Включена ✅" else "Отключена ⏹"}")
            }
            "push", "notifications" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsForwardNotificationsEnabled = enable) }
                CommandResult.Text("Пересылка Push-уведомлений по SMS: ${if (enable) "Включена ✅" else "Отключена ⏹"}")
            }
            "battery_charge", "charge", "charging" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsForwardBatteryChargingEnabled = enable) }
                CommandResult.Text("Пересылка статуса зарядки по SMS: ${if (enable) "Включена ✅" else "Отключена ⏹"}")
            }
            "battery_low", "low_battery", "low" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsForwardBatteryLowEnabled = enable) }
                CommandResult.Text(
                    "Пересылка низкого заряда батареи (≤20% каждые 3%) по SMS: ${if (enable) "Включена ✅" else "Отключена ⏹"}",
                )
            }
            "battery" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig {
                    it.copy(
                        smsForwardBatteryChargingEnabled = enable,
                        smsForwardBatteryLowEnabled = enable,
                    )
                }
                CommandResult.Text("Все оповещения о батарее по SMS: ${if (enable) "Включены ✅" else "Отключены ⏹"}")
            }
            "translit" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsTransliterationEnabled = enable) }
                CommandResult.Text("Транслитерация SMS (Латиница GOST): ${if (enable) "Включена ✅" else "Отключена ⏹"}")
            }
            "add" -> {
                val phone: String =
                    args.getOrNull(1)?.trim()
                        ?: (if (command.source == CommandSource.SMS && command.senderId.isNotBlank()) command.senderId.trim() else null)
                        ?: return CommandResult.Error("Укажите номер: <code>/add_forward +79991234567</code>")
                if (currentTargets.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
                    return CommandResult.Text("ℹ️ Номер <code>$phone</code> уже есть в списке пересылки SMS.")
                }
                botConfigRepository.updateConfig {
                    it.copy(
                        smsForwardTargetNumbers = currentTargets + phone,
                        smsForwardSmsEnabled = true,
                    )
                }
                CommandResult.Text("✅ Номер <code>$phone</code> добавлен в список пересылки SMS. Пересылка активирована!")
            }
            "del", "remove" -> {
                val phone =
                    args.getOrNull(1)?.trim()
                        ?: return CommandResult.Error("Укажите номер: <code>/del_forward +79991234567</code>")
                val matched = currentTargets.firstOrNull { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }
                if (matched == null) {
                    return CommandResult.Error("Номер <code>$phone</code> не найден в списке пересылки.")
                }
                val updated = currentTargets - matched
                botConfigRepository.updateConfig {
                    it.copy(smsForwardTargetNumbers = updated)
                }
                CommandResult.Text("🗑 Номер <code>$phone</code> удален из списка пересылки.")
            }
            "clear" -> {
                botConfigRepository.updateConfig {
                    it.copy(smsForwardTargetNumbers = emptySet())
                }
                CommandResult.Text("⚠️ Список номеров пересылки SMS очищен.")
            }
            else -> {
                val phone = args[0].trim()
                if (currentTargets.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
                    return CommandResult.Text("ℹ️ Номер <code>$phone</code> уже есть в списке пересылки SMS.")
                }
                botConfigRepository.updateConfig {
                    it.copy(
                        smsForwardTargetNumbers = currentTargets + phone,
                        smsForwardSmsEnabled = true,
                    )
                }
                CommandResult.Text("✅ Номер <code>$phone</code> добавлен для пересылки SMS!")
            }
        }
    }

    private suspend fun handleAddForward(command: IncomingCommand): CommandResult {
        val phone: String =
            command.args.firstOrNull()?.trim()
                ?: (if (command.source == CommandSource.SMS && command.senderId.isNotBlank()) command.senderId.trim() else null)
                ?: return CommandResult.Error("Укажите номер телефона: <code>/add_forward +79991234567</code>")
        val currentTargets = botConfigRepository.config.value.smsForwardTargetNumbers
        if (currentTargets.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
            return CommandResult.Text("ℹ️ Номер <code>$phone</code> уже есть в списке пересылки SMS.")
        }
        botConfigRepository.updateConfig {
            it.copy(
                smsForwardTargetNumbers = currentTargets + phone,
                smsForwardSmsEnabled = true,
            )
        }
        return CommandResult.Text("✅ Номер <code>$phone</code> добавлен в список пересылки SMS/звонков. Пересылка активирована!")
    }

    private suspend fun handleDelForward(command: IncomingCommand): CommandResult {
        val phone =
            command.args.firstOrNull()?.trim()
                ?: return CommandResult.Error("Укажите номер телефона: <code>/del_forward +79991234567</code>")
        val currentTargets = botConfigRepository.config.value.smsForwardTargetNumbers
        val matched = currentTargets.firstOrNull { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }
        if (matched == null) {
            return CommandResult.Error("Номер <code>$phone</code> не найден в списке пересылки.")
        }
        botConfigRepository.updateConfig {
            it.copy(smsForwardTargetNumbers = currentTargets - matched)
        }
        return CommandResult.Text("🗑 Номер <code>$phone</code> удален из списка пересылки.")
    }

    private suspend fun handleEventsCommand(
        command: IncomingCommand,
        args: List<String>,
    ): CommandResult {
        val callerId = command.chatId.takeIf { it != 0L } ?: command.senderId.toLongOrNull() ?: 0L
        val config = botConfigRepository.config.value

        if (args.isEmpty()) {
            val sub = config.userSubscriptions[callerId] ?: UserSubscription(userId = callerId)
            val (text, markup) = EventsMenuBuilder.build(callerId, sub, callerId)
            return CommandResult.Text(text, keyboard = markup)
        }

        val firstArg = args[0].lowercase()

        // 1. View user list, roles, and subscriptions
        if (firstArg == "list") {
            val users = config.allowedUserIds
            if (users.isEmpty()) {
                val sub = config.userSubscriptions[callerId] ?: UserSubscription(userId = callerId)
                val (text, markup) = EventsMenuBuilder.build(callerId, sub, callerId)
                return CommandResult.Text("ℹ️ Белый список пользователей пуст. Меню ваших подписок:\n\n$text", keyboard = markup)
            }
            val sb = StringBuilder("📋 <b>Статус рассылки событий по пользователям:</b>\n\n")
            users.forEachIndexed { idx, uId ->
                val sub = config.userSubscriptions[uId] ?: UserSubscription(userId = uId)
                val isSelf = uId == callerId
                val selfTag = if (isSelf) " (Вы)" else ""
                val notif = if (sub.receiveNotifications) "🟢" else "🔴"
                val sms = if (sub.receiveSms) "🟢" else "🔴"
                val calls = if (sub.receiveCalls) "🟢" else "🔴"
                val sys = if (sub.receiveSystem) "🟢" else "🔴"
                val isCommandsOnly = !sub.receiveNotifications && !sub.receiveSms && !sub.receiveCalls && !sub.receiveSystem
                val role = if (isCommandsOnly) "🕹 <i>Только команды</i>" else "📬 <i>Получатель</i>"
                sb.appendLine("${idx + 1}. <code>$uId</code>$selfTag — $role")
                sb.appendLine("   • Notif: $notif | SMS: $sms | Calls: $calls | Sys: $sys")
            }
            sb.append("\n<i>Управление: <code>/events [id]</code> или <code>/events &lt;on|off&gt;</code></i>")
            return CommandResult.Text(sb.toString())
        }

        // 2. Enable / Disable all for self
        if (firstArg == "off") {
            val current = config.userSubscriptions[callerId] ?: UserSubscription(userId = callerId)
            val updated =
                current.copy(
                    receiveNotifications = false,
                    receiveSms = false,
                    receiveCalls = false,
                    receiveSystem = false,
                )
            botConfigRepository.updateConfig { it.copy(userSubscriptions = it.userSubscriptions + (callerId to updated)) }
            return CommandResult.Text(
                "🔕 Рассылка всех событий для вашего аккаунта (<code>$callerId</code>) <b>выключена</b>.\n" +
                    "Вы переведены в роль <b>Только управление</b> (все команды доступны, фоновый спам отключен).",
            )
        }

        if (firstArg == "on") {
            val current = config.userSubscriptions[callerId] ?: UserSubscription(userId = callerId)
            val updated =
                current.copy(
                    receiveNotifications = true,
                    receiveSms = true,
                    receiveCalls = true,
                    receiveSystem = false,
                )
            botConfigRepository.updateConfig { it.copy(userSubscriptions = it.userSubscriptions + (callerId to updated)) }
            return CommandResult.Text(
                "🔔 Рассылка основных событий для вашего аккаунта (<code>$callerId</code>) <b>включена</b> (push-уведомления, SMS, звонки).",
            )
        }

        // 3. Manage specific user by ID: /events <targetId> [off|on|category]
        val targetId = firstArg.toLongOrNull()
        if (targetId != null) {
            val targetSub = config.userSubscriptions[targetId] ?: UserSubscription(userId = targetId)
            if (args.size == 1) {
                val (text, markup) = EventsMenuBuilder.build(targetId, targetSub, callerId)
                return CommandResult.Text(text, keyboard = markup)
            }
            val subAction = args[1].lowercase()
            return when (subAction) {
                "off" -> {
                    val updated =
                        targetSub.copy(
                            receiveNotifications = false,
                            receiveSms = false,
                            receiveCalls = false,
                            receiveSystem = false,
                        )
                    botConfigRepository.updateConfig { it.copy(userSubscriptions = it.userSubscriptions + (targetId to updated)) }
                    CommandResult.Text("🔕 Рассылка событий для пользователя <code>$targetId</code> выключена (режим «Только команды»).")
                }
                "on" -> {
                    val updated =
                        targetSub.copy(
                            receiveNotifications = true,
                            receiveSms = true,
                            receiveCalls = true,
                            receiveSystem = false,
                        )
                    botConfigRepository.updateConfig { it.copy(userSubscriptions = it.userSubscriptions + (targetId to updated)) }
                    CommandResult.Text("🔔 Рассылка событий для пользователя <code>$targetId</code> включена.")
                }
                else -> {
                    val category = subAction
                    val state = args.getOrNull(2)?.lowercase()
                    val turnOn = state == "on" || state == "1" || state == "true"
                    val updated =
                        when (category) {
                            "notif", "notifications" -> targetSub.copy(receiveNotifications = turnOn)
                            "sms" -> targetSub.copy(receiveSms = turnOn)
                            "calls" -> targetSub.copy(receiveCalls = turnOn)
                            "system", "sys" -> targetSub.copy(receiveSystem = turnOn)
                            else -> return CommandResult.Error("Неизвестная категория: $category. Доступно: notif, sms, calls, system, on, off")
                        }
                    botConfigRepository.updateConfig { it.copy(userSubscriptions = it.userSubscriptions + (targetId to updated)) }
                    val stateLabel = if (turnOn) "включена 🟢" else "выключена 🔴"
                    CommandResult.Text("✅ Категория <b>$category</b> для <code>$targetId</code> $stateLabel.")
                }
            }
        }

        // 4. Text category toggle for self: /events <notif|sms|calls|system> <on|off>
        val category = firstArg
        val state = args.getOrNull(1)?.lowercase()
        val current = config.userSubscriptions[callerId] ?: UserSubscription(userId = callerId)
        val turnOn = state == null || state == "on" || state == "1" || state == "true"
        val updated =
            when (category) {
                "notif", "notifications" -> current.copy(receiveNotifications = turnOn)
                "sms" -> current.copy(receiveSms = turnOn)
                "calls" -> current.copy(receiveCalls = turnOn)
                "system", "sys" -> current.copy(receiveSystem = turnOn)
                else ->
                    return CommandResult.Error(
                        "Формат команды:\n" +
                            "• <code>/events</code> — открыть интерактивное меню\n" +
                            "• <code>/events list</code> — список подписок пользователей\n" +
                            "• <code>/events &lt;on|off&gt;</code> — включить/выключить всё\n" +
                            "• <code>/events &lt;notif|sms|calls|system&gt; &lt;on|off&gt;</code> — переключить категорию\n" +
                            "• <code>/events &lt;user_id&gt; [on|off]</code> — настройка конкретного пользователя",
                    )
            }
        botConfigRepository.updateConfig { it.copy(userSubscriptions = it.userSubscriptions + (callerId to updated)) }
        val stateLabel = if (turnOn) "включена 🟢" else "выключена 🔴"
        return CommandResult.Text("✅ Категория <b>$category</b> для вашего аккаунта $stateLabel.")
    }
}
