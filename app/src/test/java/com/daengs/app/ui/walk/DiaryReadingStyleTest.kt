package com.daengs.app.ui.walk

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.location.GeoPoint
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryReadingStyleTest {
    @get:Rule val compose = createComposeRule()
    private var rendered: android.view.View? = null

    @Test fun `reader cards keep edit delete and reading controls and render actual source labels`() {
        compose.setContent {
            val view = LocalView.current
            SideEffect { rendered = view.rootView }
            DiaryReadingStylePreview(DiaryReadingExample.LIST)
        }
        compose.onNodeWithText("보리와 노을 한 바퀴").assertIsDisplayed()
        compose.onNodeWithText("보리").assertIsDisplayed()
        compose.onNodeWithContentDescription("걸은 시간", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("장면 1 수정").assertIsDisplayed()
        compose.onNodeWithContentDescription("장면 1 삭제").assertIsDisplayed()
        capture("reading-list-390")
        val top = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("풀 냄새에 잠깐 멈춤").performClick()
        compose.onNodeWithText("풀잎 앞에서 남긴 킁킁 기록.").assertIsDisplayed()
        compose.onNodeWithContentDescription("장면 수정").assertIsDisplayed()
        compose.onNodeWithContentDescription("장면 삭제").assertIsDisplayed()
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        capture("reading-body-390")
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `large text multi dog header excludes unrelated primary and keeps tab reachable`() {
        compose.setContent {
            val view = LocalView.current
            SideEffect { rendered = view.rootView }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DiaryReadingStylePreview(DiaryReadingExample.MULTI_DOG)
            }
        }
        compose.onNodeWithText("보리 · 두부 · 이름 미확인 1마리").assertIsDisplayed()
        compose.onNodeWithText("장면 2").assertIsDisplayed()
        compose.onNodeWithText("동선 탐색").assertIsDisplayed()
        capture("reading-multiple-320")
        val primary = Pet("other", "다른 아이", "mix", null, null, null, null, null, isPrimary = true)
        val actual = primary.copy(id = "a", name = "보리", isPrimary = false)
        assertEquals(listOf("a", "missing"), diaryParticipants(listOf("a", "missing", "a"), listOf(primary, actual)).map { it.first })
        assertEquals(listOf(actual, null), diaryParticipants(listOf("a", "missing"), listOf(primary, actual)).map { it.second })
    }

    @Test fun `real local photo preview opens the same photo and clears when file identity changes`() {
        val file = File.createTempFile("diary-reading-", ".png")
        val bitmap = Bitmap.createBitmap(180, 120, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GRAY)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var photo by mutableStateOf(WalkPhoto("p", "s", 0, GeoPoint(37.5, 127.0), file))
        var opened = 0
        try {
            compose.setContent { DaengsTheme { DiaryReadingPhoto(photo) { opened++ } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("산책 중 촬영한 사진").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("사진 보기").performClick()
            compose.runOnIdle { assertEquals(1, opened); photo = photo.copy(id = "missing", file = File(file.parentFile, "missing-photo.png")) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("사진 파일을 읽을 수 없어요.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("산책 중 촬영한 사진").assertDoesNotExist()
            compose.onNodeWithText("사진 보기").assertIsDisplayed()
        } finally { file.delete() }
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `long titles and remote photo notices remain reachable without expanding the drawer`() {
        var example by mutableStateOf(DiaryReadingExample.LONG_TITLE)
        compose.setContent {
            val view = LocalView.current
            SideEffect { rendered = view.rootView }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DiaryReadingStylePreview(example)
            }
        }
        capture("reading-long-title-320")
        val top = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("이 장면에는 확인된 위치가 없어요.").performScrollTo().assertIsDisplayed()
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { example = DiaryReadingExample.REMOTE_PHOTO }
        compose.onNodeWithText("사진 파일은 촬영한 기기에서 볼 수 있어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("산책 중 촬영한 사진").assertDoesNotExist()
        compose.onNodeWithText("사진 보기").assertDoesNotExist()
    }

    @Test fun `loading error and empty states keep their recovery actions`() {
        var example by mutableStateOf(DiaryReadingExample.LOADING)
        compose.setContent { DiaryReadingStylePreview(example) }
        compose.onNodeWithText("장면을 정리하고 있어요.").assertIsDisplayed()
        compose.onNodeWithText("새로고침").assertIsDisplayed()
        compose.runOnIdle { example = DiaryReadingExample.ERROR }
        compose.onNodeWithText("다시 시도").assertIsDisplayed()
        compose.runOnIdle { example = DiaryReadingExample.EMPTY }
        compose.onNodeWithText("아직 남긴 장면이 없어요.").assertIsDisplayed()
        compose.onNodeWithText("기록 남기기").assertIsDisplayed()
    }

    private fun capture(name: String) = compose.runOnIdle {
        val directory = File("build/outputs/diary-reading").apply { mkdirs() }
        val view = requireNotNull(rendered)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
