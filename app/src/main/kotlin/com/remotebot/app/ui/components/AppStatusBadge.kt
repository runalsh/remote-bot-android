package com.remotebot.app.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotebot.app.ui.theme.AppColors

enum class BadgeStatus {
    SUCCESS,
    ERROR,
    WARNING,
    INFO,
    NEUTRAL,
}

@Composable
fun AppStatusBadge(
    text: String,
    status: BadgeStatus,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val (containerColor, contentColor) =
        when (status) {
            BadgeStatus.SUCCESS ->
                if (isDark) {
                    AppColors.successContainerDark to AppColors.onSuccessContainerDark
                } else {
                    AppColors.successContainerLight to AppColors.onSuccessContainerLight
                }
            BadgeStatus.ERROR ->
                if (isDark) {
                    AppColors.errorContainerDark to Color.White
                } else {
                    AppColors.errorContainerLight to AppColors.error
                }
            BadgeStatus.WARNING ->
                if (isDark) {
                    AppColors.warningContainerDark to Color.White
                } else {
                    AppColors.warningContainerLight to AppColors.warning
                }
            BadgeStatus.INFO ->
                if (isDark) {
                    AppColors.infoContainerDark to Color.White
                } else {
                    AppColors.infoContainerLight to AppColors.info
                }
            BadgeStatus.NEUTRAL -> {
                MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
        }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
