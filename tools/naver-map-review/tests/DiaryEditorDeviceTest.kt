package com.daengs.app.ui.walk.review

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daengs.app.ui.walk.formatWalkClock
import com.daengs.app.walk.diary.StoryboardDraft
import com.naver.maps.geometry.LatLng
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Runs production dialogs and native map taps against fixture-only Room on a connected phone. */
@RunWith(AndroidJUnit4::class)
class DiaryEditorDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<DiaryEditorReviewActivity>
    private lateinit var activity: DiaryEditorReviewActivity

    private fun open(route: Boolean = true) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(Intent(context, DiaryEditorReviewActivity::class.java).putExtra("route", route))
        scenario.onActivity { activity = it }
        awaitText("산책의 시작")
    }
    @After fun close() { if (::scenario.isInitialized) scenario.close() }

    private fun awaitText(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitClosedEditor() = compose.waitUntil(10_000) {
        compose.onAllNodesWithText("기억하고 싶은 내용을 적어 주세요").fetchSemanticsNodes().isEmpty()
    }
    private fun add() {
        compose.onNodeWithContentDescription("일기 메뉴").performClick()
        compose.onNodeWithText("기록 남기기").performClick()
    }
    private fun cancelDialog() = compose.onAllNodesWithText("취소").onLast().performClick()
    private fun closeEntry() = compose.onNodeWithText("닫기").performClick()
    private fun noteText(value: String) = compose.onNodeWithText("기억하고 싶은 내용을 적어 주세요").performTextReplacement(value)

    private fun tapRoute() {
        // Give the native map the full available area through the production drawer controls.
        repeat(2) {
            val handle = compose.onNodeWithTag("diary-sheet-handle")
            if (handle.fetchSemanticsNode().config.contains(SemanticsActions.Collapse))
                handle.performSemanticsAction(SemanticsActions.Collapse) { it() }
        }
        compose.waitForIdle()
        var point: Pair<Float, Float>? = null
        compose.waitUntil(20_000) { scenario.onActivity { point = it.screenPoint() }; point != null }
        // Let the SDK's initial bounds animation finish before resolving the tap coordinate.
        Thread.sleep(1000)
        scenario.onActivity {
            val map = requireNotNull(it.map())
            val onRoute = map.projection.toScreenLocation(LatLng(37.56661, 126.978388 + 8 * 0.0002))
            // Tap 17 m beside the route, inside the existing 30 m picker radius and below scene pins.
            val beside = map.projection.toScreenLocation(LatLng(37.56646, 126.978388 + 8 * 0.0002))
            point = it.screenPoint()?.let { (x, y) -> x + beside.x - onRoute.x to y + beside.y - onRoute.y }
        }
        val (x, y) = requireNotNull(point)
        val topOfDrawer = compose.onNodeWithTag("diary-sheet-handle").fetchSemanticsNode().boundsInWindow.top
        assertTrue("Route tap must be above the drawer: $y / $topOfDrawer", y < topOfDrawer)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val at = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(at, at + 80, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertTrue(instrumentation.uiAutomation.injectInputEvent(down, true))
            assertTrue(instrumentation.uiAutomation.injectInputEvent(up, true))
        } finally { down.recycle(); up.recycle() }
        awaitText("이 지점에 기록 남기기")
    }

    @Test fun nativeLocationRevisitCancelFailureAndRetry() {
        open()
        add(); tapRoute()
        compose.onAllNodesWithText("특별한 순간").onLast().performClick()
        noteText("취소할 메모"); closeEntry(); awaitClosedEditor()
        assertEquals(1, runBlocking { activity.savedEntries() }.size)

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("diary-sheet-handle").performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.onNodeWithText("동선에서 위치를 골라 주세요.").assertDoesNotExist()

        add(); tapRoute()
        val revisitAt = DiaryEditorReviewActivity.START + 560000
        compose.onNodeWithText(formatWalkClock(revisitAt)).performClick()
        compose.onAllNodesWithText("특별한 순간").onLast().performClick()
        noteText("실기기 저장 재시도 메모")
        scenario.onActivity { it.failNextEntrySave = true }
        compose.onNodeWithText("저장").performClick()
        awaitText("검증용 저장 실패")
        compose.onNodeWithText("실기기 저장 재시도 메모").assertExists()
        assertEquals(1, runBlocking { activity.savedEntries() }.size)
        compose.onNodeWithText("저장").performClick(); awaitClosedEditor()
        val saved = runBlocking { activity.savedEntries() }.single { it.id != "original-note" }
        assertEquals("실기기 저장 재시도 메모", saved.note)
        assertEquals(revisitAt, saved.recordedAtMillis)
        assertEquals(revisitAt, saved.locationCapturedAtMillis)
        assertEquals(126.978388 + 8 * 0.0002, saved.point!!.longitude, 0.0000001)
        assertEquals(3f, saved.accuracyMeters)
        compose.onNodeWithText("동선에서 위치를 골라 주세요.").assertDoesNotExist()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitText("검증 기록 다시 열기")
        compose.onNodeWithText("검증 기록 다시 열기").performClick()
        awaitText("산책의 시작")
        assertEquals(2, runBlocking { activity.savedEntries() }.size)
    }

    @Test fun unlocatedNoteCancelAndSave() {
        open(route = false)
        add(); noteText("취소한 위치 없는 메모"); closeEntry(); awaitClosedEditor()
        assertEquals(1, runBlocking { activity.savedEntries() }.size)
        add(); noteText("위치 없는 실기기 메모")
        compose.onNodeWithText("저장").performClick(); awaitClosedEditor()
        val saved = runBlocking { activity.savedEntries() }.single { it.id != "original-note" }
        assertEquals("위치 없는 실기기 메모", saved.note)
        assertEquals(DiaryEditorReviewActivity.START, saved.recordedAtMillis)
        assertNull(saved.point); assertNull(saved.locationCapturedAtMillis)
    }

    @Test fun sceneEditPersistsAndPhotoDeleteRequiresConfirmation() {
        open()
        compose.onNodeWithContentDescription("장면 1 메뉴").performScrollTo().performClick()
        compose.onNodeWithContentDescription("장면 1 수정").performClick()
        compose.onNodeWithText("장면 제목").performTextReplacement("실기기에서 고친 시작")
        compose.onNodeWithText("장면 내용").performTextReplacement("취소와 저장을 확인한 장면")
        compose.onNodeWithText("저장").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("장면 수정").fetchSemanticsNodes().isEmpty() }
        awaitText("실기기에서 고친 시작")
        compose.onNodeWithContentDescription("장면 1 메뉴").performScrollTo().performClick()
        compose.onNodeWithContentDescription("장면 1 수정").performClick()
        compose.onNodeWithText("취소와 저장을 확인한 장면").assertExists()
        cancelDialog()
        val draft = runBlocking { activity.dao.storyboard(DiaryEditorReviewActivity.SESSION) }
        assertEquals("취소와 저장을 확인한 장면", StoryboardDraft.parse(draft?.payload).edits.single().body)

        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText("산책 사진"))
        compose.onNodeWithText("산책 사진").performClick()
        compose.onNodeWithTag("diary-scene-body").performScrollToNode(hasText("사진 보기"))
        compose.onNodeWithText("사진 보기").performClick()
        compose.onNodeWithText("사진 삭제").performClick()
        cancelDialog()
        assertNotNull(runBlocking { activity.dao.photo(DiaryEditorReviewActivity.PHOTO) })
        assertTrue(activity.photoFile.exists())
        compose.onNodeWithText("사진 삭제").performClick()
        compose.onNode(hasText("삭제") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) { !activity.photoFile.exists() }
        assertNull(runBlocking { activity.dao.photo(DiaryEditorReviewActivity.PHOTO) })
    }

    @Test fun loginReplacementAndSessionDeletionDismissOpenEditors() {
        open()
        compose.onNodeWithContentDescription("장면 1 메뉴").performScrollTo().performClick()
        compose.onNodeWithContentDescription("장면 1 수정").performClick()
        compose.onNodeWithText("장면 내용").performTextReplacement("이전 로그인 초안")
        scenario.onActivity { it.replaceLogin() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("장면 수정").fetchSemanticsNodes().isEmpty() }
        awaitText("산책의 시작")
        compose.onNodeWithText("이전 로그인 초안").assertDoesNotExist()
        compose.onNodeWithContentDescription("장면 1 메뉴").performScrollTo().performClick()
        compose.onNodeWithContentDescription("장면 1 수정").performClick()
        scenario.onActivity { it.removeSession() }
        awaitText("삭제되었거나 현재 계정에서 볼 수 없는 산책이에요.")
        compose.onNodeWithText("장면 수정").assertDoesNotExist()
    }
}
