package com.remotebot.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TgResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null,
)

@Serializable
data class TgUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TgMessage? = null,
    @SerialName("callback_query") val callbackQuery: TgCallbackQuery? = null,
)

@Serializable
data class TgCallbackQuery(
    val id: String,
    val from: TgUser,
    val message: TgMessage? = null,
    val data: String? = null,
)

@Serializable
data class TgMessage(
    @SerialName("message_id") val messageId: Long,
    val from: TgUser? = null,
    val chat: TgChat,
    val date: Long,
    val text: String? = null,
    val caption: String? = null,
    val document: TgDocument? = null,
    @SerialName("reply_to_message") val replyToMessage: TgMessage? = null,
)

@Serializable
data class TgDocument(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class TgFile(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("file_path") val filePath: String? = null,
)

@Serializable
data class TgUser(
    val id: Long,
    @SerialName("is_bot") val isBot: Boolean = false,
    @SerialName("first_name") val firstName: String = "",
    val username: String? = null,
)

@Serializable
data class TgChat(
    val id: Long,
    val type: String = "private",
    val title: String? = null,
    val username: String? = null,
)

@Serializable
data class InlineKeyboardMarkup(
    @SerialName("inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>,
)

@Serializable
data class InlineKeyboardButton(
    val text: String,
    @SerialName("callback_data") val callbackData: String? = null,
)
