package com.remotebot.core.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.remotebot.core.common.logger.AppLogger
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
        AppLogger.i("FCM", "Initializing Firebase dynamically: projectId=${config.projectId}, appId=${config.appId}")
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
                        AppLogger.d("FCM", "FirebaseApp already initialized with identical options, reusing instance")
                        return@runCatching defaultApp
                    }
                    defaultApp.delete()
                }
            }
            val app = FirebaseApp.initializeApp(context.applicationContext, options)
            AppLogger.i("FCM", "FirebaseApp initialized successfully: ${app.name}")
            app
        }.onFailure { err ->
            AppLogger.e("FCM", "Failed to initialize FirebaseApp: ${err.localizedMessage}", err)
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
                "Google Play Services installed (v${info.versionName})"
            } else {
                "Google Play Services are disabled in the system"
            }
        } catch (_: Exception) {
            "Google Play Services package (com.google.android.gms) not found on the device"
        }

    /**
     * Retrieve current device FCM push token
     */
    suspend fun getFcmToken(
        context: Context? = null,
        timeoutMs: Long = 12000L,
    ): Result<String> {
        AppLogger.i("FCM", "Requesting FCM push token (FirebaseMessaging.getInstance().token), timeout=${timeoutMs}ms...")
        return runCatching {
            if (!isInitialized()) {
                val err = "Firebase is not initialized. Provide google-services.json."
                AppLogger.e("FCM", err)
                throw IllegalStateException(err)
            }
            if (context != null && !isGooglePlayServicesAvailable(context)) {
                val status = getGooglePlayServicesStatus(context)
                AppLogger.e("FCM", "Google Play Services error: $status")
                throw IllegalStateException(status)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(timeoutMs) {
                    val token = FirebaseMessaging.getInstance().token.await()
                    if (token.isNullOrBlank()) {
                        AppLogger.w("FCM", "Empty FCM token returned from FirebaseMessaging")
                        throw IllegalStateException("Empty FCM token received")
                    }
                    AppLogger.i("FCM", "FCM token obtained successfully: ${token.take(8)}...${token.takeLast(4)}")
                    AppLogger.t("FCM", "Full FCM Device Token (length=${token.length}): $token")
                    token
                }
            }
        }.onFailure { error ->
            AppLogger.e("FCM", "Failed to obtain FCM token: ${error.localizedMessage}", error)
        }
    }
}
