package com.remotebot.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.ui.screens.push.FcmGoogleSection
import com.remotebot.app.ui.screens.push.HmsHuaweiSection
import com.remotebot.app.ui.screens.push.PushGatewaySection
import com.remotebot.app.ui.screens.push.PushServersPoolCard
import com.remotebot.app.ui.theme.AppColors
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.push.DynamicFirebaseManager
import com.remotebot.core.push.DynamicHmsManager
import com.remotebot.domain.model.ConnectionMode
import com.remotebot.domain.model.PushProvider
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.launch

@Composable
fun FcmSettingsTab(
    configRepo: BotConfigRepository,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val config by configRepo.config.collectAsState()

    var pushProvider by remember(config.pushProvider) {
        mutableStateOf(config.pushProvider)
    }

    // Google FCM states
    var googleJsonText by remember(config.googleServicesJson) {
        mutableStateOf(config.googleServicesJson ?: "")
    }
    var fcmToken by remember { mutableStateOf<String?>(null) }
    var fcmStatusMessage by remember { mutableStateOf<String?>(null) }
    var isFcmSuccess by remember { mutableStateOf(false) }
    var isFcmProcessing by remember { mutableStateOf(false) }

    // Huawei HMS states
    var hmsJsonText by remember(config.agconnectServicesJson) {
        mutableStateOf(config.agconnectServicesJson ?: "")
    }
    var hmsToken by remember { mutableStateOf<String?>(null) }
    var hmsStatusMessage by remember { mutableStateOf<String?>(null) }
    var isHmsSuccess by remember { mutableStateOf(false) }
    var isHmsProcessing by remember { mutableStateOf(false) }

    // Push Gateway states
    var pushGatewayUrl by remember(config.pushGatewayUrl) {
        mutableStateOf(config.pushGatewayUrl ?: "")
    }
    var pushSecretKey by remember(config.pushSecretKey) {
        mutableStateOf(config.pushSecretKey ?: "")
    }
    var connectionMode by remember(config.connectionMode) {
        mutableStateOf(config.connectionMode)
    }

    LaunchedEffect(Unit) {
        if (DynamicFirebaseManager.isInitialized()) {
            val tokenResult = DynamicFirebaseManager.getFcmToken()
            fcmToken = tokenResult.getOrNull()
        }
        if (!config.hmsAppId.isNullOrBlank()) {
            val hmsResult = DynamicHmsManager.getHmsToken(context, config.hmsAppId!!)
            hmsToken = hmsResult.getOrNull()
        }
    }

    // Launcher for google-services.json
    val googlePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val content = stream.bufferedReader().readText()
                        googleJsonText = content
                        logger.i("FCM", "Загружен файл google-services.json (${content.length} симв.)")
                        Toast.makeText(context, "Файл google-services.json загружен", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    logger.e("FCM", "Ошибка чтения google-services.json: ${e.message}", e)
                    Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // Launcher for agconnect-services.json
    val hmsPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val content = stream.bufferedReader().readText()
                        hmsJsonText = content
                        logger.i("HMS", "Загружен файл agconnect-services.json (${content.length} симв.)")
                        Toast.makeText(context, "Файл agconnect-services.json загружен", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    logger.e("HMS", "Ошибка чтения agconnect-services.json: ${e.message}", e)
                    Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // TOP: Active status and Push Wakeup mode toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (connectionMode == ConnectionMode.PUSH_WAKEUP) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(10.dp)
                                        .background(
                                            if (connectionMode ==
                                                ConnectionMode.PUSH_WAKEUP
                                            ) {
                                                AppColors.success
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            androidx.compose.foundation.shape.CircleShape,
                                        ),
                            )
                            Text(
                                text = "Режим Push Wakeup (Пробуждение)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text =
                                if (connectionMode == ConnectionMode.PUSH_WAKEUP) {
                                    "🟢 АКТИВЕН: Бот спит и просыпается по входящим пушам (экономия 99% батареи)"
                                } else {
                                    "⚪ ВЫКЛЮЧЕН: Активен постоянный фоновый Long Polling в Telegram"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (connectionMode == ConnectionMode.PUSH_WAKEUP) {
                                    AppColors.success
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }

                    Switch(
                        checked = connectionMode == ConnectionMode.PUSH_WAKEUP,
                        onCheckedChange = { isChecked ->
                            val target = if (isChecked) ConnectionMode.PUSH_WAKEUP else ConnectionMode.LONG_POLLING
                            connectionMode = target
                            scope.launch {
                                configRepo.updateConfig { it.copy(connectionMode = target) }
                                logger.i("Push", "Переключен режим доставки: $target")
                            }
                        },
                    )
                }
            }
        }

        // Push provider selection card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "🔔 Провайдер Push-уведомлений",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Выберите активную службу доставки пушей. Можно использовать FCM, HMS или оба канала одновременно:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PushProviderSegmentedSlider(
                    selectedProvider = pushProvider,
                    onProviderSelected = { selected ->
                        pushProvider = selected
                        scope.launch { configRepo.updateConfig { it.copy(pushProvider = selected) } }
                    },
                )
            }
        }

        // Google FCM block (displayed for FCM or HYBRID)
        if (pushProvider == PushProvider.FCM || pushProvider == PushProvider.HYBRID) {
            FcmGoogleSection(
                context = context,
                scope = scope,
                configRepo = configRepo,
                googleJsonText = googleJsonText,
                onGoogleJsonTextChange = { googleJsonText = it },
                googlePickerLauncher = googlePickerLauncher,
                fcmToken = fcmToken,
                onFcmTokenChange = { fcmToken = it },
                fcmStatusMessage = fcmStatusMessage,
                onFcmStatusMessageChange = { fcmStatusMessage = it },
                isFcmSuccess = isFcmSuccess,
                onIsFcmSuccessChange = { isFcmSuccess = it },
                isFcmProcessing = isFcmProcessing,
                onIsFcmProcessingChange = { isFcmProcessing = it },
            )
        }

        // Huawei HMS block (displayed for HMS or HYBRID)
        if (pushProvider == PushProvider.HMS || pushProvider == PushProvider.HYBRID) {
            HmsHuaweiSection(
                context = context,
                scope = scope,
                configRepo = configRepo,
                hmsJsonText = hmsJsonText,
                onHmsJsonTextChange = { hmsJsonText = it },
                hmsPickerLauncher = hmsPickerLauncher,
                hmsToken = hmsToken,
                onHmsTokenChange = { hmsToken = it },
                hmsStatusMessage = hmsStatusMessage,
                onHmsStatusMessageChange = { hmsStatusMessage = it },
                isHmsSuccess = isHmsSuccess,
                onIsHmsSuccessChange = { isHmsSuccess = it },
                isHmsProcessing = isHmsProcessing,
                onIsHmsProcessingChange = { isHmsProcessing = it },
            )
        }

        // Single gateway (for quick connection of one address)
        PushGatewaySection(
            context = context,
            scope = scope,
            configRepo = configRepo,
            logger = logger,
            pushGatewayUrl = pushGatewayUrl,
            onPushGatewayUrlChange = { pushGatewayUrl = it },
            pushSecretKey = pushSecretKey,
            onPushSecretKeyChange = { pushSecretKey = it },
            connectionMode = connectionMode,
            onConnectionModeChange = { connectionMode = it },
        )

        // Multi-server push server pool (Failover, Round-Robin, Lowest Latency)
        PushServersPoolCard(
            context = context,
            scope = scope,
            config = config,
            configRepo = configRepo,
            logger = logger,
        )
    }
}

@Composable
fun PushProviderSegmentedSlider(
    selectedProvider: PushProvider,
    onProviderSelected: (PushProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items =
        listOf(
            PushProvider.FCM to "🔥 Google FCM",
            PushProvider.HMS to "🦅 Huawei HMS",
            PushProvider.HYBRID to "⚡ Гибридный",
        )
    val selectedIndex =
        when (selectedProvider) {
            PushProvider.FCM -> 0
            PushProvider.HMS -> 1
            PushProvider.HYBRID -> 2
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(3.dp),
    ) {
        val segmentWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "push_slider_indicator",
        )

        // Floating slider thumb
        Box(
            modifier =
                Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(9.dp))
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary),
        )

        // Selection options
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, (provider, title) ->
                val isSelected = index == selectedIndex
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onProviderSelected(provider) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
