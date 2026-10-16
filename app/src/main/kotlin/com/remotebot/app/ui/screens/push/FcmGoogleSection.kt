package com.remotebot.app.ui.screens.push

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.ui.components.StatusMessageText
import com.remotebot.app.ui.theme.AppColors
import com.remotebot.core.push.DynamicFirebaseManager
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Composable
fun FcmGoogleSection(
    context: Context,
    scope: CoroutineScope,
    configRepo: BotConfigRepository,
    googleJsonText: String,
    onGoogleJsonTextChange: (String) -> Unit,
    googlePickerLauncher: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    fcmToken: String?,
    onFcmTokenChange: (String?) -> Unit,
    fcmStatusMessage: String?,
    onFcmStatusMessageChange: (String?) -> Unit,
    isFcmSuccess: Boolean,
    onIsFcmSuccessChange: (Boolean) -> Unit,
    isFcmProcessing: Boolean,
    onIsFcmProcessingChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Google Firebase (FCM)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Динамическая загрузка google-services.json",
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
                    onClick = { googlePickerLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("📁 Выбрать файл")
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotBlank()) {
                                    onGoogleJsonTextChange(text)
                                    Toast.makeText(context, "Вставлено из буфера (${text.length} симв.)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Ошибка буфера: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("📋 Из буфера")
                }

                if (googleJsonText.isNotBlank()) {
                    IconButton(onClick = { onGoogleJsonTextChange("") }) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистить")
                    }
                }
            }

            val googleValidationError =
                if (googleJsonText.isNotBlank()) {
                    DynamicFirebaseManager.parseGoogleServicesJson(googleJsonText).exceptionOrNull()?.message
                } else {
                    null
                }

            OutlinedTextField(
                value = googleJsonText,
                onValueChange = onGoogleJsonTextChange,
                label = { Text("Содержимое google-services.json") },
                placeholder = { Text("{\n  \"project_info\": {\n    \"project_id\": \"my-app\", ...\n  }\n}") },
                isError = googleValidationError != null,
                supportingText = {
                    if (googleValidationError != null) {
                        Text("❌ $googleValidationError", color = MaterialTheme.colorScheme.error)
                    } else if (googleJsonText.isNotBlank()) {
                        Text("✅ JSON валиден и готов к инициализации", color = AppColors.success)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )

            Button(
                onClick = {
                    if (googleJsonText.isBlank()) {
                        onFcmStatusMessageChange("Вставьте google-services JSON перед инициализацией")
                        onIsFcmSuccessChange(false)
                        return@Button
                    }
                    onIsFcmProcessingChange(true)
                    scope.launch {
                        val parseResult = DynamicFirebaseManager.parseGoogleServicesJson(googleJsonText)
                        if (parseResult.isFailure) {
                            onIsFcmProcessingChange(false)
                            onFcmStatusMessageChange("Ошибка парсинга: ${parseResult.exceptionOrNull()?.localizedMessage}")
                            onIsFcmSuccessChange(false)
                            return@launch
                        }

                        val parsed = parseResult.getOrThrow()
                        val initResult = DynamicFirebaseManager.initialize(context, parsed)
                        if (initResult.isFailure) {
                            onIsFcmProcessingChange(false)
                            onFcmStatusMessageChange("Ошибка инициализации Firebase: ${initResult.exceptionOrNull()?.localizedMessage}")
                            onIsFcmSuccessChange(false)
                            return@launch
                        }

                        configRepo.updateConfig { current ->
                            current.copy(
                                googleServicesJson = googleJsonText,
                                fcmProjectId = parsed.projectId,
                                fcmApiKey = parsed.apiKey,
                                fcmAppId = parsed.appId,
                                fcmSenderId = parsed.gcmSenderId,
                            )
                        }

                        val tokenResult = DynamicFirebaseManager.getFcmToken(context)
                        onIsFcmProcessingChange(false)
                        if (tokenResult.isSuccess) {
                            val token = tokenResult.getOrThrow()
                            onFcmTokenChange(token)
                            onIsFcmSuccessChange(true)
                            onFcmStatusMessageChange("✅ Firebase инициализирован! Токен получен.")
                            Toast.makeText(context, "Firebase инициализирован!", Toast.LENGTH_SHORT).show()
                        } else {
                            onIsFcmSuccessChange(false)
                            val errorMsg = tokenResult.exceptionOrNull()?.localizedMessage ?: "Сбой получения токена"
                            onFcmStatusMessageChange("❌ $errorMsg")
                        }
                    }
                },
                enabled = !isFcmProcessing && googleJsonText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.warning),
            ) {
                if (isFcmProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Инициализация...")
                } else {
                    Text("⚡ Применить и инициализировать FCM")
                }
            }

            StatusMessageText(
                message = fcmStatusMessage,
                isError = !isFcmSuccess,
            )

            val isGmsAvailable = remember { DynamicFirebaseManager.isGooglePlayServicesAvailable(context) }
            val isConfigured = googleJsonText.isNotBlank()
            val isInit = isConfigured && DynamicFirebaseManager.isInitialized()

            Surface(
                color =
                    if (!fcmToken.isNullOrBlank()) {
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
                            text = "Статус Google FCM:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color =
                                if (!isGmsAvailable) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else if (isInit && !fcmToken.isNullOrBlank()) {
                                    AppColors.success.copy(alpha = 0.15f)
                                } else if (isInit) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                        ) {
                            Text(
                                text =
                                    if (!isGmsAvailable) {
                                        "🔴 Нет Google Play Services"
                                    } else if (isInit && !fcmToken.isNullOrBlank()) {
                                        "🟢 Активен"
                                    } else if (isInit) {
                                        "🟡 Ожидание токена"
                                    } else {
                                        "⚪ Не инициализирован"
                                    },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (!isGmsAvailable) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else if (isInit && !fcmToken.isNullOrBlank()) {
                                        AppColors.success
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }

                    if (!isGmsAvailable) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚠️ Внимание: ${DynamicFirebaseManager.getGooglePlayServicesStatus(context)}. Для работы FCM требуется Google Play Services.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!fcmToken.isNullOrBlank()) {
                        Text(
                            "Токен FCM устройства (${fcmToken.length} симв.):",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.success,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fcmToken,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 3,
                        )
                    } else {
                        Text(
                            "⚪ FCM не инициализирован. Загрузите файл google-services.json выше.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

}
