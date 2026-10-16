package com.remotebot.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remotebot.app.ui.components.AppCard
import com.remotebot.app.ui.components.AppTextField
import com.remotebot.core.common.backup.BackupManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.ProxyConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupTab(
    configRepo: BotConfigRepository,
    proxyRepo: ProxyConfigRepository,
    backupManager: BackupManager,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var exportedJson by remember { mutableStateOf("") }
    var importJsonText by remember { mutableStateOf("") }

    // Лаунчер сохранения бэкапа в файл
    val exportFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val botCfg = configRepo.config.value
                        val proxyCfg = proxyRepo.proxyConfig.value
                        val json = backupManager.exportBackup(botCfg, proxyCfg)
                        val pfd =
                            try {
                                context.contentResolver.openFileDescriptor(uri, "rwt")
                            } catch (_: Exception) {
                                try {
                                    context.contentResolver.openFileDescriptor(uri, "wt")
                                } catch (_: Exception) {
                                    null
                                }
                            }

                        if (pfd != null) {
                            pfd.use { descriptor ->
                                java.io.FileOutputStream(descriptor.fileDescriptor).use { os ->
                                    os.write(json.toByteArray(Charsets.UTF_8))
                                    os.flush()
                                    descriptor.fileDescriptor.sync()
                                }
                            }
                        } else {
                            val outputStream =
                                context.contentResolver.openOutputStream(uri, "wt")
                                    ?: context.contentResolver.openOutputStream(uri)
                                    ?: throw IllegalStateException("Не удалось открыть поток для записи в файл ($uri)")
                            outputStream.use { os ->
                                os.write(json.toByteArray(Charsets.UTF_8))
                                os.flush()
                            }
                        }

                        // Обновляем метаданные в медиахранилище, если это файловый путь
                        uri.path?.let { path ->
                            android.media.MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("application/json"), null)
                        }

                        withContext(Dispatchers.Main) {
                            logger.i("Backup", "Конфигурация успешно сохранена в файл: $uri")
                            logger.t("Backup", "Файл бэкапа записан ($uri, размер ${json.length} байт)")
                            Toast.makeText(context, "Файл бэкапа сохранен (${json.length} байт)!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            logger.e("Backup", "Ошибка сохранения бэкапа в файл: ${e.localizedMessage}", e)
                            logger.t("Backup", "Стек ошибки экспорта:\n${e.stackTraceToString()}")
                            Toast.makeText(context, "Ошибка сохранения файла: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                logger.d("Backup", "Пользователь отменил выбор файла для сохранения")
            }
        }

    // Лаунчер открытия бэкапа из файла
    val importFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val content =
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                inputStream.bufferedReader(Charsets.UTF_8).readText()
                            } ?: ""

                        if (content.isBlank()) {
                            withContext(Dispatchers.Main) {
                                logger.w("Backup", "Выбранный файл пуст")
                                Toast.makeText(context, "Выбранный файл пуст", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        val result = backupManager.parseBackup(content.trim())
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                val data = result.getOrThrow()
                                logger.d(
                                    "Backup",
                                    "Бэкап из файла $uri успешно валидирован: ver=${data.version}, date=${data.exportedAtMs}",
                                )
                                logger.t(
                                    "Backup",
                                    "Восстановление конфигурации: botConfig=${data.botConfig}, proxyConfig=${data.proxyConfig}",
                                )
                                scope.launch {
                                    configRepo.updateConfig { data.botConfig }
                                    proxyRepo.updateProxy(data.proxyConfig)
                                    logger.i("Backup", "Конфигурация успешно восстановлена из файла: $uri")
                                    Toast.makeText(context, "Конфигурация восстановлена из файла!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                val err = result.exceptionOrNull()
                                logger.w("Backup", "Ошибка валидации файла бэкапа: ${err?.message}")
                                logger.t("Backup", "Стек ошибки импорта файла:\n${err?.stackTraceToString()}")
                                Toast.makeText(context, "Ошибка в файле бэкапа: ${err?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            logger.e("Backup", "Ошибка чтения файла бэкапа: ${e.localizedMessage}", e)
                            logger.t("Backup", "Стек ошибки чтения:\n${e.stackTraceToString()}")
                            Toast.makeText(context, "Сбой чтения файла: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                logger.d("Backup", "Пользователь отменил выбор файла для импорта")
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
        // Экспорт
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Экспорт конфигурации", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val defaultName = "remotebot-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.json"
                        logger.d("Backup", "Открытие системного пикера для сохранения файла ($defaultName)...")
                        exportFileLauncher.launch(defaultName)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("📁 В файл .json")
                }

                OutlinedButton(
                    onClick = {
                        logger.d("Backup", "Формирование резервной копии настроек бота и прокси...")
                        val botCfg = configRepo.config.value
                        val proxyCfg = proxyRepo.proxyConfig.value
                        exportedJson = backupManager.exportBackup(botCfg, proxyCfg)
                        logger.i("Backup", "Экспортирована резервная копия конфигурации в буфер")
                        logger.t("Backup", "Экспортированный JSON (${exportedJson.length} символов):\n$exportedJson")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("RemoteBot Backup", exportedJson))
                        Toast.makeText(context, "Бэкап скопирован в буфер", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("📋 В буфер")
                }
            }

            if (exportedJson.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = exportedJson,
                    onValueChange = {},
                    readOnly = true,
                    label = "Сгенерированный JSON",
                    singleLine = false,
                    maxLines = 6,
                    showClearButton = false,
                )
            }
        }

        // Импорт
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Восстановление конфигурации", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    logger.d("Backup", "Открытие системного пикера файлов для импорта...")
                    importFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("📂 Выбрать и восстановить из файла .json")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Или вставьте JSON вручную:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))

            AppTextField(
                value = importJsonText,
                onValueChange = { importJsonText = it },
                label = "Вставьте JSON бэкапа",
                placeholder = "{\"version\": \"2.0.0\", ...}",
                singleLine = false,
                maxLines = 6,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    logger.d("Backup", "Парсинг и валидация JSON бэкапа (${importJsonText.length} символов)...")
                    logger.t("Backup", "Входной JSON:\n$importJsonText")
                    val result = backupManager.parseBackup(importJsonText.trim())
                    if (result.isSuccess) {
                        val data = result.getOrThrow()
                        logger.d("Backup", "JSON успешно валидирован: version=${data.version}, created=${data.exportedAtMs}")
                        logger.t("Backup", "Восстанавливаемый botConfig: ${data.botConfig}, proxyConfig: ${data.proxyConfig}")
                        scope.launch {
                            configRepo.updateConfig { data.botConfig }
                            proxyRepo.updateProxy(data.proxyConfig)
                            logger.i("Backup", "Конфигурация успешно восстановлена из импортированного JSON")
                            Toast.makeText(context, "Конфигурация восстановлена!", Toast.LENGTH_LONG).show()
                            importJsonText = ""
                        }
                    } else {
                        val err = result.exceptionOrNull()
                        logger.w("Backup", "Ошибка валидации JSON при импорте: ${err?.message}")
                        logger.t("Backup", "Стек ошибки импорта:\n${err?.stackTraceToString()}")
                        Toast.makeText(context, "Ошибка валидации JSON: ${err?.message}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = importJsonText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Восстановить из текста")
            }
        }
    }
}
