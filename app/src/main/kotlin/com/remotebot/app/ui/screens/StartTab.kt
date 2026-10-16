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
import com.remotebot.domain.provider.RootStatusProvider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch

@Composable
fun StartTab(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    logger: AppLogger,
    rootProvider: RootStatusProvider,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val isServiceRunning by RemoteBotService.isServiceRunning.collectAsState()

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
        // Карточка 1: Фоновая служба
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "1. Фоновая служба (Foreground Service)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Статус службы: ", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(6.dp))
                AppStatusBadge(
                    text = if (isServiceRunning) "🟢 АКТИВНА" else "⚪ ОСТАНОВЛЕНА",
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
                        logger.i("Service", "Ручной запуск фоновой службы")
                        onStartService()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.success),
                ) {
                    Text("▶ Старт")
                }
                Button(
                    onClick = {
                        logger.i("Service", "Ручная остановка фоновой службы")
                        onStopService()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("⏹ Стоп")
                }
            }
        }

        // Карточка 2: Шкала привилегий и разрешений
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
                        text = "2. Шкала привилегий",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$grantedCount из $totalCount",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    text =
                        if (grantedCount == totalCount) {
                            "✅ Все 20 системных разрешений и привилегий предоставлены"
                        } else {
                            "Выдано $grantedCount из $totalCount системных разрешений приложения"
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
                    Text("🛡️ Открыть раздел «Права»")
                }
            }
        }

        // Карточка 3: Doze Mode и оптимизация батареи
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "3. Энергосбережение (Doze Mode)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (isIgnoringBattery) {
                Text("✅ Оптимизация отключена: ОС не будет усыплять бота", color = AppColors.success, fontWeight = FontWeight.Medium)
            } else {
                Text("⚠️ Оптимизация батареи активна: ОС может останавливать опрос в фоне", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        logger.i("Start", "Запрос отключения оптимизации батареи Doze Mode")
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
                    Text("Запросить исключение из энергосбережения")
                }
            }
        }

        // Карточка 4: Root привилегии
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "4. Привилегии Root (Superuser)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Статус Root: ", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(6.dp))
                AppStatusBadge(
                    text = if (isRootGranted) "✅ Предоставлено" else "❌ Отказано",
                    status = if (isRootGranted) BadgeStatus.SUCCESS else BadgeStatus.ERROR,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    isCheckingRoot = true
                    logger.d("Root", "Запуск проверки Root через RootStatusProvider...")
                    scope.launch {
                        val granted = rootProvider.checkRoot(forceRefresh = true)
                        isCheckingRoot = false
                        if (granted) {
                            logger.i("Root", "Права Superuser (Root) успешно подтверждены")
                            Toast.makeText(context, "Root-доступ предоставлен!", Toast.LENGTH_SHORT).show()
                        } else {
                            logger.w("Root", "Root права не предоставлены")
                            Toast.makeText(context, "Root не предоставлен.", Toast.LENGTH_SHORT).show()
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
                    Text("Запрос su сессии...")
                } else {
                    Text("Запросить Root доступ")
                }
            }
        }
    }
}
