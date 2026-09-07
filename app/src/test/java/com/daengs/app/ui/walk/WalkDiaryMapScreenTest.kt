package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.walk.diary.DiaryScene
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkDiaryMapScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `선택한 장면에서 다른 산책 장면으로 이동하고 원본 산책을 연다`() {
        val a = DiaryScene("a/n", "a", 0, "첫날 메모", "메모 내용", null, "직접 남긴 기록")
        val b = a.copy(id = "b/n", sessionId = "b", atMillis = 10000, title = "다음날 메모")
        var opened: String? = null
        compose.setContent {
            var selected by remember { mutableStateOf<DiaryScene?>(null) }
            WalkDiaryMapContent(listOf(a, b), selected, false, false, false, null, null,
                {}, {}, { selected = it }, { selected = null }, { opened = it }, {}, {},
                map = { Box(Modifier.fillMaxSize().testTag("diary-map")) })
        }
        compose.onNodeWithText("첫날 메모").performClick()
        compose.onNodeWithText("이전").assertIsNotEnabled()
        compose.onNodeWithText("확인된 위치가 없어 지도에 점을 찍지 않았어요.").assertExists()
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("다음날 메모").assertExists()
        compose.onNodeWithText("원본 산책").performClick()
        assertEquals("b", opened)
        compose.onNodeWithTag("diary-map").assertIsDisplayed()
        compose.onNodeWithText("장면 목록").performClick()
        compose.onNodeWithText("2개 장면 · 오래된 순").assertExists()
    }

    @Test fun `같은 축은 복수 선택하고 전체는 해당 축만 초기화한다`() {
        compose.setContent {
            var seasons by remember { mutableStateOf(emptyList<String>()) }
            var weather by remember { mutableStateOf(listOf("RAIN")) }
            DiaryHistoryFilters(true, seasons, weather, {}, { seasons = it }, { weather = it })
        }
        compose.onNodeWithText("봄").performClick()
        compose.onNodeWithText("가을").performClick()
        compose.onNodeWithText("봄").assertIsSelected()
        compose.onNodeWithText("가을").assertIsSelected()
        compose.onNodeWithText("모든 계절").performClick()
        compose.onNodeWithText("봄").assertIsNotSelected()
        compose.onNodeWithText("비").assertIsSelected()
    }

    @Test fun `작은 화면에서도 지도와 카드 이동 버튼이 함께 보인다`() {
        var renderedView: android.view.View? = null
        val scene = DiaryScene("s/n", "s", 0, "벤치 옆에서 잠깐 쉬었다", "나무 그늘에서 함께 쉰 날",
            null, "직접 남긴 메모")
        compose.setContent {
            val view = androidx.compose.ui.platform.LocalView.current
            SideEffect { renderedView = view.rootView }
            Column {
                DiaryHistoryFilters(true, listOf("AUTUMN"), listOf("RAIN"), {}, {}, {})
                WalkDiaryMapContent(listOf(scene), scene, false, false, false, null, null,
                    {}, {}, {}, {}, {}, {}, {}, modifier = Modifier.weight(1f),
                    map = { Box(Modifier.fillMaxSize().background(Color(0xFFE1EBDE)).testTag("diary-map")) })
            }
        }
        compose.onNodeWithTag("diary-map").assertIsDisplayed()
        compose.onNodeWithText("원본 산책").assertIsDisplayed()
        compose.onNodeWithText("장면 목록").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("diary-map").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height >= 120)
        assertTrue(compose.onNodeWithText("원본 산책").fetchSemanticsNode().boundsInRoot.top >= bounds.bottom)
        val output = java.io.File("build/outputs/walk-diary-map-card.png")
        requireNotNull(output.parentFile).mkdirs()
        compose.runOnIdle {
            val view = requireNotNull(renderedView)
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            output.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
