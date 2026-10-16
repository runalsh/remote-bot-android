package com.remotebot.app.ui.screens.push

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.ConnectionMode
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PushGatewaySection(
    context: Context,
    scope: CoroutineScope,
    configRepo: BotConfigRepository,
    logger: AppLogger,
    pushGatewayUrl: String,
    onPushGatewayUrlChange: (String) -> Unit,
    pushSecretKey: String,
    onPushSecretKeyChange: (String) -> Unit,
    connectionMode: ConnectionMode,
    onConnectionModeChange: (ConnectionMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "⚙️ Настройки Push Gateway (Шлюз)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Промежуточный шлюз (Cloudflare Worker, Go или Python), который принимает вебхуки от Telegram и отправляет пробуждающий Push (FCM / HMS) на это устройство:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = pushGatewayUrl,
                onValueChange = onPushGatewayUrlChange,
                placeholder = { Text("Push Gateway URL (https://pushgw.yourdomain.com)", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            )

            OutlinedTextField(
                value = pushSecretKey,
                onValueChange = onPushSecretKeyChange,
                placeholder = { Text("Push Secret Key (секретный ключ)", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            )

            // Retry policy configuration
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            val currentConfig = configRepo.config.collectAsState().value
            var maxAttempts by remember(currentConfig.pushRetryMaxAttempts) {
                mutableStateOf(currentConfig.pushRetryMaxAttempts.toFloat())
            }
            var delaySeconds by remember(currentConfig.pushRetryDelaySeconds) {
                mutableStateOf(currentConfig.pushRetryDelaySeconds.toFloat())
            }

            Text(
                text = "🔁 Политика повторных попыток (Retry Policy):",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        maxAttempts = 3f
                        delaySeconds = 1f
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text("Агрессивный\n(3 × 1с)", fontSize = 11.sp, lineHeight = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        maxAttempts = 5f
                        delaySeconds = 5f
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text("Умеренный\n(5 × 5с)", fontSize = 11.sp, lineHeight = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        maxAttempts = 10f
                        delaySeconds = 30f
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text("Экономный\n(10 × 30с)", fontSize = 11.sp, lineHeight = 13.sp)
                }
            }

            // Slider 1: Attempts
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Максимум попыток:", style = MaterialTheme.typography.bodySmall)
                    Text("${maxAttempts.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = maxAttempts,
                    onValueChange = { maxAttempts = it },
                    valueRange = 1f..10f,
                    steps = 8,
                )
            }

            // Slider 2: Delay
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Задержка между попытками:", style = MaterialTheme.typography.bodySmall)
                    Text("${delaySeconds.toInt()} сек", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = delaySeconds,
                    onValueChange = { delaySeconds = it },
                    valueRange = 1f..60f,
                    steps = 58,
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        configRepo.updateConfig { current ->
                            current.copy(
                                pushGatewayUrl = pushGatewayUrl.trim().ifBlank { null },
                                pushSecretKey = pushSecretKey.trim().ifBlank { null },
                                pushRetryMaxAttempts = maxAttempts.toInt(),
                                pushRetryDelaySeconds = delaySeconds.toInt(),
                            )
                        }
                        logger.i(
                            "Push",
                            "Сохранены параметры Push Gateway: url=$pushGatewayUrl, retry=${maxAttempts.toInt()}x${delaySeconds.toInt()}s",
                        )
                        Toast.makeText(context, "Параметры сохранены", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить параметры Push Gateway")
            }
        }
    }
}
