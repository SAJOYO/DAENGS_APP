package com.daengs.app.walk.sync

import com.daengs.app.walk.support.diaryFixture
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.store.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkDiarySyncTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val capability get() = JSONObject("""{"diary_formats":["walk-diary-bundle-v1"]}""")

    @Test fun `an expired running lease can return recovered ready output without forcing regeneration`() = checkDb { dao ->
        WalkDiarySync(dao, { "owner" }, request = { _, path, method, body ->
            when {
                path.endsWith("capabilities") -> capability
                method == "GET" -> diaryFixture().put("status", "running").put("bundle", JSONObject.NULL)
                else -> { assertFalse(body!!.getBoolean("refresh")); diaryFixture().put("generation", 2) }
            }
        }).sync("token", id, "remote", refresh = true)
        assertEquals(2L, dao.sceneAnalysis(id)!!.generation)
        assertEquals("ready", dao.sceneAnalysis(id)!!.status)
    }

    @Test fun `unavailable model keeps ready original cards and cancellation keeps lease state`() = checkDb { dao ->
        var cancel = false
        val sync = WalkDiarySync(dao, { "owner" }, request = { _, path, _, _ ->
            if (path.endsWith("capabilities")) capability else {
                if (cancel) throw kotlinx.coroutines.CancellationException()
                diaryFixture().also { r ->
                    val b = r.getJSONObject("bundle")
                    b.put("model_status", "unavailable").put("title_origin", "system").put("failure_code", "provider_failed")
                    b.getJSONArray("scenes").getJSONObject(0).getJSONObject("narration")
                        .put("status", "unavailable").put("text", JSONObject.NULL).put("evidence_ids", org.json.JSONArray())
                }
            }
        })
        sync.sync("token", id, "remote")
        val view = storyboardAnalysisView(dao.sceneAnalysis(id), dao.entries(id))
        assertTrue(view.canReview)
        assertTrue(view.notice.contains("배경 문장을 만들지 못했어요"))
        assertEquals(4, view.bundle!!.scenes.size)
        cancel = true
        assertTrue(runCatching { sync.sync("token", id, "remote") }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals("running", dao.sceneAnalysis(id)!!.status)
    }

    private fun checkDb(block: suspend (WalkDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow(id, 0, endedAtMillis = 10000, ownerId = "owner", serverWalkId = "remote"))
            listOf("note", "action").forEach { dao.insertEntry(WalkEntryRow(it, id, "{}", 1, it, false)) }
            block(dao)
        } finally { db.close() }
    }

    @Test fun `cached GET avoids model POST and keeps source titles after Room reload`() = checkDb { dao ->
        var posts = 0
        val sync = WalkDiarySync(dao, { "owner" }, request = { _, path, method, _ ->
            if (path.endsWith("capabilities")) capability else {
                if (method == "POST") posts++
                diaryFixture()
            }
        })
        sync.sync("token", id, "remote")
        assertEquals(0, posts)
        val view = storyboardAnalysisView(dao.sceneAnalysis(id), dao.entries(id))
        assertTrue(view.canReview)
        assertEquals("두부와 함께 남긴 아침", view.bundle!!.title)
        assertTrue(dao.sceneAnalysis(id)!!.entryStamp.startsWith("diary:"))
    }

    @Test fun `pending generation uses exact manifest then polls running instead of posting again`() = checkDb { dao ->
        dao.insertPhotoSync(WalkPhotoSyncRow(id, "owner", "publisher", 1, 1))
        var reads = 0; var posts = 0
        val sync = WalkDiarySync(dao, { "owner" }, pause = {}, request = { _, path, method, body ->
            when {
                path.endsWith("capabilities") -> capability
                method == "GET" -> if (reads++ == 0) diaryFixture().put("status", "pending").put("bundle", JSONObject.NULL) else diaryFixture()
                else -> {
                    posts++
                    assertEquals(5, body!!.getInt("target_scene_count"))
                    assertEquals("publisher", body.getJSONObject("expected_photo_manifest").getString("publisher_id"))
                    assertEquals(1, body.getJSONObject("expected_entries").getInt("note"))
                    diaryFixture().put("status", "running").put("bundle", JSONObject.NULL)
                }
            }
        })
        sync.sync("token", id, "remote")
        assertEquals(1, posts); assertEquals(2, reads)
        assertEquals("ready", dao.sceneAnalysis(id)!!.status)
    }

    @Test fun `explicit refresh posts once with refresh enabled`() = checkDb { dao ->
        var posts = 0
        WalkDiarySync(dao, { "owner" }, request = { _, path, method, body ->
            if (path.endsWith("capabilities")) capability else {
                if (method == "POST") { posts++; assertTrue(body!!.getBoolean("refresh")) }
                diaryFixture()
            }
        }).sync("token", id, "remote", refresh = true)
        assertEquals(1, posts)
    }

    @Test fun `photo edit during generation rejects late result and invalidates previously cached title`() = checkDb { dao ->
        dao.insertPhotoSync(WalkPhotoSyncRow(id, "owner", "publisher", 1, 1))
        var edit = false
        val sync = WalkDiarySync(dao, { "owner" }, request = { _, path, _, _ ->
            if (path.endsWith("capabilities")) capability else {
                if (edit) dao.touchPhotoSync(id, "owner")
                diaryFixture()
            }
        })
        sync.sync("token", id, "remote")
        val previous = dao.sceneAnalysis(id)!!.bundle
        edit = true
        assertTrue(runCatching { sync.sync("token", id, "remote") }.isFailure)
        assertEquals(previous, dao.sceneAnalysis(id)!!.bundle)
        assertNull(storyboardAnalysisView(dao.sceneAnalysis(id), dao.entries(id), dao.photoSync(id)).bundle)
    }

    @Test fun `unacknowledged photos stop generation and a different server publisher is rejected`() = checkDb { dao ->
        dao.insertPhotoSync(WalkPhotoSyncRow(id, "owner", "local-publisher", 1, 0))
        var calls = 0
        val sync = WalkDiarySync(dao, { "owner" }, request = { _, path, _, _ ->
            if (path.endsWith("capabilities")) capability else { calls++; diaryFixture() }
        })
        assertTrue(runCatching { sync.sync("token", id, "remote") }.isFailure)
        assertEquals(0, calls)
        dao.freezePhotoUpload(id, "owner", 1, "frozen")
        dao.acknowledgePhotoUpload(id, "owner", 1, "frozen")
        assertTrue(runCatching { sync.sync("token", id, "remote") }.isFailure)
        assertEquals(1, calls)
        assertNull(dao.sceneAnalysis(id)!!.bundle)
    }

    @Test fun `disabled and old servers use legacy but network errors never downgrade`() = checkDb { dao ->
        var legacy = 0
        for (missing in listOf(false, true)) {
            WalkDiarySync(dao, { "owner" }, legacy = { _, _, _, _ -> legacy++ }, request = { _, _, _, _ ->
                if (missing) throw WalkHttpException(404, "missing") else JSONObject("""{"diary_formats":[]}""")
            }).sync("token", id, "remote")
        }
        assertEquals(2, legacy)
        assertTrue(runCatching {
            WalkDiarySync(dao, { "owner" }, legacy = { _, _, _, _ -> legacy++ }, request = { _, _, _, _ ->
                throw java.io.IOException("offline")
            }).sync("token", id, "remote")
        }.isFailure)
        assertEquals(2, legacy)
    }

    @Test fun `running lease stays running after bounded polling and retries never force refresh`() = checkDb { dao ->
        var polls = 0; var posts = 0
        val sync = WalkDiarySync(dao, { "owner" }, pause = {}, request = { _, path, method, body ->
            if (path.endsWith("capabilities")) capability else {
                if (method == "POST") { posts++; assertFalse(body!!.getBoolean("refresh")) } else polls++
                diaryFixture().put("status", "running").put("bundle", JSONObject.NULL)
            }
        })
        assertTrue(runCatching { sync.sync("token", id, "remote") }.isFailure)
        assertEquals(9, polls)
        assertEquals(1, posts)
        assertEquals("running", dao.sceneAnalysis(id)!!.status)
    }

    @Test fun `account switch and changed entry revision discard responses`() = checkDb { dao ->
        var owner = "owner"
        val sync = WalkDiarySync(dao, { owner }, request = { _, path, _, _ ->
            if (path.endsWith("capabilities")) capability else { owner = "other"; diaryFixture() }
        })
        assertTrue(runCatching { sync.sync("token", id, "remote") }.isFailure)
        assertNull(dao.sceneAnalysis(id)!!.bundle)
        owner = "owner"
        assertTrue(runCatching { WalkDiarySync(dao, { owner }, request = { _, path, _, _ ->
            if (path.endsWith("capabilities")) capability else diaryFixture().also { it.getJSONObject("entry_revisions").put("note", 2) }
        }).sync("token", id, "remote") }.isFailure)
        assertNull(dao.sceneAnalysis(id)!!.bundle)
    }
}
