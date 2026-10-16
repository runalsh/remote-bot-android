package com.remotebot.channels.telegram.handlers

import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.TaskerTask
import com.remotebot.domain.repository.AutomationRepository
import com.remotebot.domain.repository.TaskerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutomationCommandsHandler(
    private val taskerRepository: TaskerRepository,
    private val automationRepository: AutomationRepository,
) : CommandHandler {
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
            else -> CommandResult.Error("Неизвестная команда автоматизации: $cmd")
        }
    }

    private suspend fun handleTasker(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val installed = taskerRepository.isTaskerInstalled()
            val instText = if (installed) "✅ Установлен" else "⚠️ Не обнаружен (установите Tasker)"
            return CommandResult.Text(
                "🤖 <b>Интеграция с Tasker:</b> $instText\n\nИспользование: <code>/tasker &lt;TaskName&gt; [par1] [par2]</code>\nПример: <code>/tasker NightMode 1 on</code>",
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
                val extraStr = if (paramsStr.isNotBlank()) " (Параметры: $paramsStr)" else ""
                CommandResult.Text("✅ <b>Tasker задача отправлена на исполнение:</b> <code>$taskName</code>$extraStr")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка отправки задачи в Tasker: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleScript(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                "Формат: <code>/script &lt;код_скрипта&gt;</code>\nПример: <code>/script date +%Y-%m-%d</code>\nПример: <code>/script echo 'Hello world'</code>",
            )
        }

        val code = args.joinToString(" ")
        val result = automationRepository.executeScript(code)

        return if (result.isSuccess) {
            CommandResult.Text("⚡ <b>Результат выполнения скрипта (${result.executionTimeMs} ms):</b>\n\n<code>${result.output}</code>")
        } else {
            CommandResult.Error(
                "❌ <b>Ошибка выполнения (${result.executionTimeMs} ms):</b>\n${result.error}\n\nВывод:\n<code>${result.output}</code>",
            )
        }
    }

    private suspend fun handleTimers(args: List<String>): CommandResult {
        if (args.isEmpty() || args[0] == "list") {
            val timers = automationRepository.timers.value
            if (timers.isEmpty()) {
                return CommandResult.Text(
                    "⏱ <b>Активных таймеров нет.</b>\nСоздать: <code>/timers add 60s /photo</code> или <code>/timers add 10m /location</code>",
                )
            }

            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val sb = StringBuilder("⏱ <b>Запланированные таймеры (${timers.size}):</b>\n\n")
            timers.forEach { t ->
                val timeStr = dateFormat.format(Date(t.triggerAtMs))
                val interval = t.intervalMs
                val intervalStr = if (interval != null) " [повтор каждые ${interval / 1000}с]" else " [однократно]"
                sb.append("• <b>ID: ${t.id}</b> | <code>${t.commandText}</code>\n   <i>Сработает в $timeStr$intervalStr</i>\n\n")
            }
            sb.append("<i>Удалить таймер: /timers del &lt;ID&gt;</i>")
            return CommandResult.Text(sb.toString().trimEnd())
        }

        if (args[0] == "add" && args.size >= 3) {
            val delayStr = args[1].lowercase()
            val commandToRun = args.subList(2, args.size).joinToString(" ")

            val delayMs =
                parseDurationMs(delayStr)
                    ?: return CommandResult.Error("Неверный формат времени задержки: <code>$delayStr</code> (примеры: 30s, 5m, 2h)")

            val timer = automationRepository.addTimer(commandToRun, delayMs)
            return CommandResult.Text(
                "✅ <b>Таймер #${timer.id} создан!</b>\nКоманда <code>${timer.commandText}</code> выполнится через $delayStr.",
            )
        }

        if (args[0] == "del" && args.size >= 2) {
            val timerId = args[1]
            automationRepository.removeTimer(timerId)
            return CommandResult.Text("🗑️ Таймер #$timerId удален.")
        }

        return CommandResult.Error(
            "Формат: <code>/timers list</code> или <code>/timers add &lt;время&gt; &lt;команда&gt;</code> или <code>/timers del &lt;ID&gt;</code>",
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
