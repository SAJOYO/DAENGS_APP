package com.daengs.app.ui.chat

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 고른 사진이 **세로로 길어도** 확인 버튼이 화면 안에 있어야 한다.
 *
 * 사진을 가로에 꽉 채우고 비율대로 높이를 주면, 폰 스크린샷 같은 9:20 사진은 화면보다
 * 길어져 제목·버튼이 위아래로 밀려난다 — 사진을 고르고 얼굴을 맞춰도 누를 것이 없었다.
 * 화면 크기를 폰(411×891dp)으로 못 박고 4배 긴 사진을 넣어 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class GuideFrameScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `세로로 긴 사진이어도 확인 버튼이 보인다`() {
        val tall = Bitmap.createBitmap(300, 1200, Bitmap.Config.ARGB_8888)
        var confirmed: FloatArray? = null
        compose.setContent {
            GuideFrameScreen(
                photo = tall,
                onCancel = {},
                onConfirm = { confirmed = it },
                title = "얼굴만 원 안에 넣어 주세요",
                confirmLabel = "이 얼굴로",
                circle = true,
                guidance = "안내",
            )
        }

        compose.onNodeWithText("얼굴만 원 안에 넣어 주세요").assertIsDisplayed()
        compose.onNodeWithText("이 얼굴로").assertIsDisplayed()
        compose.onNodeWithText("이 얼굴로").performClick()
        assertEquals(4, confirmed?.size)
    }

    @Test
    fun `가로 사진은 그대로 버튼이 보인다`() {
        val wide = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
        compose.setContent {
            GuideFrameScreen(photo = wide, onCancel = {}, onConfirm = {}, confirmLabel = "이 자리로 진단")
        }
        compose.onNodeWithText("이 자리로 진단").assertIsDisplayed()
    }
}
