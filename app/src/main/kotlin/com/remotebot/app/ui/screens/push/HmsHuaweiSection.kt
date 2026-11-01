package com.remotebot.app.ui.screens.push

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.R
import com.remotebot.app.ui.components.StatusMessageText
import com.remotebot.app.ui.theme.AppColors
import com.remotebot.core.push.DynamicHmsManager
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HmsHuaweiSection(
    context: Context,
    scope: CoroutineScope,
    configRepo: BotConfigRepository,
    hmsJsonText: String,
    onHmsJsonTextChange: (String) -> Unit,
    hmsPickerLauncher: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    hmsToken: String?,
    onHmsTokenChange: (String?) -> Unit,
    hmsStatusMessage: String?,
    onHmsStatusMessageChange: (String?) -> Unit,
    isHmsSuccess: Boolean,
    onIsHmsSuccessChange: (Boolean) -> Unit,
    isHmsProcessing: Boolean,
    onIsHmsProcessingChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🦅", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.push_hms_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.push_hms_card_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { hmsPickerLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.push_hms_choose_file))
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotBlank()) {
                                    onHmsJsonTextChange(text)
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.push_paste_clipboard_toast, text.length),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.push_clipboard_error_toast, e.message ?: ""),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.push_hms_from_clipboard))
                }

                if (hmsJsonText.isNotBlank()) {
                    IconButton(onClick = { onHmsJsonTextChange("") }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                    }
                }
            }

            val hmsValidationError =
                if (hmsJsonText.isNotBlank()) {
                    DynamicHmsManager.parseAgconnectJson(hmsJsonText).exceptionOrNull()?.message
                } else {
                    null
                }

            OutlinedTextField(
                value = hmsJsonText,
                onValueChange = onHmsJsonTextChange,
                label = { Text(stringResource(R.string.push_hms_json_label)) },
                placeholder = { Text("{\n  \"client\": {\n    \"app_id\": \"105581234\", ...\n  }\n}") },
                isError = hmsValidationError != null,
                supportingText = {
                    if (hmsValidationError != null) {
                        Text("❌ $hmsValidationError", color = MaterialTheme.colorScheme.error)
                    } else if (hmsJsonText.isNotBlank()) {
                        Text(stringResource(R.string.push_hms_json_valid), color = AppColors.success)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )

            Button(
                onClick = {
                    if (hmsJsonText.isBlank()) {
                        onHmsStatusMessageChange(context.getString(R.string.push_hms_need_json))
                        onIsHmsSuccessChange(false)
                        return@Button
                    }
                    onIsHmsProcessingChange(true)
                    scope.launch {
                        val parseResult = DynamicHmsManager.parseAgconnectJson(hmsJsonText)
                        if (parseResult.isFailure) {
                            onIsHmsProcessingChange(false)
                            onHmsStatusMessageChange(
                                context.getString(
                                    R.string.push_hms_parse_error,
                                    parseResult.exceptionOrNull()?.localizedMessage ?: "",
                                ),
                            )
                            onIsHmsSuccessChange(false)
                            return@launch
                        }

                        val parsed = parseResult.getOrThrow()
                        val initResult = DynamicHmsManager.initialize(context, parsed)
                        if (initResult.isFailure) {
                            onIsHmsProcessingChange(false)
                            onHmsStatusMessageChange(
                                context.getString(
                                    R.string.push_hms_init_error,
                                    initResult.exceptionOrNull()?.localizedMessage ?: "",
                                ),
                            )
                            onIsHmsSuccessChange(false)
                            return@launch
                        }

                        configRepo.updateConfig { current ->
                            current.copy(
                                agconnectServicesJson = hmsJsonText,
                                hmsAppId = parsed.appId,
                                hmsClientId = parsed.clientId,
                                hmsClientSecret = parsed.clientSecret,
                                hmsApiKey = parsed.apiKey,
                            )
                        }

                        val tokenResult = DynamicHmsManager.getHmsToken(context, parsed.appId)
                        onIsHmsProcessingChange(false)
                        if (tokenResult.isSuccess) {
                            val token = tokenResult.getOrThrow()
                            onHmsTokenChange(token)
                            onIsHmsSuccessChange(true)
                            onHmsStatusMessageChange(context.getString(R.string.push_hms_initialized_token))
                            Toast.makeText(context, context.getString(R.string.push_hms_init_success), Toast.LENGTH_SHORT).show()
                        } else {
                            onIsHmsSuccessChange(false)
                            val errorMsg = tokenResult.exceptionOrNull()?.localizedMessage ?: context.getString(R.string.push_token_error)
                            onHmsStatusMessageChange("❌ $errorMsg")
                        }
                    }
                },
                enabled = !isHmsProcessing && hmsJsonText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.error),
            ) {
                if (isHmsProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.push_fcm_btn_initializing))
                } else {
                    Text(stringResource(R.string.push_hms_btn_init))
                }
            }

            StatusMessageText(
                message = hmsStatusMessage,
                isError = !isHmsSuccess,
            )

            val isHmsCoreInstalled = remember { DynamicHmsManager.isHmsCoreInstalled(context) }

            Surface(
                color =
                    if (!hmsToken.isNullOrBlank()) {
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.5f,
                        )
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.push_hms_status_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val isConfigured = hmsJsonText.isNotBlank()
                        val isInit = isConfigured && DynamicHmsManager.isInitialized()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color =
                                if (!isHmsCoreInstalled) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else if (isInit && !hmsToken.isNullOrBlank()) {
                                    AppColors.success.copy(alpha = 0.15f)
                                } else if (isInit) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                        ) {
                            Text(
                                text =
                                    if (!isHmsCoreInstalled) {
                                        stringResource(R.string.push_status_no_hms)
                                    } else if (isInit && !hmsToken.isNullOrBlank()) {
                                        stringResource(R.string.push_status_active_dot)
                                    } else if (isInit) {
                                        stringResource(R.string.push_status_waiting_token)
                                    } else {
                                        stringResource(R.string.push_status_not_init)
                                    },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (!isHmsCoreInstalled) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else if (isInit && !hmsToken.isNullOrBlank()) {
                                        AppColors.success
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }

                    if (!isHmsCoreInstalled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.push_hms_core_warning),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!hmsToken.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.push_hms_token_ready),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.success,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = hmsToken,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 3,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("HMS Device Token", hmsToken)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.push_hms_token_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.push_hms_copy_token))
                        }
                    } else {
                        Text(
                            stringResource(R.string.push_hms_not_init_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
