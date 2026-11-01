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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.R
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
        title = stringResource(R.string.perm_standard_section_title),
        subtitle = stringResource(R.string.perm_standard_section_subtitle),
        emojiIcon = "📱",
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    // Batch standard permissions request banner
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚀", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.perm_batch_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.perm_standard_batch_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    logger.i("Permissions", "Starting the batch standard-permissions request")
                    onBatchRequest()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.perm_batch_btn))
            }
        }
    }

    PermissionItemCard(
        icon = "📷",
        title = stringResource(R.string.perm_camera),
        isGranted = isCameraGranted,
        description = stringResource(R.string.perm_camera_desc),
        commandsHint = "/photo, /videorec, /detectmotion, /flashlight",
        buttonText = stringResource(R.string.perm_btn_camera),
    ) {
        onRequestCamera()
    }

    PermissionItemCard(
        icon = "🎙️",
        title = stringResource(R.string.perm_microphone),
        isGranted = isMicGranted,
        description = stringResource(R.string.perm_microphone_desc),
        commandsHint = "/rec, /rec_file, /sound_detector, /tts, /device_search",
        buttonText = stringResource(R.string.perm_btn_microphone),
    ) {
        onRequestMic()
    }

    PermissionItemCard(
        icon = "📍",
        title = stringResource(R.string.perm_location),
        isGranted = isLocationGranted,
        description = stringResource(R.string.perm_location_desc),
        commandsHint = stringResource(R.string.perm_hint_location),
        buttonText = stringResource(R.string.perm_btn_location),
    ) {
        onRequestLocation()
    }

    PermissionItemCard(
        icon = "👥",
        title = stringResource(R.string.perm_contacts),
        isGranted = isContactsGranted,
        description = stringResource(R.string.perm_contacts_desc),
        commandsHint = stringResource(R.string.perm_hint_contacts),
        buttonText = stringResource(R.string.perm_btn_contacts),
    ) {
        onRequestContacts()
    }

    PermissionItemCard(
        icon = "🔋",
        title = stringResource(R.string.perm_doze_mode),
        isGranted = isBatteryIgnored,
        description = stringResource(R.string.perm_doze_mode_desc),
        commandsHint = "24/7 Long Polling, WSS /ws, Push Wakeup",
        buttonText = stringResource(R.string.perm_btn_doze),
    ) {
        logger.i("Permissions", "Requesting Doze Mode exemption")
        try {
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            logger.w("Permissions", "Doze direct request open failure: ${e.localizedMessage}")
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
            title = stringResource(R.string.perm_post_notifications),
            isGranted = isPostNotifGranted,
            description = stringResource(R.string.perm_post_notifications_desc),
            commandsHint = "Foreground Service",
            buttonText = stringResource(R.string.perm_btn_post_notifications),
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
                stringResource(R.string.perm_media_photos)
            } else {
                stringResource(R.string.perm_media_storage)
            },
        isGranted = isPhotosGranted,
        description = stringResource(R.string.perm_media_photos_desc),
        commandsHint = "/gallery, /get_file",
        buttonText = stringResource(R.string.perm_btn_media_photos),
    ) {
        onRequestPhotos()
    }

    PermissionItemCard(
        icon = "🎵",
        title =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                stringResource(R.string.perm_media_audio)
            } else {
                stringResource(R.string.perm_media_storage)
            },
        isGranted = isAudioMediaGranted,
        description = stringResource(R.string.perm_media_audio_desc),
        commandsHint = stringResource(R.string.perm_hint_audio),
        buttonText = stringResource(R.string.perm_btn_media_audio),
    ) {
        onRequestAudioMedia()
    }

    PermissionItemCard(
        icon = "💠",
        title =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {
                stringResource(R.string.perm_bluetooth_connect)
            } else {
                stringResource(R.string.perm_bluetooth_legacy)
            },
        isGranted = isBluetoothGranted,
        description = stringResource(R.string.perm_bluetooth_desc),
        commandsHint = "/bluetooth [on|off|toggle]",
        buttonText = stringResource(R.string.perm_btn_bluetooth),
    ) {
        onRequestBluetooth()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        PermissionItemCard(
            icon = "📶",
            title = stringResource(R.string.perm_unlimited_data),
            isGranted = isUnrestrictedDataGranted,
            description = stringResource(R.string.perm_unlimited_data_desc),
            commandsHint = "Data Saver 24/7",
            buttonText = stringResource(R.string.perm_btn_unlimited_data),
        ) {
            val uid = context.applicationInfo.uid
            // If Root is granted, immediately add UID to network policy whitelist
            if (rootProvider.isRootGrantedSync()) {
                logger.d("Permissions", "Requesting Data Saver restrictions removal via Root (UID=$uid)")
                try {
                    val cmd = "cmd netpolicy add restrict-background-whitelist $uid"
                    logger.t("Permissions", "Root cmd (Data Saver): $cmd")
                    val res =
                        com.topjohnwu.superuser.Shell
                            .cmd(cmd)
                            .exec()
                    if (res.isSuccess) {
                        logger.i("Permissions", "Unlimited data granted successfully via Root (cmd netpolicy)")
                        onRefreshRequest()
                        return@PermissionItemCard
                    } else {
                        logger.t("Permissions", "Root cmd error (Data Saver): code=${res.code}, err=${res.err.joinToString(";")}")
                    }
                } catch (e: Exception) {
                    logger.w("Permissions", "Root grant failed: ${e.message}")
                }
            } else {
                logger.d("Permissions", "Opening the Data Saver settings screen (without Root)")
            }

            try {
                val intent =
                    Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                context.startActivity(intent)
            } catch (e: Exception) {
                logger.w("Permissions", "Data Saver direct navigation failure: ${e.localizedMessage}")
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
