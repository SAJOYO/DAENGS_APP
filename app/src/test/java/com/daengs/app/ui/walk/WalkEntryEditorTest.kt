package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.Column
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

/**
 * **다이얼로그 창 없이 알맹이만 그린다.**
 *
 * `WalkEntryEditor` 를 그대로 그리면 `setContent` 가 60초를 다 쓰고
 * `AppNotIdleException` 으로 죽는다 — `AlertDialog` 안에 텍스트필드가 있으면
 * Robolectric 에서 영영 idle 이 안 된다. 다이얼로그만·텍스트필드만이면 멀쩡하고
 * 둘이 만나야 터진다 (2026-09-06 확인). `mainClock.autoAdvance = false` 도 안 먹는다 —
 * Robolectric 의 idling 은 compose 시계가 아니라 looper 를 돌린다.
 *
 * **화면 버그가 아니다.** 그래서 화면을 바꾸는 대신 [WalkEntryEditorContent] 에
 * 다이얼로그 대신 평범한 `Column` 을 넘겨 같은 알맹이를 창 없이 그린다.
 * 실제 다이얼로그 배치는 `@Preview` 와 실기기에서 본다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class WalkEntryEditorTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val initial = WalkEntry(id = "n", sessionId = "s", type = WalkMomentType.NOTE,
        recordedAtMillis = 0, note = "처음 메모", baseVersion = WalkEntryVersion(1, "first"))

    @Test fun `위치 없는 행동도 목록 편집과 저장을 제공하며 동기화 대기를 표시한다`() {
        val entry = WalkEntry(id = "action", sessionId = "s", type = WalkMomentType.BARKING,
            recordedAtMillis = 1000, syncPending = true)
        var saved: WalkEntry? = null
        compose.setContent { DaengsTheme {
            WalkEntryEditorContent(listOf(entry), entry, emptyList(), null, false, { saved = it }, {}, {}) {
                title, body, confirm, dismiss -> Column { title(); body(); confirm(); dismiss() }
            }
        } }
        compose.onNodeWithText("위치 없이 남긴 행동").assertExists()
        compose.onNodeWithText("기기에 저장했어요 · 동기화 대기 중").assertExists()
        compose.onNodeWithText("저장").assertIsEnabled().performClick()
        assertEquals(entry.id, saved?.id)
        assertNull(saved?.point)
        assertTrue(listOf(entry).entryMoments().isEmpty())
    }

    /** 다이얼로그 대신 그냥 세로로 쌓는다. 슬롯 넷을 그대로 받는다. */
    private fun 편집기를연다(entries: () -> List<WalkEntry>, onSave: (WalkEntry) -> Unit = {}) =
        compose.setContent { DaengsTheme {
            WalkEntryEditorContent(entries(), initial, emptyList(), null, false, onSave, {}, {}) {
                title, body, confirm, dismiss ->
                Column { title(); body(); confirm(); dismiss() }
            }
        } }

    @Test fun `원격 수정 중 작성한 메모는 유지하고 확인 전 저장을 막는다`() {
        val entries = mutableStateOf(listOf(initial))
        var saved: WalkEntry? = null
        편집기를연다({ entries.value }, onSave = { saved = it })
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
        편집기를연다({ entries.value })
        compose.onNode(hasSetTextAction()).performTextReplacement("보존할 초안")
        compose.runOnIdle { entries.value = emptyList() }
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).assertTextContains("보존할 초안")
        compose.onNodeWithText("확인했어요 · 작성 중인 내용으로 계속").assertDoesNotExist()
    }
}
