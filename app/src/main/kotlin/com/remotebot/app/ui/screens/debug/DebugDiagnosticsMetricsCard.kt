package com.remotebot.app.ui.screens.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.core.common.diagnostics.AppDiagnosticsTracker
import com.remotebot.app.R
import com.remotebot.domain.model.ServerConnectionStats
import com.remotebot.domain.model.ServerType
import com.remotebot.domain.model.WakeupSource
import kotlinx.coroutines.delay

@Composable
fun DebugDiagnosticsMetricsCard(onResetStats: () -> Unit = { AppDiagnosticsTracker.clearHistory() }) {
    // Update ticker only while card is active on screen (component in composition)
    var refreshTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500L) // refresh every 1.5s
            AppDiagnosticsTracker.updateSnapshot()
            refreshTick = System.currentTimeMillis()
        }
    }

    val snapshot by AppDiagnosticsTracker.metricsSnapshot.collectAsState()
    var selectedWindowIndex by remember { mutableStateOf(0) } // 0: 5m, 1: 15m, 2: 30m, 3: 1h, 4: since start

    val currentWindow =
        when (selectedWindowIndex) {
            0 -> snapshot.last5Min
            1 -> snapshot.last15Min
            2 -> snapshot.last30Min
            3 -> snapshot.last1Hour
            else -> snapshot.sinceStart
        }
    val currentWindowLabel =
        when (currentWindow.windowName) {
            "5m" -> stringResource(R.string.dbg_win_5m)
            "15m" -> stringResource(R.string.dbg_win_15m)
            "30m" -> stringResource(R.string.dbg_win_30m)
            "1h" -> stringResource(R.string.dbg_win_1h)
            else -> stringResource(R.string.dbg_win_since_start)
        }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Card header
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
                            text = stringResource(R.string.dbg_diag_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text =
                                if (snapshot.isTrackingEnabled) {
                                    stringResource(R.string.dbg_metrics_active)
                                } else {
                                    stringResource(R.string.dbg_metrics_paused)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (snapshot.isTrackingEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onResetStats,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.dbg_btn_reset2), fontSize = 11.sp)
                }
            }

            // Time window selector (5 min, 15 min, 30 min, 1 hour, since startup)
            val windowLabels = listOf(stringResource(R.string.dbg_w5), stringResource(R.string.dbg_w15), stringResource(R.string.dbg_w30), stringResource(R.string.dbg_w60), stringResource(R.string.dbg_wstart))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                windowLabels.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedWindowIndex == idx,
                        onClick = { selectedWindowIndex = idx },
                        label = { Text(label, fontSize = 12.sp) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 1. Application wakeup events section
            SectionHeader(title = stringResource(R.string.dbg_wakeups, currentWindowLabel))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricMiniCard(
                    title = stringResource(R.string.dbg_total),
                    value = "${currentWindow.wakeupsTotal}",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                )
                MetricMiniCard(
                    title = "FCM Push",
                    value = "${currentWindow.wakeupsBySource[WakeupSource.PUSH_FCM] ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                MetricMiniCard(
                    title = "HMS Push",
                    value = "${currentWindow.wakeupsBySource[WakeupSource.PUSH_HMS] ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                MetricMiniCard(
                    title = "SMS",
                    value = "${currentWindow.wakeupsBySource[WakeupSource.SMS] ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }
            if ((currentWindow.wakeupsBySource[WakeupSource.PHONE_CALL] ?: 0) > 0 ||
                (currentWindow.wakeupsBySource[WakeupSource.BATTERY_EVENT] ?: 0) > 0
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricMiniCard(
                        title = stringResource(R.string.dbg_calls),
                        value = "${currentWindow.wakeupsBySource[WakeupSource.PHONE_CALL] ?: 0}",
                        modifier = Modifier.weight(1f),
                    )
                    MetricMiniCard(
                        title = stringResource(R.string.dbg_power),
                        value = "${currentWindow.wakeupsBySource[WakeupSource.BATTERY_EVENT] ?: 0}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. Failed authentication attempts section
            SectionHeader(title = stringResource(R.string.dbg_auth_err, currentWindowLabel))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricMiniCard(
                    title = stringResource(R.string.dbg_auth_tg),
                    value = "${currentWindow.tgAuthFailures}",
                    modifier = Modifier.weight(1f),
                    color = if (currentWindow.tgAuthFailures > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                MetricMiniCard(
                    title = stringResource(R.string.dbg_auth_sms),
                    value = "${currentWindow.smsAuthFailures}",
                    modifier = Modifier.weight(1f),
                    color = if (currentWindow.smsAuthFailures > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3. Network connections by server section (Push Gateway & Telegram API)
            SectionHeader(title = stringResource(R.string.dbg_srv_stats))

            val pushServers = currentWindow.serverStats.filter { it.serverType == ServerType.PUSH_GATEWAY }
            val tgServers = currentWindow.serverStats.filter { it.serverType == ServerType.TELEGRAM_API }

            if (currentWindow.serverStats.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.dbg_no_net, currentWindowLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                if (pushServers.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.dbg_push_srv),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    pushServers.forEach { srv ->
                        ServerStatRow(srv)
                    }
                }

                if (tgServers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dbg_tg_srv),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    tgServers.forEach { srv ->
                        ServerStatRow(srv)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ServerStatRow(stats: ServerConnectionStats) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stats.serverName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.dbg_total_fmt, stats.totalCount),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stats.serverId != stats.serverName) {
                Text(
                    text = stats.serverId,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BadgeChip(label = stringResource(R.string.dbg_success), count = stats.successCount, color = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                BadgeChip(label = stringResource(R.string.dbg_failure), count = stats.failureCount, color = Color(0xFFC62828), modifier = Modifier.weight(1f))
                BadgeChip(label = stringResource(R.string.dbg_timeout), count = stats.timeoutCount, color = Color(0xFFE65100), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = label, fontSize = 10.sp, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

