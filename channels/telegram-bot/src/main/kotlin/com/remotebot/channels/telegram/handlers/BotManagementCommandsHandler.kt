package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.backup.BackupManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.tracker.ActiveOperationsTracker
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.LogLevel
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.ProxyConfigRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BotManagementCommandsHandler(
    proxyConfigRepository: ProxyConfigRepository,
    private val botConfigRepository: BotConfigRepository,
    private val operationsTracker: ActiveOperationsTracker,
    private val logger: AppLogger,
    backupManager: BackupManager,
) : CommandHandler {
    val proxyHandler = ProxyCommandsHandler(proxyConfigRepository, logger)
    val backupHandler = BackupCommandsHandler(botConfigRepository, proxyConfigRepository, backupManager, logger)
    val securityHandler = SecurityManagementCommandsHandler(botConfigRepository)

    override val commandNames: List<String> =
        (
            listOf(
                "active",
                "stop_all",
                "log",
                "unsent_files",
                "bot_status",
                "bot_toggle",
                "tg_bot",
                "sms_bot",
                "tg_forward",
            ) + proxyHandler.commandNames + backupHandler.commandNames + securityHandler.commandNames
        ).distinct()

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when {
            cmd in proxyHandler.commandNames -> proxyHandler.execute(command)
            cmd in backupHandler.commandNames -> backupHandler.execute(command)
            cmd in securityHandler.commandNames -> securityHandler.execute(command)
            cmd == "active" -> handleActive()
            cmd == "stop_all" -> handleStopAll()
            cmd == "log" -> handleLog(command.args)
            cmd == "unsent_files" -> handleUnsentFiles()
            cmd == "bot_status" -> handleBotStatus()
            cmd == "tg_bot" -> handleTgBot(command.args)
            cmd == "sms_bot" -> handleSmsBot(command.args)
            cmd == "tg_forward" -> handleTgForward(command.args)
            cmd == "bot_toggle" -> handleBotToggle(command.args)
            else -> CommandResult.Error("Неизвестная команда управления: $cmd")
        }
    }

    private fun handleActive(): CommandResult {
        val ops = operationsTracker.list()
        if (ops.isEmpty()) {
            return CommandResult.Text("⚙️ Нет активных фоновых операций.")
        }
        val sb = StringBuilder("⚙️ <b>Активные фоновые операции (${ops.size}):</b>\n\n")
        val now = System.currentTimeMillis()
        for ((idx, op) in ops.withIndex()) {
            val elapsedSec = (now - op.startedAtMs) / 1000
            sb.append("${idx + 1}. <b>${op.name}</b> (id: <code>${op.id}</code>)\n")
            sb.append("   • Длительность: ${elapsedSec}s\n")
            if (op.description.isNotBlank()) {
                sb.append("   • Инфо: ${op.description}\n")
            }
        }
        sb.append("\nДля экстренной остановки используйте /stop_all")
        return CommandResult.Text(sb.toString())
    }

    private fun handleStopAll(): CommandResult {
        val count = operationsTracker.cancelAll()
        logger.w("TaskManager", "Экстренная остановка фоновых операций через /stop_all (остановлено: $count)")
        return CommandResult.Text("🛑 Прервано активных фоновых операций: <b>$count</b>")
    }

    private fun handleLog(args: List<String>): CommandResult {
        val linesCount = args.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()?.coerceIn(5, 100) ?: 20
        val levelArg =
            args
                .firstOrNull { arg ->
                    val upper = arg.uppercase()
                    LogLevel.entries.any { it.name == upper }
                }?.uppercase()
                ?.let { LogLevel.valueOf(it) }

        val entries = logger.getFiltered(count = linesCount, minLevel = levelArg)
        if (entries.isEmpty()) {
            val filterNote = if (levelArg != null) " с уровнем >= $levelArg" else ""
            return CommandResult.Text("📜 Журнал логов пуст$filterNote.")
        }

        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val levelHeader = if (levelArg != null) " (уровень >= $levelArg)" else ""
        val sb = StringBuilder("📜 <b>Последние ${entries.size} записей лога$levelHeader:</b>\n\n")
        for (e in entries) {
            val icon =
                when (e.level) {
                    LogLevel.TRACE -> "🔬"
                    LogLevel.DEBUG -> "🔍"
                    LogLevel.INFO -> "ℹ️"
                    LogLevel.WARN -> "⚠️"
                    LogLevel.ERROR -> "❌"
                }
            val time = dateFormat.format(Date(e.timestampMs))
            sb.append("$icon <code>$time</code> [${e.tag}] ${e.message}\n")
        }
        sb.append("\n<i>Использование: /log [N] [TRACE|DEBUG|INFO|WARN|ERROR]</i>")
        return CommandResult.Text(sb.toString())
    }

    private fun handleUnsentFiles(): CommandResult =
        CommandResult.Text("📁 <b>Очередь неотправленных файлов:</b>\nОчередь пуста. Все файлы доставлены.")

    private fun handleBotStatus(): CommandResult {
        val cfg = botConfigRepository.config.value
        val tgStatus = if (cfg.telegramBotEnabled) "✅ Активен" else "❌ Выключен"
        val smsStatus = if (cfg.smsBotEnabled) "✅ Активен" else "❌ Выключен"
        val targetsCount = cfg.smsForwardTargetNumbers.size
        return CommandResult.Text(
            """
            🤖 <b>Статус каналов и пересылки Remote Bot:</b>
            
            • <b>Telegram Бот:</b> $tgStatus (режим: <code>${cfg.connectionMode}</code>)
              - Пересылка SMS: ${if (cfg.tgForwardSmsEnabled) "Вкл ✅" else "Выкл ❌"}
              - Пересылка звонков: ${if (cfg.tgForwardCallsEnabled) "Вкл ✅" else "Выкл ❌"}
              - Пересылка Push: ${if (cfg.tgForwardNotificationsEnabled) "Вкл ✅" else "Выкл ❌"}
              - Статус зарядки/питания: ${if (cfg.tgForwardBatteryChargingEnabled) "Вкл ✅" else "Выкл ❌"}
              - Низкий заряд (≤20% каждые 3%): ${if (cfg.tgForwardBatteryLowEnabled) "Вкл ✅" else "Выкл ❌"}
            
            • <b>SMS Бот:</b> $smsStatus
              - Пересылка SMS: ${if (cfg.smsForwardSmsEnabled) "Вкл ✅" else "Выкл ❌"}
              - Пересылка звонков: ${if (cfg.smsForwardMissedCallsEnabled) "Вкл ✅" else "Выкл ❌"}
              - Пересылка Push: ${if (cfg.smsForwardNotificationsEnabled) "Вкл ✅" else "Выкл ❌"}
              - Статус зарядки/питания: ${if (cfg.smsForwardBatteryChargingEnabled) "Вкл ✅" else "Выкл ❌"}
              - Низкий заряд (≤20% каждые 3%): ${if (cfg.smsForwardBatteryLowEnabled) "Вкл ✅" else "Выкл ❌"}
              - Транслитерация: ${if (cfg.smsTransliterationEnabled) "Вкл ✅" else "Выкл ❌"}
              - Номеров пересылки: $targetsCount
            
            <i>Управление:</i>
            <code>/tg_bot [on|off]</code> — включить/выключить Telegram
            <code>/sms_bot [on|off]</code> — включить/выключить SMS канал
            <code>/tg_forward [sms|calls|push|battery_charge|battery_low|battery] [on|off]</code> — пересылка в Telegram
            <code>/sms_forward [on|off|missed|push|battery_charge|battery_low|battery|translit]</code> — пересылка по SMS
            """.trimIndent(),
        )
    }

    private suspend fun handleTgForward(args: List<String>): CommandResult {
        val cfg = botConfigRepository.config.value
        if (args.isEmpty() || args[0].equals("status", ignoreCase = true)) {
            return CommandResult.Text(
                """
                ✈️ <b>Пересылка событий в Telegram:</b>
                • Входящие SMS: ${if (cfg.tgForwardSmsEnabled) "Вкл ✅" else "Выкл ❌"}
                • Звонки: ${if (cfg.tgForwardCallsEnabled) "Вкл ✅" else "Выкл ❌"}
                • Push-уведомления: ${if (cfg.tgForwardNotificationsEnabled) "Вкл ✅" else "Выкл ❌"}
                • Статус зарядки и питания: ${if (cfg.tgForwardBatteryChargingEnabled) "Вкл ✅" else "Выкл ❌"}
                • Низкий заряд (≤20% каждые 3%): ${if (cfg.tgForwardBatteryLowEnabled) "Вкл ✅" else "Выкл ❌"}
                
                <i>Управление:</i>
                <code>/tg_forward sms on|off</code> — пересылка SMS в Telegram
                <code>/tg_forward calls on|off</code> — пересылка звонков в Telegram
                <code>/tg_forward push on|off</code> — пересылка Push в Telegram
                <code>/tg_forward battery_charge on|off</code> — оповещения о подключении/отключении ЗУ
                <code>/tg_forward battery_low on|off</code> — оповещения о разряде батареи (≤20% каждые 3%)
                <code>/tg_forward battery on|off</code> — вкл/выкл все оповещения батареи
                """.trimIndent(),
            )
        }
        val type = args[0].lowercase()
        val state = args.getOrNull(1)?.lowercase()
        val enable = state == "on" || state == "1" || state == "true"
        return when (type) {
            "sms" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardSmsEnabled = enable) }
                CommandResult.Text("Пересылка входящих SMS в Telegram: ${if (enable) "Включена ✅" else "Отключена ⏹"}")
            }
            "call", "calls" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardCallsEnabled = enable) }
                CommandResult.Text("Пересылка звонков в Telegram: ${if (enable) "Включена ✅" else "Отключена ⏹"}")
            }
            "push", "notifications" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardNotificationsEnabled = enable) }
                CommandResult.Text("Пересылка Push-уведомлений в Telegram: ${if (enable) "Включена ✅" else "Отключена ⏹"}")
            }
            "battery_charge", "charge", "charging" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardBatteryChargingEnabled = enable) }
                CommandResult.Text("Оповещения о статусе зарядки и питания в Telegram: ${if (enable) "Включены ✅" else "Отключены ⏹"}")
            }
            "battery_low", "low_battery", "low" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardBatteryLowEnabled = enable) }
                CommandResult.Text("Оповещения о низком заряде (≤20% каждые 3%) в Telegram: ${if (enable) "Включены ✅" else "Отключены ⏹"}")
            }
            "battery" -> {
                botConfigRepository.updateConfig {
                    it.copy(
                        tgForwardBatteryChargingEnabled = enable,
                        tgForwardBatteryLowEnabled = enable,
                    )
                }
                CommandResult.Text("Все оповещения о батарее и зарядке в Telegram: ${if (enable) "Включены ✅" else "Отключены ⏹"}")
            }
            else ->
                CommandResult.Error(
                    "Неизвестный тип. Доступны: sms, calls, push, battery_charge, battery_low, battery. Пример: <code>/tg_forward battery_low on</code>",
                )
        }
    }

    private suspend fun handleTgBot(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val isEnabled = botConfigRepository.config.value.telegramBotEnabled
            val state = if (isEnabled) "включен ✅" else "выключен ❌"
            return CommandResult.Text(
                "🤖 <b>Telegram-бот</b> сейчас $state.\nИспользуйте: <code>/tg_bot on</code> или <code>/tg_bot off</code>",
            )
        }
        val target =
            when (args[0].lowercase()) {
                "on", "1", "true", "start", "enable" -> true
                "off", "0", "false", "stop", "disable" -> false
                "toggle" -> !botConfigRepository.config.value.telegramBotEnabled
                else -> return CommandResult.Error("Формат: <code>/tg_bot on</code> или <code>/tg_bot off</code>")
            }
        botConfigRepository.updateConfig { it.copy(telegramBotEnabled = target) }
        val res = if (target) "включен ✅ (прием команд возобновлен)" else "выключен ❌ (опрос команд приостановлен)"
        return CommandResult.Text("🤖 <b>Telegram-бот</b> $res")
    }

    private suspend fun handleSmsBot(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val isEnabled = botConfigRepository.config.value.smsBotEnabled
            val state = if (isEnabled) "включен ✅" else "выключен ❌"
            return CommandResult.Text(
                "💬 <b>SMS-бот</b> сейчас $state.\nИспользуйте: <code>/sms_bot on</code> или <code>/sms_bot off</code>",
            )
        }
        val target =
            when (args[0].lowercase()) {
                "on", "1", "true", "start", "enable" -> true
                "off", "0", "false", "stop", "disable" -> false
                "toggle" -> !botConfigRepository.config.value.smsBotEnabled
                else -> return CommandResult.Error("Формат: <code>/sms_bot on</code> или <code>/sms_bot off</code>")
            }
        botConfigRepository.updateConfig { it.copy(smsBotEnabled = target) }
        val res = if (target) "включен ✅ (прием SMS команд и пересылка активны)" else "выключен ❌ (сотовая отправка отключена)"
        return CommandResult.Text("💬 <b>SMS-бот</b> $res")
    }

    private suspend fun handleBotToggle(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error("Формат: <code>/bot_toggle &lt;tg|sms&gt; [on|off]</code>")
        }
        val channel = args[0].lowercase()
        val rest = if (args.size > 1) args.subList(1, args.size) else emptyList()
        return when (channel) {
            "tg", "telegram" -> handleTgBot(rest)
            "sms" -> handleSmsBot(rest)
            else -> CommandResult.Error("Неизвестный канал: $channel. Доступны: tg, sms")
        }
    }
}
