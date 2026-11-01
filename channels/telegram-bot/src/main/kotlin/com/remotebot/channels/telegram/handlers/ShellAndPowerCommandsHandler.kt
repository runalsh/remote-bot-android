package com.remotebot.channels.telegram.handlers

import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.BotLanguage
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.BotConfigRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ShellAndPowerCommandsHandler(
    private val logger: AppLogger,
    private val rootStatusProvider: RootStatusProvider,
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

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

    override val isDestructive: Boolean = false // Destructiveness is checked per command

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")

        return when (cmd) {
            "shell", "sh" -> handleShell(command.args)
            "reboot" -> handleReboot()
            "shutdown", "poweroff" -> handleShutdown()
            "recovery" -> handleRecovery()
            "fastboot", "bootloader" -> handleFastboot()
            else -> CommandResult.Error(strings.shUnknownCmd(cmd))
        }
    }

    private suspend fun handleShell(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val rawCommand = args.joinToString(" ").trim()
            if (rawCommand.isBlank()) {
                return@withContext CommandResult.Text(
                    strings.shHelp(),
                )
            }

            logger.i("Shell", "Executing command: $rawCommand")
            logger.t("Shell", "Running via libsu Shell.cmd(\"$rawCommand\")")

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
                    combinedOutput.append(strings.shNoOutput(exitCode))
                }

                val fullText = combinedOutput.toString()
                val escapedText =
                    if (fullText.length > 3500) {
                        fullText.take(3500) + strings.shTruncated(3500)
                    } else {
                        fullText
                    }

                val codeEmoji = if (exitCode == 0) "✅" else "⚠️"

                logger.i("Shell", "Command completed (code $exitCode):\n$fullText")

                CommandResult.Text(
                    strings.shResultBlock(codeEmoji, mode, rawCommand, exitCode, escapeHtml(escapedText)),
                )
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                logger.w("Shell", "Command execution timeout (30s): $rawCommand")
                CommandResult.Error(strings.shTimeout())
            } catch (e: Exception) {
                logger.e("Shell", "Execution error: ${e.localizedMessage}", e)
                CommandResult.Error(strings.shExecError(e.localizedMessage ?: ""))
            }
        }

    private fun scheduleDelayedPowerCommand(
        actionName: String,
        statusText: String,
        shellCmd: String,
    ): CommandResult {
        logger.w("Power", "Power command scheduled ($actionName) in 10 seconds: $shellCmd")
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.delay(10_000L)
                logger.i("Power", "Executing deferred command: $shellCmd")
                Shell.cmd(shellCmd).exec()
            } catch (e: Exception) {
                logger.e("Power", "Deferred execution error for $actionName: ${e.message}", e)
            }
        }
        return CommandResult.Text(strings.shDelayedFooter(statusText))
    }

    private suspend fun handleReboot(): CommandResult =
        withContext(Dispatchers.IO) {
            if (!rootStatusProvider.isRootGrantedSync() && !rootStatusProvider.checkRoot()) {
                return@withContext CommandResult.Error(
                    strings.shRootRestart(),
                )
            }
            scheduleDelayedPowerCommand(
                actionName = "reboot",
                statusText = strings.shStatusReboot(),
                shellCmd = "svc power reboot || reboot",
            )
        }

    private suspend fun handleShutdown(): CommandResult =
        withContext(Dispatchers.IO) {
            if (!rootStatusProvider.isRootGrantedSync() && !rootStatusProvider.checkRoot()) {
                return@withContext CommandResult.Error(
                    strings.shRootShutdown(),
                )
            }
            scheduleDelayedPowerCommand(
                actionName = "shutdown",
                statusText = strings.shStatusShutdown(),
                shellCmd = "svc power shutdown || reboot -p || poweroff",
            )
        }

    private suspend fun handleRecovery(): CommandResult =
        withContext(Dispatchers.IO) {
            if (!rootStatusProvider.isRootGrantedSync() && !rootStatusProvider.checkRoot()) {
                return@withContext CommandResult.Error(
                    strings.shRootRecovery(),
                )
            }
            scheduleDelayedPowerCommand(
                actionName = "recovery",
                statusText = strings.shStatusRecovery(),
                shellCmd = "reboot recovery",
            )
        }

    private suspend fun handleFastboot(): CommandResult =
        withContext(Dispatchers.IO) {
            if (!rootStatusProvider.isRootGrantedSync() && !rootStatusProvider.checkRoot()) {
                return@withContext CommandResult.Error(
                    strings.shRootBootloader(),
                )
            }
            scheduleDelayedPowerCommand(
                actionName = "fastboot",
                statusText = strings.shStatusFastboot(),
                shellCmd = "reboot bootloader",
            )
        }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
