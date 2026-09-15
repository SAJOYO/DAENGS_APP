package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.store.*
import com.daengs.app.walk.support.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkDiaryBoardSyncTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val capability get() = JSONObject("""{"diary_formats":["walk-diary-bundle-v1","walk-diary-board-v1"]}""")
    private fun checkDb(preparing: Boolean = false, block: suspend (WalkDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow(id, 0, endedAtMillis = if (preparing) null else 10000,
                ownerId = "owner", serverWalkId = "remote"))
            listOf(WalkEntry("note", id, WalkMomentType.NOTE, 500, note = "함께 걸었다"),
                WalkEntry("action", id, WalkMomentType.SNIFFING, 700)).forEach {
                dao.insertEntry(WalkEntryRow(it.id, id, it.toJson().toString(), 1, it.id, false))
            }
            if (preparing) {
                dao.closeAndPrepareDiary(id, 10000)
                val state = requireNotNull(dao.prepareLocalDiary(id, "owner"))
                assertEquals(30000, state.deadlineAtMillis)
                assertNotNull(state.baseBundle)
                assertNull(state.publishedBundle)
            } else assertNull(dao.diaryPublication(id)) // Completed legacy walk, outside publication.
            block(dao)
        } finally { db.close() }
    }

    @Test fun `new format refresh only reads and preserves whole body edits through Room`() = checkDb { dao ->
        var posts = 0
        val sync = WalkDiarySync(dao, { "owner" }, request = { _, path, method, _ ->
            if (path.endsWith("capabilities")) capability else {
                assertTrue(path.contains("bundle_format=walk-diary-board-v1"))
                if (method == "POST") posts++
                diaryBoardFixture()
            }
        })
        sync.sync("token", id, "remote")
        val saved = dao.sceneAnalysis(id)!!.bundle
        val b = GeoStoryboardBundle.parse(saved!!)
        val note = b.scenes.single { it.id == "entry:note" }
        val checkpoint = b.scenes.single { it.id.startsWith("checkpoint:") }
        dao.saveDiarySceneEdit(id, "owner", note, "직접 붙인 제목", "내가 완성한 한 장면")
        dao.saveDiarySceneEdit(id, "owner", checkpoint, "비워 둔 장면", "")
        val hidden = StoryboardDraft.parse(dao.storyboard(id)!!.payload).edit(b.scenes.last(), hidden = true)
        dao.saveStoryboard(WalkStoryboardRow(id, hidden.toJson()))
        sync.sync("token", id, "remote", refresh = true)
        assertEquals(0, posts)
        assertEquals(saved, dao.sceneAnalysis(id)!!.bundle)
        val edits = applyStoryboardEdits(b.scenes, StoryboardDraft.parse(dao.storyboard(id)!!.payload))
        assertEquals("내가 완성한 한 장면", edits.single { it.id == note.id }.sceneBody())
        assertEquals("직접 붙인 제목", edits.single { it.id == note.id }.title)
        assertEquals("", edits.single { it.id == checkpoint.id }.sceneBody())
        assertTrue(edits.last().hidden)
        assertTrue(storedStoryboardAnalysisView(dao.sceneAnalysis(id), dao.entries(id)).canReview)
    }

    @Test fun `preparing publication sends remaining budget and only checks capabilities after publication`() = checkDb(preparing = true) { dao ->
        var posts = 0
        var requests = 0
        val sync = WalkDiarySync(dao, { "owner" }, nowMillis = { 14000 }, request = { _, path, method, body ->
            requests++
            when {
                path.endsWith("capabilities") -> capability.put("diary_publication",
                    JSONObject().put("format", ServerDiaryBoard.FORMAT).put("budget_ms", 20000))
                method == "GET" -> diaryBoardFixture().put("status", "pending").put("bundle", JSONObject.NULL)
                else -> {
                    posts++
                    assertEquals(16000, body!!.getLong("preparation_budget_ms"))
                    assertFalse(body.getBoolean("refresh"))
                    diaryBoardFixture()
                }
            }
        })
        sync.sync("token", id, "remote")
        assertEquals(1, posts)
        val published = dao.diaryPublication(id)!!.publishedBundle
        assertNotNull(published)
        val requestsBeforeRefresh = requests
        sync.sync("token", id, "remote", refresh = true)
        assertEquals(1, posts)
        assertEquals(requestsBeforeRefresh + 1, requests) // Capability check only; closed legacy board remains untouched.
        assertEquals(published, dao.diaryPublication(id)!!.publishedBundle)
    }

    @Test fun `pending context retries with the decreasing original budget then publishes early`() = checkDb(preparing = true) { dao ->
        var now = 14000L
        val budgets = mutableListOf<Long>()
        fun pending() = diaryBoardFixture().put("status", "pending").put("bundle", JSONObject.NULL)
        WalkDiarySync(dao, { "owner" }, nowMillis = { now }, pause = { now += 2000 },
            request = { _, path, method, body ->
                when {
                    path.endsWith("capabilities") -> capability.put("diary_publication",
                        JSONObject().put("format", ServerDiaryBoard.FORMAT).put("budget_ms", 20000))
                    method == "GET" -> pending()
                    else -> {
                        budgets += body!!.getLong("preparation_budget_ms")
                        assertFalse(body.getBoolean("refresh"))
                        if (budgets.size == 1) pending() else diaryBoardFixture()
                    }
                }
            }).sync("token", id, "remote", refresh = true)
        assertEquals(listOf(16000L, 14000L), budgets)
        assertEquals(30000L, dao.diaryPublication(id)!!.deadlineAtMillis)
        assertEquals(16000L, dao.diaryPublication(id)!!.publishedAtMillis)
    }

    @Test fun `pending context stops polling at deadline without extending it`() = checkDb(preparing = true) { dao ->
        var now = 26000L
        val budgets = mutableListOf<Long>()
        WalkDiarySync(dao, { "owner" }, nowMillis = { now }, pause = { now += 2000 },
            request = { _, path, method, body ->
                assertTrue("No request may start after expiry", now < 30000)
                if (path.endsWith("capabilities")) capability.put("diary_publication",
                    JSONObject().put("format", ServerDiaryBoard.FORMAT).put("budget_ms", 20000))
                else {
                    if (method == "POST") budgets += body!!.getLong("preparation_budget_ms")
                    diaryBoardFixture().put("status", "pending").put("bundle", JSONObject.NULL)
                }
            }).sync("token", id, "remote")
        assertEquals(listOf(4000L, 2000L), budgets)
        assertEquals(30000L, dao.diaryPublication(id)!!.deadlineAtMillis)
        assertNull(dao.diaryPublication(id)!!.publishedBundle)
    }

    @Test fun `new preparation respects the older server ten second maximum`() = checkDb(preparing = true) { dao ->
        WalkDiarySync(dao, { "owner" }, nowMillis = { 11000 }, request = { _, path, method, body ->
            when {
                path.endsWith("capabilities") -> capability.put("diary_publication",
                    JSONObject().put("format", ServerDiaryBoard.FORMAT).put("budget_ms", 10000))
                method == "GET" -> diaryBoardFixture().put("status", "pending").put("bundle", JSONObject.NULL)
                else -> {
                    assertEquals(10000L, body!!.getLong("preparation_budget_ms"))
                    diaryBoardFixture()
                }
            }
        }).sync("token", id, "remote")
        assertNotNull(dao.diaryPublication(id)!!.publishedBundle)
    }

    @Test fun `preparing publication without publication capability never starts generation`() = checkDb(preparing = true) { dao ->
        var reads = 0
        WalkDiarySync(dao, { "owner" }, nowMillis = { 14000 }, legacy = { _, _, _, _ -> error("no legacy generation") },
            request = { _, path, method, _ ->
                reads++
                assertEquals("GET", method)
                if (path.endsWith("capabilities")) capability else diaryBoardFixture().put("status", "pending").put("bundle", JSONObject.NULL)
            }).sync("token", id, "remote")
        assertEquals(2, reads)
        assertNull(dao.diaryPublication(id)!!.publishedBundle)
    }

    @Test fun `expired preparation can discover formats but cannot request a legacy board`() = checkDb(preparing = true) { dao ->
        val state = requireNotNull(dao.diaryPublication(id))
        for (now in listOf(state.deadlineAtMillis, state.deadlineAtMillis + 1)) {
            var checks = 0
            WalkDiarySync(dao, { "owner" }, nowMillis = { now },
                request = { _, path, method, _ ->
                    assertEquals("/storyboard/capabilities", path)
                    assertEquals("GET", method)
                    checks++
                    capability
                })
                .sync("token", id, "remote", refresh = true)
            assertEquals(1, checks)
            assertEquals(state, dao.diaryPublication(id)) // No direct DAO publication concealing the expiry guard.
            assertNull(dao.sceneAnalysis(id))
        }
    }

    @Test fun `a pending read crossing the deadline cannot start a generation`() = checkDb(preparing = true) { dao ->
        var now = 29999L
        var reads = 0
        WalkDiarySync(dao, { "owner" }, nowMillis = { now }, request = { _, path, method, _ ->
            assertEquals("GET", method)
            reads++
            if (path.endsWith("capabilities")) capability.put("diary_publication",
                JSONObject().put("format", ServerDiaryBoard.FORMAT))
            else {
                now = 30000
                diaryBoardFixture().put("status", "pending").put("bundle", JSONObject.NULL)
            }
        }).sync("token", id, "remote")
        assertEquals(2, reads)
        assertNull(dao.diaryPublication(id)!!.publishedBundle)
    }

    @Test fun `pending posts negotiated contract once then reads completed board`() = checkDb { dao ->
        dao.insertPhotoSync(WalkPhotoSyncRow(id, "owner", "publisher", 1, 1))
        var reads = 0; var posts = 0
        WalkDiarySync(dao, { "owner" }, pause = {}, request = { _, path, method, body ->
            when {
                path.endsWith("capabilities") -> capability
                method == "GET" -> if (reads++ == 0) diaryBoardFixture().put("status", "pending").put("bundle", JSONObject.NULL) else diaryBoardFixture()
                else -> {
                    posts++
                    assertEquals(ServerDiaryBoard.FORMAT, body!!.getString("bundle_format"))
                    assertFalse(body.getBoolean("refresh"))
                    assertEquals("publisher", body.getJSONObject("expected_photo_manifest").getString("publisher_id"))
                    diaryBoardFixture().put("status", "running").put("bundle", JSONObject.NULL)
                }
            }
        }).sync("token", id, "remote", refresh = true)
        assertEquals(1, posts); assertEquals(2, reads)
        assertEquals(7, GeoStoryboardBundle.parse(dao.sceneAnalysis(id)!!.bundle!!).scenes.size)
    }

    @Test fun `saved v1 remains v1 even when new capability and refresh are selected`() = checkDb { dao ->
        WalkDiarySync(dao, { "owner" }, request = { _, path, method, _ ->
            assertEquals("GET", method)
            if (path.endsWith("capabilities")) capability else diaryFixture().put("target_scene_count", 3)
        }).sync("token", id, "remote", refresh = true)
        assertEquals(ServerDiaryBundle.RESPONSE, JSONObject(dao.sceneAnalysis(id)!!.bundle!!).getString("format"))
        assertEquals(4, GeoStoryboardBundle.parse(dao.sceneAnalysis(id)!!.bundle!!).scenes.size)
    }

    @Test fun `stored legacy response is kept in its original format without posting`() = checkDb { dao ->
        val raw = titledDiaryFixture().put("session_id", id)
        val response = JSONObject().put("session_id", id).put("status", "ready").put("generation", 1)
            .put("input_revision", "a".repeat(64)).put("entry_revisions", JSONObject("""{"note":1,"action":1}"""))
            .put("bundle", raw)
        WalkDiarySync(dao, { "owner" }, legacy = { _, _, _, _ -> error("must not regenerate") }, request = { _, path, method, _ ->
            assertEquals("GET", method)
            if (path.endsWith("capabilities")) capability else response
        }).sync("token", id, "remote", refresh = true)
        assertEquals(raw.toString(), dao.sceneAnalysis(id)!!.bundle)
    }

    @Test fun `legacy analysis rejects failed malformed foreign account and mismatched revision responses`() = checkDb { dao ->
        var owner = "owner"
        var legacy = 0
        for (mode in listOf("network", "parse", "account", "record")) {
            owner = "owner"
            val sync = WalkDiarySync(dao, { owner }, legacy = { _, _, _, _ -> legacy++ }, request = { _, path, _, _ ->
                if (path.endsWith("capabilities")) capability else {
                    when (mode) {
                        "network" -> throw java.io.IOException("offline")
                        "parse" -> diaryBoardFixture().also { it.getJSONObject("bundle").put("scenes", org.json.JSONArray()) }
                        "account" -> { owner = "another"; diaryBoardFixture() }
                        else -> diaryBoardFixture().also { it.getJSONObject("entry_revisions").put("note", 2) }
                    }
                }
            })
            val error = runCatching { sync.sync("token", id, "remote") }.exceptionOrNull()
            assertTrue("$mode must fail as a sync error, not an assertion or fixture error: $error", error is java.io.IOException)
            assertNull(dao.sceneAnalysis(id)!!.bundle)
        }
        assertEquals(0, legacy)
    }
}
