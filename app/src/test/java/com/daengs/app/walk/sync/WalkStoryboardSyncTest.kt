package com.daengs.app.walk.sync

import com.daengs.app.walk.support.titledDiaryFixture
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.store.*
import com.daengs.app.walk.store.storedStoryboardAnalysisView
import com.daengs.app.walk.support.sceneAnchorFixture
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkStoryboardSyncTest {
    @Test fun `기존 기록의 분석 stamp는 마이그레이션 후에도 그대로다`() {
        val row = WalkEntryRow("e", "s", "content", 3, "mutation", false)
        val old = org.json.JSONArray().put(org.json.JSONArray(listOf("e", 3, "mutation", false, null))).toString()
        assertEquals(com.daengs.app.walk.diary.storyboardHash(old), storyboardEntryStamp(listOf(row)))
    }
    @Test fun `v4 source GPS identity survives Room storage`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            val fixture = sceneAnchorFixture().first
            val bundle = JSONObject(fixture.rawJson).put("session_id", "s")
            WalkStoryboardSync(dao, { owner }) { _, _, body ->
                assertEquals("walk-storyboard-candidates-v4", body.getString("bundle_format"))
                response().put("bundle", bundle)
            }.sync(token, "s", "remote")
            val stored = storedStoryboardAnalysisView(dao.sceneAnalysis("s"), emptyList()).bundle!!
            assertEquals(fixture.scenes.map { it.observation }, stored.scenes.map { it.observation })
        } finally { db.close() }
    }
    @Test fun `v3 title persists and only old format rejection falls back to v2`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            WalkStoryboardSync(dao, { owner }) { _, _, _ -> response().put("bundle",
                titledDiaryFixture()) }.sync(token, "s", "remote")
            assertEquals("함께 남긴 산책 기록", storedStoryboardAnalysisView(dao.sceneAnalysis("s"), emptyList()).bundle!!.title)
            val formats = mutableListOf<String>()
            WalkStoryboardSync(dao, { owner }) { _, _, body ->
                formats += body.getString("bundle_format")
                if (formats.size <= 2) throw WalkHttpException(422,
                    """[{"type":"literal_error","loc":["body","bundle_format"]}]""")
                response(2)
            }.sync(token, "s", "remote")
            assertEquals(listOf("walk-storyboard-candidates-v4", "walk-storyboard-candidates-v3", "walk-storyboard-candidates-v2"), formats)
            assertEquals("ready", dao.sceneAnalysis("s")!!.status)
            var calls = 0
            val failed = runCatching { WalkStoryboardSync(dao, { owner }) { _, _, _ ->
                calls++; throw WalkHttpException(422, "other validation failure")
            }.sync(token, "s", "remote") }
            assertTrue(failed.isFailure); assertEquals(1, calls)
        } finally { db.close() }
    }
    private val owner = "owner"
    private val token = "header."+java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"sub\":\"owner\"}".toByteArray())+".signature"
    private fun response(generation: Long = 1): JSONObject {
        val bundle = JSONObject(javaClass.getResource("/storyboard/pinless.json")!!.readText())
            .put("session_id", "s").put("synthetic", false)
        return JSONObject().put("session_id", "s").put("generation", generation).put("input_revision", "revision")
            .put("status", "ready").put("entry_revisions", JSONObject()).put("bundle", bundle)
    }

    @Test fun `pending preparation keeps previous scenes readable without marking them current`() {
        val stamp = storyboardEntryStamp(emptyList())
        val pending = WalkSceneAnalysisRow("s", 0, stamp, "revision", "pending", null, null)
        val first = storedStoryboardAnalysisView(pending, emptyList())
        assertNull(first.bundle)
        assertFalse(first.canReview)
        assertTrue(first.notice.contains("준비하고 있어요"))

        val saved = pending.copy(generation = 1, status = "ready",
            bundle = response().getJSONObject("bundle").toString(), bundleEntryStamp = stamp)
        val ready = storedStoryboardAnalysisView(saved, emptyList())
        assertTrue(ready.canReview)
        val previous = storedStoryboardAnalysisView(saved.copy(status = "pending"), emptyList())
        assertEquals(ready.bundle!!.scenes, previous.bundle!!.scenes)
        assertFalse(previous.canReview)
        assertEquals(storedStoryboardAnalysisView(saved.copy(status = "running"), emptyList()).notice, previous.notice)
        assertTrue(previous.notice.contains("이전에 저장한 장면"))

        val dirty = listOf(WalkEntryRow("new", "s", "{}", 0, "mutation", true))
        val changed = storedStoryboardAnalysisView(saved.copy(status = "pending"), dirty)
        assertNull(changed.bundle)
        assertFalse(changed.canReview)
        assertTrue(changed.notice.contains("기록 동기화 후"))
    }

    @Test fun `network failure preserves readable source and retry restores review eligibility`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            dao.saveStoryboard(WalkStoryboardRow("s", "user edits and reviewed snapshot"))
            var fail = false
            val sync = WalkStoryboardSync(dao, { owner }) { _, _, _ ->
                if (fail) {
                    val during = storedStoryboardAnalysisView(dao.sceneAnalysis("s"), dao.entries("s"))
                    assertNotNull(during.bundle)
                    assertFalse(during.canReview)
                    throw java.io.IOException("connection lost")
                }
                response()
            }
            sync.sync(token, "s", "remote")
            val original = dao.sceneAnalysis("s")!!.bundle
            fail = true
            assertTrue(runCatching { sync.sync(token, "s", "remote") }.isFailure)
            assertEquals(original, dao.sceneAnalysis("s")!!.bundle)
            val cached = storedStoryboardAnalysisView(dao.sceneAnalysis("s"), dao.entries("s"))
            assertNotNull(cached.bundle)
            assertFalse(cached.canReview)
            assertTrue(cached.notice.contains("이전에 저장한 장면"))
            assertEquals("user edits and reviewed snapshot", dao.storyboard("s")!!.payload)
            fail = false
            sync.sync(token, "s", "remote")
            assertTrue(storedStoryboardAnalysisView(dao.sceneAnalysis("s"), dao.entries("s")).canReview)
        } finally { db.close() }
    }

    @Test fun `cancelled refresh keeps source available until a new sync completes`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            WalkStoryboardSync(dao, { owner }) { _, _, _ -> response() }.sync(token, "s", "remote")
            val original = dao.sceneAnalysis("s")!!.bundle
            val interrupted = WalkStoryboardSync(dao, { owner }) { _, _, _ -> throw kotlinx.coroutines.CancellationException() }
            val failure = runCatching { interrupted.sync(token, "s", "remote", refresh = true) }.exceptionOrNull()
            assertTrue(failure is kotlinx.coroutines.CancellationException)
            assertEquals(original, dao.sceneAnalysis("s")!!.bundle)
            assertFalse(storedStoryboardAnalysisView(dao.sceneAnalysis("s"), dao.entries("s")).canReview)
            WalkStoryboardSync(dao, { owner }) { _, _, _ -> response(2) }.sync(token, "s", "remote")
            assertTrue(storedStoryboardAnalysisView(dao.sceneAnalysis("s"), dao.entries("s")).canReview)
        } finally { db.close() }
    }

    @Test fun `nonready malformed and obsolete responses cannot erase last successful bundle`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            var remote = response(5)
            val sync = WalkStoryboardSync(dao, { owner }) { _, _, _ -> remote }
            sync.sync(token, "s", "remote")
            val saved = dao.sceneAnalysis("s")!!.bundle
            val badResponses = listOf("pending", "running", "failed", "stale").map {
                response(6).put("status", it).put("bundle", JSONObject.NULL)
            } + listOf(response(4), response(7).also { it.getJSONObject("bundle").put("synthetic", true) })
            for (bad in badResponses) {
                remote = bad
                assertTrue(runCatching { sync.sync(token, "s", "remote") }.isFailure)
                assertEquals(saved, dao.sceneAnalysis("s")!!.bundle)
                assertFalse(storedStoryboardAnalysisView(dao.sceneAnalysis("s"), dao.entries("s")).canReview)
            }
            remote = response(8)
            sync.sync(token, "s", "remote")
            assertEquals(8L, dao.sceneAnalysis("s")!!.generation)
            assertTrue(storedStoryboardAnalysisView(dao.sceneAnalysis("s"), dao.entries("s")).canReview)
        } finally { db.close() }
    }

    @Test fun `changed or deleted inputs never relabel old cache as their own source`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            val sync = WalkStoryboardSync(dao, { owner }) { _, _, _ -> response() }
            sync.sync(token, "s", "remote")
            val original = dao.sceneAnalysis("s")!!
            dao.insertEntry(WalkEntryRow("deleted", "s", null, 2, "deletion", false))
            val changedStamp = storyboardEntryStamp(dao.entries("s"))
            assertTrue(dao.acceptSceneAnalysis(original.copy(entryStamp = changedStamp, status = "running", bundle = null), owner))
            assertEquals(original.bundleEntryStamp, dao.sceneAnalysis("s")!!.bundleEntryStamp)
            assertEquals(original.bundle, dao.sceneAnalysis("s")!!.bundle)
            val stale = storedStoryboardAnalysisView(dao.sceneAnalysis("s"), dao.entries("s"))
            assertNull(stale.bundle)
            assertFalse(stale.canReview)
            assertFalse(dao.acceptSceneAnalysis(original.copy(generation = 20), owner))
        } finally { db.close() }
    }

    @Test fun `real response persists separately from edits and survives reading again`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            dao.saveStoryboard(WalkStoryboardRow("s", "existing user draft"))
            val sync = WalkStoryboardSync(dao, { owner }) { _, path, body ->
                assertEquals("/remote/storyboard", path)
                assertEquals(0, body.getJSONObject("expected_entries").length())
                assertEquals("walk-storyboard-candidates-v4", body.getString("bundle_format"))
                response()
            }
            sync.sync(token, "s", "remote")
            assertEquals("ready", dao.sceneAnalysis("s")!!.status)
            assertEquals("existing user draft", dao.storyboard("s")!!.payload)
            assertFalse(JSONObject(dao.sceneAnalysis("s")!!.bundle!!).getBoolean("synthetic"))
            dao.deleteSession("s")
            assertNull(dao.sceneAnalysis("s"))
        } finally { db.close() }
    }

    @Test fun `source mutation during request rejects late result and preserves edits`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            val sync = WalkStoryboardSync(dao, { owner }) { _, _, _ ->
                dao.insertEntry(WalkEntryRow("new", "s", "{}", 0, "mutation", true))
                response()
            }
            assertTrue(runCatching { sync.sync(token, "s", "remote") }.isFailure)
            assertNotEquals("ready", dao.sceneAnalysis("s")!!.status)
            assertNull(dao.sceneAnalysis("s")!!.bundle)
        } finally { db.close() }
    }

    @Test fun `older generation and other owner cannot replace current source`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            val row = WalkSceneAnalysisRow("s", 5, storyboardEntryStamp(emptyList()), "new", "ready", "{}", null)
            assertTrue(dao.acceptSceneAnalysis(row, owner))
            assertFalse(dao.acceptSceneAnalysis(row.copy(generation = 4), owner))
            assertFalse(dao.acceptSceneAnalysis(row.copy(generation = 6), "other"))
            var calls = 0
            WalkStoryboardSync(dao, { "other" }) { _, _, _ -> calls++; response() }.sync(token, "s", "remote")
            assertEquals(0, calls)
            assertEquals(5L, dao.sceneAnalysis("s")!!.generation)
        } finally { db.close() }
    }

    @Test fun `failed analysis can retry but malformed or synthetic response cannot become ready`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            var synthetic = true
            val sync = WalkStoryboardSync(dao, { owner }) { _, _, _ -> response().also {
                it.getJSONObject("bundle").put("synthetic", synthetic)
            } }
            assertTrue(runCatching { sync.sync(token, "s", "remote") }.isFailure)
            assertEquals("failed", dao.sceneAnalysis("s")!!.status)
            synthetic = false
            sync.sync(token, "s", "remote")
            assertEquals("ready", dao.sceneAnalysis("s")!!.status)
        } finally { db.close() }
    }
}
