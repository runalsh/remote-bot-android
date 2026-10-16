package com.remotebot.core.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.remotebot.domain.model.ParsedFirebaseConfig
import com.remotebot.domain.parser.GoogleServicesJsonParser
import kotlinx.coroutines.tasks.await

object DynamicFirebaseManager {
    /**
     * Парсинг структуры google-services.json
     */
    fun parseGoogleServicesJson(
        jsonString: String,
        targetPackage: String? = null,
    ): Result<ParsedFirebaseConfig> = GoogleServicesJsonParser.parse(jsonString, targetPackage)

    /**
     * Динамическая инициализация FirebaseApp в рантайме с пользовательскими параметрами
     */
    fun initialize(
        context: Context,
        config: ParsedFirebaseConfig,
    ): Result<FirebaseApp> {
        return runCatching {
            val options =
                FirebaseOptions
                    .Builder()
                    .setApplicationId(config.appId)
                    .setApiKey(config.apiKey)
                    .setProjectId(config.projectId)
                    .setGcmSenderId(config.gcmSenderId)
                    .build()

            val existingApps = FirebaseApp.getApps(context)
            if (existingApps.isNotEmpty()) {
                val defaultApp =
                    try {
                        FirebaseApp.getInstance()
                    } catch (_: Exception) {
                        null
                    }
                if (defaultApp != null) {
                    if (defaultApp.options.applicationId == options.applicationId &&
                        defaultApp.options.apiKey == options.apiKey &&
                        defaultApp.options.projectId == options.projectId
                    ) {
                        return@runCatching defaultApp
                    }
                    defaultApp.delete()
                }
            }
            FirebaseApp.initializeApp(context.applicationContext, options)
        }
    }

    /**
     * Проверка, инициализирован ли сейчас FirebaseApp
     */
    fun isInitialized(): Boolean =
        try {
            FirebaseApp.getInstance()
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Проверка доступности Google Play Services на устройстве
     */
    fun isGooglePlayServicesAvailable(context: Context): Boolean =
        try {
            val pm = context.packageManager
            val info = pm.getPackageInfo("com.google.android.gms", 0)
            info.applicationInfo?.enabled == true
        } catch (_: Exception) {
            false
        }

    /**
     * Текстовое описание статуса Google Play Services
     */
    fun getGooglePlayServicesStatus(context: Context): String =
        try {
            val pm = context.packageManager
            val info = pm.getPackageInfo("com.google.android.gms", 0)
            if (info.applicationInfo?.enabled == true) {
                "Google Play Services установлены (v${info.versionName})"
            } else {
                "Google Play Services отключены в системе"
            }
        } catch (_: Exception) {
            "Пакет Google Play Services (com.google.android.gms) не найден на устройстве"
        }

    /**
     * Получение текущего FCM Push токена устройства
     */
    suspend fun getFcmToken(
        context: Context? = null,
        timeoutMs: Long = 12000L,
    ): Result<String> =
        runCatching {
            if (!isInitialized()) {
                throw IllegalStateException("Firebase не инициализирован. Задайте google-services.json.")
            }
            if (context != null && !isGooglePlayServicesAvailable(context)) {
                val status = getGooglePlayServicesStatus(context)
                throw IllegalStateException(status)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(timeoutMs) {
                    FirebaseMessaging.getInstance().token.await()
                }
            }
        }
}


