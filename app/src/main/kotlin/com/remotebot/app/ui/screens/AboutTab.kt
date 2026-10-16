package com.remotebot.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.BuildConfig
import com.remotebot.app.ui.components.AppCard
import com.remotebot.app.ui.components.AppOutlinedCard
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.launch

@Composable
fun AboutTab(
    configRepo: BotConfigRepository? = null,
    onNavigateToDebug: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    fun copyToClipboard(
        label: String,
        text: String,
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
    }

    fun openUrl(url: String) {
        try {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header and application badge
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                ),
            contentPadding = 20.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🤖", fontSize = 28.sp)
                    }
                }
                Column {
                    Text(
                        text = "Remote Bot",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Удаленный контроль Android через Telegram & SMS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Card 1: App version and build time
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("ℹ️", fontSize = 18.sp)
                Text(
                    text = "Версия и параметры сборки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            val displayVersion = if (BuildConfig.VERSION_NAME.startsWith("v")) BuildConfig.VERSION_NAME else "v${BuildConfig.VERSION_NAME}"
            AboutInfoRow(
                title = "Версия приложения",
                value = "$displayVersion (код: ${BuildConfig.VERSION_CODE})",
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime > 2000L) {
                        tapCount = 1
                    } else {
                        tapCount++
                    }
                    lastTapTime = now

                    if (tapCount >= 5) {
                        tapCount = 0
                        Toast.makeText(context, "Вы вошли в режим отладки 🛠️", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            configRepo?.updateConfig { it.copy(isDebugModeEnabled = true) }
                            onNavigateToDebug?.invoke()
                        }
                    } else if (tapCount >= 2) {
                        val left = 5 - tapCount
                        val word =
                            if (left == 1) {
                                "тап"
                            } else if (left in 2..4) {
                                "тапа"
                            } else {
                                "тапов"
                            }
                        Toast.makeText(context, "Осталось $left $word до открытия Debug меню...", Toast.LENGTH_SHORT).show()
                    }
                },
            )

            AboutInfoRow(
                title = "Дата и время сборки",
                value = BuildConfig.BUILD_TIME,
            )

            AboutInfoRow(
                title = "Идентификатор сборки (Commit)",
                value = BuildConfig.BUILD_ID,
            )

            AboutInfoRow(
                title = "Тип сборки",
                value = "${BuildConfig.BUILD_TYPE.uppercase()} (SDK 35 / Android 15)",
            )

            AboutInfoRow(
                title = "Система устройства",
                value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )

            AboutInfoRow(
                title = "Архитектура CPU",
                value = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            )
        }

        // Card 2: GitHub repository link
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🐙", fontSize = 18.sp)
                Text(
                    text = "Исходный код (GitHub)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Открытый репозиторий проекта, история изменений, документация и релизы:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = BuildConfig.GITHUB_URL,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { openUrl(BuildConfig.GITHUB_URL) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("🌐 Открыть GitHub")
                }
                OutlinedButton(
                    onClick = { copyToClipboard("Ссылка GitHub", BuildConfig.GITHUB_URL) },
                ) {
                    Text("📋 Скопировать")
                }
            }
        }

        // Card 3: Author and Telegram
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("✈️", fontSize = 18.sp)
                Text(
                    text = "Автор и поддержка в Telegram",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "По вопросам работы бота, предложениям функций и обратной связи:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = BuildConfig.TELEGRAM_AUTHOR_USERNAME,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = BuildConfig.TELEGRAM_AUTHOR_URL,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { openUrl(BuildConfig.TELEGRAM_AUTHOR_URL) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("💬 Открыть Telegram")
                }
                OutlinedButton(
                    onClick = { copyToClipboard("Telegram автора", BuildConfig.TELEGRAM_AUTHOR_USERNAME) },
                ) {
                    Text("📋 Скопировать")
                }
            }
        }

        // Card 4: Tech stack and license
        AppOutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "⚖️ Лицензия & Архитектура",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text =
                    "• Лицензия: Apache License 2.0\n" +
                        "• Архитектура: Clean Architecture + Модульный Kotlin Gradle проект\n" +
                        "• Стек: Jetpack Compose, Material 3, Coroutines, Flow, Koin, Ktor, Room, CameraX, libsu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutInfoRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClick() }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    } else {
                        Modifier
                    },
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
