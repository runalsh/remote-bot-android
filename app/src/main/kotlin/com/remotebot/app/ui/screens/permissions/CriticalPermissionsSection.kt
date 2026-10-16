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
import androidx.compose.ui.unit.dp
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
        title = "1. Системный доступ (Root и Установка)",
        subtitle = "Полный системный контроль, установка приложений, память, настройки",
        emojiIcon = "🛡️",
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    PermissionItemCard(
        icon = "👑",
        title = "Root доступ (Superuser / libsu)",
        isGranted = isRootGranted,
        description = "Прямой доступ к ядру Linux, системным демонам и скрытым командам",
        commandsHint = "/shell, /reboot, /shutdown, /recovery, /fastboot, /screenshot, /screen_rec, /airplane, /hotspot, /mobile_data, /gps, /adb, /adb_wifi, /kill, /script",
        buttonText = "Запросить Root доступ",
    ) {
        logger.i("Permissions", "Запрос Root доступа через RootStatusProvider")
        scope.launch {
            val granted = rootProvider.checkRoot(forceRefresh = true)
            if (granted) {
                Toast.makeText(context, "Root получен!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Root отклонён или недоступен", Toast.LENGTH_SHORT).show()
            }
            onRefreshRequest()
        }
    }

    PermissionItemCard(
        icon = "📦",
        title = "Установка приложений (Install Unknown Apps)",
        isGranted = isInstallAppsGranted,
        description = "Разрешение на установку и тихое обновление APK пакетов из внешних источников",
        commandsHint = "/install <файл.apk> (тихая установка и обновление бота)",
        buttonText = "Разрешить установку приложений",
    ) {
        logger.i("Permissions", "Запрос разрешения установки неизвестных приложений")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent =
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                context.startActivity(intent)
            } catch (e: Exception) {
                logger.w("Permissions", "Сбой прямого экрана Install Apps: ${e.localizedMessage}")
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }
    }

    PermissionItemCard(
        icon = "📁",
        title = "Доступ ко всем файлам (MANAGE_EXTERNAL_STORAGE)",
        isGranted = isManageStorageGranted,
        description = "Полный доступ к внутренней памяти и SD-карте без ограничений Scoped Storage",
        commandsHint = "/get_file, /download, /ls, /dir, /ls_interactive, /unsent_files",
        buttonText = "Открыть доступ ко всем файлам",
    ) {
        logger.i("Permissions", "Запрос доступа ко всем файлам (MANAGE_EXTERNAL_STORAGE)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent =
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                context.startActivity(intent)
            } catch (e: Exception) {
                logger.w("Permissions", "Сбой прямого экрана All Files: ${e.localizedMessage}")
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
        title = "Специальные возможности (Accessibility Service)",
        isGranted = isAccessibilityGranted,
        description =
            "Автоматизация интерфейса, чтение экрана, клики и обработка USSD-диалогов" +
                if (!isAccessibilityGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "\n⚠️ Если переключатель неактивен: Настройки -> Приложения -> Remote Bot -> Меню 3 точки -> Разрешить ограниченные настройки"
                } else {
                    "" +
                        if (!isAccessibilityGranted && isRootGranted) {
                            "\n👑 Доступна быстрая активация через Root в 1 клик!"
                        } else {
                            ""
                        }
                },
        commandsHint = "/ussd, перехват USSD-диалогов, автоматизация UI, обход окон",
        buttonText = if (isRootGranted) "Включить через Root (1 клик)" else "Открыть спец. возможности",
    ) {
        if (isRootGranted) {
            logger.d("Permissions", "Запрос включения Accessibility Service через Root")
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
                        logger.i("Permissions", "Спец. возможности успешно включены через Root")
                        Toast.makeText(context, "Спец. возможности включены через Root!", Toast.LENGTH_SHORT).show()
                        onRefreshRequest()
                    } else {
                        logger.w("Permissions", "Сбой включения через Root, открываем системные настройки Accessibility")
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } else {
            logger.d("Permissions", "Открытие экрана настроек Accessibility (без Root)")
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                logger.w("Permissions", "Сбой открытия Accessibility: ${e.localizedMessage}")
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }
    }

    PermissionItemCard(
        icon = "⚙️",
        title = "Изменение системных настроек (Write Settings)",
        isGranted = isWriteSettingsGranted,
        description = "Прямая модификация параметров яркости, тайм-аутов и системных флагов",
        commandsHint = "/auto_brightness, /toggles",
        buttonText = "Разрешить запись настроек",
    ) {
        logger.i("Permissions", "Открытие настроек Write Settings")
        try {
            val intent =
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            logger.w("Permissions", "Сбой открытия настроек Write Settings: ${e.localizedMessage}")
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    PermissionItemCard(
        icon = "🪟",
        title = "Показ поверх других окон (Overlays / Alert Window)",
        isGranted = isOverlayGranted,
        description = "Отображение всплывающих диалогов, плавающих окон и уведомлений на экране",
        commandsHint = "/toggles, системные всплывающие диалоги и окна",
        buttonText = "Разрешить показ поверх окон",
    ) {
        logger.i("Permissions", "Открытие настроек наложения поверх окон (Overlay)")
        try {
            val intent =
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            logger.w("Permissions", "Сбой открытия настроек Overlay: ${e.localizedMessage}")
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
}
