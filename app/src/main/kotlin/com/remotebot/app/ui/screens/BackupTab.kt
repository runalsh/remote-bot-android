package com.remotebot.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remotebot.app.R
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

    var exportedJson by remember { mutableStateOf("") }
    var importJsonText by remember { mutableStateOf("") }

    // Launcher for exporting backup to file
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
                                    ?: throw IllegalStateException("Failed to open an output stream for file ($uri)")
                            outputStream.use { os ->
                                os.write(json.toByteArray(Charsets.UTF_8))
                                os.flush()
                            }
                        }

                        // Update media store metadata if this is a file path
                        uri.path?.let { path ->
                            android.media.MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("application/json"), null)
                        }

                        withContext(Dispatchers.Main) {
                            logger.i("Backup", "Configuration successfully saved to file: $uri")
                            logger.t("Backup", "Backup file written ($uri, size ${json.length} bytes)")
                            Toast.makeText(context, context.getString(R.string.backup_file_saved, json.length), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            logger.e("Backup", "Backup save-to-file error: ${e.localizedMessage}", e)
                            logger.t("Backup", "Export error stack:\n${e.stackTraceToString()}")
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.backup_file_save_error, e.localizedMessage ?: ""),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }
            } else {
                logger.d("Backup", "User cancelled the save-file picker")
            }
        }

    // Launcher for importing backup from file
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
                                logger.w("Backup", "Selected file is empty")
                                Toast.makeText(context, context.getString(R.string.backup_file_empty), Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        val result = backupManager.parseBackup(content.trim())
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                val data = result.getOrThrow()
                                logger.d(
                                    "Backup",
                                    "Backup from file $uri validated successfully: ver=${data.version}, date=${data.exportedAtMs}",
                                )
                                logger.t(
                                    "Backup",
                                    "Restoring configuration: botConfig=${data.botConfig}, proxyConfig=${data.proxyConfig}",
                                )
                                scope.launch {
                                    configRepo.updateConfig { data.botConfig }
                                    proxyRepo.updateProxy(data.proxyConfig)
                                    logger.i("Backup", "Configuration successfully restored from file: $uri")
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.backup_restore_file_success),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }
                            } else {
                                val err = result.exceptionOrNull()
                                logger.w("Backup", "Backup file validation error: ${err?.message}")
                                logger.t("Backup", "File import error stack:\n${err?.stackTraceToString()}")
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.backup_file_format_error, err?.message ?: ""),
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            logger.e("Backup", "Backup file read error: ${e.localizedMessage}", e)
                            logger.t("Backup", "Read error stack:\n${e.stackTraceToString()}")
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.backup_read_error, e.localizedMessage ?: ""),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }
            } else {
                logger.d("Backup", "User cancelled the import-file picker")
            }
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Export
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.backup_export_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val defaultName = "remotebot-backup-${SimpleDateFormat(
                                "yyyyMMdd-HHmmss",
                                Locale.getDefault(),
                            ).format(Date())}.json"
                            logger.d("Backup", "Opening the system save-file picker ($defaultName)...")
                            exportFileLauncher.launch(defaultName)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.backup_export_button))
                    }

                    OutlinedButton(
                        onClick = {
                            logger.d("Backup", "Building the bot and proxy settings backup...")
                            val botCfg = configRepo.config.value
                            val proxyCfg = proxyRepo.proxyConfig.value
                            exportedJson = backupManager.exportBackup(botCfg, proxyCfg)
                            logger.i("Backup", "Configuration backup exported to the clipboard")
                            logger.t("Backup", "Exported JSON (${exportedJson.length} chars):\n$exportedJson")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("RemoteBot Backup", exportedJson))
                            Toast.makeText(context, context.getString(R.string.backup_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.backup_clipboard_button))
                    }
                }

                if (exportedJson.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppTextField(
                        value = exportedJson,
                        onValueChange = {},
                        readOnly = true,
                        label = stringResource(R.string.backup_file_json),
                        singleLine = false,
                        maxLines = 6,
                        showClearButton = false,
                    )
                }
            }
        }

        // Import
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.backup_import_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        logger.d("Backup", "Opening the system file picker for import...")
                        importFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_import_file_button))
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.backup_or_paste_manually),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))

                AppTextField(
                    value = importJsonText,
                    onValueChange = { importJsonText = it },
                    label = stringResource(R.string.backup_json_label),
                    placeholder = "{\"version\": \"2.0.0\", ...}",
                    singleLine = false,
                    maxLines = 6,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        logger.d("Backup", "Parsing and validating backup JSON (${importJsonText.length} chars)...")
                        logger.t("Backup", "Input JSON:\n$importJsonText")
                        val result = backupManager.parseBackup(importJsonText.trim())
                        if (result.isSuccess) {
                            val data = result.getOrThrow()
                            logger.d("Backup", "JSON validated successfully: version=${data.version}, created=${data.exportedAtMs}")
                            logger.t("Backup", "Restorable botConfig: ${data.botConfig}, proxyConfig: ${data.proxyConfig}")
                            scope.launch {
                                configRepo.updateConfig { data.botConfig }
                                proxyRepo.updateProxy(data.proxyConfig)
                                logger.i("Backup", "Configuration successfully restored from the imported JSON")
                                Toast.makeText(context, context.getString(R.string.backup_restored_success), Toast.LENGTH_LONG).show()
                                importJsonText = ""
                            }
                        } else {
                            val err = result.exceptionOrNull()
                            logger.w("Backup", "JSON validation error on import: ${err?.message}")
                            logger.t("Backup", "Import error stack:\n${err?.stackTraceToString()}")
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.backup_validation_error, err?.message ?: ""),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    },
                    enabled = importJsonText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_btn_restore_text))
                }
            }
        }
    }
}
