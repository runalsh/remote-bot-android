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
                    ?: throw IllegalArgumentException("Missing 'project_info' section in the configuration")

            val projectId =
                projectInfo["project_id"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Missing 'project_id' field in 'project_info'")

            val projectNumber =
                projectInfo["project_number"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Missing 'project_number' field in 'project_info'")

            val clients =
                root["client"]?.jsonArray
                    ?: throw IllegalArgumentException("Missing 'client' array in the configuration")

            if (clients.isEmpty()) {
                throw IllegalArgumentException("The 'client' array is empty")
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
                    ?: throw IllegalArgumentException("Missing 'client_info' section")

            val appId =
                clientInfo["mobilesdk_app_id"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Missing 'mobilesdk_app_id' field")

            val apiKeys =
                selectedClient["api_key"]?.jsonArray
                    ?: throw IllegalArgumentException("Missing 'api_key' array")

            if (apiKeys.isEmpty()) {
                throw IllegalArgumentException("The 'api_key' array is empty")
            }

            val apiKey =
                apiKeys[0].jsonObject["current_key"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Missing 'current_key' field in 'api_key'")

            ParsedFirebaseConfig(
                appId = appId,
                apiKey = apiKey,
                projectId = projectId,
                gcmSenderId = projectNumber,
            )
        }
}
