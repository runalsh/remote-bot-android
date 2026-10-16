package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.provider.RootStatusProvider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ShellAndPowerCommandsHandler(
    private val logger: AppLogger,
    private val rootStatusProvider: RootStatusProvider,
) : CommandHandler {
    override val commandNames: List<String> =
        listOf(
            "shell",
            "sh",
            "reboot",
            "shutdown",
            "poweroff",
            "recovery",
            "fastboot",
            "bootloader",
        )

    override val isDestructive: Boolean = false // Проверка деструктивности индивидуально по команде

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")

        return when (cmd) {
            "shell", "sh" -> handleShell(command.args)
            "reboot" -> handleReboot()
            "shutdown", "poweroff" -> handleShutdown()
            "recovery" -> handleRecovery()
            "fastboot", "bootloader" -> handleFastboot()
            else -> CommandResult.Error("Неизвестная shell-команда: $cmd")
        }
    }

    private suspend fun handleShell(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val rawCommand = args.joinToString(" ").trim()
            if (rawCommand.isBlank()) {
                return@withContext CommandResult.Text(
                    """
                    💻 <b>Linux Shell Терминал</b>
                    
                    Использование: <code>/shell &lt;команда&gt;</code>
                    Примеры:
                    • <code>/shell uname -a</code>
                    • <code>/shell df -h</code>
                    • <code>/shell getprop ro.build.version.release</code>
                    • <code>/shell ip addr show</code>
                    • <code>/shell ls -la /sdcard</code>
                    """.trimIndent(),
                )
            }

            logger.i("Shell", "Выполнение команды: $rawCommand")
            logger.t("Shell", "Запуск через libsu Shell.cmd(\"$rawCommand\")")

            try {
                val result =
                    withTimeout(30000L) {
                        Shell.cmd(rawCommand).exec()
                    }

                val isRoot =
                    rootStatusProvider.isRootGrantedSync() ||
                        try {
                            Shell.getShell().isRoot
                        } catch (_: Exception) {
                            false
                        }
                val mode = if (isRoot) "root" else "sh"
                val exitCode = result.code

                val stdout = result.out.joinToString("\n").trim()
                val stderr = result.err.joinToString("\n").trim()

                val combinedOutput = StringBuilder()
                if (stdout.isNotBlank()) {
                    combinedOutput.append(stdout)
                }
                if (stderr.isNotBlank()) {
                    if (combinedOutput.isNotEmpty()) combinedOutput.append("\n[STDERR]\n")
                    combinedOutput.append(stderr)
                }

                if (combinedOutput.isEmpty()) {
                    combinedOutput.append("(Команда завершилась без текстового вывода, код: $exitCode)")
                }

                val fullText = combinedOutput.toString()
                val escapedText =
                    if (fullText.length > 3500) {
                        fullText.take(3500) + "\n\n⚠️ [Вывод превысил лимит, показаны первые 3500 символов]"
                    } else {
                        fullText
                    }

                val codeEmoji = if (exitCode == 0) "✅" else "⚠️"

                logger.i("Shell", "Команда завершена (код $exitCode):\n$fullText")

                CommandResult.Text(
                    """
                    $codeEmoji <b>[$mode] $rawCommand</b>
                    <i>Код завершения: $exitCode</i>
                    
                    <pre>${escapeHtml(escapedText)}</pre>
                    """.trimIndent(),
                )
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                logger.w("Shell", "Таймаут выполнения команды (30 сек): $rawCommand")
                CommandResult.Error("⏱ Таймаут: выполнение команды заняло более 30 секунд и было прервано.")
            } catch (e: Exception) {
                logger.e("Shell", "Ошибка исполнения: ${e.localizedMessage}", e)
                CommandResult.Error("❌ Ошибка выполнения: ${e.localizedMessage}")
            }
        }

    private fun scheduleDelayedPowerCommand(
        actionName: String,
        statusText: String,
        shellCmd: String,
    ): CommandResult {
        logger.w("Power", "Запланировано выполнение power-команды ($actionName) через 10 секунд: $shellCmd")
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.delay(10_000L)
                logger.i("Power", "Выполнение отложенной команды: $shellCmd")
                Shell.cmd(shellCmd).exec()
            } catch (e: Exception) {
                logger.e("Power", "Ошибка отложенного выполнения $actionName: ${e.message}", e)
            }
        }
        return CommandResult.Text("$statusText\n<i>⏳ Выполнение произойдет через 10 секунд...</i>")
    }

    private suspend fun handleReboot(): CommandResult =
        withContext(Dispatchers.IO) {
            if (!rootStatusProvider.isRootGrantedSync() && !rootStatusProvider.checkRoot()) {
                return@withContext CommandResult.Error(
                    "❌ Для перезагрузки требуются права Superuser (Root). Предоставьте Root в Magisk/KernelSU/APatch.",
                )
            }
            scheduleDelayedPowerCommand(
                actionName = "reboot",
                statusText = "🔄 <b>Устройство будет перезагружено</b>",
                shellCmd = "svc power reboot || reboot",
            )
        }

    private suspend fun handleShutdown(): CommandResult =
        withContext(Dispatchers.IO) {
            if (!rootStatusProvider.isRootGrantedSync() && !rootStatusProvider.checkRoot()) {
                return@withContext CommandResult.Error(
                    "❌ Для выключения требуются права Superuser (Root). Предоставьте Root в Magisk/KernelSU/APatch.",
                )
            }
            scheduleDelayedPowerCommand(
                actionName = "shutdown",
                statusText = "⏻ <b>Устройство будет выключено</b>",
                shellCmd = "svc power shutdown || reboot -p || poweroff",
            )
        }

    private suspend fun handleRecovery(): CommandResult =
        withContext(Dispatchers.IO) {
            if (!rootStatusProvider.isRootGrantedSync() && !rootStatusProvider.checkRoot()) {
                return@withContext CommandResult.Error(
                    "❌ Для перехода в Recovery требуются права Superuser (Root). Предоставьте Root в Magisk/KernelSU/APatch.",
                )
            }
            scheduleDelayedPowerCommand(
                actionName = "recovery",
                statusText = "🛠 <b>Устройство будет перезагружено в режим Recovery</b>",
                shellCmd = "reboot recovery",
            )
        }

    private suspend fun handleFastboot(): CommandResult =
        withContext(Dispatchers.IO) {
            if (!rootStatusProvider.isRootGrantedSync() && !rootStatusProvider.checkRoot()) {
                return@withContext CommandResult.Error(
                    "❌ Для перехода в Bootloader требуются права Superuser (Root). Предоставьте Root в Magisk/KernelSU/APatch.",
                )
            }
            scheduleDelayedPowerCommand(
                actionName = "fastboot",
                statusText = "⚡ <b>Устройство будет перезагружено в режим Bootloader (Fastboot)</b>",
                shellCmd = "reboot bootloader",
            )
        }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
