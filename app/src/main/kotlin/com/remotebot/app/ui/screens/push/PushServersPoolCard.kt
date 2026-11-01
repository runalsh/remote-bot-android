package com.remotebot.app.ui.screens.push

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.remotebot.app.R
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.EndpointSelectionStrategy
import com.remotebot.domain.model.PushProvider
import com.remotebot.domain.model.PushServerConfig
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PushServersPoolCard(
    context: Context,
    scope: CoroutineScope,
    config: BotConfig,
    configRepo: BotConfigRepository,
    logger: AppLogger,
) {
    var serversList by remember(config.pushServers) {
        mutableStateOf(config.pushServers)
    }
    var strategy by remember(config.pushServerSelectionStrategy) {
        mutableStateOf(config.pushServerSelectionStrategy)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.push_pool_servers_title, serversList.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.push_pool_servers_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    val newServer =
                        PushServerConfig(
                            id = UUID.randomUUID().toString(),
                            name = context.getString(R.string.push_pool_server_item_title, serversList.size + 1),
                            gatewayUrl = "",
                            secretKey = "",
                            provider = PushProvider.FCM,
                        )
                    serversList = serversList + newServer
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.sms_trusted_numbers_add), tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (serversList.isEmpty()) {
                Text(
                    text = stringResource(R.string.push_pool_empty_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.push_pool_strategy_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EndpointSelectionStrategy.entries.forEach { st ->
                        val selected = strategy == st
                        FilterChip(
                            selected = selected,
                            onClick = {
                                strategy = st
                                scope.launch {
                                    configRepo.updateConfig { it.copy(pushServerSelectionStrategy = st) }
                                }
                            },
                            label = {
                                Text(
                                    when (st) {
                                        EndpointSelectionStrategy.FAILOVER -> stringResource(R.string.tg_strategy_failover)
                                        EndpointSelectionStrategy.ROUND_ROBIN -> stringResource(R.string.tg_strategy_round_robin)
                                        EndpointSelectionStrategy.LOWEST_LATENCY -> stringResource(R.string.tg_strategy_latency)
                                    },
                                    fontSize = 11.sp,
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                serversList.forEachIndexed { index, server ->
                    PushServerItemEditor(
                        index = index,
                        server = server,
                        context = context,
                        onUpdate = { updated ->
                            val list = serversList.toMutableList()
                            list[index] = updated
                            serversList = list
                        },
                        onDelete = {
                            val list = serversList.toMutableList()
                            list.removeAt(index)
                            serversList = list
                        },
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            configRepo.updateConfig { current ->
                                current.copy(
                                    pushServers = serversList,
                                    pushServerSelectionStrategy = strategy,
                                )
                            }
                            logger.i("Push", "Push server pool updated: ${serversList.size} items, strategy: $strategy")
                            Toast.makeText(context, context.getString(R.string.push_pool_servers_saved), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.push_pool_save_btn))
                }
            }
        }
    }
}

@Composable
private fun PushServerItemEditor(
    index: Int,
    server: PushServerConfig,
    context: Context,
    onUpdate: (PushServerConfig) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val content = stream.bufferedReader().readText()
                        if (server.provider == PushProvider.HMS) {
                            onUpdate(server.copy(agconnectServicesJson = content))
                        } else {
                            onUpdate(server.copy(googleServicesJson = content))
                        }
                        Toast.makeText(context, context.getString(R.string.push_pool_json_loaded), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.push_pool_read_file_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "#${index + 1} ${server.name.ifBlank { stringResource(R.string.push_pool_server_badge) }}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) stringResource(R.string.push_pool_collapse) else stringResource(R.string.push_pool_configure))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            OutlinedTextField(
                value = server.gatewayUrl,
                onValueChange = { onUpdate(server.copy(gatewayUrl = it)) },
                label = { Text(stringResource(R.string.push_pool_domain_url)) },
                placeholder = { Text("https://push-s${index + 1}.yourdomain.com", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (expanded) {
                OutlinedTextField(
                    value = server.name,
                    onValueChange = { onUpdate(server.copy(name = it)) },
                    label = { Text(stringResource(R.string.push_pool_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = server.secretKey,
                    onValueChange = { onUpdate(server.copy(secretKey = it)) },
                    label = { Text(stringResource(R.string.push_pool_secret)) },
                    placeholder = { Text(stringResource(R.string.push_pool_secret_placeholder), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(stringResource(R.string.push_pool_provider_type), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PushProvider.FCM to "Google FCM", PushProvider.HMS to "Huawei HMS").forEach { (prov, title) ->
                        FilterChip(
                            selected = server.provider == prov,
                            onClick = { onUpdate(server.copy(provider = prov)) },
                            label = { Text(title, fontSize = 11.sp) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasJson =
                        if (server.provider == PushProvider.HMS) {
                            !server.agconnectServicesJson.isNullOrBlank()
                        } else {
                            !server.googleServicesJson.isNullOrBlank()
                        }
                    Text(
                        text =
                            if (hasJson) stringResource(R.string.push_json_loaded) else stringResource(R.string.push_json_not_loaded),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasJson) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { filePickerLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Text(stringResource(R.string.push_pool_choose_json), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
