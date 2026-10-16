package com.remotebot.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.service.RemoteBotService
import com.remotebot.app.ui.screens.AboutTab
import com.remotebot.app.ui.screens.ActiveTasksTab
import com.remotebot.app.ui.screens.BackupTab
import com.remotebot.app.ui.screens.FcmSettingsTab
import com.remotebot.app.ui.screens.LogsTab
import com.remotebot.app.ui.screens.NotificationsSettingsTab
import com.remotebot.app.ui.screens.PermissionsTab
import com.remotebot.app.ui.screens.ProxySettingsTab
import com.remotebot.app.ui.screens.SmsSettingsTab
import com.remotebot.app.ui.screens.StartTab
import com.remotebot.app.ui.screens.TelegramSettingsTab
import com.remotebot.app.ui.screens.debug.DebugTab
import com.remotebot.app.ui.theme.AppTheme
import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.core.common.backup.BackupManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.tracker.ActiveOperationsTracker
import com.remotebot.domain.model.LogLevel
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.AppManagerRepository
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.FileManagerRepository
import com.remotebot.domain.repository.LocationRepository
import com.remotebot.domain.repository.NotificationRepository
import com.remotebot.domain.repository.ProxyConfigRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val configRepository: BotConfigRepository by inject()
    private val proxyRepository: ProxyConfigRepository by inject()
    private val telegramApiClient: TelegramApiClient by inject()
    private val appLogger: AppLogger by inject()
    private val operationsTracker: ActiveOperationsTracker by inject()
    private val backupManager: BackupManager by inject()
    private val rootStatusProvider: RootStatusProvider by inject()
    private val appManagerRepository: AppManagerRepository by inject()
    private val notificationRepository: com.remotebot.domain.repository.NotificationRepository by inject()
    private val fileManagerRepository: com.remotebot.domain.repository.FileManagerRepository by inject()
    private val locationRepository: com.remotebot.domain.repository.LocationRepository by inject()
    private val pushServerPoolManager: com.remotebot.core.push.PushServerPoolManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(
                        configRepo = configRepository,
                        proxyRepo = proxyRepository,
                        apiClient = telegramApiClient,
                        logger = appLogger,
                        operationsTracker = operationsTracker,
                        backupManager = backupManager,
                        rootProvider = rootStatusProvider,
                        appManagerRepo = appManagerRepository,
                        notificationRepo = notificationRepository,
                        fileManagerRepo = fileManagerRepository,
                        locationRepo = locationRepository,
                        poolManager = pushServerPoolManager,
                        onStartService = {
                            RemoteBotService.startService(this)
                            Toast.makeText(this, "Служба запущена", Toast.LENGTH_SHORT).show()
                        },
                        onStopService = {
                            RemoteBotService.stopService(this)
                            Toast.makeText(this, "Служба остановлена", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

private data class BrowserTabItem(
    val title: String,
    val iconEmoji: String? = null,
    val iconRes: Int? = null,
    val description: String,
)

@Composable
fun MainScreen(
    configRepo: BotConfigRepository,
    proxyRepo: ProxyConfigRepository,
    apiClient: TelegramApiClient,
    logger: AppLogger,
    operationsTracker: ActiveOperationsTracker,
    backupManager: BackupManager,
    rootProvider: RootStatusProvider,
    appManagerRepo: AppManagerRepository,
    notificationRepo: NotificationRepository,
    fileManagerRepo: FileManagerRepository,
    locationRepo: LocationRepository,
    poolManager: com.remotebot.core.push.PushServerPoolManager,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    val config by configRepo.config.collectAsState()
    val isDebugEnabled = config.isDebugModeEnabled

    var tabHistory by rememberSaveable { mutableStateOf(listOf(0)) }
    val selectedTab = tabHistory.lastOrNull() ?: 0

    fun navigateToTab(tabIndex: Int) {
        if (tabIndex != selectedTab) {
            tabHistory = (tabHistory + tabIndex).takeLast(30)
        }
    }

    fun navigateBack(): Boolean {
        if (tabHistory.size > 1) {
            tabHistory = tabHistory.dropLast(1)
            return true
        }
        return false
    }

    // Перехват системной кнопки / жеста «Назад» для возврата к предыдущему экрану
    BackHandler(enabled = tabHistory.size > 1) {
        navigateBack()
    }

    var logEnabledLevels by rememberSaveable { mutableStateOf(LogLevel.entries.map { it.name }.toSet()) }
    val baseNavItems =
        remember {
            listOf(
                BrowserTabItem(title = "Старт", iconEmoji = "🚀", description = "Служба, Root и системные привилегии"),
                BrowserTabItem(title = "Права", iconEmoji = "🛡", description = "Разрешения и системный доступ"),
                BrowserTabItem(title = "Задачи", iconEmoji = "⏱️", description = "Активные фоновые задачи"),
                BrowserTabItem(title = "Telegram", iconRes = R.drawable.ic_telegram, description = "Управление Telegram ботом"),
                BrowserTabItem(title = "SMS", iconEmoji = "💬", description = "Управление SMS-ботом"),
                BrowserTabItem(title = "Уведомл.", iconEmoji = "🔔", description = "Фильтрация и пересылка уведомлений"),
                BrowserTabItem(title = "Push", iconEmoji = "🔥", description = "Push-уведомления (FCM / HMS) и шлюз"),
                BrowserTabItem(title = "Прокси", iconEmoji = "🌐", description = "Настройка сети и обхода DPI"),
                BrowserTabItem(title = "Логи", iconEmoji = "📜", description = "Журнал событий в реальном времени"),
                BrowserTabItem(title = "Бэкап", iconEmoji = "💾", description = "Резервная копия и восстановление"),
                BrowserTabItem(title = "О боте", iconEmoji = "ℹ️", description = "Версия, сборка и контакты"),
            )
        }

    val navItems =
        remember(isDebugEnabled) {
            if (isDebugEnabled) {
                baseNavItems + BrowserTabItem(title = "Debug", iconEmoji = "🛠️", description = "Инженерное меню отладки и Doze")
            } else {
                baseNavItems
            }
        }

    // Если режим отладки был отключен, а пользователь находился на вкладке Debug (индекс 11) - возвращаем на "О боте"
    LaunchedEffect(isDebugEnabled) {
        if (!isDebugEnabled && selectedTab >= 11) {
            navigateToTab(10)
        }
    }

    val isServiceRunning by RemoteBotService.isServiceRunning.collectAsState()

    val safeSelectedTab = if (selectedTab < navItems.size) selectedTab else 0

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
    ) {
        // Фиксированная панель переключателей экранов (3 ряда)
        M3BrowserTabsHeader(
            tabs = navItems,
            selectedTab = safeSelectedTab,
            isServiceRunning = isServiceRunning,
            onTabSelected = { navigateToTab(it) },
        )

        // Контейнер с содержимым выбранной вкладки (окна)
        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Заголовок текущего открытого окна режима
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (navItems[safeSelectedTab].iconRes != null) {
                            Image(
                                painter = painterResource(id = navItems[safeSelectedTab].iconRes!!),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Text(
                                text = navItems[safeSelectedTab].iconEmoji ?: "",
                                fontSize = 22.sp,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = navItems[safeSelectedTab].title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = navItems[safeSelectedTab].description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (navItems[safeSelectedTab].title == "Debug") {
                            val coroutineScope = rememberCoroutineScope()
                            val context = androidx.compose.ui.platform.LocalContext.current
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        configRepo.updateConfig { it.copy(isDebugModeEnabled = false) }
                                        navigateToTab(10)
                                        android.widget.Toast
                                            .makeText(
                                                context,
                                                "Режим отладки выключен",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("✕ Отключить Debug", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    AnimatedContent(
                        targetState = safeSelectedTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { width -> width / 4 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { width -> -width / 4 } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width / 4 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { width -> width / 4 } + fadeOut())
                            }
                        },
                        label = "tab_transition",
                    ) { targetTab ->
                        when (targetTab) {
                            0 ->
                                StartTab(
                                    onStartService = onStartService,
                                    onStopService = onStopService,
                                    onNavigateToPermissions = { navigateToTab(1) },
                                    logger = logger,
                                    rootProvider = rootProvider,
                                )
                            1 -> PermissionsTab(logger = logger, rootProvider = rootProvider)
                            2 -> ActiveTasksTab(operationsTracker = operationsTracker, logger = logger)
                            3 -> TelegramSettingsTab(configRepo, apiClient, logger)
                            4 -> SmsSettingsTab(configRepo, logger)
                            5 -> NotificationsSettingsTab(configRepo, appManagerRepo, logger)
                            6 -> FcmSettingsTab(configRepo, logger)
                            7 -> ProxySettingsTab(configRepo, proxyRepo, apiClient, logger)
                            8 ->
                                LogsTab(
                                    logger = logger,
                                    enabledLevelNames = logEnabledLevels,
                                    onLevelsChange = { logEnabledLevels = it },
                                )
                            9 -> BackupTab(configRepo, proxyRepo, backupManager, logger)
                            10 ->
                                AboutTab(
                                    configRepo = configRepo,
                                    onNavigateToDebug = { navigateToTab(11) },
                                )
                            11 ->
                                DebugTab(
                                    configRepo = configRepo,
                                    proxyRepo = proxyRepo,
                                    apiClient = apiClient,
                                    poolManager = poolManager,
                                    notificationRepo = notificationRepo,
                                    fileManagerRepo = fileManagerRepo,
                                    locationRepo = locationRepo,
                                    rootProvider = rootProvider,
                                    logger = logger,
                                    onExitDebugMode = { navigateToTab(10) },
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun M3BrowserTabsHeader(
    tabs: List<BrowserTabItem>,
    selectedTab: Int,
    isServiceRunning: Boolean = false,
    onTabSelected: (Int) -> Unit,
) {
    // Ряд 1 (4): Старт, Права, Задачи, Telegram
    // Ряд 2 (4): SMS, Уведомл., Push, Прокси
    // Ряд 3 (3-4): Логи, Бэкап, О боте, [Debug]
    val row1Tabs = tabs.take(4)
    val row2Tabs = tabs.drop(4).take(4)
    val row3Tabs = tabs.drop(8)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Ряд 1: 4 кнопки (Старт, Права, Задачи, Telegram)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row1Tabs.forEachIndexed { idx, item ->
                    val actualIndex = idx
                    val dotColor =
                        if (actualIndex == 0) {
                            if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFF44336)
                        } else {
                            null
                        }
                    TabChipButton(
                        item = item,
                        isSelected = selectedTab == actualIndex,
                        statusDotColor = dotColor,
                        onClick = { onTabSelected(actualIndex) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Ряд 2: 4 кнопки (SMS, Уведомл., Push, Прокси)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row2Tabs.forEachIndexed { idx, item ->
                    val actualIndex = 4 + idx
                    TabChipButton(
                        item = item,
                        isSelected = selectedTab == actualIndex,
                        onClick = { onTabSelected(actualIndex) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Ряд 3: 3-4 кнопки (Логи, Бэкап, О боте, [Debug])
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row3Tabs.forEachIndexed { idx, item ->
                    val actualIndex = 8 + idx
                    TabChipButton(
                        item = item,
                        isSelected = selectedTab == actualIndex,
                        onClick = { onTabSelected(actualIndex) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabChipButton(
    item: BrowserTabItem,
    isSelected: Boolean,
    statusDotColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = MaterialTheme.shapes.small,
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.45f,
                )
            },
        border =
            BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
        tonalElevation = if (isSelected) 3.dp else 0.dp,
        modifier = modifier.height(34.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (item.iconRes != null) {
                Image(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    text = item.iconEmoji ?: "",
                    fontSize = 12.sp,
                )
            }

            if (statusDotColor != null) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(statusDotColor, androidx.compose.foundation.shape.CircleShape),
                )
                Spacer(modifier = Modifier.width(3.dp))
            }

            Text(
                text = item.title,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
