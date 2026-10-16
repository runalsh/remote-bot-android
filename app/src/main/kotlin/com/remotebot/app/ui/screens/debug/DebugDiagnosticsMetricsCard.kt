package com.remotebot.app.ui.screens.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.core.common.diagnostics.AppDiagnosticsTracker
import com.remotebot.domain.model.ServerConnectionStats
import com.remotebot.domain.model.ServerType
import com.remotebot.domain.model.WakeupSource
import kotlinx.coroutines.delay

@Composable
fun DebugDiagnosticsMetricsCard(onResetStats: () -> Unit = { AppDiagnosticsTracker.clearHistory() }) {
    // Обновление тикера только пока карточка активна на экране (компонент в композиции)
    var refreshTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500L) // обновление каждые 1.5 сек
            AppDiagnosticsTracker.updateSnapshot()
            refreshTick = System.currentTimeMillis()
        }
    }

    val snapshot by AppDiagnosticsTracker.metricsSnapshot.collectAsState()
    var selectedWindowIndex by remember { mutableStateOf(0) } // 0: 5м, 1: 15м, 2: 30м, 3: 1ч, 4: с запуска

    val currentWindow =
        when (selectedWindowIndex) {
            0 -> snapshot.last5Min
            1 -> snapshot.last15Min
            2 -> snapshot.last30Min
            3 -> snapshot.last1Hour
            else -> snapshot.sinceStart
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
            // Заголовок карточки
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
                            text = "Метрики работы и сетевые подключения",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text =
                                if (snapshot.isTrackingEnabled) {
                                    "Сбор метрик активен (фоновый трекер)"
                                } else {
                                    "Сбор метрик приостановлен (Debug выключен)"
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
                    Text("Сброс ↺", fontSize = 11.sp)
                }
            }

            // Переключатель временных окон (5 мин, 15 мин, 30 мин, 1 час, с запуска)
            val windowLabels = listOf("5 мин", "15 мин", "30 мин", "1 час", "С запуска")
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

            // 1. Секция просыпаний приложения
            SectionHeader(title = "⏰ Просыпания приложения (${currentWindow.windowName})")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricMiniCard(
                    title = "Всего",
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
                        title = "Звонки",
                        value = "${currentWindow.wakeupsBySource[WakeupSource.PHONE_CALL] ?: 0}",
                        modifier = Modifier.weight(1f),
                    )
                    MetricMiniCard(
                        title = "Питание/АКБ",
                        value = "${currentWindow.wakeupsBySource[WakeupSource.BATTERY_EVENT] ?: 0}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. Секция неудачных аутентификаций
            SectionHeader(title = "🔒 Ошибки аутентификации (${currentWindow.windowName})")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricMiniCard(
                    title = "Telegram (не из whitelist)",
                    value = "${currentWindow.tgAuthFailures}",
                    modifier = Modifier.weight(1f),
                    color = if (currentWindow.tgAuthFailures > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                MetricMiniCard(
                    title = "SMS (неверный PIN / номер)",
                    value = "${currentWindow.smsAuthFailures}",
                    modifier = Modifier.weight(1f),
                    color = if (currentWindow.smsAuthFailures > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3. Секция сетевых коннектов по серверам (Push Gateway & Telegram API)
            SectionHeader(title = "🌐 Статистика серверов (Удачно / Ошибка / Таймаут)")

            val pushServers = currentWindow.serverStats.filter { it.serverType == ServerType.PUSH_GATEWAY }
            val tgServers = currentWindow.serverStats.filter { it.serverType == ServerType.TELEGRAM_API }

            if (currentWindow.serverStats.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "За выбранный интервал (${currentWindow.windowName}) сетевых обращений не зафиксировано.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                if (pushServers.isNotEmpty()) {
                    Text(
                        text = "🔥 Push Gateway Серверы:",
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
                        text = "✈️ Telegram API Серверы / Зеркала:",
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
                    text = "Всего: ${stats.totalCount}",
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
                BadgeChip(label = "Успех", count = stats.successCount, color = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                BadgeChip(label = "Сбой", count = stats.failureCount, color = Color(0xFFC62828), modifier = Modifier.weight(1f))
                BadgeChip(label = "Таймаут", count = stats.timeoutCount, color = Color(0xFFE65100), modifier = Modifier.weight(1f))
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

