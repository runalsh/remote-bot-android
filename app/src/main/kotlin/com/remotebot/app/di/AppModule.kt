package com.remotebot.app.di

import com.remotebot.app.data.PreferencesBotConfigRepository
import com.remotebot.app.data.PreferencesProxyConfigRepository
import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.channels.telegram.engine.TelegramBotEngine
import com.remotebot.channels.telegram.handlers.AutomationCommandsHandler
import com.remotebot.channels.telegram.handlers.BaseCommandsHandler
import com.remotebot.channels.telegram.handlers.BotManagementCommandsHandler
import com.remotebot.channels.telegram.handlers.FilesAndAppsCommandsHandler
import com.remotebot.channels.telegram.handlers.LocationCommandsHandler
import com.remotebot.channels.telegram.handlers.MediaCommandsHandler
import com.remotebot.channels.telegram.handlers.ShellAndPowerCommandsHandler
import com.remotebot.channels.telegram.handlers.SystemControlCommandsHandler
import com.remotebot.channels.telegram.handlers.TelephonyAndNotificationCommandsHandler
import com.remotebot.core.common.apps.DefaultAppManagerRepository
import com.remotebot.core.common.automation.DefaultAutomationRepository
import com.remotebot.core.common.automation.DefaultTaskerRepository
import com.remotebot.core.common.backup.BackupManager
import com.remotebot.core.common.dispatcher.DefaultCommandDispatcher
import com.remotebot.core.common.files.DefaultFileManagerRepository
import com.remotebot.core.common.location.DefaultLocationRepository
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.media.DefaultAudioRepository
import com.remotebot.core.common.media.DefaultCameraRepository
import com.remotebot.core.common.media.DefaultScreenRepository
import com.remotebot.core.common.notifications.DefaultNotificationRepository
import com.remotebot.core.common.notifications.NotificationListenerBridge
import com.remotebot.core.common.provider.DefaultPermissionsStatusProvider
import com.remotebot.core.common.root.DefaultRootStatusProvider
import com.remotebot.core.common.telephony.DefaultTelephonyRepository
import com.remotebot.core.common.tracker.ActiveOperationsTracker
import com.remotebot.core.network.di.networkModule
import com.remotebot.core.proxy.WssTunnelManager
import com.remotebot.core.security.SecureStorageManager
import com.remotebot.domain.dispatcher.CommandDispatcher
import com.remotebot.domain.provider.PermissionsStatusProvider
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.push.PushWakeupHandler
import com.remotebot.domain.repository.AppManagerRepository
import com.remotebot.domain.repository.AudioRepository
import com.remotebot.domain.repository.AutomationRepository
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.CameraRepository
import com.remotebot.domain.repository.FileManagerRepository
import com.remotebot.domain.repository.LocationRepository
import com.remotebot.domain.repository.NotificationRepository
import com.remotebot.domain.repository.ProxyConfigRepository
import com.remotebot.domain.repository.ScreenRepository
import com.remotebot.domain.repository.TaskerRepository
import com.remotebot.domain.repository.TelephonyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule =
    module {
        includes(networkModule)

        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

        single { AppLogger(androidContext()) }
        single { ActiveOperationsTracker(get()) }
        single { BackupManager() }
        single<RootStatusProvider> { DefaultRootStatusProvider(get()) }
        single<PermissionsStatusProvider> { DefaultPermissionsStatusProvider(androidContext(), get()) }

        single { SecureStorageManager(androidContext()) }
        single<BotConfigRepository> { PreferencesBotConfigRepository(androidContext(), get()) }
        single<ProxyConfigRepository> { PreferencesProxyConfigRepository(androidContext()) }

        single<CameraRepository> { DefaultCameraRepository(androidContext(), get()) }
        single<AudioRepository> { DefaultAudioRepository(androidContext(), get()) }
        single<ScreenRepository> { DefaultScreenRepository(androidContext(), get(), get(), get()) }

        single<TelephonyRepository> { DefaultTelephonyRepository(androidContext()) }
        single<NotificationRepository> {
            DefaultNotificationRepository(androidContext()).also { repo ->
                NotificationListenerBridge.notificationConsumer = { sbn ->
                    repo.onNotificationPosted(sbn)
                }
            }
        }

        single<LocationRepository> { DefaultLocationRepository(androidContext(), get()) }
        single<FileManagerRepository> { DefaultFileManagerRepository(androidContext(), get()) }
        single<AppManagerRepository> { DefaultAppManagerRepository(androidContext(), get()) }
        single<TaskerRepository> { DefaultTaskerRepository(androidContext()) }
        single<AutomationRepository> { DefaultAutomationRepository(androidContext(), get()) }

        single { WssTunnelManager(get()) }
        single {
            val configRepo: BotConfigRepository = get()
            com.remotebot.core.push.PushServerPoolManager(
                configProvider = { configRepo.config.value },
                diagnosticManager = get(),
                logger = get(),
            )
        }

        single {
            val configRepo: BotConfigRepository = get()
            TelegramApiClient(
                httpClient = get(),
                configProvider = { configRepo.config.value },
                logger = get(),
            )
        }

        single<CommandDispatcher> {
            DefaultCommandDispatcher(get()).apply {
                registerHandler(
                    BaseCommandsHandler(
                        rootStatusProvider = get(),
                        botConfigRepository = get(),
                        apiClient = get(),
                        diagnosticManager = get(),
                        permissionsStatusProvider = get(),
                    ),
                )
                registerHandler(
                    BotManagementCommandsHandler(
                        proxyConfigRepository = get(),
                        botConfigRepository = get(),
                        operationsTracker = get(),
                        logger = get(),
                        backupManager = get(),
                    ),
                )
                registerHandler(
                    SystemControlCommandsHandler(
                        context = androidContext(),
                        logger = get(),
                        rootStatusProvider = get(),
                    ),
                )
                registerHandler(
                    ShellAndPowerCommandsHandler(
                        logger = get(),
                        rootStatusProvider = get(),
                    ),
                )
                registerHandler(
                    MediaCommandsHandler(
                        cameraRepository = get(),
                        audioRepository = get(),
                        screenRepository = get(),
                        logger = get(),
                        operationsTracker = get(),
                    ),
                )
                registerHandler(
                    TelephonyAndNotificationCommandsHandler(
                        telephonyRepository = get(),
                        notificationRepository = get(),
                    ),
                )
                registerHandler(
                    LocationCommandsHandler(
                        locationRepository = get(),
                        apiClient = get(),
                        operationsTracker = get(),
                    ),
                )
                registerHandler(
                    FilesAndAppsCommandsHandler(
                        fileManagerRepository = get(),
                        appManagerRepository = get(),
                    ),
                )
                registerHandler(
                    AutomationCommandsHandler(
                        taskerRepository = get(),
                        automationRepository = get(),
                    ),
                )
            }
        }

        single<PushWakeupHandler> { get<TelegramBotEngine>() }
        single {
            TelegramBotEngine(
                apiClient = get(),
                dispatcher = get(),
                configRepository = get(),
                scope = get(),
                notificationRepository = get(),
                telephonyRepository = get(),
                locationRepository = get(),
                automationRepository = get(),
                cameraRepository = get(),
                audioRepository = get(),
                logger = get(),
            )
        }
    }
