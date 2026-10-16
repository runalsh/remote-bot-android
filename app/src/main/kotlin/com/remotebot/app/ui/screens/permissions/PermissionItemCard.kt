package com.remotebot.app.ui.screens.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.ui.components.AppCard
import com.remotebot.app.ui.components.AppStatusBadge
import com.remotebot.app.ui.components.BadgeStatus

@Composable
fun PermissionItemCard(
    icon: String,
    title: String,
    isGranted: Boolean,
    description: String,
    commandsHint: String,
    buttonText: String,
    onRequest: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = icon, fontSize = 20.sp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AppStatusBadge(
                text = if (isGranted) "✅ ВЫДАНО" else "❌ НЕТ",
                status = if (isGranted) BadgeStatus.SUCCESS else BadgeStatus.ERROR,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Команды: $commandsHint",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
        )

        if (!isGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(buttonText)
            }
        }
    }
}
