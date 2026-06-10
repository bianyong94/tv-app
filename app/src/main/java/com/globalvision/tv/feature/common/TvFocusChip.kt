package com.globalvision.tv.feature.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.globalvision.tv.ui.theme.TvFocusBorder

@Composable
fun TvFocusChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.large
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
        focused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        focused -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (focused) TvFocusBorder else Color.Transparent

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = if (focused) 1.08f else 1f
                scaleY = if (focused) 1.08f else 1f
            }
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = borderColor,
                shape = shape,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            maxLines = 1,
        )
    }
}
