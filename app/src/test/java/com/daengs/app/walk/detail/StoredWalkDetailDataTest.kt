package com.daengs.app.walk.detail

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.auth.AccountScope
import com.daengs.app.auth.Session
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.ui.walk.WalkDetailState
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.store.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room writes/readers, with only auth/network/scheduling replaced. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StoredWalkDetailDataTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var entries: WalkEntryStore
    private lateinit var photos: WalkPhotoStore
    private var account = AccountScope("owner", 1)
    private val auth = Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val calls = mutableListOf<String>()
    private val note = WalkEntry("note", "s", WalkMomentType.NOTE, 500, note = "원본 메모")

    @Before fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        dao = db.walkDao()
        entries = WalkEntryStore(dao) { account.ownerId.orEmpty() }
        photos = WalkPhotoStore(dao, temporary.newFolder("photos")) { account.ownerId.orEmpty() }
        dao.insertSession(WalkSessionRow("s", 0, "owner", 1000))
    }
    @After fun close() = db.close()

    private fun data(
        fresh: suspend () -> Session? = { calls += "auth"; auth },
        sync: suspend (String, String) -> Unit = { token, id ->
            assertEquals("token", token); assertEquals("s", id)
            calls += "sync"; dao.markRawUploaded(id, "remote", 1000)
        },
        enqueue: suspend (String) -> Unit = { calls += "enqueue:$it" },
    ) = StoredWalkDetailData("s", account, { account },
        WalkHistory(RoomWalkFixLog(dao) { account.ownerId.orEmpty() }), dao, entries, photos,
        { calls += "prepare:$it" }, enqueue, fresh, sync,
        { token, id, remote ->
            assertEquals("token", token); assertEquals("s", id); assertEquals("remote", remote)
            calls += "generate"
        })

    @Test fun `opening schedules existing publication and delivery while refresh only wakes publication`() = runBlocking {
        val data = data()
        data.open(); data.prepareDiary()
        assertEquals(listOf("prepare:s", "enqueue:s", "prepare:s"), calls)
    }

    @Test fun `exploration is scoped to the current login and disappears with its session`() = runBlocking {
        val original = data()
        original.saveExploration("checkpoint")
        assertEquals("checkpoint", data().loadExploration())
        assertNull(dao.exploration("s", "other"))
        account = AccountScope("owner", 2)
        assertTrue(runCatching { original.saveExploration("stale") }.exceptionOrNull() is CancellationException)
        assertEquals("checkpoint", data().loadExploration())
        dao.deleteSession("s")
        assertNull(data().loadExploration())
        data().saveExploration("late")
        assertNull(dao.exploration("s", "owner"))
    }

    @Test fun `generation waits for sync and uses the newly stored remote id`() = runBlocking {
        assertNull(dao.session("s")!!.serverWalkId)
        data().generateDiary()
        assertEquals(listOf("auth", "sync", "generate"), calls)
    }

    @Test fun `missing auth or remote id does not generate`() = runBlocking {
        assertTrue(runCatching { data(fresh = { null }).generateDiary() }.isFailure)
        assertTrue(calls.isEmpty())
        assertTrue(runCatching { data(sync = { _, _ -> calls += "sync" }).generateDiary() }.isFailure)
        assertEquals(listOf("auth", "sync"), calls)
    }

    @Test fun `sync failure and cancellation propagate without generating`() = runBlocking {
        for (failure in listOf(IllegalStateException("offline"), CancellationException("left screen"))) {
            calls.clear()
            val result = runCatching { data(sync = { _, _ -> throw failure }).generateDiary() }
            assertSame(failure, result.exceptionOrNull())
            assertEquals(listOf("auth"), calls)
        }
    }

    @Test fun `leaving during sync cancels the request before generation`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val data = data(sync = { _, _ -> entered.complete(Unit); awaitCancellation() })
        val job = launch { data.generateDiary() }
        entered.await(); job.cancelAndJoin()
        assertEquals(listOf("auth"), calls)
    }

    @Test fun `same owner login replacement during auth or sync stops the old operation`() = runBlocking {
        val duringAuth = data(fresh = { account = account.copy(generation = 2); auth })
        assertTrue(runCatching { duringAuth.generateDiary() }.exceptionOrNull() is CancellationException)
        assertTrue(calls.isEmpty())
        val duringSync = data(sync = { _, _ ->
            dao.markRawUploaded("s", "remote", 1000)
            account = account.copy(generation = 3)
        })
        assertTrue(runCatching { duringSync.generateDiary() }.exceptionOrNull() is CancellationException)
        assertEquals(listOf("auth"), calls)
    }

    @Test fun `entry is durable before scheduling and a conflicting edit never schedules`() = runBlocking {
        val data = data(enqueue = { id ->
            assertNotNull(dao.entry("note")!!.payload)
            calls += "enqueue:$id"
        })
        data.saveEntry(note)
        val stale = dao.entry("note")!!.entry()!!
        entries.save(stale.copy(note = "새 내용"))
        assertTrue(runCatching { data.saveEntry(stale.copy(note = "오래된 편집")) }.isFailure)
        assertEquals("새 내용", dao.entry("note")!!.entry()!!.note)
        assertEquals(listOf("enqueue:s"), calls)
    }

    @Test fun `scheduling failure leaves saved content and deleted tombstone for recovery`() = runBlocking {
        val failure = IllegalStateException("scheduler unavailable")
        val data = data(enqueue = { throw failure })
        val saveFailure = runCatching { data.saveEntry(note) }.exceptionOrNull()
        assertTrue(saveFailure is WalkDetailDeliveryPending)
        assertSame(failure, saveFailure?.cause)
        val saved = dao.entry("note")!!
        assertTrue(saved.dirty); assertNotNull(saved.payload)
        val deleteFailure = runCatching { data.deleteEntry("note") }.exceptionOrNull()
        assertTrue(deleteFailure is WalkDetailDeliveryPending)
        assertSame(failure, deleteFailure?.cause)
        val deleted = dao.entry("note")!!
        assertTrue(deleted.dirty); assertNull(deleted.payload)
        dao.acknowledgeEntry("note", 1, saved.mutationId)
        assertNull(dao.entry("note")!!.payload)
        assertTrue(dao.entry("note")!!.dirty)
    }

    @Test fun `delete schedules after its tombstone while a missing id does nothing`() = runBlocking {
        entries.save(note)
        val data = data(enqueue = { id ->
            assertNull(dao.entry("note")!!.payload); calls += "enqueue:$id"
        })
        data.deleteEntry("note"); data.deleteEntry("missing")
        assertEquals(listOf("enqueue:s"), calls)
    }

    @Test fun `committed new note completes the editor and delivery retry does not write it again`() = runBlocking {
        var unavailable = true
        var schedules = 0
        val data = data(enqueue = { schedules++; if (unavailable) error("scheduler unavailable") })
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val state = WalkDetailState(data, data, scope, {})
            var completed = 0
            state.saveEntry(note) { completed++ }
            withTimeout(5000) { while (state.savingEntry) delay(10) }
            val saved = requireNotNull(dao.entry(note.id))
            assertTrue(saved.dirty)
            assertEquals("원본 메모", saved.entry()!!.note)
            assertEquals("The committed edit must finish even when scheduling fails", 1, completed)
            assertNull(state.entryError)
            assertNotNull(state.error)
            unavailable = false
            state.retry()
            withTimeout(5000) { while (schedules < 2) delay(10) }
            assertEquals(saved, dao.entry(note.id))
            assertNull(state.error)
        } finally { scope.cancel() }
    }

    @Test fun `another walk cannot be edited or deleted through this detail`() = runBlocking {
        dao.insertSession(WalkSessionRow("other", 0, "owner", 1000))
        val other = note.copy(id = "other-note", sessionId = "other")
        entries.save(other)
        val data = data()
        assertTrue(runCatching { data.saveEntry(other) }.isFailure)
        assertTrue(runCatching { data.deleteEntry(other.id) }.isFailure)
        assertNotNull(dao.entry(other.id)!!.payload)
        assertTrue(calls.isEmpty())
    }

    @Test fun `committed deletion completes once and retry preserves its tombstone`() = runBlocking {
        entries.save(note)
        var unavailable = true
        var schedules = 0
        val data = data(enqueue = { schedules++; if (unavailable) error("scheduler unavailable") })
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val state = WalkDetailState(data, data, scope, {})
            var completed = 0
            state.deleteEntry(note.id) { completed++ }
            withTimeout(5000) { while (state.savingEntry) delay(10) }
            val deleted = requireNotNull(dao.entry(note.id))
            assertNull(deleted.payload); assertTrue(deleted.dirty)
            assertEquals(1, completed); assertNull(state.entryError); assertNotNull(state.error)
            unavailable = false
            state.retry()
            withTimeout(5000) { while (schedules < 2) delay(10) }
            assertEquals(deleted, dao.entry(note.id)); assertEquals(1, completed)
            assertNull(state.error)
        } finally { scope.cancel() }
    }

    @Test fun `delivery cancellation and changed login never report a completed old edit`() = runBlocking {
        val cancelled = CancellationException("left detail")
        assertSame(cancelled, runCatching { data(enqueue = { throw cancelled }).saveEntry(note) }.exceptionOrNull())
        val saved = dao.entry(note.id)!!.entry()!!
        val old = data(enqueue = { account = account.copy(generation = 2); error("scheduler unavailable") })
        assertTrue(runCatching { old.saveEntry(saved.copy(note = "새로 저장한 내용")) }.exceptionOrNull() is CancellationException)
        assertEquals("새로 저장한 내용", dao.entry(note.id)!!.entry()!!.note)
        assertTrue(dao.entry(note.id)!!.dirty)
    }

    @Test fun `scene edit keeps source entries and publication checks in Room`() = runBlocking {
        dao.deleteSession("s")
        dao.insertSession(WalkSessionRow("s", 0, "owner", null))
        dao.closeAndPrepareDiary("s", 1000)
        entries.save(note)
        val publication = requireNotNull(dao.prepareLocalDiary("s", "owner"))
        val scene = GeoStoryboardBundle.parse(publication.baseBundle!!).scenes.first()
        val data = data()
        assertTrue(runCatching { data.saveScene(scene, "내 제목", "내 장면") }.isFailure)
        dao.publishDiaryBase("s", publication.deadlineAtMillis)
        data.saveScene(scene, "내 제목", "내 장면")
        val edit = StoryboardDraft.parse(dao.storyboard("s")!!.payload).edits.single()
        assertEquals(SceneBodyScope.SCENE, edit.bodyScope)
        assertEquals("내 장면", edit.body)
        assertEquals("원본 메모", dao.entry("note")!!.entry()!!.note)
        val detail = requireNotNull(data.load())
        assertEquals("내 제목", data.observeDiary(detail).first()!!.scenes.first().title)
        assertTrue(calls.isEmpty())
    }

    @Test fun `reads share the saved session and disappear after deletion or a new login`() = runBlocking {
        entries.save(note)
        val data = data()
        assertEquals(listOf("note"), data.entries.first().map { it.id })
        val detail = requireNotNull(data.load())
        assertEquals("s", detail.summary.sessionId)
        assertEquals(detail.summary, data.observeDiary(detail).first()!!.summary)
        account = account.copy(generation = 2)
        assertFalse(data.isCurrentAccount())
        assertTrue(data.entries.first().isEmpty())
        assertNull(data.observeDiary(detail).first())
        assertTrue(runCatching { data.load() }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { data.saveEntry(note) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { data.deleteEntry("note") }.exceptionOrNull() is CancellationException)
        data.prepareDiary()
        assertTrue(calls.isEmpty())
        val current = data()
        dao.deleteSession("s")
        assertNull(current.load())
        assertNull(current.observeDiary(detail).first())
    }

    @Test fun `photo deletion delegates file and row cleanup and rejects other walks`() = runBlocking {
        val capture = WalkPhotoCapture("s", "owner", 500,
            LocationSample(GeoPoint(37.5, 127.0), 400, 1_000_000_000, 5f))
        fun shot() = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val own = photos.save(capture, shot())
        dao.insertSession(WalkSessionRow("other", 0, "owner", 1000))
        val other = photos.save(capture.copy(sessionId = "other"), shot())
        val data = data()
        assertTrue(runCatching { data.deletePhoto(other.id) }.isFailure)
        assertTrue(other.file.exists())
        data.deletePhoto(own.id)
        assertNull(dao.photo(own.id)); assertFalse(own.file.exists())
        assertNotNull(dao.photo(other.id)); assertTrue(other.file.exists())
    }
}
