package com.remotebot.domain.parser

import com.remotebot.domain.model.ParsedAgconnectConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AgconnectJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonString: String): Result<ParsedAgconnectConfig> =
        runCatching {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val client =
                root["client"]?.jsonObject
                    ?: throw IllegalArgumentException("В конфигурации отсутствует секция 'client'")

            val appId =
                client["app_id"]?.jsonPrimitive?.content
                    ?: root["app_info"]
                        ?.jsonObject
                        ?.get("app_id")
                        ?.jsonPrimitive
                        ?.content
                    ?: throw IllegalArgumentException("Отсутствует поле 'app_id' в секции 'client'")

            val clientId =
                client["client_id"]?.jsonPrimitive?.content
                    ?: appId

            val clientSecret = client["client_secret"]?.jsonPrimitive?.content
            val apiKey = client["api_key"]?.jsonPrimitive?.content
            val packageName = client["package_name"]?.jsonPrimitive?.content

            ParsedAgconnectConfig(
                appId = appId,
                clientId = clientId,
                clientSecret = clientSecret,
                apiKey = apiKey,
                packageName = packageName,
                rawJson = jsonString,
            )
        }
}
