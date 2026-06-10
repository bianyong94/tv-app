package com.globalvision.tv.feature.common

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import com.globalvision.tv.core.model.TvPosterItem
import com.globalvision.tv.ui.theme.TvFocusBorder

@Composable
fun TvPosterCard(
    item: TvPosterItem,
    width: androidx.compose.ui.unit.Dp = 220.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = MaterialTheme.shapes.medium
    Card(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .graphicsLayer {
                scaleX = if (focused) 1.05f else 1f
                scaleY = if (focused) 1.05f else 1f
            }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvFocusBorder else Color.Transparent,
                shape = cardShape,
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(cardShape),
            ) {
                AsyncImage(
                    model = item.posterUrl.takeIf { it.isNotBlank() },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            color = if (focused) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        ),
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 2.dp),
                maxLines = 1,
            )
            if (item.remark.isNotBlank() || item.year.isNotBlank()) {
                Text(
                    text = listOf(item.year, item.remark).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
