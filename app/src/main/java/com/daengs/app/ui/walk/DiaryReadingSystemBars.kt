package com.daengs.app.ui.walk

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** This reader is always light, even when the system's automatic bar style is dark. */
@Composable
internal fun DiaryReadingSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.readingActivity()?.window ?: return
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

private tailrec fun Context.readingActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.readingActivity()
    else -> null
}
