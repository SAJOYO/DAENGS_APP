package com.daengs.app.ui.walk

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class WalkEntryEditorTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val initial = WalkEntry(id = "n", sessionId = "s", type = WalkMomentType.NOTE,
        recordedAtMillis = 0, note = "처음 메모", baseVersion = WalkEntryVersion(1, "first"))

    @Test fun `원격 수정 중 작성한 메모는 유지하고 확인 전 저장을 막는다`() {
        val entries = mutableStateOf(listOf(initial))
        var saved: WalkEntry? = null
        compose.setContent { DaengsTheme {
            WalkEntryEditor(entries.value, initial, emptyList(), null, false, { saved = it }, {}, {})
        } }
        compose.onNode(hasSetTextAction()).performTextReplacement("내가 작성 중인 메모")
        compose.runOnIdle { entries.value = listOf(initial.copy(note = "다른 기기의 메모",
            baseVersion = WalkEntryVersion(2, "remote"))) }
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).assertTextContains("내가 작성 중인 메모")
        compose.onNodeWithText("최신 기록: 다른 기기의 메모").assertExists()
        compose.onNodeWithText("확인했어요 · 작성 중인 내용으로 계속").performClick()
        compose.onNodeWithText("저장").performClick()
        assertEquals("내가 작성 중인 메모", saved?.note)
        assertEquals(WalkEntryVersion(2, "remote"), saved?.baseVersion)
    }

    @Test fun `편집 중 서버 삭제가 도착해도 초안은 남고 저장으로 되살리지 않는다`() {
        val entries = mutableStateOf(listOf(initial))
        compose.setContent { DaengsTheme {
            WalkEntryEditor(entries.value, initial, emptyList(), null, false, {}, {}, {})
        } }
        compose.onNode(hasSetTextAction()).performTextReplacement("보존할 초안")
        compose.runOnIdle { entries.value = emptyList() }
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).assertTextContains("보존할 초안")
        compose.onNodeWithText("확인했어요 · 작성 중인 내용으로 계속").assertDoesNotExist()
    }
}
