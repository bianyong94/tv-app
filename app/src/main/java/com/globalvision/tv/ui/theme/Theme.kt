package com.globalvision.tv.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val TvColorScheme = darkColorScheme(
    background = TvBackground,
    surface = TvSurface,
    surfaceVariant = TvSurfaceVariant,
    primary = TvPrimary,
    onPrimary = TvOnPrimary,
)

@Composable
fun GlobalVisionTvTheme(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    val uiScale = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        when {
            configuration.screenWidthDp >= 2200 || configuration.screenHeightDp >= 1200 -> 1.22f
            configuration.screenWidthDp >= 1600 || configuration.screenHeightDp >= 900 -> 1.12f
            else -> 1f
        }
    }
    val scaledDensity = remember(baseDensity, uiScale) {
        Density(
            density = baseDensity.density * uiScale,
            fontScale = baseDensity.fontScale * uiScale,
        )
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = TvColorScheme,
            typography = TvTypography,
            content = content,
        )
    }
}
