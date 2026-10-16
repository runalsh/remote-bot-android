package com.remotebot.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.ui.components.SegmentedSlider
import com.remotebot.app.ui.components.SliderSegmentItem
import com.remotebot.app.ui.components.StatusMessageText
import com.remotebot.app.ui.screens.notifications.AppPickerDialog
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.repository.AppManagerRepository
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.launch

@Composable
fun NotificationsSettingsTab(
    configRepo: BotConfigRepository,
    appManagerRepo: AppManagerRepository,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val config by configRepo.config.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var tgForwardSms by remember(config.tgForwardSmsEnabled) { mutableStateOf(config.tgForwardSmsEnabled) }
    var tgForwardCalls by remember(config.tgForwardCallsEnabled) { mutableStateOf(config.tgForwardCallsEnabled) }
    var tgForwardNotifs by remember(config.tgForwardNotificationsEnabled) { mutableStateOf(config.tgForwardNotificationsEnabled) }
    var tgForwardBatteryCharging by remember(
        config.tgForwardBatteryChargingEnabled,
    ) { mutableStateOf(config.tgForwardBatteryChargingEnabled) }
    var tgForwardBatteryLow by remember(config.tgForwardBatteryLowEnabled) { mutableStateOf(config.tgForwardBatteryLowEnabled) }

    var smsForwardSms by remember(config.smsForwardSmsEnabled) { mutableStateOf(config.smsForwardSmsEnabled) }
    var smsForwardCalls by remember(config.smsForwardMissedCallsEnabled) { mutableStateOf(config.smsForwardMissedCallsEnabled) }
    var smsForwardNotifs by remember(config.smsForwardNotificationsEnabled) { mutableStateOf(config.smsForwardNotificationsEnabled) }
    var smsForwardBatteryCharging by remember(config.smsForwardBatteryChargingEnabled) {
        mutableStateOf(config.smsForwardBatteryChargingEnabled)
    }
    var smsForwardBatteryLow by remember(config.smsForwardBatteryLowEnabled) { mutableStateOf(config.smsForwardBatteryLowEnabled) }

    var selectedApps by remember(config.notificationForwardingApps) {
        mutableStateOf(config.notificationForwardingApps)
    }
    var stopWords by remember(config.notificationStopWords) {
        mutableStateOf(config.notificationStopWords)
    }
    var newStopWordInput by remember { mutableStateOf("") }
    var dedupTimeoutInput by remember(config.notificationDedupTimeoutSeconds) {
        mutableStateOf(config.notificationDedupTimeoutSeconds.toString())
    }
    var rateLimitPerMinuteInput by remember(config.forwardRateLimitPerMinute) {
        mutableStateOf(config.forwardRateLimitPerMinute.toString())
    }
    var replyUnauthorizedEnabled by remember(config.replyUnauthorizedResponseEnabled) {
        mutableStateOf(config.replyUnauthorizedResponseEnabled)
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    if (showAppPicker) {
        AppPickerDialog(
            appManagerRepo = appManagerRepo,
            initialSelected = selectedApps,
            logger = logger,
            onDismiss = { showAppPicker = false },
            onConfirm = { chosen ->
                selectedApps = chosen
                showAppPicker = false
                statusMessage = null
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card 0: Forwarding modes and channels (Telegram and SMS)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📡 Каналы пересылки уведомлений",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Выберите, куда пересылать каждый тип событий (в Telegram и/или по сотовому SMS):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 1. Incoming SMS
                ForwardingTypeRow(
                    icon = "📨",
                    title = "Входящие SMS",
                    description = "Дублирование входящих SMS-сообщений",
                    tgChecked = tgForwardSms,
                    onTgChange = {
                        tgForwardSms = it
                        statusMessage = null
                    },
                    smsChecked = smsForwardSms,
                    onSmsChange = {
                        smsForwardSms = it
                        statusMessage = null
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // 2. Missed calls
                ForwardingTypeRow(
                    icon = "📞",
                    title = "Пропущенные звонки",
                    description = "Оповещение о пропущенных телефонных вызовах",
                    tgChecked = tgForwardCalls,
                    onTgChange = {
                        tgForwardCalls = it
                        statusMessage = null
                    },
                    smsChecked = smsForwardCalls,
                    onSmsChange = {
                        smsForwardCalls = it
                        statusMessage = null
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // 3. App push notifications
                ForwardingTypeRow(
                    icon = "🔔",
                    title = "Push-уведомления приложений",
                    description = "Пересылка уведомлений от приложений (список ниже)",
                    tgChecked = tgForwardNotifs,
                    onTgChange = {
                        tgForwardNotifs = it
                        statusMessage = null
                    },
                    smsChecked = smsForwardNotifs,
                    onSmsChange = {
                        smsForwardNotifs = it
                        statusMessage = null
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // 4. Charging and discharging status
                ForwardingTypeRow(
                    icon = "⚡",
                    title = "Статус зарядки и питания",
                    description = "Оповещение при подключении к сети/USB и отключении зарядки",
                    tgChecked = tgForwardBatteryCharging,
                    onTgChange = {
                        tgForwardBatteryCharging = it
                        statusMessage = null
                    },
                    smsChecked = smsForwardBatteryCharging,
                    onSmsChange = {
                        smsForwardBatteryCharging = it
                        statusMessage = null
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // 5. Low battery
                ForwardingTypeRow(
                    icon = "🪫",
                    title = "Низкий заряд батареи (≤ 20%)",
                    description = "Оповещения каждые 3% при разряде аккумулятора от 20% и ниже",
                    tgChecked = tgForwardBatteryLow,
                    onTgChange = {
                        tgForwardBatteryLow = it
                        statusMessage = null
                    },
                    smsChecked = smsForwardBatteryLow,
                    onSmsChange = {
                        smsForwardBatteryLow = it
                        statusMessage = null
                    },
                )
            }
        }

        // Card 1: App selection
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📱 Приложения для пересылки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text =
                        if (selectedApps.isEmpty()) {
                            "⚠️ Ни одно приложение не выбрано. Уведомления не пересылаются.\nНажмите кнопку ниже, чтобы выбрать нужные приложения."
                        } else {
                            "Выбрано приложений: ${selectedApps.size}. Уведомления пересылаются только от этих приложений."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("⚙️ Выбрать приложения (${selectedApps.size})")
                }

                if (selectedApps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text =
                            "Выбранные пакеты:\n" + selectedApps.take(5).joinToString("\n") +
                                if (selectedApps.size > 5) "\n...и ещё ${selectedApps.size - 5}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // Card 2: Stop-words (text blacklist)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🚫 Ключевые слова для исключения (стоп-слова)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Если заголовок или текст уведомления содержит любое из этих слов или фраз, оно НЕ пересылается (блокируется для Telegram и SMS).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = newStopWordInput,
                        onValueChange = {
                            newStopWordInput = it
                            statusMessage = null
                        },
                        placeholder = { Text("Новое стоп-слово (спам, код, OTP...)", fontSize = 12.5.sp) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    )
                    IconButton(
                        onClick = {
                            val trimmed = newStopWordInput.trim()
                            if (trimmed.isNotEmpty() && !stopWords.contains(trimmed)) {
                                stopWords = stopWords + trimmed
                                newStopWordInput = ""
                                statusMessage = null
                            }
                        },
                        modifier = Modifier.size(42.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Добавить",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                if (stopWords.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Список стоп-слов пуст. Фильтрация по тексту не применяется.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Активные стоп-слова (${stopWords.size}):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    stopWords.forEachIndexed { index, word ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = word,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .size(28.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(6.dp),
                                            ).clickable {
                                                val list = stopWords.toMutableList()
                                                list.removeAt(index)
                                                stopWords = list
                                                statusMessage = null
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "—",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 3: Duplicate protection (anti-spam timeout)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "⏱ Защита от дубликатов (анти-спам)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Таймаут (в секундах) для подавления одинаковых повторных уведомлений от одного и того же приложения (применяется для Telegram и SMS).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = dedupTimeoutInput,
                    onValueChange = {
                        dedupTimeoutInput = it.filter { char -> char.isDigit() }
                        statusMessage = null
                    },
                    label = { Text("Таймаут дедупликации (секунд)") },
                    placeholder = { Text("15") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Быстрый выбор:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))

                SegmentedSlider(
                    items =
                        listOf(
                            SliderSegmentItem("5", "5 сек"),
                            SliderSegmentItem("15", "15 сек"),
                            SliderSegmentItem("30", "30 сек"),
                            SliderSegmentItem("60", "1 мин"),
                            SliderSegmentItem("300", "5 мин"),
                            SliderSegmentItem("900", "15 мин"),
                        ),
                    selectedValue = dedupTimeoutInput,
                    onValueSelected = {
                        dedupTimeoutInput = it
                        statusMessage = null
                    },
                )
            }
        }

        // Card 4: Rate limiting (anti-flood and spam protection)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🛡️ Ограничение частоты (Rate Limiting)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Принцип скользящего окна (последние 60 сек): при превышении порога пересылки SMS от одного номера лишние сообщения дропаются без очереди, защищая баланс SIM-карты. Выберите «Выкл» для отключения ограничения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = if (rateLimitPerMinuteInput == "0") "Выкл" else rateLimitPerMinuteInput,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        rateLimitPerMinuteInput =
                            if (input.contains("выкл", ignoreCase = true) || (digits.isEmpty() && input.isNotBlank())) {
                                "0"
                            } else {
                                digits
                            }
                        statusMessage = null
                    },
                    label = { Text("Лимит SMS в минуту") },
                    placeholder = { Text("30") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Быстрый выбор:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))

                SegmentedSlider(
                    items =
                        listOf(
                            SliderSegmentItem("0", "Выкл"),
                            SliderSegmentItem("5", "5"),
                            SliderSegmentItem("10", "10"),
                            SliderSegmentItem("20", "20"),
                            SliderSegmentItem("30", "30"),
                            SliderSegmentItem("40", "40"),
                            SliderSegmentItem("50", "50"),
                            SliderSegmentItem("60", "60"),
                        ),
                    selectedValue = rateLimitPerMinuteInput,
                    onValueSelected = {
                        rateLimitPerMinuteInput = it
                        statusMessage = null
                    },
                )
            }
        }

        // Card 5: Security and replies to unauthorized requests
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🔒 Безопасность каналов (Telegram & SMS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { replyUnauthorizedEnabled = !replyUnauthorizedEnabled }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Отвечать при отказе в доступе",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Если включено (по умолчанию) — бот отправляет ответ об ошибке («Unauthorized User ID» в Telegram, «неверный PIN» по SMS). Если выключено — игнорирует неавторизованные запросы молча (тихий режим).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = replyUnauthorizedEnabled,
                        onCheckedChange = { replyUnauthorizedEnabled = it },
                    )
                }
            }
        }

        // Save button
        Button(
            onClick = {
                val dedupSeconds = dedupTimeoutInput.toIntOrNull() ?: 15
                val rateLimit = rateLimitPerMinuteInput.toIntOrNull() ?: 30
                scope.launch {
                    configRepo.updateConfig {
                        it.copy(
                            tgForwardSmsEnabled = tgForwardSms,
                            tgForwardCallsEnabled = tgForwardCalls,
                            tgForwardNotificationsEnabled = tgForwardNotifs,
                            tgForwardBatteryChargingEnabled = tgForwardBatteryCharging,
                            tgForwardBatteryLowEnabled = tgForwardBatteryLow,
                            smsForwardSmsEnabled = smsForwardSms,
                            smsForwardMissedCallsEnabled = smsForwardCalls,
                            smsForwardNotificationsEnabled = smsForwardNotifs,
                            smsForwardBatteryChargingEnabled = smsForwardBatteryCharging,
                            smsForwardBatteryLowEnabled = smsForwardBatteryLow,
                            notificationForwardingApps = selectedApps,
                            notificationStopWords = stopWords,
                            notificationDedupTimeoutSeconds = dedupSeconds,
                            forwardRateLimitPerMinute = rateLimit,
                            replyUnauthorizedResponseEnabled = replyUnauthorizedEnabled,
                        )
                    }
                    isSuccess = true
                    statusMessage = "✅ Настройки пересылки сохранены!"
                    Toast.makeText(context, "Настройки пересылки сохранены!", Toast.LENGTH_SHORT).show()
                    logger.i(
                        "NotifConfig",
                        "Сохранено: TG=[sms:$tgForwardSms, calls:$tgForwardCalls, notif:$tgForwardNotifs, charge:$tgForwardBatteryCharging, low:$tgForwardBatteryLow], SMS=[sms:$smsForwardSms, calls:$smsForwardCalls, notif:$smsForwardNotifs, charge:$smsForwardBatteryCharging, low:$smsForwardBatteryLow], apps=${selectedApps.size}, dedup=${dedupSeconds}s, rateLimit=$rateLimit/m, replyUnauthorized=$replyUnauthorizedEnabled",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить настройки пересылки")
        }

        StatusMessageText(
            message = statusMessage,
            isError = !isSuccess,
        )
    }
}

@Composable
private fun ForwardingTypeRow(
    icon: String,
    title: String,
    description: String,
    tgChecked: Boolean,
    onTgChange: (Boolean) -> Unit,
    smsChecked: Boolean,
    onSmsChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(icon, fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Telegram checkbox
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { onTgChange(!tgChecked) },
                shape = RoundedCornerShape(8.dp),
                color = if (tgChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (tgChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "✈️ Telegram",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (tgChecked) FontWeight.Bold else FontWeight.Normal,
                    )
                    Checkbox(
                        checked = tgChecked,
                        onCheckedChange = onTgChange,
                    )
                }
            }

            // SMS checkbox
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { onSmsChange(!smsChecked) },
                shape = RoundedCornerShape(8.dp),
                color =
                    if (smsChecked) {
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.7f,
                        )
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                border =
                    BorderStroke(
                        1.dp,
                        if (smsChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "💬 SMS",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (smsChecked) FontWeight.Bold else FontWeight.Normal,
                    )
                    Checkbox(
                        checked = smsChecked,
                        onCheckedChange = onSmsChange,
                    )
                }
            }
        }
    }
}
