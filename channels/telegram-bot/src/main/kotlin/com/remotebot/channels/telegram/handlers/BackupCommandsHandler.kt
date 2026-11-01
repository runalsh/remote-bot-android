package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.backup.BackupManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.ProxyConfigRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupCommandsHandler(
    private val botConfigRepository: BotConfigRepository,
    private val proxyConfigRepository: ProxyConfigRepository,
    private val backupManager: BackupManager,
    private val logger: AppLogger,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository.config.value.botLanguage)

    override val commandNames: List<String> = listOf("backup", "restore_backup")

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "backup" -> handleBackup()
            "restore_backup" -> handleRestoreBackup(command.args)
            else -> CommandResult.Error(strings.bkpUnknownCmd(cmd))
        }
    }

    private fun handleBackup(): CommandResult {
        val botCfg = botConfigRepository.config.value
        val proxyCfg = proxyConfigRepository.proxyConfig.value
        val jsonStr = backupManager.exportBackup(botCfg, proxyCfg)

        return try {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val tempFile = File.createTempFile("remotebot_backup_${dateStr}_", ".json")
            tempFile.writeText(jsonStr, Charsets.UTF_8)
            tempFile.setReadable(true, false)

            CommandResult.File(
                filePath = tempFile.absolutePath,
                caption = null,
                autoDelete = true,
                extraMessage = strings.bkpFileCaption(),
            )
        } catch (e: Exception) {
            logger.e("Backup", "Failed to create backup file: ${e.message}", e)
            CommandResult.Text(
                strings.bkpJsonBlock(jsonStr),
            )
        }
    }

    private suspend fun handleRestoreBackup(args: List<String>): CommandResult {
        val rawJson = args.joinToString(" ").trim()
        if (rawJson.isBlank()) {
            return CommandResult.Text(
                strings.bkpRestoreHelp(),
            )
        }

        val result = backupManager.parseBackup(rawJson)
        return if (result.isSuccess) {
            val backup = result.getOrThrow()
            botConfigRepository.updateConfig {
                backup.botConfig
            }
            proxyConfigRepository.updateProxy(backup.proxyConfig)
            logger.i("Backup", "Configuration restored from backup successfully (version ${backup.version})")
            CommandResult.Text(strings.bkpRestored(backup.version.toString()))
        } else {
            val err = result.exceptionOrNull()?.localizedMessage ?: strings.bkpUnknownError()
            logger.w("Backup", "Backup restore error: $err")
            CommandResult.Error(strings.bkpDeserializeError(err))
        }
    }
}
