package com.remotebot.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.R
import com.remotebot.app.ui.components.RemovableInputRow
import com.remotebot.app.ui.components.SegmentedSlider
import com.remotebot.app.ui.components.SliderSegmentItem
import com.remotebot.app.ui.components.StatusMessageText
import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.BotLanguage
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
    var botLanguage by remember(config.botLanguage) { mutableStateOf(config.botLanguage) }
    var customApiRoot by remember(config.customApiRoot) { mutableStateOf(config.customApiRoot ?: "") }
    var apiEndpointsList by remember(config.apiEndpoints) { mutableStateOf(config.apiEndpoints) }
    var endpointStrategy by remember(config.endpointSelectionStrategy) { mutableStateOf(config.endpointSelectionStrategy) }
    var allowedIdsList by remember(config.allowedUserIds) {
        mutableStateOf(if (config.allowedUserIds.isEmpty()) emptyList<String>() else config.allowedUserIds.map { it.toString() })
    }
    var userSubs by remember(config.userSubscriptions) { mutableStateOf(config.userSubscriptions) }
    var storageDirInput by remember(config.storageDirectory) { mutableStateOf(config.storageDirectory ?: "") }

    var endpointHealthMap by remember { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }

    val allEndpointsToCheck =
        remember(customApiRoot, apiEndpointsList) {
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
            val ok =
                withContext(Dispatchers.IO) {
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
                        conn =
                            (url.openConnection() as java.net.HttpURLConnection).apply {
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
                    .makeText(
                        context,
                        context.getString(R.string.tg_folder_selected_toast, resolvedPath),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
            }
        }

    var isChecking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            // Main Telegram bot active toggle
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
                                text = stringResource(R.string.tg_bot_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text =
                                    if (telegramBotEnabled) {
                                        stringResource(R.string.tg_bot_enabled_desc)
                                    } else {
                                        stringResource(R.string.tg_bot_disabled_desc)
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
        }

        item {
            // Token
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.tg_token_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = {
                            tokenInput = it
                            statusMessage = null
                        },
                        placeholder = { Text(stringResource(R.string.tg_token_placeholder), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    )
                }
            }
        }

        item {
            // Whitelist ID
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.tg_whitelist_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = { allowedIdsList = allowedIdsList + "" },
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.tg_whitelist_add),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (allowedIdsList.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tg_whitelist_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.tg_whitelist_only_desc),
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
                                placeholder = stringResource(R.string.tg_user_id_placeholder, index + 1),
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
                                    !currentSub.receiveNotifications &&
                                        !currentSub.receiveSms &&
                                        !currentSub.receiveCalls &&
                                        !currentSub.receiveSystem

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
                                                userSubs +
                                                (parsedId to currentSub.copy(receiveNotifications = !currentSub.receiveNotifications))
                                        },
                                        label = { Text(stringResource(R.string.tg_sub_push), fontSize = 11.sp) },
                                    )
                                    FilterChip(
                                        selected = currentSub.receiveSms,
                                        onClick = {
                                            userSubs = userSubs + (parsedId to currentSub.copy(receiveSms = !currentSub.receiveSms))
                                        },
                                        label = { Text(stringResource(R.string.tg_sub_sms), fontSize = 11.sp) },
                                    )
                                    FilterChip(
                                        selected = currentSub.receiveCalls,
                                        onClick = {
                                            userSubs = userSubs + (parsedId to currentSub.copy(receiveCalls = !currentSub.receiveCalls))
                                        },
                                        label = { Text(stringResource(R.string.tg_sub_calls), fontSize = 11.sp) },
                                    )
                                    FilterChip(
                                        selected = currentSub.receiveSystem,
                                        onClick = {
                                            userSubs = userSubs + (parsedId to currentSub.copy(receiveSystem = !currentSub.receiveSystem))
                                        },
                                        label = { Text(stringResource(R.string.tg_sub_system), fontSize = 11.sp) },
                                    )
                                }
                                if (isCommandsOnly) {
                                    Text(
                                        text = stringResource(R.string.tg_sub_control_only),
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
        }

        item {
            // Connection mode
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.tg_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
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
                                Text(
                                    stringResource(R.string.tg_push_params_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    stringResource(
                                        R.string.tg_set_gateway,
                                        config.pushGatewayUrl ?: stringResource(R.string.tg_set_not_configured),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    stringResource(
                                        R.string.tg_set_secret_key,
                                        if (config.pushSecretKey.isNullOrBlank()) stringResource(R.string.tg_set_not_set) else "••••••••",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val providerTitle =
                                    when (config.pushProvider) {
                                        com.remotebot.domain.model.PushProvider.FCM -> "🔥 Google FCM"
                                        com.remotebot.domain.model.PushProvider.HMS -> "🦅 Huawei HMS"
                                        com.remotebot.domain.model.PushProvider.HYBRID -> "🔀 Hybrid (FCM + HMS)"
                                    }
                                Text(
                                    stringResource(R.string.tg_set_push_provider, providerTitle),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (config.pushProvider == com.remotebot.domain.model.PushProvider.FCM ||
                                    config.pushProvider == com.remotebot.domain.model.PushProvider.HYBRID
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.tg_set_firebase_project,
                                            config.fcmProjectId ?: stringResource(R.string.tg_set_not_initialized),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (config.pushProvider == com.remotebot.domain.model.PushProvider.HMS ||
                                    config.pushProvider == com.remotebot.domain.model.PushProvider.HYBRID
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.tg_set_hms_appid,
                                            config.hmsAppId ?: stringResource(R.string.tg_set_not_initialized),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.tg_push_hint),
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
                            label = { Text(stringResource(R.string.tg_api_root_label)) },
                            placeholder = { Text(stringResource(R.string.tg_api_root_placeholder)) },
                            supportingText = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.tg_status_label), fontSize = 11.sp)
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
                                            true -> stringResource(R.string.tg_status_online)
                                            false -> stringResource(R.string.tg_status_offline)
                                            null -> stringResource(R.string.tg_status_checking)
                                        },
                                        fontSize = 11.sp,
                                        color = mainDotColor,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.tg_status_default_hint), fontSize = 11.sp)
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
                                text = stringResource(R.string.tg_failover_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(onClick = { apiEndpointsList = apiEndpointsList + "" }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.tg_failover_add),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Status legend
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                Text(
                                    stringResource(R.string.tg_legend_online),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFC107)))
                                Text(
                                    stringResource(R.string.tg_legend_checking),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF44336)))
                                Text(
                                    stringResource(R.string.tg_legend_offline),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (apiEndpointsList.isEmpty()) {
                            Text(
                                text = stringResource(R.string.tg_failover_empty),
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
                                    placeholder = stringResource(R.string.tg_mirror_placeholder, index + 1),
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
                                text = stringResource(R.string.tg_strategy_title),
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
                                                    EndpointSelectionStrategy.FAILOVER -> stringResource(R.string.tg_strategy_failover)
                                                    EndpointSelectionStrategy.ROUND_ROBIN ->
                                                        stringResource(
                                                            R.string.tg_strategy_round_robin,
                                                        )
                                                    EndpointSelectionStrategy.LOWEST_LATENCY -> stringResource(R.string.tg_strategy_latency)
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
        }

        item {
            // 3.5 Bot Language
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.tg_lang_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when (botLanguage) {
                                    BotLanguage.AUTO -> stringResource(R.string.tg_set_lang_auto)
                                    BotLanguage.EN -> stringResource(R.string.tg_set_lang_en)
                                    BotLanguage.RU -> stringResource(R.string.tg_set_lang_ru)
                                },
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tg_set_lang_auto)) },
                                onClick = {
                                    botLanguage = BotLanguage.AUTO
                                    expanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tg_set_lang_en)) },
                                onClick = {
                                    botLanguage = BotLanguage.EN
                                    expanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tg_set_lang_ru)) },
                                onClick = {
                                    botLanguage = BotLanguage.RU
                                    expanded = false
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.tg_lang_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            // 4. Directory for saving files and media
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.tg_storage_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.tg_storage_desc),
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
                            Text(stringResource(R.string.tg_btn_choose_folder))
                        }
                        if (storageDirInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = { storageDirInput = "" },
                            ) {
                                Text(stringResource(R.string.tg_btn_reset))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = storageDirInput,
                        onValueChange = { storageDirInput = it },
                        label = { Text(stringResource(R.string.tg_folder_path_label)) },
                        placeholder = { Text("/storage/emulated/0/Download/RemoteBot") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }

        item {
            // Save and verify button
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
                        Toast.makeText(context, context.getString(R.string.tg_err_no_token), Toast.LENGTH_SHORT).show()
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

                    logger.d("BotConfig", "Started validating and saving the bot settings (mode=$connectionMode)")
                    logger.t(
                        "BotConfig",
                        "Token: ${cleanToken.take(
                            8,
                        )}... (length ${cleanToken.length}), API Root: ${cleanApiRoot ?: "api.telegram.org"}, Whitelist: $parsedAllowedIds, Folder: $cleanStorageDir",
                    )

                    isChecking = true
                    statusMessage = context.getString(R.string.tg_checking_connection)
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
                                botLanguage = botLanguage,
                            )
                        }

                        try {
                            logger.d("BotConfig", "Requesting getMe() from the Telegram API...")
                            val botUser = withContext(Dispatchers.IO) { apiClient.getMe() }
                            logger.t(
                                "BotConfig",
                                "getMe() responded successfully: id=${botUser.id}, username=@${botUser.username}, is_bot=${botUser.isBot}",
                            )

                            if (connectionMode == ConnectionMode.PUSH_WAKEUP) {
                                val gateway = config.pushGatewayUrl?.trim()?.trimEnd('/') ?: ""
                                val secret = config.pushSecretKey?.trim() ?: ""
                                if (gateway.isBlank()) {
                                    isChecking = false
                                    statusMessage = context.getString(R.string.tg_gateway_url_missing)
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
                                            tokenError = "Huawei App ID is not configured"
                                        } else {
                                            logger.d("BotConfig", "Requesting an HMS token (App ID: $hmsAppId)...")
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
                                        logger.d("BotConfig", "Requesting an FCM token via DynamicFirebaseManager...")
                                        val tokenRes =
                                            com.remotebot.core.push.DynamicFirebaseManager
                                                .getFcmToken()
                                        tokenRes
                                            .onSuccess { targetToken = it }
                                            .onFailure { tokenError = it.localizedMessage ?: it.javaClass.simpleName }
                                    }
                                    com.remotebot.domain.model.PushProvider.HYBRID -> {
                                        logger.d("BotConfig", "Requesting tokens for hybrid mode...")
                                        val fcmRes =
                                            com.remotebot.core.push.DynamicFirebaseManager
                                                .getFcmToken()
                                        val fcmT = fcmRes.getOrNull()
                                        val hmsAppId = config.hmsAppId?.trim() ?: ""
                                        val hmsT = if (hmsAppId.isNotBlank()) {
                                            com.remotebot.core.push.DynamicHmsManager
                                                .getHmsToken(context, hmsAppId).getOrNull()
                                        } else null

                                        if (!fcmT.isNullOrBlank() && !hmsT.isNullOrBlank()) {
                                            targetToken = "$fcmT+$hmsT"
                                            providerRoute = "hybrid"
                                        } else if (!fcmT.isNullOrBlank()) {
                                            targetToken = fcmT
                                            providerRoute = "fcm"
                                        } else if (!hmsT.isNullOrBlank()) {
                                            targetToken = hmsT
                                            providerRoute = "hms"
                                        } else {
                                            tokenError = "FCM: ${fcmRes.exceptionOrNull()?.message}, HMS app_id=$hmsAppId"
                                        }
                                    }
                                }

                                if (!targetToken.isNullOrBlank()) {
                                    logger.t(
                                        "BotConfig",
                                        "Push Token received ($providerRoute): ${targetToken!!.take(16)}...${targetToken!!.takeLast(8)}",
                                    )
                                    val webhookUrl = "$gateway/tg/$providerRoute/$targetToken/$secret"
                                    logger.d("BotConfig", "Registering the Telegram webhook on gateway $webhookUrl...")
                                    withContext(Dispatchers.IO) {
                                        apiClient.setWebhook(webhookUrl)
                                    }
                                    logger.t("BotConfig", "Telegram API setWebhook() completed successfully")
                                    statusMessage =
                                        context.getString(
                                            R.string.tg_push_active_status,
                                            botUser.username ?: "bot",
                                            provider.name,
                                            providerRoute,
                                            targetToken!!.take(12),
                                        )
                                    logger.i("BotConfig", statusMessage!!.replace("\n", " | "))
                                } else {
                                    statusMessage =
                                        context.getString(
                                            R.string.tg_push_no_token_warning,
                                            botUser.username ?: "bot",
                                            tokenError ?: "No push token",
                                        )
                                    logger.w("BotConfig", statusMessage!!.replace("\n", " | "))
                                }
                            } else {
                                logger.d("BotConfig", "Resetting the webhook for Long Polling (deleteWebhook)...")
                                withContext(Dispatchers.IO) {
                                    apiClient.deleteWebhook()
                                }
                                logger.t("BotConfig", "Telegram API deleteWebhook() completed successfully")
                                statusMessage =
                                    context.getString(
                                        R.string.tg_long_polling_active_status,
                                        botUser.username ?: "bot",
                                        botUser.firstName,
                                    )
                                logger.i("BotConfig", statusMessage!!.replace("\n", " | "))
                            }

                            isSuccess = true
                            Toast.makeText(context, context.getString(R.string.tg_saved_toast), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            statusMessage = context.getString(R.string.tg_connection_error_status, e.localizedMessage ?: "Connection error")
                            isSuccess = false
                            logger.e("BotConfig", "Settings save failure: ${e.localizedMessage}", e)
                            logger.t("BotConfig", "Full error stack trace:\n${e.stackTraceToString()}")
                        } finally {
                            isChecking = false
                        }
                    }
                },
                enabled = !isChecking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tg_btn_checking))
                } else {
                    Text(stringResource(R.string.tg_btn_save_bot))
                }
            }
        }

        item {
            StatusMessageText(
                message = statusMessage,
                isError = !isSuccess,
            )
        }
    }
}

@Composable
fun BotSettingsTab(
    configRepo: BotConfigRepository,
    apiClient: TelegramApiClient,
    logger: AppLogger,
) = TelegramSettingsTab(configRepo, apiClient, logger)
