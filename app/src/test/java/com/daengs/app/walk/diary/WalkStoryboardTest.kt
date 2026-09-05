package com.daengs.app.walk.diary

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkStoryboardTest {
    private val walk = WalkSummary("s", emptyList(), 0, 10000, null, 120.0, 10000, emptyList(), null)
    private val note = WalkEntry(id = "n", sessionId = "s", type = WalkMomentType.NOTE,
        recordedAtMillis = 1000, note = "함께 쉬었다")

    @Test fun `기록 없는 산책도 시작과 마무리가 있다`() {
        assertEquals(listOf("start", "end"), storyboardScenes(walk, emptyList(), StoryboardDraft()).map { it.id })
    }

    @Test fun `편집한 문구는 원본 정정 후에도 남고 확인이 필요하다`() {
        val scene = storyboardScenes(walk, listOf(note), StoryboardDraft())[1]
        val draft = StoryboardDraft().edit(scene, title = "우리의 쉼터", body = "직접 작성")
        val changed = storyboardScenes(walk, listOf(note.copy(note = "정정한 원본")), draft)[1]
        assertEquals("직접 작성", changed.body)
        assertTrue(changed.needsReview)
        assertTrue(changed.evidence.contains("정정한 원본"))
        assertFalse(storyboardScenes(walk, listOf(note.copy(note = "정정한 원본")),
            draft.edit(changed, acknowledge = true))[1].needsReview)
    }

    @Test fun `숨김은 원본을 지우지 않고 복원할 수 있다`() {
        val scene = storyboardScenes(walk, listOf(note), StoryboardDraft())[1]
        val hidden = StoryboardDraft().edit(scene, hidden = true)
        val scenes = storyboardScenes(walk, listOf(note), hidden)
        assertEquals(3, scenes.size)
        assertFalse(storyboardSnapshot("s", scenes).contains("entry:n"))
        assertTrue(storyboardSnapshot("s", storyboardScenes(walk, listOf(note),
            hidden.edit(scenes[1], hidden = false))).contains("entry:n"))
    }

    @Test fun `삭제된 근거는 검토본에서 빠지지만 사용자 문구와 과거 검토본은 유지된다`() {
        val scene = storyboardScenes(walk, listOf(note), StoryboardDraft())[1]
        var draft = StoryboardDraft().edit(scene, body = "내 문구")
        val snapshot = storyboardSnapshot("s", storyboardScenes(walk, listOf(note), draft))
        draft = draft.copy(reviewed = snapshot)
        val restored = StoryboardDraft.parse(draft.toJson())
        val scenes = storyboardScenes(walk, emptyList(), restored)
        assertEquals("내 문구", scenes.single { it.id == scene.id }.body)
        assertFalse(scenes.single { it.id == scene.id }.available)
        assertFalse(storyboardSnapshot("s", scenes).contains("entry:n"))
        assertEquals(snapshot, restored.reviewed)
    }

    @Test fun `저장한 편집과 검토본을 다시 읽고 세션 삭제 시 함께 삭제한다`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000))
            val draft = StoryboardDraft(reviewed = "snapshot")
            dao.saveStoryboard(WalkStoryboardRow("s", draft.toJson()))
            assertEquals(draft, StoryboardDraft.parse(dao.storyboard("s")!!.payload))
            dao.deleteSession("s")
            assertNull(dao.storyboard("s"))
        } finally { db.close() }
    }
}
