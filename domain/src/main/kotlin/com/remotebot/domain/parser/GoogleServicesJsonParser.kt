package com.remotebot.domain.parser

import com.remotebot.domain.model.ParsedFirebaseConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object GoogleServicesJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        jsonString: String,
        targetPackage: String? = null,
    ): Result<ParsedFirebaseConfig> =
        runCatching {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val projectInfo =
                root["project_info"]?.jsonObject
                    ?: throw IllegalArgumentException("В конфигурации отсутствует секция 'project_info'")

            val projectId =
                projectInfo["project_id"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("В 'project_info' отсутствует поле 'project_id'")

            val projectNumber =
                projectInfo["project_number"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("В 'project_info' отсутствует поле 'project_number'")

            val clients =
                root["client"]?.jsonArray
                    ?: throw IllegalArgumentException("В конфигурации отсутствует массив 'client'")

            if (clients.isEmpty()) {
                throw IllegalArgumentException("Массив 'client' пуст")
            }

            var selectedClient = clients[0].jsonObject
            if (targetPackage != null) {
                for (elem in clients) {
                    val c = elem.jsonObject
                    val pkg =
                        c["client_info"]
                            ?.jsonObject
                            ?.get("android_client_info")
                            ?.jsonObject
                            ?.get("package_name")
                            ?.jsonPrimitive
                            ?.content
                    if (pkg == targetPackage) {
                        selectedClient = c
                        break
                    }
                }
            }

            val clientInfo =
                selectedClient["client_info"]?.jsonObject
                    ?: throw IllegalArgumentException("Отсутствует секция 'client_info'")

            val appId =
                clientInfo["mobilesdk_app_id"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Отсутствует поле 'mobilesdk_app_id'")

            val apiKeys =
                selectedClient["api_key"]?.jsonArray
                    ?: throw IllegalArgumentException("Отсутствует массив 'api_key'")

            if (apiKeys.isEmpty()) {
                throw IllegalArgumentException("Массив 'api_key' пуст")
            }

            val apiKey =
                apiKeys[0].jsonObject["current_key"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Отсутствует поле 'current_key' в 'api_key'")

            ParsedFirebaseConfig(
                appId = appId,
                apiKey = apiKey,
                projectId = projectId,
                gcmSenderId = projectNumber,
            )
        }
}
