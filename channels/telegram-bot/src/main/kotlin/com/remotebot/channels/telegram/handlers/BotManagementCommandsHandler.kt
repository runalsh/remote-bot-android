package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.backup.BackupManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.tracker.ActiveOperationsTracker
import com.remotebot.domain.model.BotLanguage
import com.remotebot.channels.telegram.strings.TelegramBotStrings
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
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

    val proxyHandler = ProxyCommandsHandler(proxyConfigRepository, logger, botConfigRepository)
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
                "bot_language",
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
            cmd == "bot_language" -> handleBotLanguage(command.args)
            else -> CommandResult.Error(strings.bmUnknownCmd(cmd))
        }
    }


    private suspend fun handleBotLanguage(args: List<String>): CommandResult {
        val lang = args.firstOrNull()?.trim()?.lowercase()
        if (lang.isNullOrEmpty()) {
            val cur = botConfigRepository.config.value.botLanguage.name.lowercase()
            return CommandResult.Text(strings.botLanguageCurrent(cur))
        }
        val target =
            when (lang) {
                "auto" -> BotLanguage.AUTO
                "en" -> BotLanguage.EN
                "ru" -> BotLanguage.RU
                else -> return CommandResult.Text(strings.botLanguageInvalid(lang))
            }
        botConfigRepository.updateConfig { it.copy(botLanguage = target) }
        return CommandResult.Text(strings.botLanguageSet(lang))
    }
    private fun handleActive(): CommandResult {
        val ops = operationsTracker.list()
        if (ops.isEmpty()) {
            return CommandResult.Text(strings.bmNoOps())
        }
        val sb = StringBuilder(strings.bmOpsHeader(ops.size))
        val now = System.currentTimeMillis()
        for ((idx, op) in ops.withIndex()) {
            val elapsedSec = (now - op.startedAtMs) / 1000
            sb.append("${idx + 1}. <b>${op.name}</b> (id: <code>${op.id}</code>)\n")
            sb.append(strings.bmOpDuration(elapsedSec) + "\n")
            if (op.description.isNotBlank()) {
                sb.append(strings.bmOpInfo(op.description) + "\n")
            }
        }
        sb.append(strings.bmStopHint())
        return CommandResult.Text(sb.toString())
    }

    private fun handleStopAll(): CommandResult {
        val count = operationsTracker.cancelAll()
        logger.w("TaskManager", "Emergency stop of background operations via /stop_all (stopped: $count)")
        return CommandResult.Text(strings.bmStopped(count))
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
            val filterNote = if (levelArg != null) strings.bmLogFilterNote(levelArg.toString()) else ""
            return CommandResult.Text(strings.bmLogEmpty(filterNote))
        }

        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val levelHeader = if (levelArg != null) strings.bmLogLevelHeader(levelArg.toString()) else ""
        val sb = StringBuilder(strings.bmLogHeader(entries.size, levelHeader))
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
        sb.append(strings.bmLogUsage())
        return CommandResult.Text(sb.toString())
    }

    private fun handleUnsentFiles(): CommandResult =
        CommandResult.Text(strings.bmUnsentEmpty())

    private fun handleBotStatus(): CommandResult {
        val cfg = botConfigRepository.config.value
        val tgStatus = if (cfg.telegramBotEnabled) strings.bmActive() else strings.bmDisabled()
        val smsStatus = if (cfg.smsBotEnabled) strings.bmActive() else strings.bmDisabled()
        val targetsCount = cfg.smsForwardTargetNumbers.size
        return CommandResult.Text(
            strings.bmStatusPanel(
                    tgStatus,
                    cfg.connectionMode.toString(),
                    smsStatus,
                    if (cfg.tgForwardSmsEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.tgForwardCallsEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.tgForwardNotificationsEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.tgForwardBatteryChargingEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.tgForwardBatteryLowEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.smsForwardSmsEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.smsForwardMissedCallsEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.smsForwardNotificationsEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.smsForwardBatteryChargingEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.smsForwardBatteryLowEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.smsTransliterationEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    targetsCount,
                ),
        )
    }

    private suspend fun handleTgForward(args: List<String>): CommandResult {
        val cfg = botConfigRepository.config.value
        if (args.isEmpty() || args[0].equals("status", ignoreCase = true)) {
            return CommandResult.Text(
                strings.bmTgForwardPanel(
                    if (cfg.tgForwardSmsEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.tgForwardCallsEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.tgForwardNotificationsEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.tgForwardBatteryChargingEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                    if (cfg.tgForwardBatteryLowEnabled) strings.bmInlineOn() else strings.bmInlineOff(),
                ),
            )
        }
        val type = args[0].lowercase()
        val state = args.getOrNull(1)?.lowercase()
        val enable = state == "on" || state == "1" || state == "true"
        return when (type) {
            "sms" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardSmsEnabled = enable) }
                CommandResult.Text(strings.bmFwdTgSms(if (enable) strings.bmEnabledFem() else strings.bmDisabledFem()))
            }
            "call", "calls" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardCallsEnabled = enable) }
                CommandResult.Text(strings.bmFwdTgCalls(if (enable) strings.bmEnabledFem() else strings.bmDisabledFem()))
            }
            "push", "notifications" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardNotificationsEnabled = enable) }
                CommandResult.Text(strings.bmFwdTgPush(if (enable) strings.bmEnabledFem() else strings.bmDisabledFem()))
            }
            "battery_charge", "charge", "charging" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardBatteryChargingEnabled = enable) }
                CommandResult.Text(strings.bmFwdTgCharge(if (enable) strings.bmEnabledPlur() else strings.bmDisabledPlur()))
            }
            "battery_low", "low_battery", "low" -> {
                botConfigRepository.updateConfig { it.copy(tgForwardBatteryLowEnabled = enable) }
                CommandResult.Text(strings.bmFwdTgLow(if (enable) strings.bmEnabledPlur() else strings.bmDisabledPlur()))
            }
            "battery" -> {
                botConfigRepository.updateConfig {
                    it.copy(
                        tgForwardBatteryChargingEnabled = enable,
                        tgForwardBatteryLowEnabled = enable,
                    )
                }
                CommandResult.Text(strings.bmFwdTgBattery(if (enable) strings.bmEnabledPlur() else strings.bmDisabledPlur()))
            }
            else ->
                CommandResult.Error(
                    strings.bmTgForwardUnknownType(),
                )
        }
    }

    private suspend fun handleTgBot(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val isEnabled = botConfigRepository.config.value.telegramBotEnabled
            val state = if (isEnabled) strings.bmStateOn() else strings.bmStateOff()
            return CommandResult.Text(
                strings.bmTgBotState(state),
            )
        }
        val target =
            when (args[0].lowercase()) {
                "on", "1", "true", "start", "enable" -> true
                "off", "0", "false", "stop", "disable" -> false
                "toggle" -> !botConfigRepository.config.value.telegramBotEnabled
                else -> return CommandResult.Error(strings.bmTgBotUsage())
            }
        botConfigRepository.updateConfig { it.copy(telegramBotEnabled = target) }
        val res = if (target) strings.bmTgOnRes() else strings.bmTgOffRes()
        return CommandResult.Text(strings.bmTgBotResult(res))
    }

    private suspend fun handleSmsBot(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val isEnabled = botConfigRepository.config.value.smsBotEnabled
            val state = if (isEnabled) strings.bmStateOn() else strings.bmStateOff()
            return CommandResult.Text(
                strings.bmSmsBotState(state),
            )
        }
        val target =
            when (args[0].lowercase()) {
                "on", "1", "true", "start", "enable" -> true
                "off", "0", "false", "stop", "disable" -> false
                "toggle" -> !botConfigRepository.config.value.smsBotEnabled
                else -> return CommandResult.Error(strings.bmSmsBotUsage())
            }
        botConfigRepository.updateConfig { it.copy(smsBotEnabled = target) }
        val res = if (target) strings.bmSmsOnRes() else strings.bmSmsOffRes()
        return CommandResult.Text(strings.bmSmsBotResult(res))
    }

    private suspend fun handleBotToggle(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(strings.bmBotToggleUsage())
        }
        val channel = args[0].lowercase()
        val rest = if (args.size > 1) args.subList(1, args.size) else emptyList()
        return when (channel) {
            "tg", "telegram" -> handleTgBot(rest)
            "sms" -> handleSmsBot(rest)
            else -> CommandResult.Error(strings.bmUnknownChannel(channel))
        }
    }
}
