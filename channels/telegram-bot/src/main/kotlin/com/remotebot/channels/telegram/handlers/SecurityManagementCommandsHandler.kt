package com.remotebot.channels.telegram.handlers

import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.UserSubscription
import com.remotebot.domain.repository.BotConfigRepository

class SecurityManagementCommandsHandler(
    private val botConfigRepository: BotConfigRepository,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository.config.value.botLanguage)

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
            else -> CommandResult.Error(strings.secUnknownCmd(cmd))
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
                    strings.secUserListEmpty()
                } else {
                    currentList.mapIndexed { idx, id -> "${idx + 1}. <code>$id</code>" }.joinToString("\n")
                }
            return CommandResult.Text(
                strings.secUserListBody(listStr),
            )
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "add" -> {
                val id: Long =
                    args.getOrNull(1)?.toLongOrNull()
                        ?: (if (command.source == CommandSource.TELEGRAM) command.senderId.toLongOrNull() else null)
                        ?: return CommandResult.Error(strings.secAddUserIdHint())
                if (currentList.contains(id)) {
                    return CommandResult.Text(strings.secUserExists(id.toString()))
                }
                botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList + id) }
                CommandResult.Text(strings.secUserAdded(id.toString()))
            }
            "del", "remove" -> {
                val id =
                    args.getOrNull(1)?.toLongOrNull()
                        ?: return CommandResult.Error(strings.secDelUserIdHint())
                if (!currentList.contains(id)) {
                    return CommandResult.Error(strings.secUserNotFound(id.toString()))
                }
                botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList - id) }
                CommandResult.Text(strings.secUserDeleted(id.toString()))
            }
            "clear" -> {
                botConfigRepository.updateConfig { it.copy(allowedUserIds = emptySet()) }
                CommandResult.Text(strings.secUserListCleared())
            }
            else -> {
                val directId = sub.toLongOrNull()
                if (directId != null) {
                    botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList + directId) }
                    CommandResult.Text(strings.secUserAdded(directId.toString()))
                } else {
                    CommandResult.Error(strings.secAddDelUserFmt())
                }
            }
        }
    }

    private suspend fun handleAddUser(command: IncomingCommand): CommandResult {
        val id: Long =
            command.args.firstOrNull()?.toLongOrNull()
                ?: (if (command.source == CommandSource.TELEGRAM) command.senderId.toLongOrNull() else null)
                ?: return CommandResult.Error(strings.secUserIdTgHint())
        val currentList = botConfigRepository.config.value.allowedUserIds
        if (currentList.contains(id)) {
            return CommandResult.Text(strings.secUserExistsTg(id.toString()))
        }
        botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList + id) }
        return CommandResult.Text(strings.secUserAdded(id.toString()))
    }

    private suspend fun handleDelUser(command: IncomingCommand): CommandResult {
        val id =
            command.args.firstOrNull()?.toLongOrNull()
                ?: return CommandResult.Error(strings.secDelUserIdTgHint())
        val currentList = botConfigRepository.config.value.allowedUserIds
        if (!currentList.contains(id)) {
            return CommandResult.Error(strings.secUserNotFoundTg(id.toString()))
        }
        botConfigRepository.updateConfig { it.copy(allowedUserIds = currentList - id) }
        return CommandResult.Text(strings.secUserDeletedTg(id.toString()))
    }

    private suspend fun handleWhitelistPhone(
        command: IncomingCommand,
        args: List<String>,
    ): CommandResult {
        val currentList = botConfigRepository.config.value.allowedPhoneNumbers
        if (args.isEmpty() || args[0].equals("list", ignoreCase = true)) {
            val listStr =
                if (currentList.isEmpty()) {
                    strings.secPhoneListEmpty()
                } else {
                    currentList.mapIndexed { idx, p -> "${idx + 1}. <code>$p</code>" }.joinToString("\n")
                }
            return CommandResult.Text(
                strings.secPhoneListBody(listStr),
            )
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "add" -> {
                val phone: String =
                    args.getOrNull(1)?.trim()
                        ?: (if (command.source == CommandSource.SMS && command.senderId.isNotBlank()) command.senderId.trim() else null)
                        ?: return CommandResult.Error(strings.secAddPhoneHint())
                if (currentList.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
                    return CommandResult.Text(strings.secPhoneExists(phone))
                }
                botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList + phone) }
                CommandResult.Text(strings.secPhoneAdded(phone))
            }
            "del", "remove" -> {
                val phone =
                    args.getOrNull(1)?.trim()
                        ?: return CommandResult.Error(strings.secDelPhoneHint())
                val matched = currentList.firstOrNull { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }
                if (matched == null) {
                    return CommandResult.Error(strings.secPhoneNotFound(phone))
                }
                botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList - matched) }
                CommandResult.Text(strings.secPhoneDeleted(phone))
            }
            "clear" -> {
                botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = emptySet()) }
                CommandResult.Text(strings.secPhoneListCleared())
            }
            else -> {
                val phone = args[0].trim()
                if (currentList.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
                    return CommandResult.Text(strings.secPhoneExists(phone))
                }
                botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList + phone) }
                CommandResult.Text(strings.secPhoneAdded(phone))
            }
        }
    }

    private suspend fun handleAddPhone(command: IncomingCommand): CommandResult {
        val phone: String =
            command.args.firstOrNull()?.trim()
                ?: (if (command.source == CommandSource.SMS && command.senderId.isNotBlank()) command.senderId.trim() else null)
                ?: return CommandResult.Error(strings.secAddPhonePhoneHint())
        val currentList = botConfigRepository.config.value.allowedPhoneNumbers
        if (currentList.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
            return CommandResult.Text(strings.secPhoneExists(phone))
        }
        botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList + phone) }
        return CommandResult.Text(strings.secPhoneNoPinAdded(phone))
    }

    private suspend fun handleDelPhone(command: IncomingCommand): CommandResult {
        val phone =
            command.args.firstOrNull()?.trim()
                ?: return CommandResult.Error(strings.secDelPhonePhoneHint())
        val currentList = botConfigRepository.config.value.allowedPhoneNumbers
        val matched = currentList.firstOrNull { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }
        if (matched == null) {
            return CommandResult.Error(strings.secPhoneNotFoundTg(phone))
        }
        botConfigRepository.updateConfig { it.copy(allowedPhoneNumbers = currentList - matched) }
        return CommandResult.Text(strings.secPhoneDeleted(phone))
    }

    private suspend fun handleSmsForward(
        command: IncomingCommand,
        args: List<String>,
    ): CommandResult {
        val currentTargets = botConfigRepository.config.value.smsForwardTargetNumbers
        if (args.isEmpty() || args[0].equals("list", ignoreCase = true)) {
            val listStr =
                if (currentTargets.isEmpty()) {
                    strings.secFwdListEmpty()
                } else {
                    currentTargets.mapIndexed { idx, p -> "${idx + 1}. <code>$p</code>" }.joinToString("\n")
                }
            val cfg = botConfigRepository.config.value
            return CommandResult.Text(
                strings.secFwdListBody(listStr, strings.secOnOff(cfg.smsForwardSmsEnabled), strings.secOnOff(cfg.smsForwardMissedCallsEnabled), strings.secOnOff(cfg.smsForwardNotificationsEnabled), strings.secOnOff(cfg.smsForwardBatteryChargingEnabled), strings.secOnOff(cfg.smsForwardBatteryLowEnabled), strings.secOnOff(cfg.smsTransliterationEnabled)),
            )
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "on", "enable" -> {
                botConfigRepository.updateConfig { it.copy(smsForwardSmsEnabled = true) }
                CommandResult.Text(strings.secSmsFwdOn())
            }
            "off", "disable" -> {
                botConfigRepository.updateConfig { it.copy(smsForwardSmsEnabled = false) }
                CommandResult.Text(strings.secSmsFwdOff())
            }
            "missed" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsForwardMissedCallsEnabled = enable) }
                CommandResult.Text(strings.secMissedFwd(enable))
            }
            "push", "notifications" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsForwardNotificationsEnabled = enable) }
                CommandResult.Text(strings.secPushFwd(enable))
            }
            "battery_charge", "charge", "charging" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsForwardBatteryChargingEnabled = enable) }
                CommandResult.Text(strings.secChargeFwd(enable))
            }
            "battery_low", "low_battery", "low" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsForwardBatteryLowEnabled = enable) }
                CommandResult.Text(
                    strings.secLowBattFwd(enable),
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
                CommandResult.Text(strings.secAllBattFwd(enable))
            }
            "translit" -> {
                val state = args.getOrNull(1)?.lowercase()
                val enable = state == "on" || state == "1" || state == "true"
                botConfigRepository.updateConfig { it.copy(smsTransliterationEnabled = enable) }
                CommandResult.Text(strings.secTranslitFwd(enable))
            }
            "add" -> {
                val phone: String =
                    args.getOrNull(1)?.trim()
                        ?: (if (command.source == CommandSource.SMS && command.senderId.isNotBlank()) command.senderId.trim() else null)
                        ?: return CommandResult.Error(strings.secAddFwdHint())
                if (currentTargets.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
                    return CommandResult.Text(strings.secFwdExists(phone))
                }
                botConfigRepository.updateConfig {
                    it.copy(
                        smsForwardTargetNumbers = currentTargets + phone,
                        smsForwardSmsEnabled = true,
                    )
                }
                CommandResult.Text(strings.secFwdAdded(phone))
            }
            "del", "remove" -> {
                val phone =
                    args.getOrNull(1)?.trim()
                        ?: return CommandResult.Error(strings.secDelFwdHint())
                val matched = currentTargets.firstOrNull { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }
                if (matched == null) {
                    return CommandResult.Error(strings.secFwdNotFound(phone))
                }
                val updated = currentTargets - matched
                botConfigRepository.updateConfig {
                    it.copy(smsForwardTargetNumbers = updated)
                }
                CommandResult.Text(strings.secFwdDeleted(phone))
            }
            "clear" -> {
                botConfigRepository.updateConfig {
                    it.copy(smsForwardTargetNumbers = emptySet())
                }
                CommandResult.Text(strings.secFwdCleared())
            }
            else -> {
                val phone = args[0].trim()
                if (currentTargets.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
                    return CommandResult.Text(strings.secFwdExists(phone))
                }
                botConfigRepository.updateConfig {
                    it.copy(
                        smsForwardTargetNumbers = currentTargets + phone,
                        smsForwardSmsEnabled = true,
                    )
                }
                CommandResult.Text(strings.secFwdAddedShort(phone))
            }
        }
    }

    private suspend fun handleAddForward(command: IncomingCommand): CommandResult {
        val phone: String =
            command.args.firstOrNull()?.trim()
                ?: (if (command.source == CommandSource.SMS && command.senderId.isNotBlank()) command.senderId.trim() else null)
                ?: return CommandResult.Error(strings.secAddFwdPhoneHint())
        val currentTargets = botConfigRepository.config.value.smsForwardTargetNumbers
        if (currentTargets.any { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }) {
            return CommandResult.Text(strings.secFwdExists(phone))
        }
        botConfigRepository.updateConfig {
            it.copy(
                smsForwardTargetNumbers = currentTargets + phone,
                smsForwardSmsEnabled = true,
            )
        }
        return CommandResult.Text(strings.secFwdAddedCalls(phone))
    }

    private suspend fun handleDelForward(command: IncomingCommand): CommandResult {
        val phone =
            command.args.firstOrNull()?.trim()
                ?: return CommandResult.Error(strings.secDelFwdPhoneHint())
        val currentTargets = botConfigRepository.config.value.smsForwardTargetNumbers
        val matched = currentTargets.firstOrNull { it.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }
        if (matched == null) {
            return CommandResult.Error(strings.secFwdNotFound(phone))
        }
        botConfigRepository.updateConfig {
            it.copy(smsForwardTargetNumbers = currentTargets - matched)
        }
        return CommandResult.Text(strings.secFwdDeleted(phone))
    }

    private suspend fun handleEventsCommand(
        command: IncomingCommand,
        args: List<String>,
    ): CommandResult {
        val callerId = command.chatId.takeIf { it != 0L } ?: command.senderId.toLongOrNull() ?: 0L
        val config = botConfigRepository.config.value

        if (args.isEmpty()) {
            val sub = config.userSubscriptions[callerId] ?: UserSubscription(userId = callerId)
            val (text, markup) = EventsMenuBuilder.build(callerId, sub, callerId, strings)
            return CommandResult.Text(text, keyboard = markup)
        }

        val firstArg = args[0].lowercase()

        // 1. View user list, roles, and subscriptions
        if (firstArg == "list") {
            val users = config.allowedUserIds
            if (users.isEmpty()) {
                val sub = config.userSubscriptions[callerId] ?: UserSubscription(userId = callerId)
                val (text, markup) = EventsMenuBuilder.build(callerId, sub, callerId, strings)
                return CommandResult.Text(strings.secEventsEmpty(text), keyboard = markup)
            }
            val sb = StringBuilder(strings.secSubsHead())
            users.forEachIndexed { idx, uId ->
                val sub = config.userSubscriptions[uId] ?: UserSubscription(userId = uId)
                val isSelf = uId == callerId
                val selfTag = if (isSelf) strings.secSelfTag() else ""
                val notif = if (sub.receiveNotifications) "🟢" else "🔴"
                val sms = if (sub.receiveSms) "🟢" else "🔴"
                val calls = if (sub.receiveCalls) "🟢" else "🔴"
                val sys = if (sub.receiveSystem) "🟢" else "🔴"
                val isCommandsOnly = !sub.receiveNotifications && !sub.receiveSms && !sub.receiveCalls && !sub.receiveSystem
                val role = if (isCommandsOnly) strings.secRoleCmds() else strings.secRoleRecv()
                sb.appendLine("${idx + 1}. <code>$uId</code>$selfTag — $role")
                sb.appendLine("   • Notif: $notif | SMS: $sms | Calls: $calls | Sys: $sys")
            }
            sb.append(strings.secEventsHint())
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
                strings.secEventsOffSelf(callerId.toString()),
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
                strings.secEventsOnSelf(callerId.toString()),
            )
        }

        // 3. Manage specific user by ID: /events <targetId> [off|on|category]
        val targetId = firstArg.toLongOrNull()
        if (targetId != null) {
            val targetSub = config.userSubscriptions[targetId] ?: UserSubscription(userId = targetId)
            if (args.size == 1) {
                val (text, markup) = EventsMenuBuilder.build(targetId, targetSub, callerId, strings)
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
                    CommandResult.Text(strings.secEventsOffUser(targetId.toString()))
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
                    CommandResult.Text(strings.secEventsOnUser(targetId.toString()))
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
                            else -> return CommandResult.Error(strings.secUnknownCategory(category))
                        }
                    botConfigRepository.updateConfig { it.copy(userSubscriptions = it.userSubscriptions + (targetId to updated)) }
                    val stateLabel = strings.secStateLabel(turnOn)
                    CommandResult.Text(strings.secCategoryForUser(category, targetId.toString(), stateLabel))
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
                        strings.secEventsUsage(),
                    )
            }
        botConfigRepository.updateConfig { it.copy(userSubscriptions = it.userSubscriptions + (callerId to updated)) }
        val stateLabel = strings.secStateLabel(turnOn)
        return CommandResult.Text(strings.secCategorySelf(category, stateLabel))
    }
}
