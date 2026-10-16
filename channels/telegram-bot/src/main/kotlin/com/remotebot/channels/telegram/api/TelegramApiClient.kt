package com.remotebot.channels.telegram.api

import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.TgResponse
import com.remotebot.domain.model.TgUpdate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class TelegramApiClient(
    private val httpClient: HttpClient,
    private val configProvider: () -> BotConfig,
    private val logger: com.remotebot.core.common.logger.AppLogger? = null,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // Multi-Endpoint Failover & Balancing state
    @Volatile
    private var currentEndpointIndex = 0

    @Volatile
    var lastActiveEndpointUrl: String = "https://api.telegram.org"
        private set

    /**
     * Возвращает упорядоченный список базовых URL (API root) согласно выбранной стратегии:
     * FAILOVER, ROUND_ROBIN или LOWEST_LATENCY.
     */
    fun getCandidateRoots(): List<String> {
        val cfg = configProvider()
        val roots = mutableListOf<String>()
        val customRoot = cfg.customApiRoot
        if (!customRoot.isNullOrBlank()) {
            roots.add(customRoot.trim().trimEnd('/'))
        }
        for (ep in cfg.apiEndpoints) {
            val clean = ep.trim().trimEnd('/')
            if (clean.isNotBlank() && !roots.contains(clean)) {
                roots.add(clean)
            }
        }
        if (!roots.contains("https://api.telegram.org")) {
            roots.add("https://api.telegram.org")
        }

        return when (cfg.endpointSelectionStrategy) {
            com.remotebot.domain.model.EndpointSelectionStrategy.FAILOVER -> {
                // Если был зафиксирован рабочий индекс, ставим его первым, остальные как fallback
                if (currentEndpointIndex in roots.indices && currentEndpointIndex > 0) {
                    val active = roots[currentEndpointIndex]
                    listOf(active) + roots.filter { it != active }
                } else {
                    roots
                }
            }
            com.remotebot.domain.model.EndpointSelectionStrategy.ROUND_ROBIN -> {
                val idx = ((currentEndpointIndex + 1) % roots.size).also { currentEndpointIndex = it }
                val active = roots[idx]
                listOf(active) + roots.filter { it != active }
            }
            com.remotebot.domain.model.EndpointSelectionStrategy.LOWEST_LATENCY -> {
                roots
            }
        }
    }

    private fun getBaseUrlForRoot(root: String): String {
        val cfg = configProvider()
        lastActiveEndpointUrl = root
        return "$root/bot${cfg.botToken}"
    }

    private val baseUrl: String
        get() = getBaseUrlForRoot(getCandidateRoots().first())

    /**
     * Исполнение сетевого запроса к Telegram API с автоматическим переключением эндпоинтов (Failover)
     */
    suspend fun <T> executeWithFailover(block: suspend (baseUrl: String) -> T): T {
        val roots = getCandidateRoots()
        var lastException: Exception? = null

        for ((index, root) in roots.withIndex()) {
            try {
                val fullUrl = getBaseUrlForRoot(root)
                val result = block(fullUrl)
                currentEndpointIndex = index
                com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordConnection(
                    serverId = root,
                    serverName = root.removePrefix("https://").removePrefix("http://"),
                    serverType = com.remotebot.domain.model.ServerType.TELEGRAM_API,
                    outcome = com.remotebot.domain.model.ConnectionOutcome.SUCCESS,
                )
                return result
            } catch (e: Exception) {
                lastException = e
                val isTimeout =
                    e is java.net.SocketTimeoutException ||
                        (e.message?.contains("timeout", ignoreCase = true) == true) ||
                        (e.message?.contains("timed out", ignoreCase = true) == true)
                val outcome =
                    if (isTimeout) {
                        com.remotebot.domain.model.ConnectionOutcome.TIMEOUT
                    } else {
                        com.remotebot.domain.model.ConnectionOutcome.FAILURE
                    }
                com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordConnection(
                    serverId = root,
                    serverName = root.removePrefix("https://").removePrefix("http://"),
                    serverType = com.remotebot.domain.model.ServerType.TELEGRAM_API,
                    outcome = outcome,
                )
                logger?.w("TelegramApi", "Сбой эндпоинта $root: ${e.message}. Пробуем следующий...")
            }
        }
        throw lastException ?: RuntimeException("Все эндпоинты Telegram API недоступны")
    }

    suspend fun getUpdates(
        offset: Long,
        timeoutSeconds: Int = 30,
        limit: Int = 50,
    ): List<TgUpdate> =
        executeWithFailover { targetBaseUrl ->
            val response =
                httpClient
                    .get("$targetBaseUrl/getUpdates") {
                        parameter("offset", offset)
                        parameter("timeout", timeoutSeconds)
                        parameter("limit", limit)
                        parameter("allowed_updates", "[\"message\", \"callback_query\"]")
                    }.body<TgResponse<List<TgUpdate>>>()

            if (!response.ok) {
                val err = "Telegram API Error: ${response.description} (code ${response.errorCode})"
                logger?.e("TelegramApi", err)
                throw RuntimeException(err)
            }
            response.result ?: emptyList()
        }

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        parseMode: String = "HTML",
        replyMarkup: InlineKeyboardMarkup? = null,
        forceReply: Boolean = false,
        inputPlaceholder: String? = null,
        disableWebPagePreview: Boolean = true,
    ): Boolean {
        val serializedMarkup =
            when {
                replyMarkup != null -> json.encodeToString(replyMarkup)
                forceReply -> {
                    val placeholderField =
                        if (!inputPlaceholder.isNullOrBlank()) {
                            val escaped = inputPlaceholder.replace("\"", "\\\"")
                            ",\"input_field_placeholder\":\"$escaped\""
                        } else {
                            ""
                        }
                    """{"force_reply":true,"selective":true$placeholderField}"""
                }
                else -> null
            }
        try {
            if (text.length <= 4000) {
                val response =
                    httpClient
                        .post("$baseUrl/sendMessage") {
                            parameter("chat_id", chatId)
                            parameter("text", text)
                            parameter("parse_mode", parseMode)
                            if (disableWebPagePreview) {
                                parameter("disable_web_page_preview", true)
                                parameter("link_preview_options", """{"is_disabled":true}""")
                            }
                            if (serializedMarkup != null) {
                                parameter("reply_markup", serializedMarkup)
                            }
                        }.body<TgResponse<Unit>>()
                if (!response.ok) {
                    if (response.description?.contains("can't parse entities", ignoreCase = true) == true) {
                        logger?.w("TelegramApi", "HTML parse failed, retrying plain text for chat $chatId")
                        val cleanText = text.replace(Regex("<[^>]*>"), "")
                        val retryResponse =
                            httpClient
                                .post("$baseUrl/sendMessage") {
                                    parameter("chat_id", chatId)
                                    parameter("text", cleanText)
                                    if (disableWebPagePreview) {
                                        parameter("disable_web_page_preview", true)
                                        parameter("link_preview_options", """{"is_disabled":true}""")
                                    }
                                    if (serializedMarkup != null) {
                                        parameter("reply_markup", serializedMarkup)
                                    }
                                }.body<TgResponse<Unit>>()
                        return retryResponse.ok
                    }
                    logger?.e("TelegramApi", "sendMessage error: ${response.description} (code ${response.errorCode})")
                }
                return response.ok
            }

            // Если текст длиннее 4000 символов, разбиваем по границам строк
            val chunks = splitTextIntoChunks(text, 4000)
            var allOk = true
            for ((index, chunk) in chunks.withIndex()) {
                val markup = if (index == chunks.size - 1) serializedMarkup else null
                val response =
                    httpClient
                        .post("$baseUrl/sendMessage") {
                            parameter("chat_id", chatId)
                            parameter("text", chunk)
                            parameter("parse_mode", parseMode)
                            if (disableWebPagePreview) {
                                parameter("disable_web_page_preview", true)
                                parameter("link_preview_options", """{"is_disabled":true}""")
                            }
                            if (markup != null) {
                                parameter("reply_markup", markup)
                            }
                        }.body<TgResponse<Unit>>()
                if (!response.ok) {
                    if (response.description?.contains("can't parse entities", ignoreCase = true) == true) {
                        logger?.w("TelegramApi", "HTML parse failed in chunk $index, retrying plain text")
                        val cleanChunk = chunk.replace(Regex("<[^>]*>"), "")
                        val retryResponse =
                            httpClient
                                .post("$baseUrl/sendMessage") {
                                    parameter("chat_id", chatId)
                                    parameter("text", cleanChunk)
                                    if (disableWebPagePreview) {
                                        parameter("disable_web_page_preview", true)
                                        parameter("link_preview_options", """{"is_disabled":true}""")
                                    }
                                    if (markup != null) {
                                        parameter("reply_markup", markup)
                                    }
                                }.body<TgResponse<Unit>>()
                        if (!retryResponse.ok) allOk = false
                    } else {
                        logger?.e("TelegramApi", "sendMessage chunk error: ${response.description} (code ${response.errorCode})")
                        allOk = false
                    }
                }
            }
            return allOk
        } catch (e: Exception) {
            logger?.e("TelegramApi", "sendMessage HTTP request failed: ${e.message}", e)
            throw e
        }
    }

    private fun splitTextIntoChunks(
        text: String,
        maxChunkSize: Int,
    ): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)

        val result = mutableListOf<String>()
        val lines = text.split("\n")
        val currentChunk = StringBuilder()

        for (line in lines) {
            if (currentChunk.isNotEmpty() && currentChunk.length + line.length + 1 > maxChunkSize) {
                result.add(balanceHtmlTags(currentChunk.toString().trimEnd()))
                currentChunk.clear()
            }
            if (line.length > maxChunkSize) {
                var start = 0
                while (start < line.length) {
                    val end = minOf(start + maxChunkSize, line.length)
                    result.add(balanceHtmlTags(line.substring(start, end)))
                    start = end
                }
            } else {
                if (currentChunk.isNotEmpty()) currentChunk.append("\n")
                currentChunk.append(line)
            }
        }
        if (currentChunk.isNotEmpty()) {
            result.add(balanceHtmlTags(currentChunk.toString().trimEnd()))
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    private fun balanceHtmlTags(chunk: String): String {
        val tags = listOf("b", "i", "code", "pre", "u", "s")
        val sb = StringBuilder(chunk)
        for (tag in tags) {
            val openCount = Regex("<" + tag + ">", RegexOption.IGNORE_CASE).findAll(chunk).count()
            val closeCount = Regex("</" + tag + ">", RegexOption.IGNORE_CASE).findAll(chunk).count()
            if (openCount > closeCount) {
                repeat(openCount - closeCount) {
                    sb.append("</").append(tag).append(">")
                }
            }
        }
        return sb.toString()
    }

    suspend fun editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
        parseMode: String = "HTML",
        replyMarkup: InlineKeyboardMarkup? = null,
        disableWebPagePreview: Boolean = true,
    ): Boolean {
        return try {
            val response =
                httpClient
                    .post("$baseUrl/editMessageText") {
                        parameter("chat_id", chatId)
                        parameter("message_id", messageId)
                        parameter("text", text)
                        parameter("parse_mode", parseMode)
                        if (disableWebPagePreview) {
                            parameter("disable_web_page_preview", true)
                            parameter("link_preview_options", """{"is_disabled":true}""")
                        }
                        if (replyMarkup != null) {
                            parameter("reply_markup", json.encodeToString(replyMarkup))
                        }
                    }.body<TgResponse<Unit>>()
            if (!response.ok) {
                if (response.description?.contains("message is not modified", ignoreCase = true) == true) {
                    logger?.d("TelegramApi", "editMessageText: сообщение не изменено")
                    return true
                }
                logger?.e("TelegramApi", "editMessageText error: ${response.description} (code ${response.errorCode})")
            }
            response.ok
        } catch (e: Exception) {
            if (e.message?.contains("message is not modified", ignoreCase = true) == true) {
                logger?.d("TelegramApi", "editMessageText: сообщение не изменено")
                true
            } else {
                logger?.e("TelegramApi", "editMessageText HTTP request failed: ${e.message}", e)
                throw e
            }
        }
    }

    suspend fun answerCallbackQuery(
        callbackQueryId: String,
        text: String? = null,
        showAlert: Boolean = false,
    ): Boolean {
        val safeText = if (text != null && text.length > 195) text.take(190) + "…" else text
        return try {
            val response =
                httpClient
                    .post("$baseUrl/answerCallbackQuery") {
                        parameter("callback_query_id", callbackQueryId)
                        if (safeText != null) {
                            parameter("text", safeText)
                            parameter("show_alert", showAlert)
                        }
                    }.body<TgResponse<Boolean>>()
            if (!response.ok) {
                logger?.w("TelegramApi", "answerCallbackQuery error: ${response.description}")
            }
            response.ok
        } catch (e: Exception) {
            logger?.w("TelegramApi", "answerCallbackQuery failed: ${e.message}")
            false
        }
    }

    suspend fun sendDocument(
        chatId: Long,
        file: File,
        caption: String? = null,
    ): Boolean {
        val response =
            httpClient
                .post("$baseUrl/sendDocument") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("chat_id", chatId.toString())
                                caption?.let {
                                    append("caption", it)
                                    append("parse_mode", "HTML")
                                }
                                append(
                                    "document",
                                    file.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                    },
                                )
                            },
                        ),
                    )
                }.body<TgResponse<Unit>>()
        return response.ok
    }

    suspend fun sendPhoto(
        chatId: Long,
        file: File,
        caption: String? = null,
    ): Boolean {
        val response =
            httpClient
                .post("$baseUrl/sendPhoto") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("chat_id", chatId.toString())
                                caption?.let {
                                    append("caption", it)
                                    append("parse_mode", "HTML")
                                }
                                append(
                                    "photo",
                                    file.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                    },
                                )
                            },
                        ),
                    )
                }.body<TgResponse<Unit>>()
        return response.ok
    }

    suspend fun sendLocation(
        chatId: Long,
        latitude: Double,
        longitude: Double,
    ): Boolean =
        try {
            val response =
                httpClient
                    .post("$baseUrl/sendLocation") {
                        parameter("chat_id", chatId)
                        parameter("latitude", latitude)
                        parameter("longitude", longitude)
                    }.body<TgResponse<Unit>>()
            response.ok
        } catch (e: Exception) {
            logger?.d("TelegramApi", "sendLocation failed: ${e.message}")
            false
        }

    suspend fun sendPhotoBytes(
        chatId: Long,
        bytes: ByteArray,
        fileName: String,
        caption: String? = null,
    ): Boolean {
        val response =
            httpClient
                .post("$baseUrl/sendPhoto") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("chat_id", chatId.toString())
                                caption?.let {
                                    append("caption", it)
                                    append("parse_mode", "HTML")
                                }
                                append(
                                    "photo",
                                    bytes,
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                    },
                                )
                            },
                        ),
                    )
                }.body<TgResponse<Unit>>()
        return response.ok
    }

    suspend fun sendVideo(
        chatId: Long,
        file: File,
        caption: String? = null,
        durationSeconds: Int? = null,
    ): Boolean {
        val response =
            httpClient
                .post("$baseUrl/sendVideo") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("chat_id", chatId.toString())
                                caption?.let {
                                    append("caption", it)
                                    append("parse_mode", "HTML")
                                }
                                durationSeconds?.let { append("duration", it.toString()) }
                                append("supports_streaming", "true")
                                append(
                                    "video",
                                    file.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                    },
                                )
                            },
                        ),
                    )
                }.body<TgResponse<Unit>>()
        return response.ok
    }

    suspend fun sendVoice(
        chatId: Long,
        file: File,
        caption: String? = null,
        durationSeconds: Int? = null,
    ): Boolean {
        val response =
            httpClient
                .post("$baseUrl/sendVoice") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("chat_id", chatId.toString())
                                caption?.let {
                                    append("caption", it)
                                    append("parse_mode", "HTML")
                                }
                                durationSeconds?.let { append("duration", it.toString()) }
                                append(
                                    "voice",
                                    file.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                    },
                                )
                            },
                        ),
                    )
                }.body<TgResponse<Unit>>()
        return response.ok
    }

    suspend fun sendAudio(
        chatId: Long,
        file: File,
        caption: String? = null,
        durationSeconds: Int? = null,
        title: String? = null,
        performer: String? = null,
    ): Boolean {
        val response =
            httpClient
                .post("$baseUrl/sendAudio") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("chat_id", chatId.toString())
                                caption?.let {
                                    append("caption", it)
                                    append("parse_mode", "HTML")
                                }
                                durationSeconds?.let { append("duration", it.toString()) }
                                title?.let { append("title", it) }
                                performer?.let { append("performer", it) }
                                append(
                                    "audio",
                                    file.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                    },
                                )
                            },
                        ),
                    )
                }.body<TgResponse<Unit>>()
        return response.ok
    }

    suspend fun sendMediaGroupPhotos(
        chatId: Long,
        files: List<File>,
        caption: String? = null,
    ): Boolean {
        if (files.isEmpty()) return false
        if (files.size == 1) return sendPhoto(chatId, files.first(), caption)

        val mediaArray =
            files.mapIndexed { index, _ ->
                val isFirst = index == 0
                val obj =
                    mutableMapOf<String, String>(
                        "type" to "photo",
                        "media" to "attach://photo$index",
                    )
                if (isFirst && caption != null) {
                    obj["caption"] = caption
                    obj["parse_mode"] = "HTML"
                }
                obj
            }

        val response =
            httpClient
                .post("$baseUrl/sendMediaGroup") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("chat_id", chatId.toString())
                                append("media", json.encodeToString(mediaArray))
                                files.forEachIndexed { index, file ->
                                    append(
                                        "photo$index",
                                        file.readBytes(),
                                        Headers.build {
                                            append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                        },
                                    )
                                }
                            },
                        ),
                    )
                }.body<TgResponse<Unit>>()
        return response.ok
    }

    suspend fun getMe(): com.remotebot.domain.model.TgUser {
        val response = httpClient.get("$baseUrl/getMe").body<TgResponse<com.remotebot.domain.model.TgUser>>()
        val user = response.result
        if (!response.ok || user == null) {
            val desc = response.description ?: "Неверный токен"
            if (desc.equals("Not Found", ignoreCase = true)) {
                throw RuntimeException(
                    "Telegram API: Not Found (404). Проверьте токен бота (без слова 'bot') и поле 'Кастомный API Root' (оставьте пустым).",
                )
            }
            throw RuntimeException("Telegram API: $desc (code ${response.errorCode ?: 400})")
        }
        return user
    }

    suspend fun getFile(fileId: String): com.remotebot.domain.model.TgFile {
        val response =
            httpClient
                .get("$baseUrl/getFile") {
                    parameter("file_id", fileId)
                }.body<TgResponse<com.remotebot.domain.model.TgFile>>()
        val file = response.result
        if (!response.ok || file == null) {
            throw RuntimeException("Telegram getFile error: ${response.description ?: "Unknown"}")
        }
        return file
    }

    suspend fun downloadFileBytes(filePath: String): ByteArray {
        val cfg = configProvider()
        val root = cfg.customApiRoot?.trimEnd('/') ?: "https://api.telegram.org"
        val downloadUrl = "$root/file/bot${cfg.botToken}/$filePath"
        return httpClient.get(downloadUrl).body<ByteArray>()
    }

    suspend fun setWebhook(webhookUrl: String): Boolean {
        val response =
            httpClient
                .post("$baseUrl/setWebhook") {
                    parameter("url", webhookUrl)
                    parameter("allowed_updates", "[\"message\", \"callback_query\"]")
                }.body<TgResponse<Boolean>>()
        if (!response.ok) {
            throw RuntimeException("Ошибка setWebhook: ${response.description ?: "Unknown error"}")
        }
        return response.ok
    }

    suspend fun deleteWebhook(): Boolean {
        val response =
            httpClient
                .post("$baseUrl/deleteWebhook") {
                    parameter("drop_pending_updates", false)
                }.body<TgResponse<Boolean>>()
        return response.ok
    }
}
