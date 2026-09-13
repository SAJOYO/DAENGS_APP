package com.daengs.app.ui.walk

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual reader/explorer compositions at every review size; fonts must reach Text layout. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryReadingMatrixTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(qualifiers = "w320dp-h640dp") fun small() = matrix(320, 1f)
    @Test @Config(qualifiers = "w320dp-h640dp") fun smallLargeText() = matrix(320, 1.3f)
    @Test @Config(qualifiers = "w360dp-h800dp") fun medium() = matrix(360, 1f)
    @Test @Config(qualifiers = "w360dp-h800dp") fun mediumLargeText() = matrix(360, 1.3f)
    @Test @Config(qualifiers = "w390dp-h844dp") fun standard() = matrix(390, 1f)
    @Test @Config(qualifiers = "w390dp-h844dp") fun standardLargeText() = matrix(390, 1.3f)

    private fun matrix(width: Int, fontScale: Float) {
        var sample by mutableStateOf(0)
        var rendered: android.view.View? = null
        val reader = DiaryReadingExample.entries
        val explorer = ExplorerPanelExample.entries
        compose.setContent {
            val view = LocalView.current
            SideEffect { rendered = view.rootView }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                key(sample) {
                    if (sample < reader.size) DiaryReadingStylePreview(reader[sample])
                    else WalkExplorerPanelPreview(explorer[sample - reader.size])
                }
            }
        }
        for (index in 0 until reader.size + explorer.size) {
            compose.runOnIdle { sample = index }
            val title = if (index == reader.indexOf(DiaryReadingExample.LONG_TITLE))
                "보리와 함께 오래도록 기억하고 싶은 저녁 산책의 기록"
                else if (index < reader.size) "보리와 노을 한 바퀴" else "함께 걸었던 길"
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(title).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { assertEquals("Stress font must survive the theme", fontScale, it.layoutInput.density.fontScale, .001f) }
            compose.onNodeWithContentDescription("서랍 펼치기").assertIsDisplayed()
            compose.onNodeWithContentDescription("서랍 접기").assertIsDisplayed()
            val name = if (index < reader.size) "reader-${reader[index]}" else "explorer-${explorer[index - reader.size]}"
            capture("$width-$fontScale-$name", requireNotNull(rendered))
            if (index >= reader.size) {
                compose.onNodeWithText("풀 냄새에 잠깐 멈춤").assertDoesNotExist()
                compose.onNodeWithText("동선 탐색").assertIsDisplayed()
            } else when (reader[index]) {
                DiaryReadingExample.LONG_TITLE, DiaryReadingExample.BODY, DiaryReadingExample.ADDRESS -> {
                    val top = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
                    compose.onNodeWithContentDescription("장면 수정").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithContentDescription("장면 삭제").performScrollTo().assertIsDisplayed()
                    assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
                }
                DiaryReadingExample.REMOTE_PHOTO -> compose.onNodeWithText("사진 파일은 촬영한 기기에서 볼 수 있어요.").performScrollTo().assertIsDisplayed()
                DiaryReadingExample.EMPTY -> compose.onNodeWithText("기록 남기기").assertIsDisplayed()
                DiaryReadingExample.LOADING -> compose.onNodeWithText("새로고침").assertIsDisplayed()
                DiaryReadingExample.ERROR -> compose.onNodeWithText("다시 시도").assertIsDisplayed()
                DiaryReadingExample.LOADING_ERROR -> {
                    compose.onAllNodesWithText("장면 갱신을 마치지 못했어요. 연결 상태를 확인하고 다시 불러와 주세요.").assertCountEquals(1)
                    compose.onNodeWithText("새로고침").assertIsDisplayed()
                }
                DiaryReadingExample.BACKUP -> {
                    compose.onNodeWithContentDescription("경로 백업 오류 · 다시 전송").performClick()
                    compose.onNodeWithContentDescription("경로 백업 재전송 요청됨").assertIsNotEnabled()
                    compose.onNodeWithText("저장한 장면을 보여드려요.").performScrollTo().assertIsDisplayed()
                }
                else -> Unit
            }
        }
    }

    private fun capture(name: String, view: android.view.View) = compose.runOnIdle {
        val directory = File(System.getenv("DAENGS_READING_REVIEW_DIR") ?: "build/outputs/diary-reading-matrix").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
