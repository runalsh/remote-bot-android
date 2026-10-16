package com.remotebot.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SliderSegmentItem<T>(
    val value: T,
    val title: String,
)

@Composable
fun <T> SegmentedSlider(
    items: List<SliderSegmentItem<T>>,
    selectedValue: T,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val selectedIndex =
        items.indexOfFirst { it.value == selectedValue }.let {
            if (it >= 0) it else 0
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(3.dp),
    ) {
        val segmentWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "segmented_slider_indicator",
        )

        // Floating slider thumb
        Box(
            modifier =
                Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(9.dp))
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary),
        )

        // Selection options
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onValueSelected(item.value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
