package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.reading.DiarySceneText
import com.daengs.app.ui.walk.reading.RelationalSceneOriginals
import com.daengs.app.walk.diary.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelationalDiaryDisplayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `failed space keeps action original notes and existing location header visible`() {
        val note = "  원문 첫 줄\n다음 줄  "
        val scene = DiaryScene("s/1", "s", 0, "산책 장면 1", "보리가 냄새를 맡았다.", null, "",
            content = DiarySceneContent("", "relational", locationLabel = "", address = "반포2동", temperatureC = 20.0),
            originalNotes = listOf(note), originalPhotos = listOf(DiaryOriginalPhoto("p", null)),
            notice = "공간 문장을 만들지 못했어요.")
        compose.setContent { DaengsTheme { Column {
            DiarySceneHeading(scene, DiarySceneKind.GENERAL, detail = true)
            DiarySceneText(scene.body)
            RelationalSceneOriginals(scene) {}
        } } }
        compose.onNodeWithText("반포2동 · 20°C").assertIsDisplayed()
        compose.onNodeWithText(scene.body).assertIsDisplayed()
        compose.onNodeWithText(note).assertIsDisplayed()
        compose.onNodeWithText(scene.notice).assertIsDisplayed()
        compose.onNodeWithText("사진 파일은 촬영한 기기에서 볼 수 있어요.").assertIsDisplayed()
    }
}
