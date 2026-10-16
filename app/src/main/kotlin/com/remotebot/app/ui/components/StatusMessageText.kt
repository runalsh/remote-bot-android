package com.remotebot.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotebot.app.ui.theme.AppColors

@Composable
fun StatusMessageText(
    message: String?,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (message.isNullOrBlank()) return

    val color =
        if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            AppColors.success
        }

    Text(
        text = message,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(vertical = 4.dp),
    )
}
