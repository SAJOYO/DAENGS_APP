package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.map.style.WalkStyleStore
import com.daengs.app.ui.theme.DaengsTheme
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp")
class WalkSpeedLegendTest {
    @get:Rule val compose = createComposeRule()

    @Test fun policyRefreshUpdatesVisibleScaleWithoutReopeningMap() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = WalkStyleStore(context)
        store.preferences.edit().clear().commit()
        compose.setContent { DaengsTheme { WalkSpeedLegend() } }
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithText("2+").assertIsDisplayed()
        val source = context.assets.open("walk-style-v1.json").bufferedReader().use { it.readText() }
        val update = JSONObject(source).put("speed_max_mps", 4.0)
            .put("boundaries_mps", org.json.JSONArray(listOf(.8, 1.6, 2.4, 3.2)))
        compose.runOnIdle { store.cachePolicy(update.toString()) }
        compose.onNodeWithText("4+").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("속도 미확인").assertDoesNotExist()
        compose.onNodeWithContentDescription("핑크 속도 스펙트럼. 왼쪽 0 m/s에서 오른쪽 4 m/s 이상으로 빨라져요.").assertIsDisplayed()
    }
}
