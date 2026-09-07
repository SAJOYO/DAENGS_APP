package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.map.style.WalkStyleStore
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkColorSettingsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun chooseColorFromSettingsAndReopen() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WalkStyleStore(context).preferences.edit().clear().commit()
        compose.setContent { DaengsTheme { WalkSpeedLegend(showColorSettings = true) } }
        compose.onNodeWithText("속도 m/s").assertIsDisplayed()
        compose.onNodeWithText("속도 미확인").assertDoesNotExist()
        compose.onNodeWithText("색상").performClick()
        compose.onNodeWithText("산책 지도 설정").assertIsDisplayed()
        compose.onNodeWithText("파랑").performClick()
        compose.onNodeWithText("완료").performClick()
        assertEquals("blue", WalkStyleStore(context).read().themeId)
        compose.onNodeWithContentDescription("파랑 속도 스펙트럼. 왼쪽 0 m/s에서 오른쪽 2 m/s 이상으로 빨라져요.").assertIsDisplayed()
        compose.onNodeWithText("색상").performClick()
        compose.onNodeWithText("파랑").assertIsDisplayed()
    }
}
