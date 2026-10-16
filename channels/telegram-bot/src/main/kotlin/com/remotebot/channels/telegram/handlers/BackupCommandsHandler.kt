package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.backup.BackupManager
import com.remotebot.core.common.logger.AppLogger
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
    override val commandNames: List<String> = listOf("backup", "restore_backup")

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "backup" -> handleBackup()
            "restore_backup" -> handleRestoreBackup(command.args)
            else -> CommandResult.Error("Неизвестная команда бэкапа: $cmd")
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
                extraMessage = "💾 <b>Резервная копия конфигурации Remote Bot</b>\n\n<i>Для восстановления отправьте этот файл боту или выполните:</i>\n<code>/restore_backup &lt;json&gt;</code>",
            )
        } catch (e: Exception) {
            logger.e("Backup", "Ошибка создания файла бэкапа: ${e.message}", e)
            CommandResult.Text(
                """
                💾 <b>Резервная копия конфигурации:</b>
                
                <pre><code class="language-json">
                $jsonStr
                </code></pre>
                
                <i>Для восстановления отправьте команду:</i>
                <code>/restore_backup &lt;json&gt;</code>
                """.trimIndent(),
            )
        }
    }

    private suspend fun handleRestoreBackup(args: List<String>): CommandResult {
        val rawJson = args.joinToString(" ").trim()
        if (rawJson.isBlank()) {
            return CommandResult.Text(
                """
                📥 <b>Восстановление конфигурации Remote Bot</b>

                Доступно два способа:
                1. <b>Отправить файл</b>: просто отправьте файл <code>.json</code> с резервной копией в этот чат (можно с подписью <code>/restore_backup</code>).
                2. <b>Вставить текст</b>: отправьте команду с содержимым JSON:
                <code>/restore_backup &lt;json-текст&gt;</code>
                """.trimIndent(),
            )
        }

        val result = backupManager.parseBackup(rawJson)
        return if (result.isSuccess) {
            val backup = result.getOrThrow()
            botConfigRepository.updateConfig {
                backup.botConfig
            }
            proxyConfigRepository.updateProxy(backup.proxyConfig)
            logger.i("Backup", "Конфигурация успешно восстановлена из бэкапа (версия ${backup.version})")
            CommandResult.Text("✅ <b>Конфигурация успешно восстановлена</b> из резервной копии (v${backup.version})!")
        } else {
            val err = result.exceptionOrNull()?.localizedMessage ?: "Неизвестная ошибка"
            logger.w("Backup", "Ошибка восстановления из бэкапа: $err")
            CommandResult.Error("❌ Ошибка десериализации бэкапа: $err")
        }
    }
}
