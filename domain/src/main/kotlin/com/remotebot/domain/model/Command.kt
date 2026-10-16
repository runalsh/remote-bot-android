package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CommandSource {
    TELEGRAM,
    SMS,
}

@Serializable
data class IncomingCommand(
    val id: String,
    val name: String,
    val rawText: String,
    val args: List<String> = emptyList(),
    val source: CommandSource,
    val senderId: String, // Telegram User ID or E.164 phone number
    val chatId: Long? = null, // Telegram Chat ID
    val receivedAtMs: Long = System.currentTimeMillis(),
)

@Serializable
sealed class CommandResult {
    data class Text(
        val message: String,
        val keyboard: InlineKeyboardMarkup? = null,
    ) : CommandResult()

    data class File(
        val filePath: String,
        val caption: String? = null,
        val autoDelete: Boolean = false,
        val extraMessage: String? = null,
    ) : CommandResult()

    data class Photo(
        val filePath: String,
        val caption: String? = null,
        val autoDelete: Boolean = false,
    ) : CommandResult()

    data class Video(
        val filePath: String,
        val caption: String? = null,
        val durationSeconds: Int? = null,
        val autoDelete: Boolean = false,
    ) : CommandResult()

    data class Voice(
        val filePath: String,
        val caption: String? = null,
        val durationSeconds: Int? = null,
        val autoDelete: Boolean = false,
    ) : CommandResult()

    data class Audio(
        val filePath: String,
        val caption: String? = null,
        val durationSeconds: Int? = null,
        val title: String? = null,
        val performer: String? = null,
        val autoDelete: Boolean = false,
    ) : CommandResult()

    data class MediaGroup(
        val filePaths: List<String>,
        val caption: String? = null,
        val autoDelete: Boolean = false,
    ) : CommandResult()

    data class Error(
        val errorMsg: String,
    ) : CommandResult()

    object Acknowledged : CommandResult()
}
