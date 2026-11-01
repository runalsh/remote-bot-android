package com.remotebot.channels.telegram.handlers

import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.BotLanguage
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.TaskerTask
import com.remotebot.domain.repository.AutomationRepository
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.TaskerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutomationCommandsHandler(
    private val taskerRepository: TaskerRepository,
    private val automationRepository: AutomationRepository,
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

    override val commandNames: List<String> =
        listOf(
            "tasker",
            "script",
            "timers",
        )

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "tasker" -> handleTasker(command.args)
            "script" -> handleScript(command.args)
            "timers" -> handleTimers(command.args)
            else -> CommandResult.Error(strings.autUnknownCmd(cmd))
        }
    }

    private suspend fun handleTasker(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val installed = taskerRepository.isTaskerInstalled()
            val instText = if (installed) strings.autTaskerInstalled() else strings.autTaskerMissing()
            return CommandResult.Text(
                strings.autTaskerHelp(instText),
            )
        }

        val taskName = args[0]
        val par1 = args.getOrNull(1)
        val par2 = args.getOrNull(2)

        val task =
            TaskerTask(
                taskName = taskName,
                param1 = par1,
                param2 = par2,
            )

        return taskerRepository.executeTask(task).fold(
            onSuccess = {
                val paramsStr = listOfNotNull(par1, par2).joinToString(", ")
                val extraStr = if (paramsStr.isNotBlank()) strings.autParams(paramsStr) else ""
                CommandResult.Text(strings.autTaskerSent(taskName, extraStr))
            },
            onFailure = {
                CommandResult.Error(strings.autTaskerError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleScript(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                strings.autScriptUsage(),
            )
        }

        val code = args.joinToString(" ")
        val result = automationRepository.executeScript(code)

        return if (result.isSuccess) {
            CommandResult.Text(strings.autScriptOk(result.executionTimeMs, result.output ?: ""))
        } else {
            CommandResult.Error(
                strings.autScriptErr(result.executionTimeMs, result.error ?: "", result.output ?: ""),
            )
        }
    }

    private suspend fun handleTimers(args: List<String>): CommandResult {
        if (args.isEmpty() || args[0] == "list") {
            val timers = automationRepository.timers.value
            if (timers.isEmpty()) {
                return CommandResult.Text(
                    strings.autNoTimers(),
                )
            }

            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val sb = StringBuilder(strings.autTimersHeader(timers.size))
            timers.forEach { t ->
                val timeStr = dateFormat.format(Date(t.triggerAtMs))
                val interval = t.intervalMs
                val intervalStr = if (interval != null) strings.autIntervalRepeat(interval / 1000) else strings.autIntervalOnce()
                sb.append(strings.autTimerLine(t.id, t.commandText, timeStr, intervalStr))
            }
            sb.append(strings.autTimerDelHint())
            return CommandResult.Text(sb.toString().trimEnd())
        }

        if (args[0] == "add" && args.size >= 3) {
            val delayStr = args[1].lowercase()
            val commandToRun = args.subList(2, args.size).joinToString(" ")

            val delayMs =
                parseDurationMs(delayStr)
                    ?: return CommandResult.Error(strings.autBadDelay(delayStr))

            val timer = automationRepository.addTimer(commandToRun, delayMs)
            return CommandResult.Text(
                strings.autTimerCreated(timer.id, timer.commandText, delayStr),
            )
        }

        if (args[0] == "del" && args.size >= 2) {
            val timerId = args[1]
            automationRepository.removeTimer(timerId)
            return CommandResult.Text(strings.autTimerDeleted(timerId))
        }

        return CommandResult.Error(
            strings.autTimersUsage(),
        )
    }

    private fun parseDurationMs(duration: String): Long? {
        val trimmed = duration.trim()
        val num = trimmed.filter { it.isDigit() }.toLongOrNull() ?: return null
        return when {
            trimmed.endsWith("s") -> num * 1000L
            trimmed.endsWith("m") -> num * 60 * 1000L
            trimmed.endsWith("h") -> num * 3600 * 1000L
            trimmed.endsWith("d") -> num * 86400 * 1000L
            else -> num * 1000L
        }
    }
}
