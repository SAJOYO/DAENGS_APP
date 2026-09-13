package com.daengs.app.ui.walk.review

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daengs.app.walk.diary.StoryboardDraft
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Run prepare, externally force-stop the fixture package, then run verify; never reseed verify. */
@RunWith(AndroidJUnit4::class)
class DiaryPersistenceDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<DiaryEditorReviewActivity>
    private lateinit var activity: DiaryEditorReviewActivity
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val checkpoint get() = File(context.filesDir, "diary-persistence-checkpoint.json")

    private fun open(reset: Boolean = false, owner: String = "editor-review") {
        scenario = ActivityScenario.launch(Intent(context, DiaryEditorReviewActivity::class.java)
            .putExtra("persistent", true).putExtra("reset", reset).putExtra("route", false)
            .putExtra("owner", owner))
        scenario.onActivity { activity = it }
        awaitText(if (owner != "editor-review") UNAVAILABLE else if (reset) "산책의 시작" else TITLE)
    }
    @After fun close() { if (::scenario.isInitialized) scenario.close() }
    private fun awaitText(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitNoText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }
    private fun addNote(value: String) {
        compose.onNodeWithContentDescription("일기 메뉴").performClick()
        compose.onNodeWithText("기록 남기기").performClick()
        compose.onNodeWithText("기억하고 싶은 내용을 적어 주세요").performTextReplacement(value)
    }
    private fun assertSavedContent(expected: JSONObject) {
        val row = runBlocking { activity.dao.entry(expected.getString("id")) }!!
        assertEquals(expected.getString("payload"), row.payload)
        assertEquals(expected.getString("mutation"), row.mutationId)
        assertTrue(row.dirty)
        assertEquals(NOTE, row.entry()!!.note)
        assertNull(row.entry()!!.point)
        val draft = runBlocking { activity.dao.storyboard(DiaryEditorReviewActivity.SESSION) }
        val edit = StoryboardDraft.parse(draft?.payload).edits.single()
        assertEquals(TITLE, edit.title); assertEquals(BODY, edit.body)
        assertEquals(2, runBlocking { activity.savedEntries() }.size)
        assertEquals("원래 남긴 검증 메모", runBlocking { activity.dao.entry("original-note") }!!.entry()!!.note)
    }

    @Test fun prepareEditsAndReopenDatabase() {
        checkpoint.delete()
        open(reset = true)
        addNote(NOTE)
        val before = activity.deliveryAttempts
        scenario.onActivity { it.failNextDelivery = true }
        compose.onNodeWithText("저장").performClick()
        awaitNoText("기억하고 싶은 내용을 적어 주세요")
        awaitText("변경은 기기에 저장했어요. 서버 전달을 예약하지 못했어요. 다시 시도해 주세요.")
        val saved = runBlocking { activity.savedEntries() }.single { it.id != "original-note" }
        val row = runBlocking { activity.dao.entry(saved.id) }!!
        assertTrue(row.dirty)
        compose.onNodeWithText("다시 시도").performClick()
        compose.waitUntil(10_000) { activity.deliveryAttempts >= before + 2 }
        assertEquals(row, runBlocking { activity.dao.entry(saved.id) })

        compose.onNodeWithContentDescription("장면 1 수정").performScrollTo().performClick()
        compose.onNodeWithText("장면 제목").performTextReplacement(TITLE)
        compose.onNodeWithText("장면 내용").performTextReplacement(BODY)
        compose.onNodeWithText("저장").performClick()
        awaitNoText("장면 수정"); awaitText(TITLE)
        val expected = JSONObject().put("id", row.id).put("payload", row.payload)
            .put("mutation", row.mutationId).put("process", DiaryEditorReviewActivity.PROCESS_INSTANCE)
        val photoBytes = activity.photoFile.readBytes()
        assertTrue(photoBytes.isNotEmpty())

        // Activity destruction closes Room; a fresh production database handle must read the edits.
        val oldActivity = activity
        scenario.close(); open()
        assertNotSame(oldActivity, activity)
        assertSavedContent(expected)
        assertArrayEquals(photoBytes, activity.photoFile.readBytes())
        assertNotNull(runBlocking { activity.dao.photo(DiaryEditorReviewActivity.PHOTO) })

        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText("산책 사진"))
        compose.onNodeWithText("산책 사진").performClick()
        compose.onNodeWithTag("diary-scene-body").performScrollToNode(hasText("사진 보기"))
        compose.onNodeWithText("사진 보기").performClick()
        compose.onNodeWithText("사진 삭제").performClick()
        compose.onNodeWithText("삭제").performClick()
        compose.waitUntil(10_000) { !activity.photoFile.exists() }
        assertNull(runBlocking { activity.dao.photo(DiaryEditorReviewActivity.PHOTO) })
        checkpoint.writeText(expected.toString())
    }

    @Test fun verifyEditsAfterProcessRestart() {
        check(checkpoint.isFile) { "Run prepareEditsAndReopenDatabase before this phase" }
        val expected = JSONObject(checkpoint.readText())
        assertNotEquals("Verification must run in another application process",
            expected.getString("process"), DiaryEditorReviewActivity.PROCESS_INSTANCE)
        open()
        assertSavedContent(expected)
        assertFalse(activity.photoFile.exists())
        assertNull(runBlocking { activity.dao.photo(DiaryEditorReviewActivity.PHOTO) })
        compose.onNodeWithText("장면 수정").assertDoesNotExist()
        compose.onNodeWithContentDescription("장면 1 수정").performScrollTo().performClick()
        compose.onNodeWithText(BODY).assertExists()
        compose.onNodeWithText("취소").performClick()

        scenario.close(); open(owner = "another-review-owner")
        compose.onNodeWithText(TITLE).assertDoesNotExist()
        scenario.close(); open()
        assertSavedContent(expected)
        addNote("저장하지 않은 재생성 초안")
        scenario.recreate()
        scenario.onActivity { activity = it }
        awaitText(TITLE)
        compose.onNodeWithText("저장하지 않은 재생성 초안").assertDoesNotExist()
        assertSavedContent(expected)
        scenario.onActivity { it.removeSession() }
        awaitText(UNAVAILABLE)
        scenario.close()
        // Explicitly reopen without seed after deletion, too.
        scenario = ActivityScenario.launch(Intent(context, DiaryEditorReviewActivity::class.java)
            .putExtra("persistent", true))
        scenario.onActivity { activity = it }
        awaitText(UNAVAILABLE)
        assertNull(runBlocking { activity.dao.session(DiaryEditorReviewActivity.SESSION) })
        compose.onNodeWithText(TITLE).assertDoesNotExist()
    }

    companion object {
        private const val NOTE = "DB 재개방 뒤에도 남는 메모"
        private const val TITLE = "앱을 다시 열어도 남는 시작"
        private const val BODY = "서버 예약이 실패해도 기기에 보관한 장면"
        private const val UNAVAILABLE = "삭제되었거나 현재 계정에서 볼 수 없는 산책이에요."
    }
}
