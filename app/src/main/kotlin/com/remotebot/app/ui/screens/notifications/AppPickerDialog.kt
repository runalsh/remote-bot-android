package com.remotebot.app.ui.screens.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.InstalledApp
import com.remotebot.domain.repository.AppManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppPickerDialog(
    appManagerRepo: AppManagerRepository,
    initialSelected: Set<String>,
    logger: AppLogger? = null,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var showSystem by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(initialSelected) }
    var allApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(showSystem) {
        isLoading = true
        val apps =
            withContext(Dispatchers.IO) {
                val list =
                    appManagerRepo
                        .getInstalledApps(includeSystem = showSystem, filter = null)
                        .sortedBy { it.appName.lowercase() }
                logger?.d("AppPickerDialog", "Загружен список приложений: ${list.size} шт. (showSystem=$showSystem)")
                list
            }
        allApps = apps
        isLoading = false
    }

    val filteredApps =
        remember(allApps, searchQuery) {
            if (searchQuery.isBlank()) {
                allApps
            } else {
                allApps.filter {
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Выбор приложений (${selectedPackages.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(450.dp),
            ) {
                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск по названию или пакету") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // "Show system apps" switch
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { showSystem = !showSystem },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Показывать системные приложения",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = showSystem,
                        onCheckedChange = { showSystem = it },
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val currentFilteredPkgs = filteredApps.map { it.packageName }.toSet()
                            selectedPackages = selectedPackages + currentFilteredPkgs
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Выбрать все", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val currentFilteredPkgs = filteredApps.map { it.packageName }.toSet()
                            selectedPackages = selectedPackages - currentFilteredPkgs
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Снять все", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Приложения не найдены",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isChecked = selectedPackages.contains(app.packageName)
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPackages =
                                                if (isChecked) {
                                                    selectedPackages - app.packageName
                                                } else {
                                                    selectedPackages + app.packageName
                                                }
                                        }.padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedPackages =
                                            if (checked) {
                                                selectedPackages + app.packageName
                                            } else {
                                                selectedPackages - app.packageName
                                            }
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (app.isSystemApp) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                            ) {
                                                Text(
                                                    text = "система",
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                logger?.i("AppPickerDialog", "Выбрано пакетов: ${selectedPackages.size}")
                onConfirm(selectedPackages)
            }) {
                Text("Готово (${selectedPackages.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
