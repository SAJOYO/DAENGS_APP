package com.daengs.app.walk.diary

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import com.daengs.app.walk.support.diaryBoardFixture
import com.daengs.app.walk.sync.WalkDiarySync
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.time.Instant

/** Exercises the real coordinator and sync through Room to the Reader, with only time/HTTP controlled. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkDiaryPublicationLifecycleTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val started = Instant.parse("2026-09-09T00:00:00Z").toEpochMilli()
    private val ended = started + 1_000_000

    private inner class Fixture(val test: TestScope, val db: WalkDatabase) {
        val dao = db.walkDao()
        // A separate clock prevents runTest's automatic IO waiting from expiring the publication timer.
        val clock = TestCoroutineScheduler()
        val dispatcher = UnconfinedTestDispatcher(clock)
        @Volatile var owner = "owner"
        val reader = WalkDiaryReader(dao, WalkPhotoStore(dao,
            File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "publication-lifecycle")) { owner }) { owner }
        val summary = WalkSummary(id, emptyList(), started, ended, null, 0.0, 0, emptyList(), null)
        val jobs = mutableListOf<Job>()
        var requests = 0
        val responseStarted = CompletableDeferred<Unit>()
        val syncFinished = CompletableDeferred<Unit>()
        var response: suspend () -> JSONObject = { diaryBoardFixture() }
        val now get() = ended + clock.currentTime
        val sync = WalkDiarySync(dao, { owner }, nowMillis = { now }, request = { _, path, method, _ ->
            requests++
            assertEquals("GET", method)
            if (path.endsWith("capabilities")) JSONObject().put("diary_formats", org.json.JSONArray(listOf(ServerDiaryBoard.FORMAT)))
            else { responseStarted.complete(Unit); response() }
        })

        fun publisher(): WalkDiaryPublication {
            val job = SupervisorJob(test.backgroundScope.coroutineContext[Job])
            jobs += job
            return WalkDiaryPublication(dao, { owner }, CoroutineScope(test.backgroundScope.coroutineContext + job + clock),
                { try { sync.sync("token", it, "remote") } finally { syncFinished.complete(Unit) } },
                now = { now }, dispatcher = dispatcher)
        }

        suspend fun prepare(): WalkDiaryPublicationRow {
            dao.insertSession(WalkSessionRow(id, started, "owner", null, serverWalkId = "remote"))
            listOf(
                WalkEntry("note", id, WalkMomentType.NOTE, started + 300_000, note = "함께 걸었다"),
                WalkEntry("action", id, WalkMomentType.SNIFFING, started + 420_000),
            ).forEach { dao.insertEntry(WalkEntryRow(it.id, id, it.toJson().toString(), 1, it.id, false)) }
            dao.closeAndPrepareDiary(id, ended)
            return requireNotNull(dao.prepareLocalDiary(id, owner)).also {
                assertEquals(ended + 10_000, it.deadlineAtMillis)
                assertNotNull(it.baseBundle)
                assertNull(it.publishedBundle)
            }
        }

        fun advance(millis: Long) {
            clock.advanceTimeBy(millis)
            clock.runCurrent()
            test.runCurrent()
        }
        suspend fun visible(): List<DiaryWalk> {
            val before = clock.currentTime
            // Reader uses IO. Its safety timeout must not advance the publication clock while IO is scheduled.
            val result = withContext(Dispatchers.IO) {
                withTimeout(5_000) { reader.observe(listOf(summary)).first() }
            }
            assertEquals("Reading a board must not move the publication clock", before, clock.currentTime)
            return result
        }
        suspend fun publication() = requireNotNull(dao.diaryPublication(id))
    }

    private fun checkPublication(block: suspend Fixture.() -> Unit) = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler) + testScheduler).build()
        val fixture = Fixture(this, db)
        try { fixture.block() }
        finally { fixture.jobs.forEach { it.cancelAndJoin() }; db.close() }
    }

    @Test fun `blocked sync cannot delay publication and its late board cannot replace what the Reader shows`() = checkPublication {
        val state = prepare()
        val release = CompletableDeferred<Unit>()
        response = { release.await(); diaryBoardFixture() }
        assertTrue(visible().single().preparing)
        val publisher = publisher()
        publisher.start(id)
        test.runCurrent()
        assertTrue(responseStarted.isCompleted)
        publisher.start(id)
        publisher.recover()
        test.runCurrent()
        assertEquals(2, requests) // One capability and one board read despite repeated entry.
        advance(9_999)
        assertNull(publication().publishedBundle)
        advance(1)
        val published = publication()
        assertEquals(state.baseBundle, published.publishedBundle)
        assertEquals(state.deadlineAtMillis, published.publishedAtMillis)
        assertFalse(syncFinished.isCompleted)
        val displayed = visible().single()
        assertFalse(displayed.preparing)
        assertTrue(displayed.scenes.isNotEmpty())
        release.complete(Unit)
        test.runCurrent()
        assertTrue(syncFinished.isCompleted)
        assertNotNull(dao.sceneAnalysis(id)?.bundle) // A late analysis can be stored; the published board stays fixed.
        assertEquals(published, publication())
        assertEquals(displayed, visible().single())
    }

    @Test fun `offline sync still opens the local board at the saved deadline`() = checkPublication {
        val state = prepare()
        response = { throw IOException("offline") }
        publisher().start(id)
        test.runCurrent()
        assertTrue(syncFinished.isCompleted)
        assertNull(publication().publishedBundle)
        advance(10_000)
        assertEquals(state.baseBundle, publication().publishedBundle)
        assertEquals(state.deadlineAtMillis, publication().publishedAtMillis)
        assertFalse(visible().single().preparing)
    }

    @Test fun `fast server publication reaches the Reader before the timer and survives timer and reentry`() = checkPublication {
        val state = prepare()
        response = { delay(250); diaryBoardFixture() }
        val publisher = publisher()
        publisher.start(id)
        test.runCurrent()
        advance(249)
        assertNull(publication().publishedBundle)
        advance(1)
        assertTrue(syncFinished.isCompleted)
        val published = publication()
        assertNotEquals(state.baseBundle, published.publishedBundle)
        assertEquals(ended + 250, published.publishedAtMillis)
        assertEquals("함께 남긴 산책", visible().single().title)
        advance(9_750)
        publisher.start(id)
        publisher.recover()
        test.runCurrent()
        assertEquals(published, publication())
        assertEquals(2, requests)
    }

    @Test fun `account change blocks both late sync and timer until the original owner recovers`() = checkPublication {
        val state = prepare()
        val release = CompletableDeferred<Unit>()
        response = { release.await(); diaryBoardFixture() }
        val publisher = publisher()
        publisher.start(id)
        test.runCurrent()
        assertTrue(responseStarted.isCompleted)
        owner = "another"
        advance(10_000)
        release.complete(Unit)
        test.runCurrent()
        assertTrue(syncFinished.isCompleted)
        assertNull(publication().publishedBundle)
        assertNull(dao.sceneAnalysis(id)?.bundle)
        assertTrue(visible().isEmpty())
        owner = "owner"
        publisher.recover()
        test.runCurrent()
        assertEquals(state.baseBundle, publication().publishedBundle)
        assertEquals(state.deadlineAtMillis, publication().deadlineAtMillis)
        assertEquals(2, requests)
        assertFalse(visible().single().preparing)
    }

    @Test fun `restart during preparation uses the remaining time instead of a fresh ten seconds`() = checkPublication {
        val state = prepare()
        response = { throw IOException("offline") }
        publisher().start(id)
        test.runCurrent()
        advance(4_000)
        jobs.single().cancelAndJoin()
        publisher().recover()
        test.runCurrent()
        advance(5_999)
        assertNull(publication().publishedBundle)
        advance(1)
        assertEquals(state.baseBundle, publication().publishedBundle)
        assertEquals(state.deadlineAtMillis, publication().publishedAtMillis)
        assertFalse(visible().single().preparing)
    }
}
