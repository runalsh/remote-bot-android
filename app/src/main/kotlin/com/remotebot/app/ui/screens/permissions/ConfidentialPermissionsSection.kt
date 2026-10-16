package com.remotebot.app.ui.screens.permissions

import android.content.Context
import android.content.Intent
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
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConfidentialPermissionsSection(
    context: Context,
    logger: AppLogger,
    scope: CoroutineScope,
    isRootGranted: Boolean,
    isNotifListenerGranted: Boolean,
    isSmsGranted: Boolean,
    isCallsGranted: Boolean,
    isDndGranted: Boolean,
    onRefreshRequest: () -> Unit,
    onRequestSms: () -> Unit,
    onRequestCalls: () -> Unit,
) {
    com.remotebot.app.ui.components.AppSectionHeader(
        title = "2. Связь и уведомления (Уведомления, SMS, USSD)",
        subtitle = "Перехват уведомлений банков/мессенджеров, SMS-трафик, звонки и USSD",
        emojiIcon = "💬",
        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )

    PermissionItemCard(
        icon = "🔔",
        title = "Чтение уведомлений (Notification Listener)",
        isGranted = isNotifListenerGranted,
        description =
            "Пересылка входящих уведомлений сторонних мессенджеров и банков в Telegram" +
                if (!isNotifListenerGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "\n⚠️ Если переключатель неактивен: Настройки -> Приложения -> Remote Bot -> Меню 3 точки -> Разрешить ограниченные настройки"
                } else {
                    "" +
                        if (!isNotifListenerGranted && isRootGranted) {
                            "\n👑 Доступна быстрая активация через Root в 1 клик!"
                        } else {
                            ""
                        }
                },
        commandsHint = "/notify, /notifications, /notify_send, /answer, /resp, /notify_clear, /notify_rule",
        buttonText = if (isRootGranted) "Включить через Root (1 клик)" else "Открыть настройки уведомлений",
    ) {
        if (isRootGranted) {
            logger.d("Permissions", "Запрос включения Notification Listener через Root")
            scope.launch(Dispatchers.IO) {
                val serviceName = "${context.packageName}/${com.remotebot.app.service.RemoteBotNotificationListenerService::class.java.name}"
                val cmd =
                    """
                    cmd notification allow_listener $serviceName
                    current=${'$'}(settings get secure enabled_notification_listeners)
                    if [ "${'$'}current" = "null" ] || [ -z "${'$'}current" ]; then
                        settings put secure enabled_notification_listeners "$serviceName"
                    elif [[ "${'$'}current" != *"$serviceName"* ]]; then
                        settings put secure enabled_notification_listeners "${'$'}current:$serviceName"
                    fi
                    """.trimIndent()
                logger.t("Permissions", "Root cmd (Notification Listener): $cmd")
                val res = Shell.cmd(cmd).exec()
                if (!res.isSuccess) {
                    logger.t("Permissions", "Root cmd error (Notification Listener): code=${res.code}, err=${res.err.joinToString(";")}")
                }
                withContext(Dispatchers.Main) {
                    if (res.isSuccess) {
                        logger.i("Permissions", "Доступ к уведомлениям успешно выдан через Root")
                        Toast.makeText(context, "Доступ к уведомлениям выдан через Root!", Toast.LENGTH_SHORT).show()
                        onRefreshRequest()
                    } else {
                        logger.w("Permissions", "Сбой выдачи через Root, открываем системные настройки уведомлений")
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } else {
            logger.d("Permissions", "Открытие экрана настроек Notification Listener (без Root)")
            try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                logger.w("Permissions", "Сбой открытия настроек уведомлений: ${e.localizedMessage}")
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }
    }

    PermissionItemCard(
        icon = "💬",
        title = "SMS (Отправка и Чтение)",
        isGranted = isSmsGranted,
        description = "Отправка SMS-сообщений, чтение входящих и пересылка в Telegram",
        commandsHint = "/sms, /sms_inbox, /sms_bot, /sms_forward, /whitelist_phone",
        buttonText = "Запросить доступ к SMS",
    ) {
        onRequestSms()
    }

    PermissionItemCard(
        icon = "📞",
        title = "Звонки, USSD и Телефон (Calls & USSD)",
        isGranted = isCallsGranted,
        description = "Совершение телефонных вызовов, USSD-запросы баланса и журнал звонков",
        commandsHint = "/call, /callshistory, /ussd, пересылка звонков",
        buttonText = "Запросить доступ к Звонкам и USSD",
    ) {
        onRequestCalls()
    }

    PermissionItemCard(
        icon = "🔕",
        title = "Режим «Не беспокоить» (DND Policy Access)",
        isGranted = isDndGranted,
        description = "Управление звуковыми профилями и отключением звука звонков",
        commandsHint = "/ring_mode [normal|silent|vibrate], /volume",
        buttonText = "Открыть доступ к DND",
    ) {
        logger.i("Permissions", "Открытие настроек DND Notification Policy")
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (e: Exception) {
            logger.w("Permissions", "Сбой открытия настроек DND: ${e.localizedMessage}")
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
}
