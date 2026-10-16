package com.remotebot.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.ui.components.RemovableInputRow
import com.remotebot.app.ui.components.SegmentedSlider
import com.remotebot.app.ui.components.SliderSegmentItem
import com.remotebot.app.ui.components.StatusMessageText
import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.ConnectionMode
import com.remotebot.domain.model.EndpointSelectionStrategy
import com.remotebot.domain.model.UserSubscription
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TelegramSettingsTab(
    configRepo: BotConfigRepository,
    apiClient: TelegramApiClient,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val config by configRepo.config.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var telegramBotEnabled by remember(config.telegramBotEnabled) { mutableStateOf(config.telegramBotEnabled) }
    var tokenInput by remember(config.botToken) { mutableStateOf(config.botToken) }
    var connectionMode by remember(config.connectionMode) { mutableStateOf(config.connectionMode) }
    var customApiRoot by remember(config.customApiRoot) { mutableStateOf(config.customApiRoot ?: "") }
    var apiEndpointsList by remember(config.apiEndpoints) { mutableStateOf(config.apiEndpoints) }
    var endpointStrategy by remember(config.endpointSelectionStrategy) { mutableStateOf(config.endpointSelectionStrategy) }
    var allowedIdsList by remember(config.allowedUserIds) {
        mutableStateOf(if (config.allowedUserIds.isEmpty()) emptyList<String>() else config.allowedUserIds.map { it.toString() })
    }
    var userSubs by remember(config.userSubscriptions) { mutableStateOf(config.userSubscriptions) }
    var storageDirInput by remember(config.storageDirectory) { mutableStateOf(config.storageDirectory ?: "") }

    var endpointHealthMap by remember { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }

    val allEndpointsToCheck = remember(customApiRoot, apiEndpointsList) {
        val list = mutableListOf<String>()
        val main = customApiRoot.trim().ifBlank { "https://api.telegram.org" }
        list.add(main)
        for (ep in apiEndpointsList) {
            val clean = ep.trim()
            if (clean.isNotBlank()) {
                list.add(clean)
            }
        }
        list.distinct()
    }

    LaunchedEffect(allEndpointsToCheck, tokenInput) {
        endpointHealthMap = allEndpointsToCheck.associateWith { null as Boolean? }
        for (ep in allEndpointsToCheck) {
            val ok = withContext(Dispatchers.IO) {
                var conn: java.net.HttpURLConnection? = null
                try {
                    val cleanUrl =
                        if (!ep.startsWith("http://") && !ep.startsWith("https://")) {
                            "https://$ep"
                        } else {
                            ep
                        }.trimEnd('/')
                    val pingUrl = if (tokenInput.isNotBlank()) "$cleanUrl/bot${tokenInput.trim()}/getMe" else cleanUrl
                    val url = java.net.URL(pingUrl)
                    conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 3000
                        readTimeout = 3000
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                    }
                    val code = conn.responseCode
                    code > 0
                } catch (_: Exception) {
                    false
                } finally {
                    conn?.disconnect()
                }
            }
            endpointHealthMap = endpointHealthMap + (ep to ok)
        }
    }

    fun getStatusDotColor(status: Boolean?): Color =
        when (status) {
            true -> Color(0xFF4CAF50)
            false -> Color(0xFFF44336)
            null -> Color(0xFFFFC107)
        }


    val folderPickerLauncher =
        rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .OpenDocumentTree(),
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (_: Exception) {
                }

                val resolvedPath =
                    com.remotebot.core.common.util.StorageHelper
                        .resolvePathFromTreeUri(context, uri) ?: uri.path ?: uri.toString()
                storageDirInput = resolvedPath
                android.widget.Toast
                    .makeText(context, "Выбрана папка: $resolvedPath", android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }

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
        // Главный тумблер активности Telegram-бота
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (telegramBotEnabled) {
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
                            text = "🤖 Telegram-бот",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text =
                                if (telegramBotEnabled) {
                                    "Включен (по умолчанию): опрос и обработка команд активны"
                                } else {
                                    "Выключен: опрос сообщений и выполнение команд в Telegram приостановлены"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = telegramBotEnabled,
                        onCheckedChange = { isChecked ->
                            telegramBotEnabled = isChecked
                            statusMessage = null
                        },
                    )
                }
            }
        }

        // Токен
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "1. Telegram Bot Token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        statusMessage = null
                    },
                    placeholder = { Text("Bot Token от @BotFather (123456:ABC-DEF...)", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                )
            }
        }

        // Whitelist ID
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "2. Белый список (Telegram User ID)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = { allowedIdsList = allowedIdsList + "" },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Добавить ID",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (allowedIdsList.isEmpty()) {
                    Text(
                        text = "ℹ️ Список пуст (доступ открыт для всех). Нажмите «+» справа вверху, чтобы добавить доверенный Telegram ID.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Команды выполняются ТОЛЬКО для указанных ID (чужие получают отказ):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    allowedIdsList.forEachIndexed { index, idStr ->
                        RemovableInputRow(
                            value = idStr,
                            onValueChange = { newVal ->
                                val digitsOnly = newVal.filter { it.isDigit() }
                                val list = allowedIdsList.toMutableList()
                                list[index] = digitsOnly
                                allowedIdsList = list
                            },
                            placeholder = "User ID #${index + 1} (например 1067753626)",
                            onRemove = {
                                val list = allowedIdsList.toMutableList()
                                list.removeAt(index)
                                allowedIdsList = list
                            },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )

                        val parsedId = idStr.trim().toLongOrNull()
                        if (parsedId != null) {
                            val currentSub = userSubs[parsedId] ?: UserSubscription(userId = parsedId)
                            val isCommandsOnly =
                                !currentSub.receiveNotifications && !currentSub.receiveSms && !currentSub.receiveCalls && !currentSub.receiveSystem

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = currentSub.receiveNotifications,
                                    onClick = {
                                        userSubs =
                                            userSubs + (parsedId to currentSub.copy(receiveNotifications = !currentSub.receiveNotifications))
                                    },
                                    label = { Text("🔔 Push", fontSize = 11.sp) },
                                )
                                FilterChip(
                                    selected = currentSub.receiveSms,
                                    onClick = {
                                        userSubs = userSubs + (parsedId to currentSub.copy(receiveSms = !currentSub.receiveSms))
                                    },
                                    label = { Text("💬 SMS", fontSize = 11.sp) },
                                )
                                FilterChip(
                                    selected = currentSub.receiveCalls,
                                    onClick = {
                                        userSubs = userSubs + (parsedId to currentSub.copy(receiveCalls = !currentSub.receiveCalls))
                                    },
                                    label = { Text("📞 Звонки", fontSize = 11.sp) },
                                )
                                FilterChip(
                                    selected = currentSub.receiveSystem,
                                    onClick = {
                                        userSubs = userSubs + (parsedId to currentSub.copy(receiveSystem = !currentSub.receiveSystem))
                                    },
                                    label = { Text("⚙️ Сист", fontSize = 11.sp) },
                                )
                            }
                            if (isCommandsOnly) {
                                Text(
                                    text = "🕹 Режим: только управление (события не пересылаются)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Режим подключения
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "3. Режим приема команд", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                SegmentedSlider(
                    items =
                        listOf(
                            SliderSegmentItem(ConnectionMode.LONG_POLLING, "🔄 Long Polling"),
                            SliderSegmentItem(ConnectionMode.PUSH_WAKEUP, "🚀 Push Wakeup"),
                        ),
                    selectedValue = connectionMode,
                    onValueSelected = { connectionMode = it },
                )

                if (connectionMode == ConnectionMode.PUSH_WAKEUP) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Параметры Push Wakeup:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("• Шлюз (Gateway): ${config.pushGatewayUrl ?: "не настроен"}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "• Секретный ключ: ${if (config.pushSecretKey.isNullOrBlank()) "не задан" else "••••••••"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "• Firebase Project: ${config.fcmProjectId ?: "не инициализирован"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "💡 Управление google-services.json и параметрами шлюза выполняется в разделе 🔥 FCM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    val mainEndpoint = customApiRoot.trim().ifBlank { "https://api.telegram.org" }
                    val mainStatus = endpointHealthMap[mainEndpoint]
                    val mainDotColor = getStatusDotColor(mainStatus)

                    OutlinedTextField(
                        value = customApiRoot,
                        onValueChange = { customApiRoot = it },
                        label = { Text("Основной API Root / Зеркало (по умолч. пусто)") },
                        placeholder = { Text("https://my-worker.dev или пусто для официального") },
                        supportingText = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Статус: ", fontSize = 11.sp)
                                Box(
                                    modifier =
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(mainDotColor),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    when (mainStatus) {
                                        true -> "онлайн"
                                        false -> "недоступен"
                                        null -> "проверка..."
                                    },
                                    fontSize = 11.sp,
                                    color = mainDotColor,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("(пусто = api.telegram.org)", fontSize = 11.sp)
                            }
                        },
                        leadingIcon = {
                            Box(
                                modifier =
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(mainDotColor),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Резервные API эндпоинты (Failover)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = { apiEndpointsList = apiEndpointsList + "" }) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить эндпоинт", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Легенда статусов
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                            Text("Онлайн", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFC107)))
                            Text("Проверка", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF44336)))
                            Text("Недоступен", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (apiEndpointsList.isEmpty()) {
                        Text(
                            text = "ℹ️ Нет резервных адресов. При сбое основного узла будет использоваться api.telegram.org. Нажмите «+», чтобы добавить зеркала.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        apiEndpointsList.forEachIndexed { index, ep ->
                            val cleanEp = ep.trim()
                            val epStatus = if (cleanEp.isNotBlank()) endpointHealthMap[cleanEp] else null
                            val dotColor = if (cleanEp.isNotBlank()) getStatusDotColor(epStatus) else null

                            RemovableInputRow(
                                value = ep,
                                onValueChange = { newVal ->
                                    val list = apiEndpointsList.toMutableList()
                                    list[index] = newVal
                                    apiEndpointsList = list
                                },
                                placeholder = "https://mirror${index + 1}.domain.com",
                                onRemove = {
                                    val list = apiEndpointsList.toMutableList()
                                    list.removeAt(index)
                                    apiEndpointsList = list
                                },
                                statusDotColor = dotColor,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Стратегия выбора эндпоинта:",

                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            EndpointSelectionStrategy.values().forEach { st ->
                                val selected = endpointStrategy == st
                                FilterChip(
                                    selected = selected,
                                    onClick = { endpointStrategy = st },
                                    label = {
                                        Text(
                                            when (st) {
                                                EndpointSelectionStrategy.FAILOVER -> "Failover (По порядку)"
                                                EndpointSelectionStrategy.ROUND_ROBIN -> "Round-Robin"
                                                EndpointSelectionStrategy.LOWEST_LATENCY -> "Мин. пинг"
                                            },
                                            fontSize = 11.sp,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Папка сохранения файлов и медиа
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "4. Папка сохранения медиа и файлов",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Папка на устройстве, куда сохраняются записи экрана, фото с камеры, аудиозаписи и файлы:\nПо умолчанию: /sdcard/Download/RemoteBot",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("📁 Выбрать папку...")
                    }
                    if (storageDirInput.isNotBlank()) {
                        OutlinedButton(
                            onClick = { storageDirInput = "" },
                        ) {
                            Text("Сброс")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = storageDirInput,
                    onValueChange = { storageDirInput = it },
                    label = { Text("Путь к папке (выбранный или вручную)") },
                    placeholder = { Text("/storage/emulated/0/Download/RemoteBot") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        // Кнопка сохранения и проверки
        Button(
            onClick = {
                val rawToken = tokenInput.trim().trim('"', '\'')
                val cleanToken =
                    if (rawToken.startsWith("bot", ignoreCase = true)) {
                        rawToken.substring(3).trim()
                    } else {
                        rawToken
                    }
                if (cleanToken.isBlank()) {
                    Toast.makeText(context, "Введите токен бота", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                tokenInput = cleanToken

                val cleanApiRoot =
                    customApiRoot
                        .trim()
                        .trimEnd('/')
                        .let {
                            var str = if (it.endsWith("/bot", ignoreCase = true)) it.dropLast(4).trimEnd('/') else it
                            if (str.isNotBlank() && !str.startsWith("http://") && !str.startsWith("https://")) {
                                str = "https://$str"
                            }
                            str
                        }.ifBlank { null }
                customApiRoot = cleanApiRoot ?: ""

                val parsedAllowedIds = allowedIdsList.mapNotNull { it.trim().toLongOrNull() }.toSet()
                val cleanStorageDir = storageDirInput.trim().ifBlank { null }

                logger.d("BotConfig", "Начата проверка и сохранение настроек бота (режим=$connectionMode)")
                logger.t(
                    "BotConfig",
                    "Токен: ${cleanToken.take(
                        8,
                    )}... (длина ${cleanToken.length}), API Root: ${cleanApiRoot ?: "api.telegram.org"}, Whitelist: $parsedAllowedIds, Папка: $cleanStorageDir",
                )

                isChecking = true
                statusMessage = "Проверка связи с серверами Telegram..."
                isSuccess = false

                scope.launch {
                    configRepo.updateConfig {
                        it.copy(
                            telegramBotEnabled = telegramBotEnabled,
                            botToken = cleanToken,
                            allowedUserIds = parsedAllowedIds,
                            connectionMode = connectionMode,
                            customApiRoot = cleanApiRoot,
                            apiEndpoints = apiEndpointsList.map { it.trim() }.filter { it.isNotBlank() },
                            endpointSelectionStrategy = endpointStrategy,
                            storageDirectory = cleanStorageDir,
                            userSubscriptions = userSubs,
                        )
                    }

                    try {
                        logger.d("BotConfig", "Запрос getMe() к Telegram API...")
                        val botUser = withContext(Dispatchers.IO) { apiClient.getMe() }
                        logger.t(
                            "BotConfig",
                            "getMe() успешно ответил: id=${botUser.id}, username=@${botUser.username}, is_bot=${botUser.isBot}",
                        )

                        if (connectionMode == ConnectionMode.PUSH_WAKEUP) {
                            val gateway = config.pushGatewayUrl?.trim()?.trimEnd('/') ?: ""
                            val secret = config.pushSecretKey?.trim() ?: ""
                            if (gateway.isBlank()) {
                                isChecking = false
                                statusMessage = "⚠️ Push Gateway URL не задан. Укажите адрес воркера во вкладке 🔥 Push."
                                isSuccess = false
                                return@launch
                            }

                            val provider = config.pushProvider ?: com.remotebot.domain.model.PushProvider.FCM
                            var targetToken: String? = null
                            var tokenError: String? = null
                            var providerRoute = ""

                            when (provider) {
                                com.remotebot.domain.model.PushProvider.HMS -> {
                                    providerRoute = "hms"
                                    val hmsAppId = config.hmsAppId?.trim() ?: ""
                                    if (hmsAppId.isBlank()) {
                                        tokenError = "Huawei App ID не настроен в конфигурации"
                                    } else {
                                        logger.d("BotConfig", "Запрос HMS токена (App ID: $hmsAppId)...")
                                        val tokenRes =
                                            com.remotebot.core.push.DynamicHmsManager
                                                .getHmsToken(context, hmsAppId)
                                        tokenRes
                                            .onSuccess { targetToken = it }
                                            .onFailure { tokenError = it.localizedMessage ?: it.javaClass.simpleName }
                                    }
                                }
                                com.remotebot.domain.model.PushProvider.FCM -> {
                                    providerRoute = "fcm"
                                    logger.d("BotConfig", "Запрос FCM токена через DynamicFirebaseManager...")
                                    val tokenRes =
                                        com.remotebot.core.push.DynamicFirebaseManager
                                            .getFcmToken()
                                    tokenRes
                                        .onSuccess { targetToken = it }
                                        .onFailure { tokenError = it.localizedMessage ?: it.javaClass.simpleName }
                                }
                                com.remotebot.domain.model.PushProvider.HYBRID -> {
                                    logger.d("BotConfig", "Запрос токена для гибридного режима...")
                                    val fcmRes =
                                        com.remotebot.core.push.DynamicFirebaseManager
                                            .getFcmToken()
                                    if (fcmRes.isSuccess && !fcmRes.getOrNull().isNullOrBlank()) {
                                        targetToken = fcmRes.getOrNull()
                                        providerRoute = "fcm"
                                    } else {
                                        val hmsAppId = config.hmsAppId?.trim() ?: ""
                                        if (hmsAppId.isNotBlank()) {
                                            val hmsRes =
                                                com.remotebot.core.push.DynamicHmsManager
                                                    .getHmsToken(context, hmsAppId)
                                            hmsRes
                                                .onSuccess {
                                                    targetToken = it
                                                    providerRoute = "hms"
                                                }.onFailure {
                                                    tokenError = "FCM: ${fcmRes.exceptionOrNull()?.message}, HMS: ${it.message}"
                                                }
                                        } else {
                                            tokenError = fcmRes.exceptionOrNull()?.message ?: "FCM недоступен, а Huawei App ID не задан"
                                        }
                                    }
                                }
                            }

                            if (!targetToken.isNullOrBlank()) {
                                logger.t(
                                    "BotConfig",
                                    "Получен Push Token ($providerRoute): ${targetToken!!.take(16)}...${targetToken!!.takeLast(8)}",
                                )
                                val webhookUrl = "$gateway/tg/$providerRoute/$targetToken/$secret"
                                logger.d("BotConfig", "Регистрация Webhook в Telegram на шлюз $webhookUrl...")
                                withContext(Dispatchers.IO) {
                                    apiClient.setWebhook(webhookUrl)
                                }
                                logger.t("BotConfig", "Telegram API setWebhook() выполнен успешно")
                                statusMessage =
                                    "✅ Режим Push Wakeup активен!\nБот: @${botUser.username ?: "bot"}\nПровайдер: ${provider.name} ($providerRoute)\nToken: ${targetToken!!.take(
                                        12,
                                    )}...\nWebhook Telegram зарегистрирован."
                                logger.i("BotConfig", statusMessage!!.replace("\n", " | "))
                            } else {
                                statusMessage =
                                    "⚠️ Связь с ботом @${botUser.username} есть, но не получен Push Token (${tokenError ?: "Сервисы push не ответили"}).\nПроверьте настройки во вкладке 🔥 Push."
                                logger.w("BotConfig", statusMessage!!.replace("\n", " | "))
                            }
                        } else {
                            logger.d("BotConfig", "Сброс Webhook для режима Long Polling (deleteWebhook)...")
                            withContext(Dispatchers.IO) {
                                apiClient.deleteWebhook()
                            }
                            logger.t("BotConfig", "Telegram API deleteWebhook() выполнен успешно")
                            statusMessage =
                                "✅ Подключение успешно (Long Polling)!\nБот: @${botUser.username ?: "bot"} (${botUser.firstName})"
                            logger.i("BotConfig", statusMessage!!.replace("\n", " | "))
                        }

                        isSuccess = true
                        Toast.makeText(context, "Настройки сохранены!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        statusMessage = "❌ Ошибка: ${e.localizedMessage ?: "Сбой соединения"}"
                        isSuccess = false
                        logger.e("BotConfig", "Сбой сохранения настроек: ${e.localizedMessage}", e)
                        logger.t("BotConfig", "Полный стек-трейс ошибки:\n${e.stackTraceToString()}")
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
                Text("Проверка связи...")
            } else {
                Text("Сохранить настройки бота")
            }
        }

        StatusMessageText(
            message = statusMessage,
            isError = !isSuccess,
        )
    }
}

@Composable
fun BotSettingsTab(
    configRepo: BotConfigRepository,
    apiClient: TelegramApiClient,
    logger: AppLogger,
) = TelegramSettingsTab(configRepo, apiClient, logger)
