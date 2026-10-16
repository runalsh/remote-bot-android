package com.remotebot.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.remotebot.app.ui.components.SegmentedSlider
import com.remotebot.app.ui.components.SliderSegmentItem
import com.remotebot.app.ui.components.StatusMessageText
import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.ProxyConfig
import com.remotebot.domain.model.ProxyType
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.ProxyConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProxySettingsTab(
    configRepo: BotConfigRepository,
    proxyRepo: ProxyConfigRepository,
    apiClient: TelegramApiClient,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val proxyConfig by proxyRepo.proxyConfig.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedProxyType by remember(proxyConfig.type) { mutableStateOf(proxyConfig.type) }
    var proxyHost by remember(proxyConfig.host) { mutableStateOf(proxyConfig.host) }
    var proxyPort by remember(proxyConfig.port) { mutableStateOf(proxyConfig.port.toString()) }
    var proxyUser by remember(proxyConfig.username) { mutableStateOf(proxyConfig.username ?: "") }
    var proxyPass by remember(proxyConfig.password) { mutableStateOf(proxyConfig.password ?: "") }
    var proxyWsPath by remember(proxyConfig.wsPath) { mutableStateOf(proxyConfig.wsPath) }

    var isChecking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Настройки прокси (Обход DPI)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))

                SegmentedSlider(
                    items =
                        listOf(
                            SliderSegmentItem(ProxyType.NONE, "Direct"),
                            SliderSegmentItem(ProxyType.SOCKS5, "SOCKS5"),
                            SliderSegmentItem(ProxyType.HTTP, "HTTP"),
                            SliderSegmentItem(ProxyType.WEBSOCKET_WSS, "WSS"),
                        ),
                    selectedValue = selectedProxyType,
                    onValueSelected = { selectedProxyType = it },
                )

                if (selectedProxyType != ProxyType.NONE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it },
                            placeholder = { Text("Хост / IP-адрес", fontSize = 13.sp) },
                            modifier = Modifier.weight(0.7f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        )
                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it },
                            placeholder = { Text("Порт", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.3f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = proxyUser,
                            onValueChange = { proxyUser = it },
                            placeholder = { Text("Логин", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        )
                        OutlinedTextField(
                            value = proxyPass,
                            onValueChange = { proxyPass = it },
                            placeholder = { Text("Пароль", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        )
                    }

                    if (selectedProxyType == ProxyType.WEBSOCKET_WSS) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = proxyWsPath,
                            onValueChange = { proxyWsPath = it },
                            placeholder = { Text("WSS Path (/ws)", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Прямое сетевое соединение с api.telegram.org без использования прокси-серверов.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ℹ️ Область применения прокси:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "• Telegram Bot API: запросы Long Polling, отправка сообщений, фото и файлов идут через указанный прокси.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "• Push Gateway (FCM / HMS) и вебхуки: работают напрямую через системное интернет-соединение Android.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val currentToken =
                    configRepo.config.value.botToken
                        .trim()
                if (currentToken.isBlank()) {
                    statusMessage =
                        "⚠️ Токен бота не задан! Сначала перейдите на вкладку «Бот», введите токен и нажмите «Сохранить настройки»."
                    isSuccess = false
                    return@Button
                }

                val portNumber = proxyPort.trim().toIntOrNull() ?: 1080
                val newConfig =
                    ProxyConfig(
                        type = selectedProxyType,
                        host = proxyHost.trim(),
                        port = portNumber,
                        username = proxyUser.trim().ifBlank { null },
                        password = proxyPass.trim().ifBlank { null },
                        wsPath = proxyWsPath.trim().ifBlank { "/ws" },
                    )

                logger.d(
                    "Proxy",
                    "Применение настроек прокси: тип=$selectedProxyType, host=$proxyHost, port=$portNumber, user=${proxyUser.ifBlank {
                        "none"
                    }}",
                )
                logger.t(
                    "Proxy",
                    "Конфигурация прокси: type=${newConfig.type}, host=${newConfig.host}, port=${newConfig.port}, wsPath=${newConfig.wsPath}, hasAuth=${!newConfig.username
                        .isNullOrBlank()}",
                )

                isChecking = true
                statusMessage = "Применение прокси и проверка пинга..."
                isSuccess = false

                scope.launch {
                    proxyRepo.updateProxy(newConfig)
                    try {
                        logger.d("Proxy", "Отправка getMe() через настроенный сетевой стек...")
                        val botUser = withContext(Dispatchers.IO) { apiClient.getMe() }
                        val label = if (selectedProxyType == ProxyType.NONE) "Direct" else selectedProxyType.name
                        statusMessage = "✅ Соединение успешно через $label!\nБот: @${botUser.username ?: "bot"}"
                        isSuccess = true
                        logger.i("Proxy", "Прокси успешно применен и проверен ($label) | Бот: @${botUser.username}")
                        logger.t("Proxy", "getMe() ответ через $label: id=${botUser.id}, username=@${botUser.username}")
                        Toast.makeText(context, "Прокси применен и проверен!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        statusMessage = "❌ Ошибка проверки через прокси: ${e.localizedMessage ?: "Сбой соединения"}"
                        isSuccess = false
                        logger.e("Proxy", "Ошибка проверки через прокси: ${e.localizedMessage}", e)
                        logger.t("Proxy", "Стек ошибки прокси:\n${e.stackTraceToString()}")
                    } finally {
                        isChecking = false
                    }
                }
            },
            enabled = !isChecking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Проверка пинга...")
            } else {
                Text("Сохранить прокси и проверить связь")
            }
        }

        StatusMessageText(
            message = statusMessage,
            isError = !isSuccess,
        )
    }
}
