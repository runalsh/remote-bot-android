package com.remotebot.core.push

import android.content.Context
import com.huawei.agconnect.AGConnectInstance
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.hms.aaid.HmsInstanceId
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.ParsedAgconnectConfig
import com.remotebot.domain.parser.AgconnectJsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object DynamicHmsManager {
    /**
     * Parse agconnect-services.json structure
     */
    fun parseAgconnectJson(jsonString: String): Result<ParsedAgconnectConfig> {
        AppLogger.d("HMS", "Запуск парсинга agconnect-services.json (длина: ${jsonString.length} симв.)")
        return AgconnectJsonParser
            .parse(jsonString)
            .onSuccess { config ->
                AppLogger.i("HMS", "agconnect-services.json успешно разобран: app_id=${config.appId}, client_id=${config.clientId}")
                AppLogger.t(
                    "HMS",
                    "Детали конфигурации HMS: package=${config.packageName}, has_secret=${!config.clientSecret.isNullOrBlank()}, has_api_key=${!config.apiKey
                        .isNullOrBlank()}",
                )
            }.onFailure { error ->
                AppLogger.e("HMS", "Ошибка парсинга agconnect-services.json: ${error.localizedMessage}", error)
            }
    }

    @Volatile
    private var isDynamicallyInitialized: Boolean = false

    /**
     * Dynamically initialize AGConnectInstance at runtime with custom parameters
     */
    fun initialize(
        context: Context,
        config: ParsedAgconnectConfig,
    ): Result<Unit> {
        AppLogger.i("HMS", "Инициализация AGConnectInstance для app_id=${config.appId}...")
        return runCatching {
            val builder = AGConnectOptionsBuilder()
            val rawJson = config.rawJson
            if (!rawJson.isNullOrBlank()) {
                AppLogger.t("HMS", "Установка raw InputStream в AGConnectOptionsBuilder")
                builder.setInputStream(rawJson.byteInputStream())
            }
            if (config.clientId.isNotBlank()) {
                AppLogger.t("HMS", "Установка clientId: ${config.clientId}")
                builder.setClientId(config.clientId)
            }
            val clientSecret = config.clientSecret
            if (!clientSecret.isNullOrBlank()) {
                AppLogger.t("HMS", "Установка clientSecret (длина: ${clientSecret.length})")
                builder.setClientSecret(clientSecret)
            }
            val apiKey = config.apiKey
            if (!apiKey.isNullOrBlank()) {
                AppLogger.t("HMS", "Установка apiKey: ${apiKey.take(10)}...")
                builder.setApiKey(apiKey)
            }
            AGConnectInstance.initialize(context.applicationContext, builder)
        }.onSuccess {
            isDynamicallyInitialized = true
            AppLogger.i("HMS", "AGConnectInstance успешно инициализирован в рантайме")
        }.onFailure { error ->
            isDynamicallyInitialized = false
            AppLogger.e("HMS", "Сбой инициализации AGConnectInstance: ${error.localizedMessage}", error)
        }
    }

    /**
     * Check if AGConnectInstance is currently initialized
     */
    fun isInitialized(): Boolean = isDynamicallyInitialized

    /**
     * Reset initialization state
     */
    fun reset() {
        isDynamicallyInitialized = false
    }

    /**
     * Check for HMS Core package availability on device
     */
    fun isHmsCoreInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo("com.huawei.hwid", 0)
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Retrieve current device Huawei HMS push token
     */
    suspend fun getHmsToken(
        context: Context,
        appId: String,
        timeoutMs: Long = 12000L,
    ): Result<String> {
        AppLogger.i("HMS", "Запрос HMS Push токена (HmsInstanceId) для app_id=$appId, timeout=${timeoutMs}ms...")
        if (!isHmsCoreInstalled(context)) {
            val errorMsg = "HMS Core (com.huawei.hwid) не установлен на данном устройстве. Установите HMS Core из Huawei AppGallery."
            AppLogger.e("HMS", errorMsg)
            return Result.failure(IllegalStateException(errorMsg))
        }
        return runCatching {
            withContext(Dispatchers.IO) {
                withTimeout(timeoutMs) {
                    val hmsInstanceId = HmsInstanceId.getInstance(context.applicationContext)
                    AppLogger.d("HMS", "Вызов HmsInstanceId.getToken(appId, 'HCM')...")
                    val token = hmsInstanceId.getToken(appId, "HCM")
                    if (token.isNullOrBlank()) {
                        AppLogger.w("HMS", "Получен пустой HMS токен от Huawei Mobile Services")
                        throw IllegalStateException("HMS токен пустой. Проверьте подключение к сервисам Huawei Core.")
                    }
                    AppLogger.i("HMS", "HMS токен успешно получен: ${token.take(8)}...${token.takeLast(4)}")
                    AppLogger.t("HMS", "Полный HMS Device Token (length=${token.length}): $token")
                    token
                }
            }
        }.onFailure { error ->
            AppLogger.e("HMS", "Ошибка получения HMS токена: ${error.localizedMessage}", error)
        }
    }
}

