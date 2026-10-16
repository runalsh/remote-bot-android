package com.remotebot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.ui.components.AppCard
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.tracker.ActiveOperationsTracker

@Composable
fun ActiveTasksTab(
    operationsTracker: ActiveOperationsTracker,
    logger: AppLogger,
) {
    val scrollState = rememberScrollState()
    val activeOps by operationsTracker.operationsFlow.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header card for task status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (activeOps.isNotEmpty()) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⏱️ Активные фоновые задачи",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (activeOps.isNotEmpty()) "Выполняется операций: ${activeOps.size}" else "Нет выполняющихся задач",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (activeOps.isNotEmpty()) {
                        Button(
                            onClick = {
                                logger.i("ActiveTasks", "Остановка всех активных фоновых операций (${activeOps.size} шт)")
                                logger.t("ActiveTasks", "Список отменяемых операций: ${activeOps.map { it.id to it.name }}")
                                operationsTracker.cancelAll()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text("Остановить все")
                        }
                    }
                }
            }
        }

        // Active operations list or empty state
        if (activeOps.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("☕", fontSize = 32.sp)
                    Text(
                        text = "Фоновые процессы не запущены",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Здесь в реальном времени отображаются длительные операции бота: скрытая аудиозапись, видеозапись, GPS-трекинг, скачивание и передача тяжелых файлов.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            activeOps.forEach { op ->
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 14.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = op.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ID: ${op.id}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                logger.i("ActiveTasks", "Остановка фоновой операции: ${op.name} (${op.id})")
                                operationsTracker.cancel(op.id)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text("Отмена")
                        }
                    }
                }
            }
        }
    }
}
