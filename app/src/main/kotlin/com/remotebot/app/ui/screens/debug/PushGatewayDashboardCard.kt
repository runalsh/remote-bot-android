package com.remotebot.app.ui.screens.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.R
import com.remotebot.app.ui.components.AppStatusBadge
import com.remotebot.app.ui.components.BadgeStatus
import com.remotebot.app.ui.theme.AppColors
import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.push.DynamicFirebaseManager
import com.remotebot.core.push.DynamicHmsManager
import com.remotebot.core.push.PushServerPoolManager
import com.remotebot.core.push.PushStatsTracker
import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.PushProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ServerHealthItem(
    val id: String,
    val name: String,
    val url: String,
    val isChecking: Boolean = false,
    val isHealthy: Boolean? = null,
    val latencyMs: Long? = null,
    val responseMsg: String? = null,
)

@Composable
fun PushGatewayDashboardCard(
    config: BotConfig,
    poolManager: PushServerPoolManager,
    apiClient: TelegramApiClient,
    logger: AppLogger,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val stats by PushStatsTracker.stats.collectAsState()

    var isCheckingAll by remember { mutableStateOf(false) }

    val isFcmInit = remember(config.googleServicesJson) { DynamicFirebaseManager.isInitialized() }
    val isHmsInit =
        remember(config.agconnectServicesJson) {
            DynamicHmsManager.isInitialized() && !config.agconnectServicesJson.isNullOrBlank()
        }

    val allPushServers =
        remember(config.pushServers, config.pushGatewayUrl) {
            poolManager.getAllServers()
        }
    val fcmServers =
        remember(allPushServers) {
            allPushServers.filter { it.provider == PushProvider.FCM || it.provider == PushProvider.HYBRID }
        }
    val hmsServers =
        remember(allPushServers, config.agconnectServicesJson) {
            allPushServers.filter {
                (it.provider == PushProvider.HMS || it.provider == PushProvider.HYBRID) &&
                    (!it.agconnectServicesJson.isNullOrBlank() || !config.agconnectServicesJson.isNullOrBlank())
            }
        }

    val apiRoots =
        remember(config.customApiRoot, config.apiEndpoints) {
            apiClient.getCandidateRoots()
        }

    // Ping states for each server by ID/URL
    var serverHealthMap by remember {
        mutableStateOf<Map<String, ServerHealthItem>>(emptyMap())
    }

    suspend fun pingUrl(
        key: String,
        name: String,
        targetUrl: String,
        isTelegramApi: Boolean = false,
    ): ServerHealthItem =
        withContext(Dispatchers.IO) {
            val checkUrl =
                if (isTelegramApi) {
                    targetUrl.trimEnd('/') + "/bot" + config.botToken.ifBlank { "test" } + "/getMe"
                } else {
                    targetUrl.trimEnd('/') + "/health"
                }

            val start = System.currentTimeMillis()
            try {
                val url = URL(checkUrl)
                val conn =
                    (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3500
                        readTimeout = 3500
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "RemoteBot/1.0-HealthCheck")
                    }
                val code = conn.responseCode
                val took = System.currentTimeMillis() - start
                conn.disconnect()

                // For Telegram API: 200 (valid token) or 401/404 (server reachable, token empty/invalid) counts as reachable host
                val healthy = if (isTelegramApi) (code == 200 || code == 401 || code == 404) else (code == 200)
                val msg = if (code == 200) "HTTP 200 OK (${took}ms)" else "HTTP $code (${took}ms)"
                ServerHealthItem(key, name, targetUrl, isChecking = false, isHealthy = healthy, latencyMs = took, responseMsg = msg)
            } catch (e: Exception) {
                val took = System.currentTimeMillis() - start
                ServerHealthItem(
                    key,
                    name,
                    targetUrl,
                    isChecking = false,
                    isHealthy = false,
                    latencyMs = took,
                    responseMsg =
                        e.localizedMessage ?: "Error",
                )
            }
        }

    fun runCheckAll() {
        scope.launch {
            isCheckingAll = true
            val updated = serverHealthMap.toMutableMap()

            // 1. Ping all Push FCM / HMS
            val pushTasks =
                allPushServers.map { server ->
                    async {
                        pingUrl(
                            key = "push_${server.id}",
                            name = server.name.ifBlank { context.getString(R.string.dbg_gw_name, server.provider.name) },
                            targetUrl = server.gatewayUrl,
                            isTelegramApi = false,
                        )
                    }
                }

            // 2. Ping all Telegram API servers
            val apiTasks =
                apiRoots.mapIndexed { idx, root ->
                    async {
                        pingUrl(
                            key = "api_$root",
                            name =
                                if (root.contains("api.telegram.org")) {
                                    context.getString(R.string.dbg_official_api)
                                } else {
                                    context.getString(
                                        R.string.dbg_mirror_name,
                                        idx + 1,
                                    )
                                },
                            targetUrl = root,
                            isTelegramApi = true,
                        )
                    }
                }

            val allResults = (pushTasks + apiTasks).awaitAll()
            for (res in allResults) {
                updated[res.id] = res
            }
            serverHealthMap = updated
            isCheckingAll = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Card header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("📡", fontSize = 20.sp)
                    Text(
                        text = stringResource(R.string.dbg_dash_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { runCheckAll() },
                    enabled = !isCheckingAll,
                ) {
                    if (isCheckingAll) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.dbg_check_all))
                    }
                }
            }

            // Overall Push providers status
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥 FCM:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            AppStatusBadge(
                                text = if (isFcmInit) "Initialized" else "Not Initialized",
                                status = if (isFcmInit) BadgeStatus.SUCCESS else BadgeStatus.NEUTRAL,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🛡️ HMS:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            AppStatusBadge(
                                text = if (isHmsInit) "Initialized" else "Not Initialized",
                                status = if (isHmsInit) BadgeStatus.SUCCESS else BadgeStatus.NEUTRAL,
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.dbg_active_fmt, config.pushProvider.name),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.dbg_strategy_fmt, config.pushServerSelectionStrategy.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // SECTION 1: FCM Push Servers
            Text(
                text = stringResource(R.string.dbg_fcm_section, fcmServers.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            if (fcmServers.isEmpty()) {
                Text(
                    text = stringResource(R.string.dbg_no_fcm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                fcmServers.forEach { server ->
                    val health = serverHealthMap["push_${server.id}"]
                    ServerItemRow(
                        name = server.name.ifBlank { "FCM Gateway" },
                        url = server.gatewayUrl,
                        health = health,
                        onPing = {
                            scope.launch {
                                val res = pingUrl("push_${server.id}", server.name, server.gatewayUrl, isTelegramApi = false)
                                serverHealthMap = serverHealthMap + (res.id to res)
                            }
                        },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // SECTION 2: HMS Push Servers
            Text(
                text = stringResource(R.string.dbg_hms_section, hmsServers.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            if (hmsServers.isEmpty()) {
                Text(
                    text = stringResource(R.string.dbg_no_hms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                hmsServers.forEach { server ->
                    val health = serverHealthMap["push_${server.id}"]
                    ServerItemRow(
                        name = server.name.ifBlank { "HMS Gateway" },
                        url = server.gatewayUrl,
                        health = health,
                        onPing = {
                            scope.launch {
                                val res = pingUrl("push_${server.id}", server.name, server.gatewayUrl, isTelegramApi = false)
                                serverHealthMap = serverHealthMap + (res.id to res)
                            }
                        },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // SECTION 3: Telegram API Servers (Mirrors)
            Text(
                text = stringResource(R.string.dbg_api_section, apiRoots.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            apiRoots.forEachIndexed { idx, root ->
                val health = serverHealthMap["api_$root"]
                val name =
                    if (root.contains(
                            "api.telegram.org",
                        )
                    ) {
                        stringResource(R.string.dbg_official_api)
                    } else {
                        "Telegram Mirror #${idx + 1}"
                    }
                ServerItemRow(
                    name = name,
                    url = root,
                    health = health,
                    onPing = {
                        scope.launch {
                            val res = pingUrl("api_$root", name, root, isTelegramApi = true)
                            serverHealthMap = serverHealthMap + (res.id to res)
                        }
                    },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // SECTION 4: Push Delivery Metrics
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.dbg_delivered_today), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.dbg_delivered_fmt, stats.deliveredToday, stats.totalDelivered),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.success,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.dbg_delivery_errors), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.dbg_delivered_fmt, stats.failedToday, stats.totalFailed),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.failedToday > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.dbg_avg_latency), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (stats.avgDeliveryLatencyMs > 0) "${stats.avgDeliveryLatencyMs} ms" else "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    PushStatsTracker.resetDailyStats()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(),
            ) {
                Text(stringResource(R.string.dbg_reset_push_stats))
            }
        }
    }
}

@Composable
private fun ServerItemRow(
    name: String,
    url: String,
    health: ServerHealthItem?,
    onPing: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (health?.responseMsg != null) {
                    Text(
                        text = health.responseMsg,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (health.isHealthy == true) AppColors.success else MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppStatusBadge(
                    text =
                        when (health?.isHealthy) {
                            true -> "${health.latencyMs ?: 0}ms"
                            false -> "Fail"
                            null -> "—"
                        },
                    status =
                        when (health?.isHealthy) {
                            true -> BadgeStatus.SUCCESS
                            false -> BadgeStatus.ERROR
                            null -> BadgeStatus.NEUTRAL
                        },
                )

                IconButton(
                    onClick = onPing,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.dbg_ping_server),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
