package com.remotebot.channels.telegram.handlers

import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.model.BotLanguage
import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CallType
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.NotificationRule
import com.remotebot.domain.repository.NotificationRepository
import com.remotebot.domain.repository.TelephonyRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TelephonyAndNotificationCommandsHandler(
    private val telephonyRepository: TelephonyRepository,
    private val notificationRepository: NotificationRepository,
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

    override val commandNames: List<String> =
        listOf(
            "sms",
            "sms_inbox",
            "call",
            "callshistory",
            "ussd",
            "notify",
            "notifications",
            "notify_send",
            "answer",
            "resp",
            "notify_clear",
            "notify_rule",
            "fake_notify",
            "simulate_notify",
            "notify_act",
        )

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "sms" -> handleSendSms(command.args)
            "sms_inbox" -> handleSmsInbox(command.args)
            "call" -> handleMakeCall(command.args)
            "callshistory" -> handleCallsHistory(command.args)
            "ussd" -> handleSendUssd(command.args)
            "notify", "notifications" -> {
                if (command.args.size >= 2 && command.args[0].toIntOrNull() == null) {
                    handlePostNotification(command.args)
                } else {
                    handleListNotifications(command.args)
                }
            }
            "notify_send" -> handlePostNotification(command.args)
            "fake_notify", "simulate_notify" -> handleSimulateNotification(command.args)
            "answer", "resp" -> handleAnswerNotification(command.args)
            "notify_act" -> handleNotifyAction(command.args)
            "notify_clear" -> handleClearNotification(command.args)
            "notify_rule" -> handleNotificationRule(command.args)
            else -> CommandResult.Error(strings.tnUnknownCmd(cmd))
        }
    }

    private suspend fun handleSendSms(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                strings.tnSmsUsage(),
            )
        }

        var simSlot: Int? = null
        var phoneIndex = 0
        if (args[0] == "1" || args[0] == "2") {
            simSlot = args[0].toIntOrNull()
            phoneIndex = 1
        }

        if (args.size <= phoneIndex + 1) {
            return CommandResult.Error(strings.tnSmsNeedArgs())
        }

        val phoneNumber = args[phoneIndex]
        val text = args.subList(phoneIndex + 1, args.size).joinToString(" ")

        return telephonyRepository.sendSms(phoneNumber, text, simSlot).fold(
            onSuccess = {
                val simText = if (simSlot != null) " (SIM $simSlot)" else ""
                CommandResult.Text(strings.tnSmsSent(phoneNumber, simText, text))
            },
            onFailure = {
                CommandResult.Error(strings.tnSmsError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleSmsInbox(args: List<String>): CommandResult {
        val limit = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 50) ?: 5
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        return telephonyRepository.getRecentSms(limit).fold(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    CommandResult.Text(strings.tnSmsInboxEmpty())
                } else {
                    val sb = StringBuilder(strings.tnSmsInboxHeader(limit))
                    list.forEachIndexed { index, sms ->
                        val icon = if (sms.isIncoming) "📥" else "📤"
                        val timeStr = dateFormat.format(Date(sms.dateMs))
                        val contactName = telephonyRepository.resolveContactName(sms.address)
                        val addressDisplay =
                            if (!contactName.isNullOrBlank()) {
                                "<b>${escapeHtml(contactName)}</b> (<code>${escapeHtml(sms.address)}</code>)"
                            } else {
                                "<code>${escapeHtml(sms.address)}</code>"
                            }
                        val bodyClean = escapeHtml(sms.body.trim())
                        val formattedBody = DIGIT_REGEX.replace(bodyClean) { "<code>${it.value}</code>" }
                        sb.append("$icon <b>#${index + 1} |</b> $addressDisplay (<i>$timeStr</i>)\n")
                        sb.append("$formattedBody\n\n")
                    }
                    CommandResult.Text(sb.toString().trimEnd())
                }
            },
            onFailure = {
                CommandResult.Error(strings.tnSmsReadError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleMakeCall(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(strings.tnCallUsage())
        }

        var simSlot: Int? = null
        var phoneIndex = 0
        if (args[0] == "1" || args[0] == "2") {
            simSlot = args[0].toIntOrNull()
            phoneIndex = 1
        }

        if (args.size <= phoneIndex) {
            return CommandResult.Error(strings.tnCallNeedNumber())
        }

        val phoneNumber = args[phoneIndex]
        return telephonyRepository.makeCall(phoneNumber, simSlot).fold(
            onSuccess = {
                val simText = if (simSlot != null) " (SIM $simSlot)" else ""
                CommandResult.Text(strings.tnCallInitiated(phoneNumber, simText))
            },
            onFailure = {
                CommandResult.Error(strings.tnCallError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleCallsHistory(args: List<String>): CommandResult {
        val limit = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 50) ?: 10
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        return telephonyRepository.getCallHistory(limit).fold(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    CommandResult.Text(strings.tnCallsEmpty())
                } else {
                    val sb = StringBuilder(strings.tnCallsHeader(limit))
                    list.forEachIndexed { index, call ->
                        val icon =
                            when (call.type) {
                                CallType.INCOMING -> "📥"
                                CallType.OUTGOING -> "↗️"
                                CallType.MISSED -> "🔴"
                                CallType.REJECTED -> "🚫"
                                CallType.BLOCKED -> "⛔"
                                CallType.UNKNOWN -> "📞"
                            }
                        val nameStr = if (!call.name.isNullOrBlank()) " (${call.name})" else ""
                        val timeStr = dateFormat.format(Date(call.dateMs))
                        val durationStr = if (call.durationSec > 0) strings.tnCallDuration(call.durationSec / 60, call.durationSec % 60) else ""

                        sb.append("$icon <b>#${index + 1} | ${call.number}</b>$nameStr$durationStr\n")
                        sb.append(strings.tnCallMeta(timeStr, call.type.name) + "\n\n")
                    }
                    CommandResult.Text(sb.toString().trimEnd())
                }
            },
            onFailure = {
                CommandResult.Error(strings.tnCallsReadError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleSendUssd(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(strings.tnUssdUsage())
        }

        var simSlot: Int? = null
        var codeIndex = 0
        if (args[0] == "1" || args[0] == "2") {
            simSlot = args[0].toIntOrNull()
            codeIndex = 1
        }

        if (args.size <= codeIndex) {
            return CommandResult.Error(strings.tnUssdNeedCode())
        }

        val code = args[codeIndex]
        val simText = if (simSlot != null) " (SIM $simSlot)" else ""

        return telephonyRepository.sendUssd(code, simSlot).fold(
            onSuccess = { response ->
                CommandResult.Text(strings.tnUssdResponse(simText, code, response))
            },
            onFailure = {
                CommandResult.Error(strings.tnUssdError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleListNotifications(args: List<String>): CommandResult {
        val limit = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 30) ?: 5
        val list = notificationRepository.getRecentNotifications(limit)
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        if (list.isEmpty()) {
            return CommandResult.Text(
                strings.tnNotifEmpty(),
            )
        }

        val sb = StringBuilder(strings.tnNotifHeader(limit))
        list.forEach { notif ->
            val timeStr = dateFormat.format(Date(notif.timestampMs))
            val replyBadge = if (notif.hasReplyAction) strings.tnNotifReplyBadge(notif.id) else ""
            sb.append("🔹 <b>ID: ${notif.id} | ${notif.appName}</b> (<i>$timeStr</i>)$replyBadge\n")
            if (!notif.title.isNullOrBlank()) {
                sb.append("   <b>${notif.title}</b>\n")
            }
            if (!notif.text.isNullOrBlank()) {
                sb.append("   ${notif.text}\n")
            }
            sb.append("\n")
        }
        return CommandResult.Text(sb.toString().trimEnd())
    }

    private suspend fun handlePostNotification(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                strings.tnNotifyUsage(),
            )
        }

        val title = args[0]
        val text = if (args.size > 1) args.subList(1, args.size).joinToString(" ") else ""

        return notificationRepository.postNotification(title, text).fold(
            onSuccess = {
                CommandResult.Text(strings.tnNotifyPosted(title, text))
            },
            onFailure = {
                CommandResult.Error(strings.tnNotifyError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleSimulateNotification(args: List<String>): CommandResult {
        if (args.size < 3) {
            return CommandResult.Error(
                strings.tnFakeNotifyUsage(),
            )
        }

        val packageName = args[0]
        val title = args[1]
        val text = args.subList(2, args.size).joinToString(" ")
        val appName =
            when {
                packageName.contains("sber", ignoreCase = true) -> strings.tnAppSber()
                packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                packageName.contains("telegram", ignoreCase = true) -> "Telegram"
                packageName.contains("vk", ignoreCase = true) -> "VK"
                packageName.contains("viber", ignoreCase = true) -> "Viber"
                packageName.contains("tinkoff", ignoreCase = true) || packageName.contains("tbank", ignoreCase = true) -> strings.tnAppTbank()
                else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }

        return notificationRepository.simulateNotification(packageName, appName, title, text).fold(
            onSuccess = {
                CommandResult.Text(strings.tnFakeNotifySimulated(appName, title, text))
            },
            onFailure = {
                CommandResult.Error(strings.tnFakeNotifyError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleAnswerNotification(args: List<String>): CommandResult {
        if (args.size < 2) {
            return CommandResult.Error(
                strings.tnAnswerUsage(),
            )
        }

        val id = args[0]
        val replyText = args.subList(1, args.size).joinToString(" ")

        return notificationRepository.replyToNotification(id, replyText).fold(
            onSuccess = {
                CommandResult.Text(strings.tnAnswerSent(id, replyText))
            },
            onFailure = {
                CommandResult.Error(strings.tnAnswerError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleNotifyAction(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                strings.tnActUsage(),
            )
        }

        val id = args[0]
        val actionIndex = args.getOrNull(1)?.toIntOrNull() ?: 0

        return notificationRepository.executeNotificationAction(id, actionIndex).fold(
            onSuccess = {
                CommandResult.Text(strings.tnActDone(actionIndex, id))
            },
            onFailure = {
                CommandResult.Error(strings.tnActError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleClearNotification(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(strings.tnClearUsage())
        }

        val id = args[0]
        return notificationRepository.cancelNotification(id).fold(
            onSuccess = {
                CommandResult.Text(strings.tnCleared(id))
            },
            onFailure = {
                CommandResult.Error(strings.tnClearError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleNotificationRule(args: List<String>): CommandResult {
        if (args.isEmpty() || args[0] == "list") {
            val rules = notificationRepository.rules.value
            if (rules.isEmpty()) {
                return CommandResult.Text(
                    strings.tnRulesEmpty(),
                )
            }
            val sb = StringBuilder(strings.tnRulesHeader())
            rules.forEach { rule ->
                val type = if (rule.isExclude) strings.tnRuleExclude() else strings.tnRuleAllow()
                sb.append(
                    "• <b>${rule.id}</b>: $type pkg=<code>${rule.packageName ?: "*"}</code> pattern=<code>${rule.titlePattern ?: "*"}</code>\n",
                )
            }
            return CommandResult.Text(sb.toString().trimEnd())
        }

        if (args[0] == "add" && args.size >= 2) {
            val isExclude = args.getOrNull(1)?.lowercase() == "exclude"
            val pkg = if (isExclude) args.getOrNull(2) else args.getOrNull(1)
            val pattern = if (isExclude) args.getOrNull(3) else args.getOrNull(2)

            val newRule =
                NotificationRule(
                    id = UUID.randomUUID().toString().take(6),
                    packageName = pkg,
                    titlePattern = pattern,
                    isExclude = true,
                )
            notificationRepository.addRule(newRule)
            return CommandResult.Text(strings.tnRuleAdded(newRule.id))
        }

        if (args[0] == "del" && args.size >= 2) {
            val ruleId = args[1]
            notificationRepository.removeRule(ruleId)
            return CommandResult.Text(strings.tnRuleDeleted(ruleId))
        }

        return CommandResult.Error(
            strings.tnRulesUsage(),
        )
    }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    companion object {
        private val DIGIT_REGEX = Regex("\\d+")
    }
}
