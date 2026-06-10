package com.globalvision.tv.feature.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TvScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    showTitle: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable () -> Unit,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(
                if ((showTitle && title.isNotBlank()) || actions != null || onBack != null) 16.dp else 0.dp,
            ),
        ) {
            if ((showTitle && title.isNotBlank()) || actions != null || onBack != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showTitle && title.isNotBlank()) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    }
                    actions?.invoke()
                    if (onBack != null) {
                        Button(onClick = onBack) { Text("返回") }
                    }
                }
            }
            content()
        }
    }
}
