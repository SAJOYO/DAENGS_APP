package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.support.diarySlotFixture
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h760dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiarySlotPreviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun result() = DiarySlotPreview.parse(diarySlotFixture())

    @Test fun `opening needs an explicit tap and repeated taps cannot call twice`() {
        var calls = 0
        val response = CompletableDeferred<DiarySlotPreview>()
        compose.setContent { DaengsTheme { DiarySlotPreviewBrowser({}, { calls++; response.await() }) } }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("미리보기 만들기").performClick()
        compose.onNodeWithText("미리보기 만드는 중").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(1, calls); response.complete(result()) }
        compose.waitUntil { compose.onAllNodesWithText("다시 만들기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("배경 문장을 더한 미리보기예요.").assertExists()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun `unavailable server explains failure and lets the user retry`() {
        var calls = 0
        compose.setContent { DaengsTheme { DiarySlotPreviewBrowser({}, {
            calls++
            if (calls == 1) throw DiarySlotPreviewException(404, "이 서버에서는 새 방식 미리보기를 아직 사용할 수 없어요.")
            result()
        }) } }
        compose.onNodeWithText("미리보기 만들기").performClick()
        compose.onNodeWithText("이 서버에서는 새 방식 미리보기를 아직 사용할 수 없어요.").assertExists()
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithText("다시 만들기").assertExists()
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun `all parts and original scene are readable on a small screen`() {
        var view: android.view.View? = null
        val sample = result().let { it.copy(scenes = listOf(it.scenes[1])) }
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current.rootView }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DaengsTheme { DiarySlotPreviewContent(sample, false, null, {}, {}) }
            }
        }
        compose.onNodeWithText("공간 3 · 환경 1 · 동선 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("장면 자료 보기").performScrollTo().performClick()
        compose.onNodeWithText("배경을 더하기 전").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(sample.scenes.single().baseBody).assertExists()
        compose.onNodeWithText("지역 환경 관측 · 문장에 인용").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("기온 (°C): 23\n풍속 (m/s): 2.1").assertExists()
        compose.runOnIdle { capture(requireNotNull(view), "diary-slot-preview-evidence") }
        compose.onNodeWithText("새 방식 일기 미리보기").performScrollTo()
        compose.runOnIdle { capture(requireNotNull(view), "diary-slot-preview-result") }
    }

    @Test fun `provider fallback and pending context stay visible with sparse scenes`() {
        val sample = result().let { it.copy(modelStatus = "unavailable", failureCode = "provider_failed", contextPending = true,
            scenes = listOf(it.scenes.first())) }
        compose.setContent { DaengsTheme { DiarySlotPreviewContent(sample, false, null, {}, {}) } }
        compose.onNodeWithText("배경 문장을 만들지 못해 기본 장면을 보여드려요. 다시 시도할 수 있어요.").assertExists()
        compose.onNodeWithText("주변 자료를 준비 중이라 일부 배경이 비어 있을 수 있어요. 잠시 뒤 다시 만들어 보세요.").assertExists()
        compose.onNodeWithText("장면 자료 보기").performScrollTo().performClick()
        compose.onNodeWithText("이 장면에 연결된 배경 자료가 없어요.").assertExists()
        compose.onNodeWithText("저장").assertDoesNotExist()
    }

    @Test fun `diary menu opens preview separately from published generation`() {
        var previews = 0
        var generations = 0
        compose.setContent { DaengsTheme {
            WalkDiaryMapContent(emptyList(), null, false, null, {}, {}, {}, {}, {}, {},
                map = { Box(Modifier.fillMaxSize()) }, onGenerate = { generations++ }, onSlotPreview = { previews++ })
        } }
        compose.onNodeWithContentDescription("일기 메뉴").performClick()
        compose.onNodeWithText("새 방식 미리보기").performClick()
        assertEquals(1, previews)
        assertEquals(0, generations)
    }

    private fun capture(view: android.view.View, name: String) {
        val file = java.io.File("build/outputs/$name.png")
        file.parentFile?.mkdirs()
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
