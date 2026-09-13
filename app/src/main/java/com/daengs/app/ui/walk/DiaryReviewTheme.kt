package com.daengs.app.ui.walk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.theme.DaengsTheme

/** Preview/test stress scale, applied after the app's intentional fixed-font theme. */
@Composable
internal fun DiaryReviewTheme(content: @Composable () -> Unit) {
    val requestedFontScale = LocalDensity.current.fontScale
    DaengsTheme {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, requestedFontScale)) {
            content()
        }
    }
}
