package com.remotebot.channels.telegram.engine

import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.CommandResult
import com.topjohnwu.superuser.Shell
import java.io.File

class TelegramResultSender(
    private val apiClient: TelegramApiClient,
    private val logger: AppLogger? = null,
) {
    fun ensureFileReadable(file: File) {
        if (!file.canRead()) {
            file.setReadable(true, false)
            try {
                Shell.cmd("chmod 666 \"${file.absolutePath}\"").exec()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun sendResultToChat(
        chatId: Long,
        result: CommandResult,
    ) {
        try {
            when (result) {
                is CommandResult.Text -> {
                    apiClient.sendMessage(chatId, result.message, replyMarkup = result.keyboard)
                }
                is CommandResult.File -> {
                    val file = File(result.filePath)
                    if (file.exists()) {
                        ensureFileReadable(file)
                        apiClient.sendDocument(chatId, file, result.caption)
                        val extraMsg = result.extraMessage
                        if (extraMsg != null) {
                            apiClient.sendMessage(chatId, extraMsg)
                        }
                        if (result.autoDelete) {
                            file.delete()
                        }
                    } else {
                        apiClient.sendMessage(chatId, "⚠️ Файл не найден: ${result.filePath}")
                    }
                }
                is CommandResult.Photo -> {
                    val file = File(result.filePath)
                    if (file.exists()) {
                        ensureFileReadable(file)
                        apiClient.sendPhoto(chatId, file, result.caption)
                        if (result.autoDelete) {
                            file.delete()
                        }
                    } else {
                        apiClient.sendMessage(chatId, "⚠️ Фото не найдено: ${result.filePath}")
                    }
                }
                is CommandResult.Video -> {
                    val file = File(result.filePath)
                    if (file.exists()) {
                        ensureFileReadable(file)
                        apiClient.sendVideo(chatId, file, result.caption, result.durationSeconds)
                        if (result.autoDelete) {
                            file.delete()
                        }
                    } else {
                        apiClient.sendMessage(chatId, "⚠️ Видео не найдено: ${result.filePath}")
                    }
                }
                is CommandResult.Voice -> {
                    val file = File(result.filePath)
                    if (file.exists()) {
                        ensureFileReadable(file)
                        apiClient.sendVoice(chatId, file, result.caption, result.durationSeconds)
                        if (result.autoDelete) {
                            file.delete()
                        }
                    } else {
                        apiClient.sendMessage(chatId, "⚠️ Голосовое сообщение не найдено: ${result.filePath}")
                    }
                }
                is CommandResult.Audio -> {
                    val file = File(result.filePath)
                    if (file.exists()) {
                        ensureFileReadable(file)
                        apiClient.sendAudio(chatId, file, result.caption, result.durationSeconds, result.title, result.performer)
                        if (result.autoDelete) {
                            file.delete()
                        }
                    } else {
                        apiClient.sendMessage(chatId, "⚠️ Аудиофайл не найден: ${result.filePath}")
                    }
                }
                is CommandResult.MediaGroup -> {
                    val files = result.filePaths.map { File(it) }.filter { it.exists() }
                    files.forEach { ensureFileReadable(it) }
                    if (files.isNotEmpty()) {
                        apiClient.sendMediaGroupPhotos(chatId, files, result.caption)
                        if (result.autoDelete) {
                            files.forEach { it.delete() }
                        }
                    } else {
                        apiClient.sendMessage(chatId, "⚠️ Файлы медиагруппы не найдены")
                    }
                }
                is CommandResult.Error -> {
                    apiClient.sendMessage(chatId, "❌ ${result.errorMsg}")
                }
                CommandResult.Acknowledged -> {
                    // Silent acknowledgement
                }
            }
        } catch (e: Exception) {
            logger?.e("TelegramEngine", "Ошибка отправки результата в чат $chatId: ${e.message}", e)
            try {
                apiClient.sendMessage(chatId, "⚠️ Ошибка отправки файла в чат: ${e.localizedMessage ?: e.message}")
            } catch (_: Exception) {
            }
        }
    }
}
