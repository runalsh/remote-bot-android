package com.remotebot.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.remotebot.app.service.RemoteBotService
import com.remotebot.app.ui.components.AppCard
import com.remotebot.app.ui.components.AppStatusBadge
import com.remotebot.app.ui.components.BadgeStatus
import com.remotebot.app.ui.screens.permissions.PermissionsChecker
import com.remotebot.app.ui.theme.AppColors
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.BotLanguage
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.BotConfigRepository
import androidx.compose.ui.res.stringResource
import com.remotebot.app.R
import com.remotebot.app.util.AppLocaleManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch

@Composable
fun StartTab(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    logger: AppLogger,
    rootProvider: RootStatusProvider,
    configRepo: BotConfigRepository,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val isServiceRunning by RemoteBotService.isServiceRunning.collectAsState()
    val config by configRepo.config.collectAsState()
    var botLanguage by remember(config.botLanguage) { mutableStateOf(config.botLanguage) }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isIgnoringBattery by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    val rootStatusState by rootProvider.isRootGranted.collectAsState()
    val isRootGranted = rootStatusState == true || Shell.isAppGrantedRoot() == true
    var isCheckingRoot by remember { mutableStateOf(false) }

    var refreshTrigger by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    refreshTrigger++
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val (grantedCount, totalCount) =
        remember(refreshTrigger, isRootGranted) {
            PermissionsChecker.getGrantedAndTotalCount(context, isRootGranted)
        }

    LaunchedEffect(Unit) {
        rootProvider.checkRoot(forceRefresh = false)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card 1: Background service
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.start_foreground_service_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.start_service_status) + " ", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(6.dp))
                AppStatusBadge(
                    text = if (isServiceRunning) stringResource(R.string.start_service_running) else stringResource(R.string.start_service_stopped),
                    status = if (isServiceRunning) BadgeStatus.SUCCESS else BadgeStatus.NEUTRAL,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        logger.i("Service", "Manual start of the background service")
                        onStartService()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.success),
                ) {
                    Text(stringResource(R.string.start_service_start_button))
                }
                Button(
                    onClick = {
                        logger.i("Service", "Manual stop of the background service")
                        onStopService()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.start_service_stop_button))
                }
            }
        }

        // Card 1.5: Language selector
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.start_language_title),
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
                            BotLanguage.AUTO -> stringResource(R.string.lang_auto)
                            BotLanguage.EN -> stringResource(R.string.lang_en)
                            BotLanguage.RU -> stringResource(R.string.lang_ru)
                        }
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lang_auto)) },
                        onClick = {
                            botLanguage = BotLanguage.AUTO
                            expanded = false
                            scope.launch {
                                configRepo.updateConfig { it.copy(botLanguage = BotLanguage.AUTO) }
                                AppLocaleManager.applyLocale(context, BotLanguage.AUTO)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lang_en)) },
                        onClick = {
                            botLanguage = BotLanguage.EN
                            expanded = false
                            scope.launch {
                                configRepo.updateConfig { it.copy(botLanguage = BotLanguage.EN) }
                                AppLocaleManager.applyLocale(context, BotLanguage.EN)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lang_ru)) },
                        onClick = {
                            botLanguage = BotLanguage.RU
                            expanded = false
                            scope.launch {
                                configRepo.updateConfig { it.copy(botLanguage = BotLanguage.RU) }
                                AppLocaleManager.applyLocale(context, BotLanguage.RU)
                            }
                        }
                    )
                }
            }
        }

        // Card 2: Privileges and permissions gauge
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (grantedCount == totalCount) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.start_permissions_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text =
                        if (grantedCount == totalCount) {
                            "✅ " + stringResource(R.string.start_permissions_status, grantedCount, totalCount)
                        } else {
                            stringResource(R.string.start_permissions_status, grantedCount, totalCount)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LinearProgressIndicator(
                    progress = { if (totalCount > 0) grantedCount.toFloat() / totalCount.toFloat() else 0f },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                OutlinedButton(
                    onClick = onNavigateToPermissions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.start_permissions_button))
                }
            }
        }

        // Card 3: Doze Mode and battery optimization
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.start_battery_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (isIgnoringBattery) {
                Text(stringResource(R.string.start_battery_ok), color = AppColors.success, fontWeight = FontWeight.Medium)
            } else {
                Text(stringResource(R.string.start_battery_warning), color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        logger.i("Start", "Requesting Doze Mode battery optimization exclusion")
                        try {
                            val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.start_battery_request))
                }
            }
        }

        // Card 4: Root privileges
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.start_root_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.start_root_status) + " ", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(6.dp))
                AppStatusBadge(
                    text = if (isRootGranted) stringResource(R.string.start_root_granted) else stringResource(R.string.start_root_denied),
                    status = if (isRootGranted) BadgeStatus.SUCCESS else BadgeStatus.ERROR,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    isCheckingRoot = true
                    logger.d("Root", "Starting Root check via RootStatusProvider...")
                    scope.launch {
                        val granted = rootProvider.checkRoot(forceRefresh = true)
                        isCheckingRoot = false
                        if (granted) {
                            logger.i("Root", "Superuser (Root) granted successfully")
                            Toast.makeText(context, "Root access granted!", Toast.LENGTH_SHORT).show()
                        } else {
                            logger.w("Root", "Root not granted")
                            Toast.makeText(context, "Root was not granted.", Toast.LENGTH_SHORT).show()
                        }
                        refreshTrigger++
                    }
                },
                enabled = !isCheckingRoot,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isCheckingRoot) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.start_root_checking))
                } else {
                    Text(stringResource(R.string.start_root_check_button))
                }
            }
        }
    }
}
