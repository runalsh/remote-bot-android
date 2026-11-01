package com.remotebot.core.common.backup

import com.remotebot.domain.model.AppBackupData
import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.ProxyConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupManager {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun exportBackup(
        botConfig: BotConfig,
        proxyConfig: ProxyConfig,
    ): String {
        val backup =
            AppBackupData(
                botConfig = botConfig,
                proxyConfig = proxyConfig,
            )
        return json.encodeToString(backup)
    }

    fun parseBackup(jsonString: String): Result<AppBackupData> =
        runCatching {
            require(jsonString.isNotBlank()) { "Backup JSON must not be empty" }
            val data = json.decodeFromString<AppBackupData>(jsonString)
            require(data.version.isNotBlank()) { "Invalid backup structure: missing the version field" }
            val majorVersion =
                data.version
                    .split(".")
                    .firstOrNull()
                    ?.toIntOrNull() ?: 0
            require(majorVersion in 1..2) { "Unsupported backup schema version: ${data.version}" }
            data
        }
}
