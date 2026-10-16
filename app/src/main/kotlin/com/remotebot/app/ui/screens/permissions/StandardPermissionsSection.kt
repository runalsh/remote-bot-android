package com.remotebot.app.ui.screens.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.provider.RootStatusProvider

@Composable
fun StandardPermissionsSection(
    context: Context,
    logger: AppLogger,
    rootProvider: RootStatusProvider,
    allStandardPermissions: Array<String>,
    isCameraGranted: Boolean,
    isMicGranted: Boolean,
    isLocationGranted: Boolean,
    isContactsGranted: Boolean,
    isBatteryIgnored: Boolean,
    isPostNotifGranted: Boolean,
    isPhotosGranted: Boolean,
    isAudioMediaGranted: Boolean,
    isBluetoothGranted: Boolean,
    isUnrestrictedDataGranted: Boolean,
    onBatchRequest: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestContacts: () -> Unit,
    onRequestPostNotif: () -> Unit,
    onRequestPhotos: () -> Unit,
    onRequestAudioMedia: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onRefreshRequest: () -> Unit,
) {
    com.remotebot.app.ui.components.AppSectionHeader(
        title = "3. Мультимедиа и сенсоры (Камера, Звук, Локация, Сеть)",
        subtitle = "Базовые права Android: камера, микрофон, GPS, контакты, Doze Mode",
        emojiIcon = "📱",
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    // Баннер пакетного запроса стандартных прав
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚀", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Пакетный запрос стандартных прав",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Запросить Камеру, Микрофон, GPS, Контакты и Уведомления в одном окне:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    logger.i("Permissions", "Запуск пакетного запроса стандартных разрешений")
                    onBatchRequest()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Запросить все стандартные разрешения")
            }
        }
    }

    PermissionItemCard(
        icon = "📷",
        title = "Камера (CAMERA)",
        isGranted = isCameraGranted,
        description = "Скрытая фото- и видеосъемка через переднюю и заднюю камеры (CameraX)",
        commandsHint = "/photo, /videorec, /detectmotion, /flashlight",
        buttonText = "Запросить доступ к Камере",
    ) {
        onRequestCamera()
    }

    PermissionItemCard(
        icon = "🎙️",
        title = "Микрофон (RECORD_AUDIO)",
        isGranted = isMicGranted,
        description = "Запись звука и голоса с микрофона устройства",
        commandsHint = "/rec, /rec_file, /sound_detector, /tts, /device_search",
        buttonText = "Запросить доступ к Микрофону",
    ) {
        onRequestMic()
    }

    PermissionItemCard(
        icon = "📍",
        title = "Геолокация (GPS / LBS)",
        isGranted = isLocationGranted,
        description = "Определение точных спутниковых GPS координат и вычисление адреса",
        commandsHint = "/location (алиас /trace), /track, /shake_detector, /gps",
        buttonText = "Запросить доступ к Геолокации",
    ) {
        onRequestLocation()
    }

    PermissionItemCard(
        icon = "👥",
        title = "Контакты (Contacts)",
        isGranted = isContactsGranted,
        description = "Поиск контактов по телефонной книге устройства",
        commandsHint = "Сопоставление имен абонентов в /call, /callshistory, SMS",
        buttonText = "Запросить доступ к Контактам",
    ) {
        onRequestContacts()
    }

    PermissionItemCard(
        icon = "🔋",
        title = "Энергосбережение (Doze Mode)",
        isGranted = isBatteryIgnored,
        description = "Исключение из энергосбережения для работы 24/7 без засыпания службы",
        commandsHint = "24/7 Long Polling, WSS /ws, Push Wakeup в фоне",
        buttonText = "Исключить из энергосбережения",
    ) {
        logger.i("Permissions", "Запрос отключения Doze Mode")
        try {
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            logger.w("Permissions", "Сбой открытия прямого запроса Doze: ${e.localizedMessage}")
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionItemCard(
            icon = "📣",
            title = "Уведомления службы (POST_NOTIFICATIONS)",
            isGranted = isPostNotifGranted,
            description = "Отображение статуса Foreground Service в шторке уведомлений",
            commandsHint = "Постоянное Foreground Service уведомление в шторке",
            buttonText = "Запросить показ уведомлений",
        ) {
            onRequestPostNotif()
        }
    }

    PermissionItemCard(
        icon = "🖼️",
        title =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                "Фото и видео (READ_MEDIA_IMAGES)"
            } else {
                "Хранилище медиа (READ_EXTERNAL_STORAGE)"
            },
        isGranted = isPhotosGranted,
        description = "Доступ к фотографиям и видеозаписям галереи устройства",
        commandsHint = "/gallery, /get_file",
        buttonText = "Запросить доступ к Фото и видео",
    ) {
        onRequestPhotos()
    }

    PermissionItemCard(
        icon = "🎵",
        title =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                "Музыка и аудио (READ_MEDIA_AUDIO)"
            } else {
                "Хранилище аудио (READ_EXTERNAL_STORAGE)"
            },
        isGranted = isAudioMediaGranted,
        description = "Доступ к музыкальным трекам и аудиофайлам в памяти устройства",
        commandsHint = "/player (алиас /playback), /rec_file",
        buttonText = "Запросить доступ к Аудио",
    ) {
        onRequestAudioMedia()
    }

    PermissionItemCard(
        icon = "💠",
        title =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {
                "Устройства рядом (BLUETOOTH_CONNECT)"
            } else {
                "Bluetooth адаптер (BLUETOOTH)"
            },
        isGranted = isBluetoothGranted,
        description = "Управление Bluetooth-адаптером и сопряженными устройствами (Android 12+)",
        commandsHint = "/bluetooth [on|off|toggle]",
        buttonText = "Запросить доступ к Bluetooth",
    ) {
        onRequestBluetooth()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        PermissionItemCard(
            icon = "📶",
            title = "Безлимитный мобильный трафик (Data Saver)",
            isGranted = isUnrestrictedDataGranted,
            description = "Неограниченная передача мобильных данных в фоне при включенном режиме «Экономия трафика»",
            commandsHint = "Связь с ботом 24/7 без разрывов сети при активном Data Saver",
            buttonText = "Разрешить безлимитный трафик",
        ) {
            val uid = context.applicationInfo.uid
            // Если есть Root-права, мгновенно добавляем UID в whitelist сетевой политики
            if (rootProvider.isRootGrantedSync()) {
                logger.d("Permissions", "Запрос снятия ограничений Data Saver через Root (UID=$uid)")
                try {
                    val cmd = "cmd netpolicy add restrict-background-whitelist $uid"
                    logger.t("Permissions", "Root cmd (Data Saver): $cmd")
                    val res =
                        com.topjohnwu.superuser.Shell
                            .cmd(cmd)
                            .exec()
                    if (res.isSuccess) {
                        logger.i("Permissions", "Безлимитный трафик успешно выдан через Root (cmd netpolicy)")
                        onRefreshRequest()
                        return@PermissionItemCard
                    } else {
                        logger.t("Permissions", "Root cmd error (Data Saver): code=${res.code}, err=${res.err.joinToString(";")}")
                    }
                } catch (e: Exception) {
                    logger.w("Permissions", "Не удалось выдать через Root: ${e.message}")
                }
            } else {
                logger.d("Permissions", "Открытие экрана настроек Data Saver (без Root)")
            }

            try {
                val intent =
                    Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                context.startActivity(intent)
            } catch (e: Exception) {
                logger.w("Permissions", "Сбой прямого перехода Data Saver: ${e.localizedMessage}")
                try {
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
