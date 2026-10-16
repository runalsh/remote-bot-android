package com.remotebot.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.ui.components.RemovableInputRow
import com.remotebot.app.ui.components.StatusMessageText
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.launch

@Composable
fun SmsSettingsTab(
    configRepo: BotConfigRepository,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val config by configRepo.config.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var smsBotEnabled by remember(config.smsBotEnabled) { mutableStateOf(config.smsBotEnabled) }
    var smsTransliterationEnabled by remember(config.smsTransliterationEnabled) { mutableStateOf(config.smsTransliterationEnabled) }

    var targetNumbersList by remember(config.smsForwardTargetNumbers) {
        mutableStateOf(if (config.smsForwardTargetNumbers.isEmpty()) emptyList<String>() else config.smsForwardTargetNumbers.toList())
    }
    var pinInput by remember(config.smsPin) { mutableStateOf(config.smsPin ?: "") }
    var allowedPhonesList by remember(config.allowedPhoneNumbers) {
        mutableStateOf(if (config.allowedPhoneNumbers.isEmpty()) emptyList<String>() else config.allowedPhoneNumbers.toList())
    }

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
        // Card 1: Main SMS bot master toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (smsBotEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "💬 SMS-бот",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text =
                                if (smsBotEnabled) {
                                    "Включен: обработка команд и пересылка активны"
                                } else {
                                    "Выключен (по умолчанию): отправка и пересылка по сотовой связи отключены"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = smsBotEnabled,
                        onCheckedChange = { isChecked ->
                            smsBotEnabled = isChecked
                            statusMessage = null
                        },
                    )
                }
            }
        }

        // Card 2: Forwarding destination numbers (SMS -> SMS & Push -> SMS)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "📤 Номера для пересылки",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = {
                            targetNumbersList = targetNumbersList + ""
                            statusMessage = null
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Добавить номер",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Куда отправлять входящие SMS и уведомления приложений:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (targetNumbersList.isEmpty()) {
                    Text(
                        text = "⚠️ Список номеров пуст. Нажмите «+» справа, чтобы добавить номер назначения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    targetNumbersList.forEachIndexed { index, phoneStr ->
                        RemovableInputRow(
                            value = phoneStr,
                            onValueChange = { newVal ->
                                val list = targetNumbersList.toMutableList()
                                list[index] = newVal.trim()
                                targetNumbersList = list
                                statusMessage = null
                            },
                            placeholder = "Номер #${index + 1} (+79991234567)",
                            onRemove = {
                                val list = targetNumbersList.toMutableList()
                                list.removeAt(index)
                                targetNumbersList = list
                                statusMessage = null
                            },
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // Card 3: SMS sending parameters
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🔤 Параметры отправки SMS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Transliteration
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { smsTransliterationEnabled = !smsTransliterationEnabled }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "🔤 Транслитерация (кириллица ➔ латиница)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Экономит баланс: 160 символов в одном SMS вместо 70 для русского текста (ГОСТ 7.79)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = smsTransliterationEnabled,
                        onCheckedChange = { smsTransliterationEnabled = it },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("💡", fontSize = 18.sp)
                        Text(
                            text = "Выбор типов пересылки (в Telegram и по SMS) для входящих сообщений, звонков и push-уведомлений настраивается в разделе «Уведомления».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Card 4: Remote control via SMS (Offline Bot)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🎮 Удаленное управление (Offline Bot)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "💡 Все входящие команды обязаны начинаться с префикса «rb » (например: rb 1234 /wifi on или rb /status).\n\nЗадайте PIN для управления с чужих номеров и укажите доверенные номера для управления без PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it.trim()
                        statusMessage = null
                    },
                    label = { Text("SMS PIN-код") },
                    placeholder = { Text("Например: 1234") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Доверенные номера (без PIN):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = {
                            allowedPhonesList = allowedPhonesList + ""
                            statusMessage = null
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Добавить доверенный номер",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (allowedPhonesList.isEmpty()) {
                    Text(
                        text = "Список пуст (управление только по PIN). Нажмите «+» справа, чтобы добавить доверенный номер.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    allowedPhonesList.forEachIndexed { index, phoneStr ->
                        RemovableInputRow(
                            value = phoneStr,
                            onValueChange = { newVal ->
                                val list = allowedPhonesList.toMutableList()
                                list[index] = newVal.trim()
                                allowedPhonesList = list
                                statusMessage = null
                            },
                            placeholder = "Номер #${index + 1} (+79991234567)",
                            onRemove = {
                                val list = allowedPhonesList.toMutableList()
                                list.removeAt(index)
                                allowedPhonesList = list
                                statusMessage = null
                            },
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // Save button
        Button(
            onClick = {
                val cleanPin = pinInput.trim().ifBlank { null }
                val cleanPhones = allowedPhonesList.map { it.trim() }.filter { it.isNotBlank() }.toSet()
                val cleanTargets = targetNumbersList.map { it.trim() }.filter { it.isNotBlank() }.toSet()

                // Auto-protection: if destination numbers list is empty, disable forwarding
                val effectiveBotEnabled = smsBotEnabled && (cleanTargets.isNotEmpty() || cleanPhones.isNotEmpty() || cleanPin != null)

                scope.launch {
                    configRepo.updateConfig {
                        it.copy(
                            smsBotEnabled = effectiveBotEnabled,
                            smsForwardTargetNumbers = cleanTargets,
                            smsTransliterationEnabled = smsTransliterationEnabled,
                            smsPin = cleanPin,
                            allowedPhoneNumbers = cleanPhones,
                        )
                    }
                    isSuccess = true
                    statusMessage = "✅ Настройки SMS-бота успешно сохранены!"
                    Toast.makeText(context, "Настройки SMS сохранены!", Toast.LENGTH_SHORT).show()
                    logger.i(
                        "SmsConfig",
                        "Сохранены настройки SMS: enabled=$effectiveBotEnabled, targets=$cleanTargets, translit=$smsTransliterationEnabled",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить настройки SMS-бота")
        }

        StatusMessageText(
            message = statusMessage,
            isError = !isSuccess,
        )
    }
}
