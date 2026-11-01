package com.remotebot.app.ui.screens.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remotebot.app.R
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.provider.RootStatusProvider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CriticalPermissionsSection(
    context: Context,
    logger: AppLogger,
    scope: CoroutineScope,
    rootProvider: RootStatusProvider,
    isRootGranted: Boolean,
    isInstallAppsGranted: Boolean,
    isManageStorageGranted: Boolean,
    isAccessibilityGranted: Boolean,
    isWriteSettingsGranted: Boolean,
    isOverlayGranted: Boolean,
    onRefreshRequest: () -> Unit,
    onRequestStorageLegacy: () -> Unit,
) {
    com.remotebot.app.ui.components.AppSectionHeader(
        title = stringResource(R.string.perm_critical_system_access),
        subtitle = stringResource(R.string.perm_critical_system_access_subtitle),
        emojiIcon = "🛡️",
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    PermissionItemCard(
        icon = "👑",
        title = stringResource(R.string.perm_root_access),
        isGranted = isRootGranted,
        description = stringResource(R.string.perm_root_desc),
        commandsHint = "/shell, /reboot, /shutdown, /recovery, /fastboot, /screenshot, /screen_rec, /airplane, /hotspot, /mobile_data, /gps, /adb, /adb_wifi, /kill, /script",
        buttonText = stringResource(R.string.perm_btn_root),
    ) {
        logger.i("Permissions", "Requesting Root access via RootStatusProvider")
        scope.launch {
            val granted = rootProvider.checkRoot(forceRefresh = true)
            if (granted) {
                Toast.makeText(context, context.getString(R.string.perm_root_granted_toast), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.perm_root_denied_toast), Toast.LENGTH_SHORT).show()
            }
            onRefreshRequest()
        }
    }

    PermissionItemCard(
        icon = "📦",
        title = stringResource(R.string.perm_install_unknown_apps),
        isGranted = isInstallAppsGranted,
        description = stringResource(R.string.perm_install_unknown_apps_desc),
        commandsHint = stringResource(R.string.perm_hint_install),
        buttonText = stringResource(R.string.perm_btn_install_apps),
    ) {
        logger.i("Permissions", "Requesting install-unknown-apps permission")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent =
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                context.startActivity(intent)
            } catch (e: Exception) {
                logger.w("Permissions", "Install Apps direct screen failure: ${e.localizedMessage}")
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }
    }

    PermissionItemCard(
        icon = "📁",
        title = stringResource(R.string.perm_all_files_access),
        isGranted = isManageStorageGranted,
        description = stringResource(R.string.perm_all_files_access_desc),
        commandsHint = "/get_file, /download, /ls, /dir, /ls_interactive, /unsent_files",
        buttonText = stringResource(R.string.perm_btn_all_files),
    ) {
        logger.i("Permissions", "Requesting all-files access (MANAGE_EXTERNAL_STORAGE)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent =
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                context.startActivity(intent)
            } catch (e: Exception) {
                logger.w("Permissions", "All Files direct screen failure: ${e.localizedMessage}")
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {
                    }
                }
            }
        } else {
            onRequestStorageLegacy()
        }
    }

    PermissionItemCard(
        icon = "♿",
        title = stringResource(R.string.perm_accessibility_service),
        isGranted = isAccessibilityGranted,
        description =
            stringResource(R.string.perm_accessibility_desc) +
                if (!isAccessibilityGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stringResource(R.string.perm_accessibility_restricted_hint)
                } else {
                    "" +
                        if (!isAccessibilityGranted && isRootGranted) {
                            stringResource(R.string.perm_root_quick_activation)
                        } else {
                            ""
                        }
                },
        commandsHint = stringResource(R.string.perm_hint_accessibility),
        buttonText =
            if (isRootGranted) {
                stringResource(
                    R.string.perm_btn_accessibility_root,
                )
            } else {
                stringResource(R.string.perm_btn_accessibility_settings)
            },
    ) {
        if (isRootGranted) {
            logger.d("Permissions", "Requesting Accessibility Service enablement via Root")
            scope.launch(Dispatchers.IO) {
                val serviceName = "${context.packageName}/${com.remotebot.app.service.RemoteBotAccessibilityService::class.java.name}"
                val cmd =
                    """
                    current=${'$'}(settings get secure enabled_accessibility_services)
                    if [ "${'$'}current" = "null" ] || [ -z "${'$'}current" ]; then
                        settings put secure enabled_accessibility_services "$serviceName"
                    elif [[ "${'$'}current" != *"$serviceName"* ]]; then
                        settings put secure enabled_accessibility_services "${'$'}current:$serviceName"
                    fi
                    settings put secure accessibility_enabled 1
                    """.trimIndent()
                logger.t("Permissions", "Root cmd (Accessibility): $cmd")
                val res = Shell.cmd(cmd).exec()
                if (!res.isSuccess) {
                    logger.t("Permissions", "Root cmd error (Accessibility): code=${res.code}, err=${res.err.joinToString(";")}")
                }
                withContext(Dispatchers.Main) {
                    if (res.isSuccess) {
                        logger.i("Permissions", "Accessibility enabled successfully via Root")
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.perm_accessibility_root_enabled_toast),
                                Toast.LENGTH_SHORT,
                            ).show()
                        onRefreshRequest()
                    } else {
                        logger.w("Permissions", "Root enablement failed, opening the system Accessibility settings")
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } else {
            logger.d("Permissions", "Opening the Accessibility settings screen (without Root)")
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                logger.w("Permissions", "Accessibility open failure: ${e.localizedMessage}")
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }
    }

    PermissionItemCard(
        icon = "⚙️",
        title = stringResource(R.string.perm_write_settings),
        isGranted = isWriteSettingsGranted,
        description = stringResource(R.string.perm_write_settings_desc),
        commandsHint = "/auto_brightness, /toggles",
        buttonText = stringResource(R.string.perm_btn_write_settings),
    ) {
        logger.i("Permissions", "Opening Write Settings")
        try {
            val intent =
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            logger.w("Permissions", "Write Settings open failure: ${e.localizedMessage}")
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    PermissionItemCard(
        icon = "🪟",
        title = stringResource(R.string.perm_overlay_windows),
        isGranted = isOverlayGranted,
        description = stringResource(R.string.perm_overlay_windows_desc),
        commandsHint = stringResource(R.string.perm_hint_overlay),
        buttonText = stringResource(R.string.perm_btn_overlay_windows),
    ) {
        logger.i("Permissions", "Opening the overlay (draw over apps) settings")
        try {
            val intent =
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            logger.w("Permissions", "Overlay settings open failure: ${e.localizedMessage}")
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
}
