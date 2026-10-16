package com.remotebot.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.ui.theme.AppColors
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.LogLevel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsTab(
    logger: AppLogger,
    enabledLevelNames: Set<String>,
    onLevelsChange: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val logs by logger.logsFlow.collectAsState()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    val filteredLogs =
        remember(logs, enabledLevelNames) {
            logs.filter { it.level.name in enabledLevelNames }
        }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Верхняя панель: Фильтры уровней + Экспорт/Очистка
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Логи (${filteredLogs.size}/${logs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Row {
                val exportLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("text/plain"),
                    ) { uri ->
                        if (uri != null) {
                            try {
                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                    val text =
                                        filteredLogs.joinToString("\n") {
                                            "[${dateFormat.format(Date(it.timestampMs))}] [${it.level}] [${it.tag}] ${it.message}"
                                        }
                                    output.write(text.toByteArray(Charsets.UTF_8))
                                }
                                Toast.makeText(context, "Логи успешно сохранены в файл", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text =
                        filteredLogs.joinToString(
                            "\n",
                        ) { "[${dateFormat.format(Date(it.timestampMs))}] [${it.level}] [${it.tag}] ${it.message}" }
                    clipboard.setPrimaryClip(ClipData.newPlainText("RemoteBot Logs", text))
                    Toast.makeText(context, "Скопировано ${filteredLogs.size} строк", Toast.LENGTH_SHORT).show()
                }) {
                    Text("📋")
                }
                IconButton(onClick = {
                    val text =
                        filteredLogs.joinToString(
                            "\n",
                        ) { "[${dateFormat.format(Date(it.timestampMs))}] [${it.level}] [${it.tag}] ${it.message}" }
                    val sendIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "RemoteBot Logs")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                    val shareIntent = Intent.createChooser(sendIntent, "Поделиться логами")
                    context.startActivity(shareIntent)
                }) {
                    Text("📤")
                }
                IconButton(onClick = {
                    val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportLauncher.launch("remotebot_logs_$timeStr.txt")
                }) {
                    Text("💾")
                }
                IconButton(onClick = { logger.clear() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Очистить логи", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Плитка фильтров: 3 строки
        // Строка 1: Все
        val allLevelNames = remember { LogLevel.entries.map { it.name }.toSet() }
        FilterChip(
            selected = enabledLevelNames.size == LogLevel.entries.size,
            onClick = {
                val newSet =
                    if (enabledLevelNames.size == LogLevel.entries.size) {
                        setOf(LogLevel.WARN.name, LogLevel.ERROR.name)
                    } else {
                        allLevelNames
                    }
                onLevelsChange(newSet)
            },
            label = {
                Text(
                    text = "Все уровни (${logs.size})",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = if (enabledLevelNames.size == LogLevel.entries.size) FontWeight.Bold else FontWeight.Normal,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Строка 2: ERR, WARN
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val errSelected = LogLevel.ERROR.name in enabledLevelNames
            FilterChip(
                selected = errSelected,
                onClick = {
                    val newSet = if (errSelected) enabledLevelNames - LogLevel.ERROR.name else enabledLevelNames + LogLevel.ERROR.name
                    onLevelsChange(newSet)
                },
                label = {
                    Text(
                        text = "ERR",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = if (errSelected) AppColors.logError else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (errSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.weight(1f),
            )

            val warnSelected = LogLevel.WARN.name in enabledLevelNames
            FilterChip(
                selected = warnSelected,
                onClick = {
                    val newSet = if (warnSelected) enabledLevelNames - LogLevel.WARN.name else enabledLevelNames + LogLevel.WARN.name
                    onLevelsChange(newSet)
                },
                label = {
                    Text(
                        text = "WARN",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = if (warnSelected) AppColors.logWarn else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (warnSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Строка 3: INFO, DEBUG, TRACE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val infoSelected = LogLevel.INFO.name in enabledLevelNames
            FilterChip(
                selected = infoSelected,
                onClick = {
                    val newSet = if (infoSelected) enabledLevelNames - LogLevel.INFO.name else enabledLevelNames + LogLevel.INFO.name
                    onLevelsChange(newSet)
                },
                label = {
                    Text(
                        text = "INFO",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = if (infoSelected) AppColors.logInfo else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (infoSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.weight(1f),
            )

            val debugSelected = LogLevel.DEBUG.name in enabledLevelNames
            FilterChip(
                selected = debugSelected,
                onClick = {
                    val newSet = if (debugSelected) enabledLevelNames - LogLevel.DEBUG.name else enabledLevelNames + LogLevel.DEBUG.name
                    onLevelsChange(newSet)
                },
                label = {
                    Text(
                        text = "DEBUG",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = if (debugSelected) AppColors.logDebug else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (debugSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.weight(1f),
            )

            val traceSelected = LogLevel.TRACE.name in enabledLevelNames
            FilterChip(
                selected = traceSelected,
                onClick = {
                    val newSet = if (traceSelected) enabledLevelNames - LogLevel.TRACE.name else enabledLevelNames + LogLevel.TRACE.name
                    onLevelsChange(newSet)
                },
                label = {
                    Text(
                        text = "TRACE",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = if (traceSelected) AppColors.logTrace else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (traceSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Строка 4: Лимит количества записей (50, 100, 250, 500, 1000)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Лимит:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp),
            )
            val limits = listOf(50, 100, 250, 500, 1000)
            limits.forEach { limit ->
                val isSelected = logger.maxCapacity == limit
                Surface(
                    selected = isSelected,
                    onClick = { logger.setMaxCapacity(limit) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = limit.toString(),
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (enabledLevelNames.isEmpty()) "Выберите хотя бы один уровень логирования" else "Нет событий с выбранными уровнями",
                    color = Color.Gray,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(AppColors.logConsoleBackground, shape = MaterialTheme.shapes.small)
                        .padding(8.dp),
                reverseLayout = true,
            ) {
                items(filteredLogs.reversed()) { entry ->
                    val color =
                        when (entry.level) {
                            LogLevel.TRACE -> AppColors.logTrace
                            LogLevel.DEBUG -> AppColors.logDebug
                            LogLevel.INFO -> AppColors.logInfo
                            LogLevel.WARN -> AppColors.logWarn
                            LogLevel.ERROR -> AppColors.logError
                        }
                    val timeStr = dateFormat.format(Date(entry.timestampMs))
                    Text(
                        text = "[$timeStr] [${entry.level}] [${entry.tag}] ${entry.message}",
                        color = color,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.5.dp),
                    )
                }
            }
        }
    }
}
