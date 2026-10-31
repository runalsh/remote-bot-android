package com.remotebot.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.remotebot.app.R
import com.remotebot.app.ui.components.RemovableInputRow
import com.remotebot.app.ui.components.StatusMessageText
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.repository.BotConfigRepository
import kotlinx.coroutines.launch

@Composable
fun SmsSettingsTab(
    configRepo: BotConfigRepository,
    logger: AppLogger,
) {
    val context = LocalContext.current
    val config by configRepo.config.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var smsBotEnabled by remember(config.smsBotEnabled) { mutableStateOf(config.smsBotEnabled) }
    var smsTransliterationEnabled by remember(config.smsTransliterationEnabled) { mutableStateOf(config.smsTransliterationEnabled) }

    var targetNumbersList by remember(config.smsForwardTargetNumbers) {
        mutableStateOf(if (config.smsForwardTargetNumbers.isEmpty()) emptyList<String>() else config.smsForwardTargetNumbers.toList())
    }
    var pinInput by remember(config.smsPin) { mutableStateOf(config.smsPin ?: "") }
    var allowedPhonesList by remember(config.allowedPhoneNumbers) {
        mutableStateOf(if (config.allowedPhoneNumbers.isEmpty()) emptyList<String>() else config.allowedPhoneNumbers.toList())
    }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card 1: Main SMS bot master toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (smsBotEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sms_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text =
                                if (smsBotEnabled) {
                                    stringResource(R.string.sms_card_enabled_desc)
                                } else {
                                    stringResource(R.string.sms_card_disabled_desc)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = smsBotEnabled,
                        onCheckedChange = { isChecked ->
                            smsBotEnabled = isChecked
                            statusMessage = null
                        },
                    )
                }
            }
        }

        // Card 2: Forwarding destination numbers (SMS -> SMS & Push -> SMS)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.sms_forward_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = {
                            targetNumbersList = targetNumbersList + ""
                            statusMessage = null
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.sms_forward_add_btn),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.sms_forward_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (targetNumbersList.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sms_forward_empty_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    targetNumbersList.forEachIndexed { index, phoneStr ->
                        RemovableInputRow(
                            value = phoneStr,
                            onValueChange = { newVal ->
                                val list = targetNumbersList.toMutableList()
                                list[index] = newVal.trim()
                                targetNumbersList = list
                                statusMessage = null
                            },
                            placeholder = stringResource(R.string.sms_target_placeholder, index + 1),
                            onRemove = {
                                val list = targetNumbersList.toMutableList()
                                list.removeAt(index)
                                targetNumbersList = list
                                statusMessage = null
                            },
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // Card 3: SMS sending parameters
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.sms_params_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Transliteration
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { smsTransliterationEnabled = !smsTransliterationEnabled }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.sms_translit_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.sms_translit_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = smsTransliterationEnabled,
                        onCheckedChange = { smsTransliterationEnabled = it },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("💡", fontSize = 18.sp)
                        Text(
                            text = stringResource(R.string.sms_types_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Card 4: Remote control via SMS (Offline Bot)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.sms_offline_bot_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.sms_offline_bot_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it.trim()
                        statusMessage = null
                    },
                    label = { Text(stringResource(R.string.sms_pin_label)) },
                    placeholder = { Text(stringResource(R.string.sms_pin_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.sms_trusted_numbers_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = {
                            allowedPhonesList = allowedPhonesList + ""
                            statusMessage = null
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.sms_trusted_numbers_add),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (allowedPhonesList.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sms_trusted_numbers_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    allowedPhonesList.forEachIndexed { index, phoneStr ->
                        RemovableInputRow(
                            value = phoneStr,
                            onValueChange = { newVal ->
                                val list = allowedPhonesList.toMutableList()
                                list[index] = newVal.trim()
                                allowedPhonesList = list
                                statusMessage = null
                            },
                            placeholder = stringResource(R.string.sms_target_placeholder, index + 1),
                            onRemove = {
                                val list = allowedPhonesList.toMutableList()
                                list.removeAt(index)
                                allowedPhonesList = list
                                statusMessage = null
                            },
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // Save button
        val saveSuccessMsg = stringResource(R.string.sms_saved_success)
        val saveToastMsg = stringResource(R.string.sms_saved_toast)
        Button(
            onClick = {
                val cleanPin = pinInput.trim().ifBlank { null }
                val cleanPhones = allowedPhonesList.map { it.trim() }.filter { it.isNotBlank() }.toSet()
                val cleanTargets = targetNumbersList.map { it.trim() }.filter { it.isNotBlank() }.toSet()

                // Auto-protection: if destination numbers list is empty, disable forwarding
                val effectiveBotEnabled = smsBotEnabled && (cleanTargets.isNotEmpty() || cleanPhones.isNotEmpty() || cleanPin != null)

                scope.launch {
                    configRepo.updateConfig {
                        it.copy(
                            smsBotEnabled = effectiveBotEnabled,
                            smsForwardTargetNumbers = cleanTargets,
                            smsTransliterationEnabled = smsTransliterationEnabled,
                            smsPin = cleanPin,
                            allowedPhoneNumbers = cleanPhones,
                        )
                    }
                    isSuccess = true
                    statusMessage = saveSuccessMsg
                    Toast.makeText(context, saveToastMsg, Toast.LENGTH_SHORT).show()
                    logger.i(
                        "SmsConfig",
                        "Saved SMS config: enabled=$effectiveBotEnabled, targets=$cleanTargets, translit=$smsTransliterationEnabled",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sms_save_button))
        }

        StatusMessageText(
            message = statusMessage,
            isError = !isSuccess,
        )
    }
}
