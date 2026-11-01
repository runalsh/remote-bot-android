package com.remotebot.app.ui.screens.debug

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.service.RemoteBotAccessibilityService
import com.remotebot.app.R
import com.remotebot.app.service.RemoteBotNotificationListenerService
import com.remotebot.app.service.RemoteBotService
import com.remotebot.app.ui.components.AppStatusBadge
import com.remotebot.app.ui.components.BadgeStatus
import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.channels.telegram.engine.TelegramEventForwarder
import com.remotebot.core.common.battery.BatteryEvent
import com.remotebot.core.common.battery.BatteryReceiver
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.telephony.IncomingSmsEvent
import com.remotebot.core.common.telephony.SmsReceiver
import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.ProxyConfig
import com.remotebot.domain.model.ProxyType
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.FileManagerRepository
import com.remotebot.domain.repository.LocationRepository
import com.remotebot.domain.repository.NotificationRepository
import com.remotebot.domain.repository.ProxyConfigRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

@Composable
fun DebugTab(
    configRepo: BotConfigRepository,
    proxyRepo: ProxyConfigRepository,
    apiClient: TelegramApiClient,
    poolManager: com.remotebot.core.push.PushServerPoolManager,
    notificationRepo: NotificationRepository,
    fileManagerRepo: FileManagerRepository,
    locationRepo: LocationRepository,
    rootProvider: RootStatusProvider,
    logger: AppLogger,
    onExitDebugMode: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

    val config by configRepo.config.collectAsState()
    val isFgServiceRunning by RemoteBotService.isServiceRunning.collectAsState()
    val servicePid by RemoteBotService.servicePid.collectAsState(-1)
    val serviceStartTime by RemoteBotService.serviceStartTime.collectAsState(0L)
    val serviceUptimeMillis = RemoteBotService.getServiceUptimeMillis()
    val isNotifListenerConnected by RemoteBotNotificationListenerService.isConnected.collectAsState()
    val eventsIntercepted by RemoteBotNotificationListenerService.eventsIntercepted.collectAsState(0L)
    val isAccessibilityRunning by RemoteBotAccessibilityService.isRunning.collectAsState()
    val isRootGranted by rootProvider.isRootGranted.collectAsState()
    val isRootSimulatedAbsent by rootProvider.isRootSimulatedAbsent.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Debug mode header
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🛠️", fontSize = 24.sp)
                    Column {
                        Text(
                            text = stringResource(R.string.debug_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.debug_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            configRepo.updateConfig { it.copy(isDebugModeEnabled = false) }
                            onExitDebugMode()
                            Toast.makeText(context, context.getString(R.string.debug_dbg_off), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.debug_btn_exit), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Block 1.5: Experimental Features Lab
        DebugExperimentalFeaturesCard(
            config = config,
            configRepo = configRepo,
        )

        // Runtime analytics & metrics: Wakeups, Network connections by server, Auth errors
        DebugDiagnosticsMetricsCard()

        // Block 0: System resource monitor (CPU, Memory, Network TX/RX, Disk I/O)
        DebugResourcesProfilerCard(
            context = context,
            logger = logger,
        )

        // Block 1: Notification Simulation Lab
        DebugNotificationSimulationCard(
            notificationRepo = notificationRepo,
            logger = logger,
        )

        // Block 2: Process & Lifecycle Inspector
        DebugServiceAndDozeCard(
            context = context,
            isFgServiceRunning = isFgServiceRunning,
            servicePid = servicePid,
            serviceUptimeMillis = serviceUptimeMillis,
            isNotifListenerConnected = isNotifListenerConnected,
            eventsIntercepted = eventsIntercepted,
            recentNotificationsCount = notificationRepo.recentNotificationsCount,
            isAccessibilityRunning = isAccessibilityRunning,
            logger = logger,
        )

        // Block 2.5: Push Gateway, FCM, HMS, and Telegram API servers dashboard
        PushGatewayDashboardCard(
            config = config,
            poolManager = poolManager,
            apiClient = apiClient,
            logger = logger,
        )

        // Block 3: Network & Proxy Sandbox
        DebugNetworkInspectorCard(
            config = config,
            proxyRepo = proxyRepo,
            apiClient = apiClient,
            logger = logger,
        )

        // Block 4: Storage & Cache Inspector
        DebugStorageAndCacheCard(
            notificationRepo = notificationRepo,
            fileManagerRepo = fileManagerRepo,
            logger = logger,
            context = context,
        )

        // Block 5: Environment & State Controls
        DebugEnvironmentControlsCard(
            configRepo = configRepo,
            proxyRepo = proxyRepo,
            locationRepo = locationRepo,
            rootProvider = rootProvider,
            isRootSimulatedAbsent = isRootSimulatedAbsent,
            logger = logger,
        )
    }
}

// -------------------------------------------------------------
// Block 0: System resource profiler (Memory, CPU, Network, Disk)
// -------------------------------------------------------------
@Composable
private fun DebugResourcesProfilerCard(
    context: Context,
    logger: AppLogger,
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    // Memory metrics
    val runtime = Runtime.getRuntime()
    val usedJavaHeapMb = remember(refreshKey) { (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) }
    val maxJavaHeapMb = remember(refreshKey) { runtime.maxMemory() / (1024 * 1024) }
    val nativeHeapAllocMb = remember(refreshKey) { Debug.getNativeHeapAllocatedSize() / (1024 * 1024) }
    val nativeHeapSizeMb = remember(refreshKey) { Debug.getNativeHeapSize() / (1024 * 1024) }
    val pssKb = remember(refreshKey) { Debug.getPss() }
    val pssMb = remember(refreshKey) { pssKb / 1024 }

    val actManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }
    val memInfo =
        remember(refreshKey) {
            val info = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(info)
            info
        }
    val availRamMb = remember(refreshKey) { memInfo.availMem / (1024 * 1024) }
    val totalRamMb = remember(refreshKey) { memInfo.totalMem / (1024 * 1024) }
    val usedRamMb = totalRamMb - availRamMb

    // CPU metrics
    val availableCores = remember { Runtime.getRuntime().availableProcessors() }
    val activeThreads = remember(refreshKey) { Thread.activeCount() }
    val cpuArch = remember { Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a" }

    // Real-time memory chart (Phase 13 addition)
    var memoryHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    LaunchedEffect(refreshKey) {
        memoryHistory = List(60) { pssMb.toFloat() }
    }
    DisposableEffect(refreshKey) {
        val interval = 5000L // Refresh every 5 seconds
        val job =
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                while (true) {
                    kotlinx.coroutines.delay(interval)
                    memoryHistory = (memoryHistory + (Debug.getPss() / 1024f)).takeLast(60)
                }
            }
        onDispose { job.cancel() }
    }

    // Process CPU Time calculation via /proc/self/stat
    val processCpuTimeFormatted =
        remember(refreshKey) {
            try {
                val statFile = File("/proc/self/stat")
                if (statFile.exists() && statFile.canRead()) {
                    val parts = statFile.readText().trim().split("\\s+".toRegex())
                    if (parts.size >= 15) {
                        val utime = parts[13].toLongOrNull() ?: 0L
                        val stime = parts[14].toLongOrNull() ?: 0L
                        // Divide ticks by 100 Hz (standard sysconf clock tick on Linux)
                        val totalSec = (utime + stime) / 100.0
                        String.format(context.getString(R.string.debug_cpu_time), totalSec, utime, stime)
                    } else {
                        context.getString(R.string.debug_threads_active, activeThreads)
                    }
                } else {
                    context.getString(R.string.debug_threads_active, activeThreads)
                }
            } catch (e: Exception) {
                context.getString(R.string.debug_threads_active, activeThreads)
            }
        }

    // Network traffic metrics (Upload / Download via TrafficStats)
    val myUid = remember { Process.myUid() }
    val appRxBytes = remember(refreshKey) { TrafficStats.getUidRxBytes(myUid) }
    val appTxBytes = remember(refreshKey) { TrafficStats.getUidTxBytes(myUid) }
    val totalRxBytes = remember(refreshKey) { TrafficStats.getTotalRxBytes() }
    val totalTxBytes = remember(refreshKey) { TrafficStats.getTotalTxBytes() }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "N/A"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(context.getString(R.string.debug_fmt_gb), gb)
            mb >= 1.0 -> String.format(context.getString(R.string.debug_fmt_mb), mb)
            kb >= 1.0 -> String.format(context.getString(R.string.debug_fmt_kb), kb)
            else -> context.getString(R.string.debug_fmt_bytes, bytes)
        }
    }

    // Disk, storage, and I/O metrics
    val (appInternalFilesMb, appCacheMb, appDbMb) =
        remember(refreshKey) {
            try {
                val filesDirSize =
                    context.filesDir
                        ?.walkTopDown()
                        ?.filter { it.isFile }
                        ?.sumOf { it.length() } ?: 0L
                val cacheDirSize =
                    context.cacheDir
                        ?.walkTopDown()
                        ?.filter { it.isFile }
                        ?.sumOf { it.length() } ?: 0L
                val dbDir = context.getDatabasePath("remote_bot.db")?.parentFile
                val dbDirSize = dbDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
                Triple(filesDirSize / (1024.0 * 1024.0), cacheDirSize / (1024.0 * 1024.0), dbDirSize / (1024.0 * 1024.0))
            } catch (e: Exception) {
                Triple(0.0, 0.0, 0.0)
            }
        }

    val (diskFreeGb, diskTotalGb) =
        remember(refreshKey) {
            try {
                val stat = StatFs(Environment.getDataDirectory().path)
                val free = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
                val total = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)
                Pair(free, total)
            } catch (e: Exception) {
                Pair(0.0, 0.0)
            }
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("📊", fontSize = 20.sp)
                    Column {
                        Text(
                            text = stringResource(R.string.debug_profiler_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.debug_profiler_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        refreshKey++
                        logger.i("DebugResources", "Resource metrics refreshed (key: $refreshKey)")
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.debug_refresh_cd),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Section 1: CPU and processor
            Text(
                text = stringResource(R.string.debug_cpu_section),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            MetricRow(title = stringResource(R.string.debug_cpu_cores), value = stringResource(R.string.debug_cpu_cores_val, availableCores, cpuArch))
            MetricRow(title = stringResource(R.string.debug_jvm_threads), value = stringResource(R.string.debug_jvm_threads_val, activeThreads))
            MetricRow(title = stringResource(R.string.debug_cpu_usage), value = processCpuTimeFormatted)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Section 2: Memory (RAM)
            Text(
                text = stringResource(R.string.debug_ram_section),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            MetricRow(
                title = stringResource(R.string.debug_ram_pss),
                value = stringResource(R.string.debug_fmt_pss, pssMb, pssKb),
            )
            MetricRow(
                title = stringResource(R.string.debug_ram_heap_java),
                value = stringResource(R.string.debug_fmt_used_max, usedJavaHeapMb, maxJavaHeapMb),
            )
            MetricRow(
                title = stringResource(R.string.debug_ram_heap_native),
                value = stringResource(R.string.debug_fmt_native_heap, nativeHeapAllocMb, nativeHeapSizeMb),
            )
            MetricRow(
                title = stringResource(R.string.debug_ram_total_device),
                value = stringResource(R.string.debug_fmt_ram_total, usedRamMb, totalRamMb, availRamMb),
            )
            MetricRow(
                title = stringResource(R.string.debug_ram_low_mem),
                value = if (memInfo.lowMemory) stringResource(R.string.debug_low_mem_yes) else stringResource(R.string.debug_low_mem_no),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Section 3: Network traffic (Upload / Download)
            Text(
                text = stringResource(R.string.debug_net_section),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            MetricRow(
                title = stringResource(R.string.debug_net_rx),
                value = "⬇️ ${formatBytes(appRxBytes)}",
            )
            MetricRow(
                title = stringResource(R.string.debug_net_tx),
                value = "⬆️ ${formatBytes(appTxBytes)}",
            )
            MetricRow(
                title = stringResource(R.string.debug_net_total),
                value = "RX: ${formatBytes(totalRxBytes)} | TX: ${formatBytes(totalTxBytes)}",
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Section 4: Storage, disk, and I/O
            Text(
                text = stringResource(R.string.debug_storage_section),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            MetricRow(
                title = stringResource(R.string.debug_storage_files),
                value = String.format(context.getString(R.string.debug_fmt_mb), appInternalFilesMb),
            )
            MetricRow(
                title = stringResource(R.string.debug_storage_cache),
                value = String.format(context.getString(R.string.debug_fmt_mb), appCacheMb),
            )
            MetricRow(
                title = stringResource(R.string.debug_storage_db),
                value = String.format(context.getString(R.string.debug_fmt_mb), appDbMb),
            )
            MetricRow(
                title = stringResource(R.string.debug_storage_free),
                value = String.format(context.getString(R.string.debug_fmt_free_total), diskFreeGb, diskTotalGb),
            )

            // PSS memory consumption chart (Phase 13 addition)
            if (memoryHistory.isNotEmpty()) {
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                val graphColor = MaterialTheme.colorScheme.primary
                val errorColor = MaterialTheme.colorScheme.error
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.debug_pss_chart),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = graphColor,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Canvas(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                    ) {
                        val width = size.width
                        val height = size.height
                        val padding = 4.dp.toPx()

                        if (memoryHistory.size < 2) return@Canvas

                        val maxMem = maxOf(memoryHistory.maxOrNull() ?: 0f, 1f)
                        val minMem = memoryHistory.minOrNull() ?: 0f
                        val range = maxMem - minMem ?: 1f

                        // Grid
                        for (i in 0..4) {
                            val y = padding + (i * (height - 2 * padding) / 4)
                            drawLine(
                                color = gridColor,
                                start = Offset(padding, y),
                                end = Offset(width - padding, y),
                                strokeWidth = 1f,
                            )
                        }

                        // Chart line
                        val composePath =
                            androidx.compose.ui.graphics
                                .Path()
                        val stepX = (width - 2 * padding) / (memoryHistory.size - 1)

                        for ((i, memMb) in memoryHistory.withIndex()) {
                            val normalized = (memMb - minMem) / range
                            val x = padding + i * stepX
                            val y = height - padding - normalized * (height - 2 * padding)
                            if (i == 0) {
                                composePath.moveTo(x, y)
                            } else {
                                composePath.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = composePath,
                            color = graphColor,
                            style = Stroke(width = 2.5f),
                        )

                        // Current value
                        val currentMem = memoryHistory.last()
                        drawCircle(
                            color = errorColor,
                            radius = 4f,
                            center =
                                Offset(
                                    padding + (memoryHistory.size - 1) * stepX,
                                    height - padding - ((currentMem - minMem) / range) * (height - 2 * padding),
                                ),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.debug_min, String.format(stringResource(R.string.debug_fmt_mb0f), memoryHistory.minOrNull() ?: 0)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.debug_max, String.format(stringResource(R.string.debug_fmt_mb0f), memoryHistory.maxOrNull() ?: 0)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.debug_now, String.format(stringResource(R.string.debug_fmt_mb0f), memoryHistory.lastOrNull() ?: 0)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// -------------------------------------------------------------
// Block 1: Notification Simulation Lab
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebugNotificationSimulationCard(
    notificationRepo: NotificationRepository,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedPkg by remember { mutableStateOf("org.telegram.plus") }
    var title by remember { mutableStateOf("Alice Cooper") }
    var text by remember { mutableStateOf(context.getString(R.string.debug_notif_sample)) }
    var isPostingToSystemBar by remember { mutableStateOf(false) }
    var hasRemoteInput by remember { mutableStateOf(false) }
    var isGroupSummary by remember { mutableStateOf(false) }
    var notificationCategory by remember { mutableStateOf("msg") }

    val presetApps =
        listOf(
            "ru.multifactor.app" to "Multifactor",
            "com.lastpass.authenticator" to "LastPass Authenticator",
            "com.duosecurity.duomobile" to "Duo Mobile",
            "ru.yandex.key" to stringResource(R.string.debug_app_yandex),
            "com.azure.authenticator" to "Microsoft Authenticator",
            "org.telegram.plus" to "Telegram Plus",
            "org.telegram.messenger" to "Telegram",
            "com.whatsapp" to "WhatsApp",
            "ru.sberbankmobile" to stringResource(R.string.debug_app_sber),
            "com.google.android.apps.messaging" to "Google Messages",
        )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🧪", fontSize = 18.sp)
                Text(
                    text = "Notification Simulation Lab",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = stringResource(R.string.debug_sim_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(stringResource(R.string.debug_quick_pkg), style = MaterialTheme.typography.labelMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presetApps.forEach { (pkg, name) ->
                    FilterChip(
                        selected = selectedPkg == pkg,
                        onClick = { selectedPkg = pkg },
                        label = { Text(name, fontSize = 11.sp) },
                    )
                }
            }

            OutlinedTextField(
                value = selectedPkg,
                onValueChange = { selectedPkg = it },
                label = { Text("Package Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.debug_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.debug_text_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.debug_post_shade),
                    style = MaterialTheme.typography.bodySmall,
                )
                Switch(
                    checked = isPostingToSystemBar,
                    onCheckedChange = { isPostingToSystemBar = it },
                )
            }

            // Notification simulation flags (Phase 13 additions)
            Text(stringResource(R.string.debug_sim_flags), style = MaterialTheme.typography.labelMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Switch(
                            checked = hasRemoteInput,
                            onCheckedChange = { hasRemoteInput = it },
                        )
                        Text("RemoteInput (Quick Reply)", fontSize = 11.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Switch(
                            checked = isGroupSummary,
                            onCheckedChange = { isGroupSummary = it },
                        )
                        Text("FLAG_GROUP_SUMMARY", fontSize = 11.sp)
                    }
                }
            }

            OutlinedTextField(
                value = notificationCategory,
                onValueChange = { notificationCategory = it },
                label = { Text(stringResource(R.string.debug_category_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            if (isPostingToSystemBar) {
                                notificationRepo.postNotification(title, text)
                                logger.i("DebugLab", "Posting real system notification: $title - $text")
                            } else {
                                val appName = presetApps.find { it.first == selectedPkg }?.second ?: selectedPkg
                                val mockActions =
                                    when (selectedPkg) {
                                        "ru.multifactor.app" ->
                                            listOf(
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(0, context.getString(R.string.debug_btn_confirm)),
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(1, context.getString(R.string.debug_btn_decline)),
                                            )
                                        "com.lastpass.authenticator" ->
                                            listOf(
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(0, "Reject"),
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(1, "Accept"),
                                            )
                                        "com.duosecurity.duomobile" ->
                                            listOf(
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(0, "Approve"),
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(1, "Deny"),
                                            )
                                        "ru.yandex.key" ->
                                            listOf(
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(0, context.getString(R.string.debug_btn_yes_me)),
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(1, context.getString(R.string.debug_btn_not_me)),
                                            )
                                        "com.azure.authenticator" ->
                                            listOf(
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(0, "Approve"),
                                                com.remotebot.domain.model
                                                    .NotificationActionButton(1, "Deny"),
                                            )
                                        else -> emptyList()
                                    }
                                notificationRepo.simulateNotificationWithFlags(
                                    packageName = selectedPkg,
                                    appName = appName,
                                    title = title,
                                    text = text,
                                    hasRemoteInput = hasRemoteInput,
                                    isGroupSummary = isGroupSummary,
                                    category = if (notificationCategory.isBlank()) null else notificationCategory,
                                    actions = mockActions,
                                )
                                logger.i(
                                    "DebugLab",
                                    "Injecting simulated push [$selectedPkg] flags(remoteInput=$hasRemoteInput, actions=${mockActions.size}): $title - $text",
                                )
                            }
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            Toast.makeText(context, context.getString(R.string.debug_notif_generated), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            logger.e("DebugLab", "Push simulation error: ${e.message}")
                            Toast.makeText(context, context.getString(R.string.debug_error_fmt, e.message), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.debug_btn_generate))
            }
        }
    }
}

// -------------------------------------------------------------
// Block 2: Background service and Doze Mode diagnostics
// -------------------------------------------------------------
@Composable
private fun DebugServiceAndDozeCard(
    context: Context,
    isFgServiceRunning: Boolean,
    servicePid: Int,
    serviceUptimeMillis: Long,
    isNotifListenerConnected: Boolean,
    eventsIntercepted: Long,
    recentNotificationsCount: Int,
    isAccessibilityRunning: Boolean,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dozeOutput by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🔋", fontSize = 18.sp)
                Text(
                    text = "Process & Doze Lifecycle Inspector",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Service statuses
            StatusRow(
                label = "RemoteBotService (Foreground)",
                status = if (isFgServiceRunning) stringResource(R.string.debug_fg_active) else stringResource(R.string.debug_stopped),
                isOk = isFgServiceRunning,
            )

            if (isFgServiceRunning) {
                MetricRow(
                    title = stringResource(R.string.debug_svc_pid),
                    value = "$servicePid",
                )
                MetricRow(
                    title = stringResource(R.string.debug_svc_uptime),
                    value = RemoteBotService.formatUptime(serviceUptimeMillis, context),
                )
            }

            StatusRow(
                label = "NotificationListenerService",
                status = if (isNotifListenerConnected) stringResource(R.string.debug_nls_connected) else stringResource(R.string.debug_nls_disconnected),
                isOk = isNotifListenerConnected,
            )

            if (isNotifListenerConnected) {
                val uptimeFormatted = RemoteBotService.formatUptime(serviceUptimeMillis, context)
                MetricRow(
                    title = stringResource(R.string.debug_nls_events),
                    value = eventsIntercepted.toString(),
                )
                MetricRow(
                    title = stringResource(R.string.debug_recent_cache),
                    value = stringResource(R.string.debug_fmt_entries, recentNotificationsCount),
                )
            }

            StatusRow(
                label = "AccessibilityService",
                status = if (isAccessibilityRunning) stringResource(R.string.debug_a11y_active) else stringResource(R.string.debug_a11y_disabled),
                isOk = isAccessibilityRunning,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        RemoteBotService.stopService(context)
                        RemoteBotService.startService(context)
                        logger.i("DebugLifecycle", "Forced restart of RemoteBotService completed")
                        Toast.makeText(context, context.getString(R.string.debug_svc_restarted), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.debug_btn_restart_service), fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        System.gc()
                        logger.i("DebugLifecycle", "System.gc() invoked")
                        Toast.makeText(context, context.getString(R.string.debug_gc_invoked), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.debug_btn_gc), fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(stringResource(R.string.debug_deep_doze), style = MaterialTheme.typography.labelMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val res =
                                Shell
                                    .cmd(
                                        "dumpsys battery unplug",
                                        "dumpsys deviceidle step deep",
                                    ).exec()
                            val out = (res.out + res.err).joinToString("\n")
                            dozeOutput = if (out.isNotBlank()) out else "Step Deep Doze executed"
                            logger.w("DebugDoze", "Doze Step: $dozeOutput")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("💤 Step Deep Doze", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val res =
                                Shell
                                    .cmd(
                                        "dumpsys deviceidle force-idle",
                                        "dumpsys battery unplug",
                                    ).exec()
                            val out = (res.out + res.err).joinToString("\n")
                            dozeOutput = if (out.isNotBlank()) out else "Force Idle executed"
                            logger.w("DebugDoze", "Doze Force-Idle: $dozeOutput")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("⚡ Force Idle", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val res = Shell.cmd("dumpsys battery reset", "dumpsys deviceidle unforce").exec()
                            val out = (res.out + res.err).joinToString("\n")
                            dozeOutput = if (out.isNotBlank()) out else "Doze Reset OK"
                            logger.i("DebugDoze", "Doze Reset: $dozeOutput")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("🔄 Reset", fontSize = 11.sp)
                }
            }

            if (dozeOutput != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = dozeOutput ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Block 3: Network inspector and DPI sandbox
// -------------------------------------------------------------
@Composable
private fun DebugNetworkInspectorCard(
    config: BotConfig,
    proxyRepo: ProxyConfigRepository,
    apiClient: TelegramApiClient,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val proxyConfig by proxyRepo.proxyConfig.collectAsState()

    var isTestingTg by remember { mutableStateOf(false) }
    var tgTestResult by remember { mutableStateOf<String?>(null) }

    var isTestingDirect by remember { mutableStateOf(false) }
    var directTestResult by remember { mutableStateOf<String?>(null) }

    var isTestingProxyEndpoint by remember { mutableStateOf(false) }
    var proxyTestResult by remember { mutableStateOf<String?>(null) }

    var isTestingDns by remember { mutableStateOf(false) }
    var dnsTestResult by remember { mutableStateOf<String?>(null) }

    var isTestingGateway by remember { mutableStateOf(false) }
    var gatewayTestResult by remember { mutableStateOf<String?>(null) }

    var isTestingWakeup by remember { mutableStateOf(false) }
    var wakeupTestResult by remember { mutableStateOf<String?>(null) }

    var isTestingWss by remember { mutableStateOf(false) }
    var wssTestResult by remember { mutableStateOf<String?>(null) }

    val activeApiRoot =
        remember(config.customApiRoot) {
            val root = config.customApiRoot?.trim()
            if (root.isNullOrBlank()) "https://api.telegram.org" else root.trimEnd('/')
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🌐", fontSize = 18.sp)
                    Text(
                        text = "Network & Proxy Sandbox",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color =
                        if (proxyConfig.type !=
                            ProxyType.NONE
                        ) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                ) {
                    Text(
                        text = if (proxyConfig.type != ProxyType.NONE) "Proxy: ${proxyConfig.type.name}" else stringResource(R.string.debug_direct_noproxy),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (proxyConfig.type !=
                                ProxyType.NONE
                            ) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Test 1: getMe() query via configured stack
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Telegram Bot getMe()", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = tgTestResult ?: stringResource(R.string.debug_tg_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        isTestingTg = true
                        tgTestResult = context.getString(R.string.debug_testing)
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val t0 = System.currentTimeMillis()
                                val user = apiClient.getMe()
                                val rtt = System.currentTimeMillis() - t0
                                tgTestResult = context.getString(R.string.debug_tg_ok, user.username ?: "bot", rtt)
                                logger.i("NetworkDebug", "Telegram ping success in ${rtt}ms")
                            } catch (e: Exception) {
                                tgTestResult = context.getString(R.string.debug_tg_err, e.message)
                                logger.w("NetworkDebug", "Telegram ping failed: ${e.message}")
                            } finally {
                                isTestingTg = false
                            }
                        }
                    },
                    enabled = !isTestingTg && config.botToken.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (isTestingTg) "..." else "getMe")
                }
            }

            // Test 2: Direct connection to API endpoint (bypassing proxy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.debug_direct_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = directTestResult ?: stringResource(R.string.debug_direct_hint, activeApiRoot),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        isTestingDirect = true
                        directTestResult = context.getString(R.string.debug_direct_connecting)
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val uri = java.net.URI(activeApiRoot)
                                val host = uri.host ?: "api.telegram.org"
                                val isHttps = (uri.scheme?.lowercase() ?: "https") == "https"
                                val port =
                                    if (uri.port > 0) {
                                        uri.port
                                    } else if (isHttps) {
                                        443
                                    } else {
                                        80
                                    }

                                val t0 = System.currentTimeMillis()
                                val socket =
                                    if (isHttps) {
                                        val sslSocket = SSLSocketFactory.getDefault().createSocket() as SSLSocket
                                        sslSocket.connect(InetSocketAddress(host, port), 8000)
                                        sslSocket.startHandshake()
                                        sslSocket
                                    } else {
                                        val plainSocket = Socket()
                                        plainSocket.connect(InetSocketAddress(host, port), 8000)
                                        plainSocket
                                    }
                                val rtt = System.currentTimeMillis() - t0
                                socket.close()

                                directTestResult = "✅ Direct: TLS $host:$port (${rtt}ms)"
                                logger.i("NetworkDebug", "Direct connection to $host:$port succeeded in ${rtt}ms")
                            } catch (e: Exception) {
                                directTestResult = "❌ Direct Fail: ${e.message}"
                                logger.w("NetworkDebug", "Direct connection to $activeApiRoot failed: ${e.message}")
                            } finally {
                                isTestingDirect = false
                            }
                        }
                    },
                    enabled = !isTestingDirect,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (isTestingDirect) "..." else "Direct")
                }
            }

            // Test 3: Connect to API endpoint via Proxy (if enabled)
            if (proxyConfig.type != ProxyType.NONE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Proxy API Connect (${proxyConfig.type.name})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = proxyTestResult ?: stringResource(R.string.debug_proxy_hint, proxyConfig.host, proxyConfig.port, activeApiRoot),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            isTestingProxyEndpoint = true
                            proxyTestResult = context.getString(R.string.debug_proxy_checking)
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val uri = java.net.URI(activeApiRoot)
                                    val host = uri.host ?: "api.telegram.org"
                                    val isHttps = (uri.scheme?.lowercase() ?: "https") == "https"
                                    val port =
                                        if (uri.port > 0) {
                                            uri.port
                                        } else if (isHttps) {
                                            443
                                        } else {
                                            80
                                        }

                                    val t0 = System.currentTimeMillis()

                                    // Check proxy host availability
                                    val proxySocket = Socket()
                                    proxySocket.connect(InetSocketAddress(proxyConfig.host, proxyConfig.port), 8000)
                                    val proxyRtt = System.currentTimeMillis() - t0
                                    proxySocket.close()

                                    // Request API via OkHttp client with proxy
                                    val client =
                                        okhttp3.OkHttpClient
                                            .Builder()
                                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                            .apply {
                                                when (proxyConfig.type) {
                                                    ProxyType.HTTP -> {
                                                        proxy(
                                                            java.net.Proxy(
                                                                java.net.Proxy.Type.HTTP,
                                                                InetSocketAddress(proxyConfig.host, proxyConfig.port),
                                                            ),
                                                        )
                                                        val u = proxyConfig.username
                                                        val p = proxyConfig.password
                                                        if (!u.isNullOrBlank() && !p.isNullOrBlank()) {
                                                            proxyAuthenticator { _, response ->
                                                                response.request
                                                                    .newBuilder()
                                                                    .header("Proxy-Authorization", okhttp3.Credentials.basic(u, p))
                                                                    .build()
                                                            }
                                                        }
                                                    }
                                                    ProxyType.SOCKS5 -> {
                                                        proxy(
                                                            java.net.Proxy(
                                                                java.net.Proxy.Type.SOCKS,
                                                                InetSocketAddress.createUnresolved(proxyConfig.host, proxyConfig.port),
                                                            ),
                                                        )
                                                    }
                                                    else -> {}
                                                }
                                            }.build()

                                    val req =
                                        okhttp3.Request
                                            .Builder()
                                            .url(activeApiRoot)
                                            .head()
                                            .build()

                                    val resp = client.newCall(req).execute()
                                    val totalRtt = System.currentTimeMillis() - t0
                                    resp.close()

                                    proxyTestResult = context.getString(R.string.debug_proxy_ok, resp.code, totalRtt, proxyRtt)
                                    logger.i(
                                        "NetworkDebug",
                                        "Connection via ${proxyConfig.type} to $activeApiRoot succeeded: code=${resp.code}, rtt=${totalRtt}ms",
                                    )
                                } catch (e: Exception) {
                                    proxyTestResult = "❌ Proxy Fail: ${e.message}"
                                    logger.w("NetworkDebug", "Proxy connection error to $activeApiRoot: ${e.message}")
                                } finally {
                                    isTestingProxyEndpoint = false
                                }
                            }
                        },
                        enabled = !isTestingProxyEndpoint && proxyConfig.host.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(if (isTestingProxyEndpoint) "..." else stringResource(R.string.debug_btn_proxy))
                    }
                }
            }

            // Test 4: DNS resolution test
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DNS Leak / Resolve Check", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = dnsTestResult ?: stringResource(R.string.debug_dns_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        isTestingDns = true
                        dnsTestResult = context.getString(R.string.debug_resolving)
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val uri = java.net.URI(activeApiRoot)
                                val targetHost = uri.host ?: "api.telegram.org"
                                val t0 = System.currentTimeMillis()
                                val addrs = InetAddress.getAllByName(targetHost)
                                val cost = System.currentTimeMillis() - t0
                                val ips = addrs.joinToString(", ") { it.hostAddress ?: "" }
                                dnsTestResult = "✅ $targetHost (${cost}ms): $ips"
                                logger.i("NetworkDebug", "DNS resolved in ${cost}ms: $ips")
                            } catch (e: Exception) {
                                dnsTestResult = "❌ DNS Fail: ${e.message}"
                                logger.e("NetworkDebug", "DNS resolve error: ${e.message}")
                            } finally {
                                isTestingDns = false
                            }
                        }
                    },
                    enabled = !isTestingDns,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (isTestingDns) "..." else "DNS")
                }
            }

            // Test 5: Push Gateway Health Ping
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Push Gateway Health Check", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = gatewayTestResult ?: stringResource(R.string.debug_gw_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val gwUrl = config.pushGatewayUrl?.trim()
                        if (gwUrl.isNullOrBlank()) {
                            gatewayTestResult = context.getString(R.string.debug_gw_missing)
                            return@OutlinedButton
                        }
                        isTestingGateway = true
                        gatewayTestResult = context.getString(R.string.debug_gw_requesting, gwUrl)
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val healthUrl = if (gwUrl.endsWith("/")) "${gwUrl}health" else "$gwUrl/health"
                                val urlObj = java.net.URL(healthUrl)
                                val conn = urlObj.openConnection() as java.net.HttpURLConnection
                                conn.connectTimeout = 7000
                                conn.readTimeout = 7000
                                val code = conn.responseCode
                                val body = conn.inputStream.bufferedReader().readText()
                                gatewayTestResult = "HTTP $code: $body"
                                logger.i("NetworkDebug", "Gateway health: HTTP $code ($body)")
                            } catch (e: Exception) {
                                gatewayTestResult = context.getString(R.string.debug_gw_fail, e.message)
                                logger.w("NetworkDebug", "Gateway health check failed: ${e.message}")
                            } finally {
                                isTestingGateway = false
                            }
                        }
                    },
                    enabled = !isTestingGateway,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (isTestingGateway) "..." else stringResource(R.string.debug_btn_gateway))
                }
            }

            // Test 6: Wakeup ping to own device (Phase 13 addition)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wakeup Push Ping", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = wakeupTestResult ?: stringResource(R.string.debug_wakeup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val gwUrl = config.pushGatewayUrl?.trim()
                        val secretKey = config.pushSecretKey?.trim()
                        val fcmToken = config.fcmAppId?.trim()
                        if (gwUrl.isNullOrBlank()) {
                            wakeupTestResult = context.getString(R.string.debug_gw_url_short)
                            return@OutlinedButton
                        }
                        if (secretKey.isNullOrBlank()) {
                            wakeupTestResult = context.getString(R.string.debug_secret_missing)
                            return@OutlinedButton
                        }
                        if (fcmToken.isNullOrBlank()) {
                            wakeupTestResult = context.getString(R.string.debug_token_missing)
                            return@OutlinedButton
                        }
                        isTestingWakeup = true
                        wakeupTestResult = context.getString(R.string.debug_wakeup_sending)
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val wakeupUrl =
                                    if (gwUrl.endsWith(
                                            "/",
                                        )
                                    ) {
                                        "${gwUrl}tg/$fcmToken/$secretKey"
                                    } else {
                                        "$gwUrl/tg/$fcmToken/$secretKey"
                                    }
                                val urlObj = java.net.URL(wakeupUrl)
                                val conn = urlObj.openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "POST"
                                conn.doOutput = true
                                conn.connectTimeout = 7000
                                conn.readTimeout = 7000
                                val payload = """{"data":{"action":"wakeup","update_json":""}}""".trimIndent()
                                conn.outputStream.write(payload.toByteArray())
                                val code = conn.responseCode
                                val body = if (code == 200) conn.inputStream.bufferedReader().readText() else "HTTP $code"
                                wakeupTestResult = if (code in 200..299) context.getString(R.string.debug_wakeup_sent, code) else context.getString(R.string.debug_http_fail, code, body)
                                logger.i("NetworkDebug", "Wakeup ping: $wakeupTestResult")
                            } catch (e: Exception) {
                                wakeupTestResult = context.getString(R.string.debug_wakeup_fail, e.message)
                                logger.w("NetworkDebug", "Wakeup ping failed: ${e.message}")
                            } finally {
                                isTestingWakeup = false
                            }
                        }
                    },
                    enabled = !isTestingWakeup,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (isTestingWakeup) "..." else "Wakeup")
                }
            }

            // Test 7: WSS proxy with WebSocket handshake verification (Phase 13 addition)
            if (proxyConfig.type != ProxyType.NONE && proxyConfig.host.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WSS WebSocket Handshake", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = wssTestResult ?: stringResource(R.string.debug_wss_hint, proxyConfig.host, proxyConfig.port),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            isTestingWss = true
                            wssTestResult = "WebSocket handshake..."
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val t0 = System.currentTimeMillis()
                                    // Check basic TCP connection to proxy
                                    val socket = Socket()
                                    socket.connect(InetSocketAddress(proxyConfig.host, proxyConfig.port), 8000)
                                    val pingRtt = System.currentTimeMillis() - t0

                                    // Build WebSocket Upgrade request
                                    val wsRequest =
                                        """
                                        GET ${config.customApiRoot?.let { java.net.URI(it).path ?: "/"} ?: "/"} HTTP/1.1
                                        Host: api.telegram.org
                                        Upgrade: websocket
                                        Connection: Upgrade
                                        Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                                        Sec-WebSocket-Version: 13
                                        
                                        """.trimIndent()

                                    socket.outputStream.write(wsRequest.toByteArray())
                                    socket.outputStream.flush()

                                    val response = socket.inputStream.bufferedReader().readLine()
                                    val handshakeOk = response.contains("101") || response.contains("Upgrade")

                                    socket.close()
                                    val totalRtt = System.currentTimeMillis() - t0

                                    wssTestResult =
                                        if (handshakeOk) {
                                            "✅ WSS OK (HTTP 101, RTT: ${totalRtt}ms, Ping: ${pingRtt}ms)"
                                        } else {
                                            context.getString(R.string.debug_wss_warn, response.take(50), totalRtt)
                                        }
                                    logger.i("NetworkDebug", "WSS handshake: $wssTestResult")
                                } catch (e: Exception) {
                                    wssTestResult = "❌ WSS Fail: ${e.message}"
                                    logger.w("NetworkDebug", "WSS handshake error: ${e.message}")
                                } finally {
                                    isTestingWss = false
                                }
                            }
                        },
                        enabled = !isTestingWss && proxyConfig.host.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(if (isTestingWss) "..." else "WSS")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Block 4: Storage, Cache, and Databases
// -------------------------------------------------------------
@Composable
private fun DebugStorageAndCacheCard(
    notificationRepo: NotificationRepository,
    fileManagerRepo: FileManagerRepository,
    logger: AppLogger,
    context: Context,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val echoCacheSize = TelegramEventForwarder.getEchoCacheSize()
    val dedupCacheSize = TelegramEventForwarder.getDedupCacheSize()
    val unsentFiles by fileManagerRepo.unsentFiles.collectAsState()
    val logSizeBytes = logger.getLogFileSize()
    val logSizeKb = logSizeBytes / 1024

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🗄️", fontSize = 18.sp)
                Text(
                    text = "Storage & Cache Inspector",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.debug_echo_cache), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.debug_fmt_entries, echoCacheSize), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.debug_dedup_label), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.debug_fmt_entries, dedupCacheSize), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.debug_unsent_label), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.debug_fmt_files, unsentFiles.size), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.debug_logfile_label), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.debug_fmt_kb1, logSizeKb), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            TelegramEventForwarder.clearAllCaches()
                            notificationRepo.clearRecentNotifications()
                            logger.i("DebugStorage", "Anti-echo and notification dedup cache cleared successfully")
                            Toast.makeText(context, context.getString(R.string.debug_notif_cache_cleared), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.debug_btn_reset_dedup), fontSize = 11.sp)
                }

                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            fileManagerRepo.clearUnsentFiles()
                            logger.i("DebugStorage", "Unsent files queue cleared")
                            Toast.makeText(context, context.getString(R.string.debug_files_queue_reset), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.debug_btn_reset_files), fontSize = 11.sp)
                }

                FilledTonalButton(
                    onClick = {
                        logger.clear()
                        Toast.makeText(context, context.getString(R.string.debug_logs_cleared), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.debug_btn_reset_logs), fontSize = 11.sp)
                }
            }

            // Export logs to clipboard (Phase 13 addition)
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val formattedLogs = logger.getFormattedLogsForExport(count = 500)
                            if (formattedLogs.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("RemoteBot_logs", formattedLogs)
                                clipboard.setPrimaryClip(clip)
                                logger.i("DebugExport", "Exported ${formattedLogs.lines().size} logs to clipboard")
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.debug_logs_copied, formattedLogs.lines().size),
                                        Toast.LENGTH_LONG,
                                    ).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.debug_logs_empty), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            logger.e("DebugExport", "Log export error: ${e.message}")
                            Toast.makeText(context, context.getString(R.string.debug_error_fmt, e.message), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            ) {
                Text(stringResource(R.string.debug_btn_copy_logs))
            }

            // Export full system dump to file (Debug Dump)
            OutlinedButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val dumpDir = context.getExternalFilesDir(null) ?: context.filesDir
                            val dumpFile = File(dumpDir, "debug_dump_${System.currentTimeMillis()}.txt")
                            val formattedLogs = logger.getFormattedLogsForExport(count = 200)

                            val dumpContent =
                                buildString {
                                    appendLine("=== RemoteBot Full Debug Dump ===")
                                    appendLine(
                                        "Timestamp: ${java.text.SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm:ss.SSS",
                                            java.util.Locale.US,
                                        ).format(java.util.Date())}",
                                    )
                                    appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                                    appendLine("Service PID: ${Process.myPid()}")
                                    appendLine("Echo Cache Size: $echoCacheSize")
                                    appendLine("Dedup Cache Size: $dedupCacheSize")
                                    appendLine("Unsent Files Count: ${unsentFiles.size}")
                                    appendLine("Log File Size: $logSizeKb KB")
                                    appendLine()
                                    appendLine("=== Recent Logs (last 200) ===")
                                    appendLine(formattedLogs)
                                }

                            dumpFile.writeText(dumpContent)
                            withContext(Dispatchers.Main) {
                                logger.i("DebugExport", "Full system dump saved: ${dumpFile.absolutePath}")
                                Toast.makeText(context, context.getString(R.string.debug_dump_saved, dumpFile.name), Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                logger.e("DebugExport", "Dump save error: ${e.message}")
                                Toast.makeText(context, context.getString(R.string.debug_dump_error, e.message), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.debug_btn_export_dump))
            }
        }
    }
}

// -------------------------------------------------------------
// Block 5: Environment emulation and settings reset
// -------------------------------------------------------------
@Composable
private fun DebugEnvironmentControlsCard(
    configRepo: BotConfigRepository,
    proxyRepo: ProxyConfigRepository,
    locationRepo: LocationRepository,
    rootProvider: RootStatusProvider,
    isRootSimulatedAbsent: Boolean,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showResetDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🎭", fontSize = 18.sp)
                Text(
                    text = "Environment & State Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Root emulation absent
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.debug_sim_root), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = stringResource(R.string.debug_sim_root_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isRootSimulatedAbsent,
                    onCheckedChange = { rootProvider.setSimulateRootAbsent(it) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(stringResource(R.string.debug_events_gen), style = MaterialTheme.typography.labelMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        BatteryReceiver.emitSimulatedEvent(
                            BatteryEvent.LowBattery(level = 15, isCharging = false),
                        )
                        logger.w("DebugEnv", "Emulated LowBattery 15% event")
                        Toast.makeText(context, context.getString(R.string.debug_batt_emul), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.debug_btn_batt), fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        locationRepo.simulateShakeEvent()
                        logger.w("DebugEnv", "Emulated shake sensor event")
                        Toast.makeText(context, context.getString(R.string.debug_shake_emul), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.debug_btn_shake), fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        SmsReceiver.emitSimulatedSms(
                            IncomingSmsEvent(
                                sender = "+79991234567",
                                body = context.getString(R.string.debug_sms_body),
                                timestampMs = System.currentTimeMillis(),
                            ),
                        )
                        logger.i("DebugEnv", context.getString(R.string.debug_sms_emul))
                        Toast.makeText(context, context.getString(R.string.debug_sms_emul), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("💬 SMS", fontSize = 11.sp)
                }
            }

            // Controlled crash to verify crash-recovery / UncaughtExceptionHandler
            OutlinedButton(
                onClick = {
                    logger.w("DebugEnv", "Controlled app crash initiated for crash-recovery check")
                    throw RuntimeException("Controlled Debug Crash for testing crash-recovery mechanism")
                },
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.debug_btn_crash), fontSize = 12.sp)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Factory reset settings button
            Button(
                onClick = { showResetDialog = true },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.debug_btn_factory))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.debug_reset_title)) },
            text = { Text(stringResource(R.string.debug_reset_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        coroutineScope.launch {
                            configRepo.updateConfig { BotConfig(isDebugModeEnabled = true) }
                            proxyRepo.updateProxy(
                                com.remotebot.domain.model
                                    .ProxyConfig(),
                            )
                            logger.w("DebugEnv", "Factory reset of app settings to defaults completed")
                            Toast.makeText(context, context.getString(R.string.debug_settings_reset), Toast.LENGTH_LONG).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.debug_btn_reset), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.debug_btn_cancel))
                }
            },
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    status: String,
    isOk: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        AppStatusBadge(
            text = status,
            status = if (isOk) BadgeStatus.SUCCESS else BadgeStatus.ERROR,
        )
    }
}

// -------------------------------------------------------------
// Block 1.5: Experimental features
// -------------------------------------------------------------
@Composable
private fun DebugExperimentalFeaturesCard(
    config: BotConfig,
    configRepo: BotConfigRepository,
) {
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🧪", fontSize = 20.sp)
                Column {
                    Text(
                        text = stringResource(R.string.debug_exp_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.debug_exp_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.debug_2fa_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.debug_2fa_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.enable2FaInterception,
                    onCheckedChange = { isChecked ->
                        coroutineScope.launch {
                            configRepo.updateConfig { it.copy(enable2FaInterception = isChecked) }
                        }
                    },
                )
            }
        }
    }
}

