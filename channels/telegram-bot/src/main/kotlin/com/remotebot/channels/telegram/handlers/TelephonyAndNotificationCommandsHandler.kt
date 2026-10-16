package com.remotebot.channels.telegram.handlers

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
) : CommandHandler {
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
            else -> CommandResult.Error("Неизвестная команда телефонии: $cmd")
        }
    }

    private suspend fun handleSendSms(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                "Формат: <code>/sms [sim] &lt;номер&gt; &lt;текст&gt;</code>\nПример: <code>/sms +79991234567 Привет!</code>\nПример для SIM2: <code>/sms 2 +79991234567 Привет!</code>",
            )
        }

        var simSlot: Int? = null
        var phoneIndex = 0
        if (args[0] == "1" || args[0] == "2") {
            simSlot = args[0].toIntOrNull()
            phoneIndex = 1
        }

        if (args.size <= phoneIndex + 1) {
            return CommandResult.Error("Укажите номер телефона и текст сообщения.\nПример: <code>/sms +79991234567 Привет!</code>")
        }

        val phoneNumber = args[phoneIndex]
        val text = args.subList(phoneIndex + 1, args.size).joinToString(" ")

        return telephonyRepository.sendSms(phoneNumber, text, simSlot).fold(
            onSuccess = {
                val simText = if (simSlot != null) " (SIM $simSlot)" else ""
                CommandResult.Text("📨 <b>SMS успешно отправлено</b> на номер <code>$phoneNumber</code>$simText:\n«$text»")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка отправки SMS: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleSmsInbox(args: List<String>): CommandResult {
        val limit = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 50) ?: 5
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        return telephonyRepository.getRecentSms(limit).fold(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    CommandResult.Text("📭 Входящих SMS не найдено.")
                } else {
                    val sb = StringBuilder("📬 <b>Последние SMS ($limit):</b>\n\n")
                    list.forEachIndexed { index, sms ->
                        val icon = if (sms.isIncoming) "📥" else "📤"
                        val timeStr = dateFormat.format(Date(sms.dateMs))
                        sb.append("$icon <b>#${index + 1} | ${sms.address}</b> (<i>$timeStr</i>)\n")
                        sb.append("«${sms.body}»\n\n")
                    }
                    CommandResult.Text(sb.toString().trimEnd())
                }
            },
            onFailure = {
                CommandResult.Error("❌ Не удалось прочитать SMS: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleMakeCall(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error("Формат: <code>/call [sim] &lt;номер&gt;</code>\nПример: <code>/call +79991234567</code>")
        }

        var simSlot: Int? = null
        var phoneIndex = 0
        if (args[0] == "1" || args[0] == "2") {
            simSlot = args[0].toIntOrNull()
            phoneIndex = 1
        }

        if (args.size <= phoneIndex) {
            return CommandResult.Error("Укажите номер телефона для вызова.")
        }

        val phoneNumber = args[phoneIndex]
        return telephonyRepository.makeCall(phoneNumber, simSlot).fold(
            onSuccess = {
                val simText = if (simSlot != null) " (SIM $simSlot)" else ""
                CommandResult.Text("📞 <b>Инициирован исходящий звонок</b> на номер <code>$phoneNumber</code>$simText...")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка при совершении звонка: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleCallsHistory(args: List<String>): CommandResult {
        val limit = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 50) ?: 10
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        return telephonyRepository.getCallHistory(limit).fold(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    CommandResult.Text("📞 Журнал вызовов пуст.")
                } else {
                    val sb = StringBuilder("📞 <b>Журнал звонков (последние $limit):</b>\n\n")
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
                        val durationStr = if (call.durationSec > 0) " [${call.durationSec / 60}м ${call.durationSec % 60}с]" else ""

                        sb.append("$icon <b>#${index + 1} | ${call.number}</b>$nameStr$durationStr\n")
                        sb.append("   <i>$timeStr | Тип: ${call.type.name}</i>\n\n")
                    }
                    CommandResult.Text(sb.toString().trimEnd())
                }
            },
            onFailure = {
                CommandResult.Error("❌ Не удалось получить журнал вызовов: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleSendUssd(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error("Формат: <code>/ussd [sim] &lt;код&gt;</code>\nПример: <code>/ussd *100#</code>")
        }

        var simSlot: Int? = null
        var codeIndex = 0
        if (args[0] == "1" || args[0] == "2") {
            simSlot = args[0].toIntOrNull()
            codeIndex = 1
        }

        if (args.size <= codeIndex) {
            return CommandResult.Error("Укажите USSD-код, например <code>*100#</code>")
        }

        val code = args[codeIndex]
        val simText = if (simSlot != null) " (SIM $simSlot)" else ""

        return telephonyRepository.sendUssd(code, simSlot).fold(
            onSuccess = { response ->
                CommandResult.Text("📟 <b>USSD Ответ$simText для <code>$code</code>:</b>\n\n<code>$response</code>")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка выполнения USSD-запроса: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleListNotifications(args: List<String>): CommandResult {
        val limit = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 30) ?: 5
        val list = notificationRepository.getRecentNotifications(limit)
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        if (list.isEmpty()) {
            return CommandResult.Text(
                "🔔 Активных перехваченных уведомлений нет.\n<i>Убедитесь, что боту предоставлен доступ в Настройки -> Доступ к уведомлениям.</i>",
            )
        }

        val sb = StringBuilder("🔔 <b>Последние уведомления ($limit):</b>\n\n")
        list.forEach { notif ->
            val timeStr = dateFormat.format(Date(notif.timestampMs))
            val replyBadge = if (notif.hasReplyAction) " [💬 <i>Ответ: /answer ${notif.id} текст</i>]" else ""
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
                "Формат: <code>/notify &lt;заголовок&gt; &lt;текст&gt;</code>\nПример: <code>/notify Внимание Важное сообщение</code>",
            )
        }

        val title = args[0]
        val text = if (args.size > 1) args.subList(1, args.size).joinToString(" ") else ""

        return notificationRepository.postNotification(title, text).fold(
            onSuccess = {
                CommandResult.Text("🔔 <b>Уведомление отправлено в статус-бар смартфона:</b>\nЗаголовок: <b>$title</b>\nТекст: $text")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка отправки уведомления: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleSimulateNotification(args: List<String>): CommandResult {
        if (args.size < 3) {
            return CommandResult.Error(
                "Формат: <code>/fake_notify &lt;пакет&gt; &lt;заголовок&gt; &lt;текст&gt;</code>\n" +
                    "Пример: <code>/fake_notify ru.sberbankmobile СберБанк Перевод +5000р от Диана</code>\n" +
                    "Пример 2: <code>/fake_notify com.whatsapp Диана Привет, как дела?</code>",
            )
        }

        val packageName = args[0]
        val title = args[1]
        val text = args.subList(2, args.size).joinToString(" ")
        val appName =
            when {
                packageName.contains("sber", ignoreCase = true) -> "СберБанк"
                packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                packageName.contains("telegram", ignoreCase = true) -> "Telegram"
                packageName.contains("vk", ignoreCase = true) -> "VK"
                packageName.contains("viber", ignoreCase = true) -> "Viber"
                packageName.contains("tinkoff", ignoreCase = true) || packageName.contains("tbank", ignoreCase = true) -> "Т-Банк"
                else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }

        return notificationRepository.simulateNotification(packageName, appName, title, text).fold(
            onSuccess = {
                CommandResult.Text("🧪 <b>Сэмулировано входящее уведомление от [$appName]:</b>\nОт: <b>$title</b>\nТекст: $text")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка эмуляции уведомления: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleAnswerNotification(args: List<String>): CommandResult {
        if (args.size < 2) {
            return CommandResult.Error(
                "Формат: <code>/answer &lt;ID&gt; &lt;текст ответа&gt;</code>\nПример: <code>/answer 1 Привет, скоро буду!</code>",
            )
        }

        val id = args[0]
        val replyText = args.subList(1, args.size).joinToString(" ")

        return notificationRepository.replyToNotification(id, replyText).fold(
            onSuccess = {
                CommandResult.Text("💬 <b>Ответ успешно отправлен</b> в уведомление #$id:\n«$replyText»")
            },
            onFailure = {
                CommandResult.Error("❌ Не удалось отправить ответ: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleNotifyAction(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                "Формат: <code>/notify_act &lt;ID&gt; [номер_действия]</code>\nПример: <code>/notify_act 1 0</code>",
            )
        }

        val id = args[0]
        val actionIndex = args.getOrNull(1)?.toIntOrNull() ?: 0

        return notificationRepository.executeNotificationAction(id, actionIndex).fold(
            onSuccess = {
                CommandResult.Text("⚡ <b>Действие (индекс $actionIndex) успешно выполнено</b> для уведомления #$id!")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка выполнения действия: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleClearNotification(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error("Формат: <code>/notify_clear &lt;ID/all&gt;</code>")
        }

        val id = args[0]
        return notificationRepository.cancelNotification(id).fold(
            onSuccess = {
                CommandResult.Text("🗑️ Уведомление #$id удалено.")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка удаления уведомления: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleNotificationRule(args: List<String>): CommandResult {
        if (args.isEmpty() || args[0] == "list") {
            val rules = notificationRepository.rules.value
            if (rules.isEmpty()) {
                return CommandResult.Text(
                    "📋 <b>Правила фильтрации уведомлений пусты.</b>\nДобавить: <code>/notify_rule add &lt;pkg&gt; [pattern]</code>",
                )
            }
            val sb = StringBuilder("📋 <b>Правила фильтрации уведомлений:</b>\n\n")
            rules.forEach { rule ->
                val type = if (rule.isExclude) "⛔ Исключать" else "✅ Разрешать"
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
            return CommandResult.Text("✅ Добавлено правило фильтрации #${newRule.id}")
        }

        if (args[0] == "del" && args.size >= 2) {
            val ruleId = args[1]
            notificationRepository.removeRule(ruleId)
            return CommandResult.Text("🗑️ Правило #$ruleId удалено.")
        }

        return CommandResult.Error(
            "Формат: <code>/notify_rule list</code> или <code>/notify_rule add exclude &lt;pkg&gt;</code> или <code>/notify_rule del &lt;id&gt;</code>",
        )
    }
}
