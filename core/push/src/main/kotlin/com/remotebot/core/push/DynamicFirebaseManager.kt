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
     * Parse google-services.json structure
     */
    fun parseGoogleServicesJson(
        jsonString: String,
        targetPackage: String? = null,
    ): Result<ParsedFirebaseConfig> = GoogleServicesJsonParser.parse(jsonString, targetPackage)

    /**
     * Dynamically initialize FirebaseApp at runtime with custom parameters
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
     * Check if FirebaseApp is currently initialized
     */
    fun isInitialized(): Boolean =
        try {
            FirebaseApp.getInstance()
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Check Google Play Services availability on device
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
     * Text description of Google Play Services status
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
     * Retrieve current device FCM push token
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


