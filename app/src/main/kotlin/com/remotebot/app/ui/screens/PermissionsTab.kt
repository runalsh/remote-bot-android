package com.remotebot.app.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.remotebot.app.ui.screens.permissions.ConfidentialPermissionsSection
import com.remotebot.app.ui.screens.permissions.CriticalPermissionsSection
import com.remotebot.app.ui.screens.permissions.StandardPermissionsSection
import com.remotebot.app.ui.theme.AppColors
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.provider.RootStatusProvider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PermissionsTab(
    logger: AppLogger,
    rootProvider: RootStatusProvider,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var refreshTrigger by remember { mutableStateOf(0) }
    var showManualPermissionsDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshTrigger++
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allStandardPermissions =
        remember {
            val list =
                mutableListOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_CONTACTS,
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
                list.add(Manifest.permission.READ_MEDIA_IMAGES)
                list.add(Manifest.permission.READ_MEDIA_VIDEO)
                list.add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                list.add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                list.add(Manifest.permission.BLUETOOTH)
            }
            list.toTypedArray()
        }

    // Полный список всех возможных runtime-разрешений (стандартные + SMS + звонки)
    val allRuntimePermissions =
        remember {
            val list =
                mutableListOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS,
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
                list.add(Manifest.permission.READ_MEDIA_IMAGES)
                list.add(Manifest.permission.READ_MEDIA_VIDEO)
                list.add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                    list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                list.add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                list.add(Manifest.permission.BLUETOOTH)
            }
            list.toTypedArray()
        }

    val masterAllLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val grantedCount = results.values.count { it }
            logger.i("Permissions", "Результат общего запроса всех прав: выдано $grantedCount из ${results.size}")
            refreshTrigger++
            showManualPermissionsDialog = true
            Toast
                .makeText(
                    context,
                    "Из-за ограничений Android остальные разрешения необходимо выдать по одному",
                    Toast.LENGTH_LONG,
                ).show()
        }

    val batchLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val grantedCount = results.values.count { it }
            logger.i("Permissions", "Результат пакетного запроса стандартных прав: выдано $grantedCount из ${results.size}")
            refreshTrigger++
            showManualPermissionsDialog = true
            Toast
                .makeText(
                    context,
                    "Из-за ограничений Android остальные разрешения необходимо выдать по одному",
                    Toast.LENGTH_LONG,
                ).show()
        }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTrigger++ }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTrigger++ }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshTrigger++ }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshTrigger++ }
    val callsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshTrigger++ }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTrigger++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTrigger++ }
    val photosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshTrigger++ }
    val audioMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshTrigger++ }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTrigger++ }

    // 1. Системный доступ (Root и Установка)
    val rootStatusState by rootProvider.isRootGranted.collectAsState()
    val isRootGranted = rootStatusState == true || Shell.isAppGrantedRoot() == true

    LaunchedEffect(refreshTrigger) {
        rootProvider.checkRoot(forceRefresh = true)
    }

    val isInstallAppsGranted =
        remember(refreshTrigger) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else {
                true
            }
        }

    val isManageStorageGranted =
        remember(refreshTrigger) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }

    val isWriteSettingsGranted =
        remember(refreshTrigger) {
            try {
                Settings.System.canWrite(context)
            } catch (_: Exception) {
                false
            }
        }

    val isOverlayGranted =
        remember(refreshTrigger) {
            try {
                Settings.canDrawOverlays(context)
            } catch (_: Exception) {
                false
            }
        }

    val isAccessibilityGranted =
        remember(refreshTrigger) {
            var isEnabled = false
            try {
                val enabledServices =
                    Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    ) ?: ""
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServices)
                val expectedSimpleName = com.remotebot.app.service.RemoteBotAccessibilityService::class.java.simpleName
                while (colonSplitter.hasNext()) {
                    val componentName = colonSplitter.next()
                    if (componentName.contains(context.packageName) && componentName.contains(expectedSimpleName)) {
                        isEnabled = true
                        break
                    }
                }
            } catch (_: Exception) {
            }
            isEnabled
        }

    // 2. Конфиденциальные данные и связь
    val isNotifListenerGranted =
        remember(refreshTrigger) {
            val inCompat = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            if (inCompat) return@remember true
            try {
                val enabledListeners =
                    Settings.Secure.getString(
                        context.contentResolver,
                        "enabled_notification_listeners",
                    ) ?: ""
                enabledListeners.split(":").any { it.startsWith("${context.packageName}/") }
            } catch (_: Exception) {
                false
            }
        }

    val isSmsGranted =
        remember(refreshTrigger) {
            val sendGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            val recvGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            sendGranted && recvGranted
        }

    val isCallsGranted =
        remember(refreshTrigger) {
            val callGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            val readCallLog =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
            val readPhoneState =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            callGranted && readCallLog && readPhoneState
        }

    val isDndGranted =
        remember(refreshTrigger) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.isNotificationPolicyAccessGranted == true
        }

    // 3. Мультимедиа и сенсоры (Камера, Звук, Локация, Сеть)
    val isCameraGranted =
        remember(refreshTrigger) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }

    val isMicGranted =
        remember(refreshTrigger) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }

    val isLocationGranted =
        remember(refreshTrigger) {
            val fine =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            fine || coarse
        }

    val isContactsGranted =
        remember(refreshTrigger) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        }

    val isBatteryIgnored =
        remember(refreshTrigger) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        }

    val isPostNotifGranted =
        remember(refreshTrigger) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }

    val isPhotosGranted =
        remember(refreshTrigger) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val img =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                val vid =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                img || vid
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }

    val isAudioMediaGranted =
        remember(refreshTrigger) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }

    val isBluetoothGranted =
        remember(refreshTrigger) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            }
        }

    val isUnrestrictedDataGranted =
        remember(refreshTrigger) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uid = context.applicationInfo.uid
                if (isRootGranted) {
                    try {
                        val res = Shell.cmd("cmd netpolicy list restrict-background-whitelist").exec()
                        if (res.isSuccess && res.out.any { line -> line.trim() == uid.toString() || line.contains(uid.toString()) }) {
                            return@remember true
                        }
                    } catch (_: Exception) {
                    }
                }
                try {
                    val netPolicyManagerClass = Class.forName("android.net.NetworkPolicyManager")
                    val getServiceMethod = netPolicyManagerClass.getMethod("from", Context::class.java)
                    val netPolicyManager = getServiceMethod.invoke(null, context)
                    val isUidWhitelistedMethod =
                        netPolicyManagerClass.getMethod(
                            "isUidRestrictedOnMeteredNetworks",
                            Int::class.javaPrimitiveType,
                        )
                    val isRestricted = isUidWhitelistedMethod.invoke(netPolicyManager, uid) as? Boolean
                    if (isRestricted == false) {
                        return@remember true
                    }
                } catch (_: Exception) {
                }
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val status = cm?.restrictBackgroundStatus ?: ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
                    status != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                } catch (_: Exception) {
                    true
                }
            } else {
                true
            }
        }

    val permissionItems =
        listOf(
            isRootGranted,
            isInstallAppsGranted,
            isManageStorageGranted,
            isAccessibilityGranted,
            isWriteSettingsGranted,
            isOverlayGranted,
            isNotifListenerGranted,
            isSmsGranted,
            isCallsGranted,
            isDndGranted,
            isCameraGranted,
            isMicGranted,
            isLocationGranted,
            isContactsGranted,
            isBatteryIgnored,
            isPostNotifGranted,
            isPhotosGranted,
            isAudioMediaGranted,
            isBluetoothGranted,
            isUnrestrictedDataGranted,
        )
    val totalCount = permissionItems.size
    val grantedCount = permissionItems.count { it }

    val requestAllPermissions: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val hasRoot =
                isRootGranted ||
                    runCatching {
                        val res = Shell.cmd("id").exec()
                        res.isSuccess && (res.out.any { it.contains("uid=0") } || Shell.isAppGrantedRoot() == true)
                    }.getOrDefault(false)

            if (hasRoot) {
                withContext(Dispatchers.Main) {
                    logger.d("Permissions", "Запрос пакета всех прав через Root (Superuser)")
                    Toast.makeText(context, "Выдача всех прав через Root...", Toast.LENGTH_SHORT).show()
                }
                val pkg = context.packageName
                val commands =
                    listOf(
                        "pm grant $pkg android.permission.CAMERA",
                        "pm grant $pkg android.permission.RECORD_AUDIO",
                        "pm grant $pkg android.permission.ACCESS_FINE_LOCATION",
                        "pm grant $pkg android.permission.ACCESS_COARSE_LOCATION",
                        "pm grant $pkg android.permission.ACCESS_BACKGROUND_LOCATION",
                        "pm grant $pkg android.permission.SEND_SMS",
                        "pm grant $pkg android.permission.RECEIVE_SMS",
                        "pm grant $pkg android.permission.READ_SMS",
                        "pm grant $pkg android.permission.CALL_PHONE",
                        "pm grant $pkg android.permission.READ_CALL_LOG",
                        "pm grant $pkg android.permission.READ_PHONE_STATE",
                        "pm grant $pkg android.permission.READ_CONTACTS",
                        "pm grant $pkg android.permission.POST_NOTIFICATIONS",
                        "pm grant $pkg android.permission.BLUETOOTH_CONNECT",
                        "pm grant $pkg android.permission.READ_MEDIA_IMAGES",
                        "pm grant $pkg android.permission.READ_MEDIA_VIDEO",
                        "pm grant $pkg android.permission.READ_MEDIA_AUDIO",
                        "pm grant $pkg android.permission.READ_EXTERNAL_STORAGE",
                        "appops set $pkg SYSTEM_ALERT_WINDOW allow",
                        "appops set $pkg MANAGE_EXTERNAL_STORAGE allow",
                        "appops set $pkg WRITE_SETTINGS allow",
                        "appops set $pkg REQUEST_INSTALL_PACKAGES allow",
                        "cmd notification allow_listener $pkg/com.remotebot.app.service.RemoteBotNotificationListenerService",
                        "settings put secure enabled_accessibility_services $pkg/com.remotebot.app.service.RemoteBotAccessibilityService",
                        "settings put secure accessibility_enabled 1",
                        "dumpsys deviceidle whitelist +$pkg",
                        "cmd netpolicy add restrict-background-whitelist ${context.applicationInfo.uid}",
                    )
                for (cmd in commands) {
                    logger.t("Permissions", "Root cmd: $cmd")
                    val res = Shell.cmd(cmd).exec()
                    if (!res.isSuccess) {
                        logger.t("Permissions", "Root cmd error ($cmd): code=${res.code}, err=${res.err.joinToString(";")}")
                    }
                }
                withContext(Dispatchers.Main) {
                    refreshTrigger++
                    logger.i("Permissions", "Все права успешно выданы через Root (${commands.size} команд)")
                    Toast.makeText(context, "Все права успешно выданы через Root!", Toast.LENGTH_LONG).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    logger.d("Permissions", "Запрос пакета Runtime-разрешений вручную (${allRuntimePermissions.size} разрешений)")
                    logger.t("Permissions", "Разрешения: ${allRuntimePermissions.joinToString(", ")}")
                    masterAllLauncher.launch(allRuntimePermissions)

                    if (!isBatteryIgnored) {
                        try {
                            val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    var autoRootGrantAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(isRootGranted, grantedCount, totalCount) {
        if (isRootGranted && grantedCount < totalCount && !autoRootGrantAttempted) {
            autoRootGrantAttempted = true
            logger.i("Permissions", "Обнаружен активный Root и недостающие права ($grantedCount/$totalCount). Автовыдача...")
            requestAllPermissions()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Единая карточка мастера запроса всех разрешений наверху экрана
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
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Разрешения приложения",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text =
                        if (grantedCount == totalCount) {
                            "✅ Все разрешения выданы ($grantedCount из $totalCount)"
                        } else {
                            "Предоставлено $grantedCount из $totalCount разрешений"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (grantedCount == totalCount) {
                            AppColors.success
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )

                LinearProgressIndicator(
                    progress = { if (totalCount > 0) grantedCount.toFloat() / totalCount.toFloat() else 0f },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                    color = if (grantedCount == totalCount) AppColors.successLight else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                )

                if (grantedCount < totalCount) {
                    Button(
                        onClick = { requestAllPermissions() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text =
                                if (isRootGranted) {
                                    "⚡ Выдать все права через Root"
                                } else {
                                    "⚡ Запросить все разрешения вручную"
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        CriticalPermissionsSection(
            context = context,
            logger = logger,
            scope = scope,
            rootProvider = rootProvider,
            isRootGranted = isRootGranted,
            isInstallAppsGranted = isInstallAppsGranted,
            isManageStorageGranted = isManageStorageGranted,
            isAccessibilityGranted = isAccessibilityGranted,
            isWriteSettingsGranted = isWriteSettingsGranted,
            isOverlayGranted = isOverlayGranted,
            onRefreshRequest = { refreshTrigger++ },
            onRequestStorageLegacy = {
                batchLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            },
        )

        ConfidentialPermissionsSection(
            context = context,
            logger = logger,
            scope = scope,
            isRootGranted = isRootGranted,
            isNotifListenerGranted = isNotifListenerGranted,
            isSmsGranted = isSmsGranted,
            isCallsGranted = isCallsGranted,
            isDndGranted = isDndGranted,
            onRefreshRequest = { refreshTrigger++ },
            onRequestSms = {
                smsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
            },
            onRequestCalls = {
                callsLauncher.launch(
                    arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE),
                )
            },
        )

        StandardPermissionsSection(
            context = context,
            logger = logger,
            rootProvider = rootProvider,
            allStandardPermissions = allStandardPermissions,
            isCameraGranted = isCameraGranted,
            isMicGranted = isMicGranted,
            isLocationGranted = isLocationGranted,
            isContactsGranted = isContactsGranted,
            isBatteryIgnored = isBatteryIgnored,
            isPostNotifGranted = isPostNotifGranted,
            isPhotosGranted = isPhotosGranted,
            isAudioMediaGranted = isAudioMediaGranted,
            isBluetoothGranted = isBluetoothGranted,
            isUnrestrictedDataGranted = isUnrestrictedDataGranted,
            onBatchRequest = { batchLauncher.launch(allStandardPermissions) },
            onRequestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onRequestLocation = {
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onRequestContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
            onRequestPostNotif = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onRequestPhotos = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    photosLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
                } else {
                    photosLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            },
            onRequestAudioMedia = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    audioMediaLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
                } else {
                    audioMediaLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            },
            onRequestBluetooth = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH)
                }
            },
            onRefreshRequest = { refreshTrigger++ },
        )
    }

    if (showManualPermissionsDialog) {
        AlertDialog(
            onDismissRequest = { showManualPermissionsDialog = false },
            icon = { Text("ℹ️", fontSize = 28.sp) },
            title = {
                Text(
                    text = "Ограничения Android",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Обычные разрешения получены.\n\nИз-за ограничений безопасности Android системные разрешения (доступ ко всем файлам, чтение уведомлений, специальные возможности, поверх окон и др.) невозможно выдать пакетно без Root — их необходимо выдать по одному вручную в списке ниже.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = { showManualPermissionsDialog = false }) {
                    Text("Понятно")
                }
            },
        )
    }
}
