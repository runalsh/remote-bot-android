package com.remotebot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RemovableInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    statusDotColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            showClearButton = false,
            leadingIcon =
                statusDotColor?.let { color ->
                    {
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(color),
                        )
                    }
                },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "—",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
}
