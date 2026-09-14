package com.daengs.app.ui.walk

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiaryReadingSystemBarsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `night system icons become readable and restore on leaving`() = verify(false, false)
    @Test fun `leaving restores each previous bar independently`() = verify(true, false)

    private fun verify(status: Boolean, navigation: Boolean) {
        var showing by mutableStateOf(false)
        lateinit var activity: Activity
        compose.setContent {
            val view = LocalView.current
            SideEffect { activity = requireNotNull(view.context.activity()) }
            if (showing) DiaryReadingSystemBars()
        }
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        compose.runOnIdle {
            bars.isAppearanceLightStatusBars = status
            bars.isAppearanceLightNavigationBars = navigation
            showing = true
        }
        compose.runOnIdle {
            assertTrue(bars.isAppearanceLightStatusBars)
            assertTrue(bars.isAppearanceLightNavigationBars)
            showing = false
        }
        compose.runOnIdle {
            assertEquals(status, bars.isAppearanceLightStatusBars)
            assertEquals(navigation, bars.isAppearanceLightNavigationBars)
        }
    }

    private tailrec fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
}
