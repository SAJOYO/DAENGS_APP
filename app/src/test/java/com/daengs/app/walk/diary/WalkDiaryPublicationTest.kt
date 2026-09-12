package com.daengs.app.walk.diary

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
class WalkDiaryPublicationTest {
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var reader: WalkDiaryReader
    private val summary = WalkSummary("s", emptyList(), 0, 1000, null, 0.0, 0, emptyList(), null)
    @Before fun open() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, WalkDatabase::class.java).build()
        dao = db.walkDao()
        reader = WalkDiaryReader(dao, WalkPhotoStore(dao, File(app.cacheDir, "publication")) { "owner" }) { "owner" }
    }
    @After fun close() { db.close() }
    private suspend fun prepare(ended: Long = 1000): WalkDiaryPublicationRow {
        dao.insertSession(WalkSessionRow("s", 0, "owner", null))
        dao.closeAndPrepareDiary("s", ended)
        return requireNotNull(dao.prepareLocalDiary("s", "owner"))
    }

    @Test fun `close persists one deadline and never opts old completed walks in`() = runBlocking {
        val first = prepare()
        assertEquals(21000L, first.deadlineAtMillis)
        dao.closeAndPrepareDiary("s", 9999)
        assertEquals(first, dao.diaryPublication("s"))
        dao.insertSession(WalkSessionRow("old", 0, "owner", 1000))
        dao.closeAndPrepareDiary("old", 9999)
        assertNull(dao.diaryPublication("old"))
    }

    @Test fun `pending hides every editable scene then opens a complete board at deadline`() = runBlocking {
        val state = prepare()
        val pending = reader.observe(listOf(summary)).first().single()
        assertTrue(pending.preparing)
        assertTrue(pending.scenes.isEmpty())
        val source = GeoStoryboardBundle.parse(state.baseBundle!!).scenes.first()
        assertTrue(runCatching { dao.saveDiarySceneEdit("s", "owner", source, "제목", "본문") }.isFailure)
        assertEquals(0, dao.publishDiaryBase("s", state.deadlineAtMillis - 1))
        assertEquals(1, dao.publishDiaryBase("s", state.deadlineAtMillis))
        val ready = reader.observe(listOf(summary)).first().single()
        assertFalse(ready.preparing)
        assertEquals(2, ready.scenes.size)
        assertTrue(ready.scenes.all { it.body.isNotBlank() && it.point == null })
    }

    @Test fun `first publication wins against late AI and refresh preserving human edits`() = runBlocking {
        val state = prepare()
        val raw = requireNotNull(state.baseBundle)
        assertEquals(1, dao.publishDiaryBase("s", state.deadlineAtMillis))
        val source = GeoStoryboardBundle.parse(raw).scenes.first()
        dao.saveDiarySceneEdit("s", "owner", source, "나의 시작", "직접 고친 장면")
        assertEquals(0, dao.publishDiaryCandidate("s", raw.replace("산책을 시작했다.", "뒤늦은 응답"), state.deadlineAtMillis + 1))
        assertEquals(0, dao.publishDiaryBase("s", state.deadlineAtMillis + 2))
        assertEquals(raw, dao.diaryPublication("s")!!.publishedBundle)
        assertEquals("직접 고친 장면", reader.observe(listOf(summary)).first().single().scenes.first().body)
    }

    @Test fun `DAO accepts a fast candidate and rejects a later base publication`() = runBlocking {
        val state = prepare()
        val candidate = state.baseBundle!!.replace("산책을 시작했다.", "산책길에 나섰다.")
        assertEquals(1, dao.publishDiaryCandidate("s", candidate, 1500))
        assertEquals(0, dao.publishDiaryBase("s", state.deadlineAtMillis))
        assertEquals(candidate, dao.diaryPublication("s")!!.publishedBundle)
        assertFalse(reader.observe(listOf(summary)).first().single().preparing)
    }

    @Test fun `expired preparation recovers without network and keeps original deadline`() = runBlocking {
        val state = prepare()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var calls = 0
        val publisher = WalkDiaryPublication(dao, { "owner" }, scope, { calls++ }, now = { state.deadlineAtMillis + 1 })
        try {
            publisher.recover()
            val ready = withTimeout(10000) { dao.observeDiaryPublication("s").first { it?.publishedBundle != null }!! }
            assertEquals(state.baseBundle, ready.publishedBundle)
            assertEquals(state.deadlineAtMillis, ready.deadlineAtMillis)
            assertEquals(0, calls)
        } finally { scope.cancel() }
    }

    @Test fun `stored ten second preparation is not extended by the new coordinator`() = runBlocking {
        dao.insertSession(WalkSessionRow("s", 0, "owner", 1000))
        dao.insertDiaryPublication(WalkDiaryPublicationRow("s", 1000, 11000))
        val state = requireNotNull(dao.prepareLocalDiary("s", "owner"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val publisher = WalkDiaryPublication(dao, { "owner" }, scope,
            { error("Expired preparation must not sync") }, now = { 11000 })
        try {
            publisher.recover()
            val ready = withTimeout(10000) { dao.observeDiaryPublication("s").first { it?.publishedBundle != null }!! }
            assertEquals(11000L, ready.deadlineAtMillis)
            assertEquals(state.baseBundle, ready.publishedBundle)
        } finally { scope.cancel() }
    }

    @Test fun `explicit new and deleted notes change only their own card`() = runBlocking {
        val note = WalkEntry("note", "s", WalkMomentType.NOTE, 500, note = "내가 남긴 메모")
        dao.insertSession(WalkSessionRow("s", 0, "owner", null))
        dao.insertEntry(WalkEntryRow(note.id, "s", note.toJson().toString(), 1, "m", false))
        dao.closeAndPrepareDiary("s", 1000)
        val state = dao.prepareLocalDiary("s", "owner")!!
        dao.publishDiaryBase("s", state.deadlineAtMillis)
        val source = GeoStoryboardBundle.parse(state.baseBundle!!).scenes.first()
        dao.saveDiarySceneEdit("s", "owner", source, "내 제목", "보존할 시작 장면")
        dao.editEntry("note", null, "deleted")
        val added = note.copy(id = "new", note = "나중에 추가한 메모")
        dao.insertEntry(WalkEntryRow(added.id, "s", added.toJson().toString(), 0, "new", true))
        val scenes = reader.observe(listOf(summary)).first().single().scenes
        assertEquals(3, scenes.size)
        assertTrue(scenes.none { it.entryId == "note" })
        assertEquals("나중에 추가한 메모", scenes.single { it.entryId == "new" }.body)
        assertEquals("보존할 시작 장면", scenes.first().body)
    }

    @Test fun `route fillers use actual samples and never bridge missing GPS`() {
        val session = RecordedSession("s", dogIds = emptyList(), startedAtMillis = 0, endedAtMillis = 1500000)
        val fixes = (0..100).map { i ->
            RecordedFix(i, if (i < 50) 0 else 1, i * 12000L,
                37.0 + i * 0.00009 + if (i < 50) 0.0 else 0.02, 127.0, 3f, false)
        }
        val walk = summarize(session, fixes, Int.MAX_VALUE)
        val board = GeoStoryboardBundle.parse(LocalDiaryBoard.build(walk, fixes, emptyList(), emptyList()))
        val checkpoints = board.scenes.filter { it.diary?.recordKind == "route_checkpoint" }
        assertTrue(checkpoints.isNotEmpty())
        checkpoints.forEach {
            val anchor = requireNotNull(it.observation)
            assertNotNull(StoryboardObservationIndex(walk, fixes).resolve(anchor))
            assertTrue(fixes.any { fix -> fix.clientSeq == anchor.clientSeq && fix.atMillis == it.atMillis })
        }
        assertNull(board.scenes.last().diary?.point) // No GPS at the end-button timestamp.
        assertTrue(board.scenes.none { it.diary?.recordKind?.startsWith("observed_") == true })
    }
}
