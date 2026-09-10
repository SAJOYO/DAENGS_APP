package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
    private fun checkDb(block: suspend (WalkDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow(id, 0, endedAtMillis = 10000, ownerId = "owner", serverWalkId = "remote"))
            listOf("note", "action").forEach { dao.insertEntry(WalkEntryRow(it, id, "{}", 1, it, false)) }
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
        assertTrue(storyboardAnalysisView(dao.sceneAnalysis(id), dao.entries(id)).canReview)
    }

    @Test fun `publication sends only the budget left since end and never posts after publication`() = checkDb { dao ->
        val started = 10000L
        val base = LocalDiaryBoard.build(com.daengs.app.walk.WalkSummary(id, emptyList(), 0, 10000,
            null, 0.0, 0, emptyList(), null), emptyList(), emptyList(), emptyList())
        dao.insertDiaryPublication(WalkDiaryPublicationRow(id, started, started + 10000, base))
        var posts = 0
        val sync = WalkDiarySync(dao, { "owner" }, nowMillis = { 14000 }, request = { _, path, method, body ->
            when {
                path.endsWith("capabilities") -> capability.put("diary_publication",
                    JSONObject().put("format", ServerDiaryBoard.FORMAT).put("budget_ms", 10000))
                method == "GET" -> diaryBoardFixture().put("status", "pending").put("bundle", JSONObject.NULL)
                else -> {
                    posts++
                    assertTrue(body!!.getLong("preparation_budget_ms") in 0..6000)
                    assertFalse(body.getBoolean("refresh"))
                    diaryBoardFixture()
                }
            }
        })
        sync.sync("token", id, "remote")
        assertEquals(1, posts)
        val published = dao.diaryPublication(id)!!.publishedBundle
        assertNotNull(published)
        sync.sync("token", id, "remote", refresh = true)
        assertEquals(1, posts)
        assertEquals(published, dao.diaryPublication(id)!!.publishedBundle)
    }

    @Test fun `new local preparation skips unsupported generation and an expired budget`() = checkDb { dao ->
        val started = 10000L
        dao.insertDiaryPublication(WalkDiaryPublicationRow(id, started, started + 10000, "local-base"))
        WalkDiarySync(dao, { "owner" }, nowMillis = { 14000 }, legacy = { _, _, _, _ -> error("no legacy generation") },
            request = { _, path, method, _ ->
                assertEquals("GET", method)
                if (path.endsWith("capabilities")) capability else diaryBoardFixture().put("status", "pending").put("bundle", JSONObject.NULL)
            }).sync("token", id, "remote")
        dao.publishDiaryBase(id, started + 10000)
        WalkDiarySync(dao, { "owner" }, request = { _, _, _, _ -> error("published means no network generation") })
            .sync("token", id, "remote")
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

    @Test fun `failure and account change never downgrade or publish a late board`() = checkDb { dao ->
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
            assertTrue(runCatching { sync.sync("token", id, "remote") }.isFailure)
            assertNull(dao.sceneAnalysis(id)!!.bundle)
        }
        assertEquals(0, legacy)
    }
}
