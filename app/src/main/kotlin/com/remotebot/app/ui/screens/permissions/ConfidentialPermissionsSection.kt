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
import androidx.compose.ui.res.stringResource
import com.remotebot.app.R
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
        title = stringResource(R.string.perm_confidential_section_title),
        subtitle = stringResource(R.string.perm_confidential_section_subtitle),
        emojiIcon = "💬",
        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )

    PermissionItemCard(
        icon = "🔔",
        title = stringResource(R.string.perm_notification_listener),
        isGranted = isNotifListenerGranted,
        description =
            stringResource(R.string.perm_notification_listener_desc) +
                if (!isNotifListenerGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stringResource(R.string.perm_accessibility_restricted_hint)
                } else {
                    "" +
                        if (!isNotifListenerGranted && isRootGranted) {
                            stringResource(R.string.perm_root_quick_activation)
                        } else {
                            ""
                        }
                },
        commandsHint = "/notify, /notifications, /notify_send, /answer, /resp, /notify_clear, /notify_rule",
        buttonText = if (isRootGranted) stringResource(R.string.perm_btn_notif_listener_root) else stringResource(R.string.perm_btn_notif_listener_settings),
    ) {
        if (isRootGranted) {
            logger.d("Permissions", "Requesting Notification Listener enablement via Root")
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
                        logger.i("Permissions", "Notification access granted successfully via Root")
                        Toast.makeText(context, context.getString(R.string.perm_notif_root_enabled_toast), Toast.LENGTH_SHORT).show()
                        onRefreshRequest()
                    } else {
                        logger.w("Permissions", "Root grant failed, opening the system notification settings")
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } else {
            logger.d("Permissions", "Opening the Notification Listener settings screen (without Root)")
            try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                logger.w("Permissions", "Notification settings open failure: ${e.localizedMessage}")
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }
    }

    PermissionItemCard(
        icon = "💬",
        title = stringResource(R.string.perm_sms_send_read),
        isGranted = isSmsGranted,
        description = stringResource(R.string.perm_sms_send_read_desc),
        commandsHint = "/sms, /sms_inbox, /sms_bot, /sms_forward, /whitelist_phone",
        buttonText = stringResource(R.string.perm_btn_sms),
    ) {
        onRequestSms()
    }

    PermissionItemCard(
        icon = "📞",
        title = stringResource(R.string.perm_calls_ussd),
        isGranted = isCallsGranted,
        description = stringResource(R.string.perm_calls_ussd_desc),
        commandsHint = stringResource(R.string.perm_hint_phone),
        buttonText = stringResource(R.string.perm_btn_calls),
    ) {
        onRequestCalls()
    }

    PermissionItemCard(
        icon = "🔕",
        title = stringResource(R.string.perm_dnd_policy),
        isGranted = isDndGranted,
        description = stringResource(R.string.perm_dnd_policy_desc),
        commandsHint = "/ring_mode [normal|silent|vibrate], /volume",
        buttonText = stringResource(R.string.perm_btn_dnd),
    ) {
        logger.i("Permissions", "Opening the DND Notification Policy settings")
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (e: Exception) {
            logger.w("Permissions", "DND settings open failure: ${e.localizedMessage}")
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
}
