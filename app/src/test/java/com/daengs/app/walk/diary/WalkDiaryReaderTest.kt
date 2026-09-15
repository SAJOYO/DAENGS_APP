package com.daengs.app.walk.diary

import com.daengs.app.walk.support.diaryFixture
import com.daengs.app.walk.support.titledDiaryFixture
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkDiaryReaderTest {
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var reader: WalkDiaryReader
    private var owner = "a"
    private val summary = WalkSummary("s", listOf("dog"), 0, 10000, null, 100.0, 10000, emptyList(), null)
    @Before fun open(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, WalkDatabase::class.java).build()
        dao = db.walkDao()
        reader = WalkDiaryReader(dao, WalkPhotoStore(dao, File(context.cacheDir, "diary-reader")) { owner }) { owner }
        dao.insertSession(WalkSessionRow("s", 0, "a", 10000))
    }
    @After fun close() { db.close() }

    @Test fun `relational Room publication reaches titles search edit and hide without legacy publication`() = runBlocking {
        withTimeout(10000) {
            val raw = org.json.JSONObject(javaClass.getResource("/storyboard/relational-diary-v1.json")!!.readText())
                .put("entry_revisions", org.json.JSONObject())
            val id = raw.getString("session_id")
            val savedSummary = summary.copy(sessionId = id)
            dao.insertSession(WalkSessionRow(id, 0, owner, 10000, serverWalkId = "remote"))
            dao.insertDiaryPublication(WalkDiaryPublicationRow(id, 10000, 30000, "invalid legacy base"))
            assertTrue(dao.acceptRelationalDiary(raw.toString(), id, "remote", owner, dao.relationalDiaryInputStamp(id)))
            val diary = reader.observe(listOf(savedSummary)).first().single()
            assertTrue(diary.published)
            assertFalse(diary.preparing)
            assertEquals(3, diary.scenes.size)
            assertEquals("", diary.scenes.last().body)
            assertEquals(diary.title, reader.observeTitles(listOf(id)).first()[id])
            assertTrue(dao.historySearchText(listOf(id), owner).getValue(id).contains(diary.title))
            val source = requireNotNull(diary.scenes.first().source)
            dao.saveDiarySceneEdit(id, owner, source, "수정한 제목", "수정한 문장")
            val edited = reader.observe(listOf(savedSummary)).first().single().scenes.first()
            assertEquals("수정한 문장", edited.body)
            assertEquals(source.id, edited.source!!.id)
            dao.deleteDiaryScene(id, owner, edited.source!!)
            assertEquals(2, reader.observe(listOf(savedSummary)).first().single().scenes.size)
            assertNull(dao.diaryPublication(id)!!.publishedBundle)
            val note = WalkEntry("new-note", id, WalkMomentType.NOTE, 1000, note = "수정 중인 원문")
            dao.insertEntry(WalkEntryRow(note.id, id, note.toJson().toString(), 0, "pending", true))
            val stale = reader.observe(listOf(savedSummary)).first().single()
            assertEquals(listOf(note.note), stale.scenes.single().originalNotes)
            assertNull(stale.title)
            val original = stale.scenes.single().source!!
            dao.saveDiarySceneEdit(id, owner, original, "원본 카드", "내 문장")
            assertEquals("내 문장", reader.observe(listOf(savedSummary)).first().single().scenes.single().body)
            dao.deleteDiaryScene(id, owner, original)
            assertTrue(reader.observe(listOf(savedSummary)).first().single().scenes.isEmpty())
            assertNotNull(dao.entry(note.id)!!.payload)
            owner = "other"
            assertTrue(reader.observe(listOf(savedSummary)).first().isEmpty())
        }
    }

    @Test fun `photo revision change removes the generated title in both page and map readers`() = runBlocking {
        withTimeout(10000) {
            val raw = diaryFixture().put("session_id", "s")
            raw.getJSONObject("bundle").put("client_session_id", "s")
            for (id in listOf("note", "action")) {
                val content = WalkEntry(id, "s", WalkMomentType.NOTE, 0, note = "원본 메모").toJson().toString()
                dao.insertEntry(WalkEntryRow(id, "s", content, 1, id, false))
            }
            dao.insertPhotoSync(WalkPhotoSyncRow("s", "a", "publisher", 1, 1))
            val stamp = com.daengs.app.walk.sync.diaryInputStamp(dao.entries("s"), dao.photoSync("s"), emptyList())
            assertTrue(dao.acceptSceneAnalysis(WalkSceneAnalysisRow("s", 1, stamp, "r", "ready", raw.toString(), null), owner))
            assertEquals("두부와 함께 남긴 아침", reader.observeTitles(listOf("s")).first()["s"])
            assertEquals("두부와 함께 남긴 아침", reader.observe(listOf(summary)).first().single().title)
            dao.touchPhotoSync("s", "a")
            assertTrue(reader.observeTitles(listOf("s")).first().isEmpty())
            assertNull(reader.observe(listOf(summary)).first().single().title)
        }
    }

    @Test fun `page and map read the same saved title and drop it after source mutation`() = runBlocking {
        withTimeout(10000) {
            val stamp = com.daengs.app.walk.sync.storyboardEntryStamp(emptyList())
            val source = WalkSceneAnalysisRow("s", 1, stamp, "r", "ready", titledDiaryFixture().toString(), null)
            assertTrue(dao.acceptSceneAnalysis(source, owner))
            val titles = reader.observeTitles(listOf("s"))
            assertEquals("함께 남긴 산책 기록", titles.first()["s"])
            assertEquals(titles.first()["s"], reader.observe(listOf(summary)).first().single().title)
            dao.acceptSceneAnalysis(source.copy(status = "running", bundle = null), owner)
            dao.failSceneAnalysis("s", stamp, "failed")
            assertEquals("함께 남긴 산책 기록", titles.first()["s"])
            owner = "other"
            assertTrue(reader.observeTitles(listOf("s")).first().isEmpty())
            owner = "a"
            val changed = async { titles.first { it.isEmpty() } }
            dao.insertEntry(WalkEntryRow("new", "s", null, 0, "mutation", true))
            assertTrue(changed.await().isEmpty())
            assertNull(reader.observe(listOf(summary)).first().single().title)
            assertTrue(reader.observeTitles(emptyList()).first().isEmpty())
        }
    }

    @Test fun `관측 중인 검토본 숨김 변경과 세션 삭제를 지도 목록에 반영한다`() = runBlocking {
        withTimeout(10000) {
            val data = reader.observe(listOf(summary))
            assertEquals(2, data.first().single().scenes.size)
            val source = storyboardScenes(summary, emptyList(), StoryboardDraft()).first()
            val waitForHidden = async { data.first { it.singleOrNull()?.scenes?.size == 1 } }
            dao.saveStoryboard(WalkStoryboardRow("s", StoryboardDraft().edit(source, hidden = true).toJson()))
            assertEquals(1, waitForHidden.await().single().scenes.size)
            val waitForDeletion = async { data.first { it.isEmpty() } }
            dao.deleteSession("s")
            assertTrue(waitForDeletion.await().isEmpty())
        }
    }

    @Test fun `다른 계정의 산책과 아직 종료하지 않은 산책을 반환하지 않는다`() = runBlocking {
        owner = "b"
        assertTrue(reader.observe(listOf(summary)).first().isEmpty())
        owner = "a"
        dao.insertSession(WalkSessionRow("open", 0, "a", null))
        assertTrue(reader.observe(listOf(summary.copy(sessionId = "open"))).first().isEmpty())
        assertTrue(reader.observe(emptyList()).first().isEmpty())
    }

    @Test fun `계정과 종료 검사를 통과하지 못한 일기는 조립하지 않는다`() = runBlocking {
        dao.insertEntry(WalkEntryRow("broken", "s", "not an entry", 1, "mutation", false))
        val records = reader.observe(listOf(summary))
        assertTrue(runCatching { records.first() }.isFailure)
        owner = "b"
        assertTrue(records.first().isEmpty())
        owner = "a"
        dao.insertSession(WalkSessionRow("open", 0, "a", null))
        dao.insertEntry(WalkEntryRow("broken-open", "open", "not an entry", 1, "mutation", false))
        assertTrue(reader.observe(listOf(summary.copy(sessionId = "open"))).first().isEmpty())
    }
}
