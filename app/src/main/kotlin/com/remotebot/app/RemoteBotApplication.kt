package com.remotebot.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.remotebot.app.di.appModule
import com.remotebot.app.util.AppLocaleManager
import com.remotebot.core.push.DynamicFirebaseManager
import com.remotebot.domain.model.ParsedFirebaseConfig
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class RemoteBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@RemoteBotApplication)
            modules(appModule)
        }

        // Apply saved application locale
        try {
            val configRepo: BotConfigRepository = get()
            AppLocaleManager.applyLocale(this, configRepo.config.value.botLanguage)
        } catch (_: Exception) {
        }

        // Attempt dynamic Firebase initialization from saved configuration
        try {
            val configRepo: BotConfigRepository = get()
            val config = configRepo.config.value
            val appId = config.fcmAppId
            val apiKey = config.fcmApiKey
            if (!appId.isNullOrBlank() && !apiKey.isNullOrBlank()) {
                val fcmConfig =
                    ParsedFirebaseConfig(
                        appId = appId,
                        apiKey = apiKey,
                        projectId = config.fcmProjectId ?: "",
                        gcmSenderId = config.fcmSenderId ?: "",
                    )
                DynamicFirebaseManager.initialize(this, fcmConfig)
            } else {
                FirebaseApp.initializeApp(this)
            }
        } catch (_: Exception) {
            // Firebase may not be initialized until user provides google-services.json
        }

        // Attempt dynamic Huawei HMS initialization from saved configuration
        try {
            val configRepo: BotConfigRepository = get()
            val config = configRepo.config.value
            val hmsAppId = config.hmsAppId
            if (!hmsAppId.isNullOrBlank()) {
                val hmsConfig =
                    com.remotebot.domain.model.ParsedAgconnectConfig(
                        appId = hmsAppId,
                        clientId = config.hmsClientId ?: hmsAppId,
                        clientSecret = config.hmsClientSecret,
                        apiKey = config.hmsApiKey,
                        rawJson = config.agconnectServicesJson,
                    )
                com.remotebot.core.push.DynamicHmsManager
                    .initialize(this, hmsConfig)
            }
        } catch (_: Exception) {
            // HMS may not be configured or device does not support HMS
        }

        // Initialize metrics tracker status according to saved configuration
        try {
            val configRepo: BotConfigRepository = get()
            com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.setTrackingEnabled(
                configRepo.config.value.isDebugModeEnabled,
            )
            CoroutineScope(Dispatchers.Default).launch {
                configRepo.config.collect { cfg ->
                    com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.setTrackingEnabled(
                        cfg.isDebugModeEnabled,
                    )
                }
            }
        } catch (_: Exception) {
        }

        // Asynchronous Root privileges check and auto-grant on process launch
        val rootProvider: RootStatusProvider = get()
        val permissionsProvider: com.remotebot.domain.provider.PermissionsStatusProvider = get()
        CoroutineScope(Dispatchers.IO).launch {
            val hasRoot = rootProvider.checkRoot(forceRefresh = true)
            if (hasRoot) {
                permissionsProvider.grantAllPermissionsViaRoot()
            }
        }
    }
}
