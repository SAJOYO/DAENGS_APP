package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.diary.ServerDiaryBoard
import com.daengs.app.walk.diary.relational.*
import com.daengs.app.walk.store.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelationalDiarySyncTest {
    private val id get() = relationalFixture().getString("session_id")
    private fun response(status: String, generation: Int = 1): JSONObject = relationalFixture().put("status", status)
        .put("generation", generation).also { if (status != "ready") it.put("bundle", JSONObject.NULL) }
    private val capabilities get() = JSONObject().put("diary_formats", JSONArray(listOf(ServerDiaryBoard.FORMAT, RelationalDiaryResponse.FORMAT)))

    private fun checkDb(block: suspend (WalkDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow(id, 0, endedAtMillis = 10000, ownerId = "owner", serverWalkId = "remote"))
            RelationalDiaryResponse.parse(relationalFixture().toString()).entryRevisions.forEach { (key, revision) ->
                dao.insertEntry(WalkEntryRow(key, id, "{}", revision.toInt(), "m", false))
            }
            block(dao)
        } finally { db.close() }
    }

    @Test fun `new capability selects GET POST then stores exact public response`() = checkDb { dao ->
        val methods = mutableListOf<String>()
        WalkDiarySync(dao, { "owner" }, request = { _, path, method, body ->
            if (path.endsWith("capabilities")) capabilities else {
                methods += method
                if (method == "GET") {
                    assertTrue(path.contains("bundle_format=${RelationalDiaryResponse.FORMAT}"))
                    assertTrue(dao.isRelationalDiary(id))
                    response("pending", 0)
                } else {
                    assertTrue(dao.relationalSubmissionPending(id))
                    assertEquals(RelationalDiaryResponse.FORMAT, body!!.getString("bundle_format"))
                    assertFalse(body.has("preparation_budget_ms"))
                    assertFalse(body.getBoolean("refresh"))
                    assertEquals(relationalFixture().getJSONObject("entry_revisions").toString(), body.getJSONObject("expected_entries").toString())
                    response("ready")
                }
            }
        }).sync("t", id, "remote")
        assertEquals(listOf("GET", "POST"), methods)
        assertEquals(response("ready").toString(), dao.readRelationalDiary(id, "owner")!!.published!!.rawJson)
        assertFalse(dao.relationalSubmissionPending(id))
    }

    @Test fun `running generation is polled with GET even when refresh requested`() = checkDb { dao ->
        var reads = 0
        var time = 0L
        WalkDiarySync(dao, { "owner" }, nowMillis = { time }, relationalPause = { time += 5000 }, request = { _, path, method, _ ->
            assertEquals("GET", method)
            if (path.endsWith("capabilities")) capabilities else response(if (++reads < 3) "running" else "ready")
        }).sync("t", id, "remote", refresh = true)
        assertEquals(3, reads)
        assertEquals(10000L, time)
    }

    @Test fun `lost POST response recovers by GET without another submission`() = checkDb { dao ->
        val methods = mutableListOf<String>()
        var time = 0L
        WalkDiarySync(dao, { "owner" }, nowMillis = { time }, relationalPause = { time += 5000 }, request = { _, path, method, _ ->
            if (path.endsWith("capabilities")) capabilities else {
                methods += method
                when (methods.size) {
                    1 -> response("pending", 0)
                    2 -> throw IllegalStateException("network", IOException("response lost"))
                    3 -> response("running")
                    else -> response("ready")
                }
            }
        }).sync("t", id, "remote")
        assertEquals(listOf("GET", "POST", "GET", "GET"), methods)
    }

    @Test fun `cancelled submission survives new coordinator and ready recovery ignores refresh`() = checkDb { dao ->
        val first = WalkDiarySync(dao, { "owner" }, request = { _, path, method, _ ->
            when {
                path.endsWith("capabilities") -> capabilities
                method == "GET" -> response("pending", 0)
                else -> throw CancellationException("app stopped")
            }
        })
        assertTrue(runCatching { first.sync("t", id, "remote") }.exceptionOrNull() is CancellationException)
        assertTrue(dao.relationalSubmissionPending(id))
        assertEquals(listOf(id), dao.pendingRelationalDiaries("owner"))
        var calls = 0
        WalkDiarySync(dao, { "owner" }, request = { _, path, method, _ ->
            calls++
            assertFalse(path.endsWith("capabilities")) // Durable selection; no downgrade negotiation.
            assertEquals("GET", method)
            response("ready")
        }).sync("t", id, "remote", refresh = true)
        assertEquals(1, calls)
        assertFalse(dao.relationalSubmissionPending(id))
        assertTrue(dao.pendingRelationalDiaries("owner").isEmpty())
    }

    @Test fun `failed generation is not automatically resubmitted and explicit retry can submit`() = checkDb { dao ->
        var posts = 0
        val sync = WalkDiarySync(dao, { "owner" }, request = { _, path, method, _ ->
            when {
                path.endsWith("capabilities") -> capabilities
                method == "GET" -> response("failed")
                else -> { posts++; response("ready", 2) }
            }
        })
        assertTrue(runCatching { sync.sync("t", id, "remote") }.isFailure)
        assertEquals(0, posts)
        sync.sync("t", id, "remote", refresh = true)
        assertEquals(1, posts)
    }

    @Test fun `429 ends the run without recovery GET or fallback`() = checkDb { dao ->
        val calls = mutableListOf<String>()
        val sync = WalkDiarySync(dao, { "owner" }, legacy = { _, _, _, _ -> error("legacy") }, request = { _, path, method, _ ->
            if (path.endsWith("capabilities")) capabilities else {
                calls += method
                if (method == "GET") response("pending", 0) else throw WalkHttpException(429, "limit")
            }
        })
        assertTrue(runCatching { sync.sync("t", id, "remote") }.exceptionOrNull() is WalkHttpException)
        assertEquals(listOf("GET", "POST"), calls)
        assertTrue(dao.isRelationalDiary(id))
    }

    @Test fun `uncertain pending submission requires explicit action instead of automatic duplicate POST`() = checkDb { dao ->
        dao.selectRelationalDiary(id, "remote", "owner")
        val stamp = dao.relationalDiaryInputStamp(id)
        dao.acceptRelationalDiary(response("pending", 0).toString(), id, "remote", "owner", stamp)
        dao.markRelationalSubmission(id, "remote", "owner", stamp)
        var calls = 0
        val sync = WalkDiarySync(dao, { "owner" }, request = { _, _, method, _ ->
            calls++; assertEquals("GET", method); response("pending", 0)
        })
        val failure = runCatching { sync.sync("t", id, "remote") }.exceptionOrNull()!!
        assertFalse(failure.isRetryableWalkDeliveryFailure())
        assertEquals(1, calls)
    }

    @Test fun `poll budget ends as pending without changing server state or reposting`() = checkDb { dao ->
        var time = 0L
        var reads = 0
        val sync = WalkDiarySync(dao, { "owner" }, nowMillis = { time }, relationalPause = { time += 60_000 }, request = { _, path, method, _ ->
            assertEquals("GET", method)
            if (path.endsWith("capabilities")) capabilities else { reads++; response("running") }
        })
        assertTrue(runCatching { sync.sync("t", id, "remote") }.exceptionOrNull() is RelationalDiaryStillRunning)
        assertEquals(5, reads)
        assertEquals(RelationalStatus.RUNNING, dao.readRelationalDiary(id, "owner")!!.latest.status)
    }

    @Test fun `account change during GET blocks POST and response storage`() = checkDb { dao ->
        var account = "owner"
        val sync = WalkDiarySync(dao, { account }, request = { _, path, method, _ ->
            assertEquals("GET", method)
            if (path.endsWith("capabilities")) capabilities else { account = "other"; response("ready") }
        })
        assertTrue(runCatching { sync.sync("t", id, "remote") }.exceptionOrNull() is CancellationException)
        assertNull(dao.readRelationalDiary(id, "owner"))
    }

    @Test fun `selected format suppresses base publication even when first GET fails`() = checkDb { dao ->
        dao.insertDiaryPublication(WalkDiaryPublicationRow(id, 0, 20_000, baseBundle = "old base"))
        val sync = WalkDiarySync(dao, { "owner" }, request = { _, path, _, _ ->
            if (path.endsWith("capabilities")) capabilities else throw IOException("offline")
        })
        assertTrue(runCatching { sync.sync("t", id, "remote") }.isFailure)
        assertEquals(0, dao.publishDiaryBase(id, 30_000))
        assertEquals(0, dao.publishDiaryCandidate(id, "old server", 10_000))
        assertNull(dao.diaryPublication(id)!!.publishedBundle)
        assertTrue(dao.pendingDiaryPublications().isEmpty())
        assertEquals(listOf(id), dao.pendingRelationalDiaries("owner"))
        assertTrue(dao.pendingRelationalDiaries("other").isEmpty())
    }

    @Test fun `new stale revision can start a generation after an earlier stale response`() = checkDb { dao ->
        dao.selectRelationalDiary(id, "remote", "owner")
        val stamp = dao.relationalDiaryInputStamp(id)
        dao.acceptRelationalDiary(response("stale").put("input_revision", "earlier").toString(), id, "remote", "owner", stamp)
        var posts = 0
        WalkDiarySync(dao, { "owner" }, request = { _, _, method, _ ->
            if (method == "GET") response("stale").put("input_revision", "current")
            else { posts++; response("ready", 2).put("input_revision", "current") }
        }).sync("t", id, "remote")
        assertEquals(1, posts)
        assertEquals("current", dao.readRelationalDiary(id, "owner")!!.published!!.inputRevision)
    }

    @Test fun `refresh queued behind active generation only reads its completed result`() = checkDb { dao ->
        coroutineScope {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var posts = 0
            val sync = WalkDiarySync(dao, { "owner" }, request = { _, path, method, _ ->
                when {
                    path.endsWith("capabilities") -> capabilities
                    method == "GET" -> response(if (posts == 0) "pending" else "ready", if (posts == 0) 0 else 1)
                    else -> { posts++; started.complete(Unit); release.await(); response("ready") }
                }
            })
            val first = async { sync.sync("t", id, "remote") }
            started.await()
            val duplicate = async(start = CoroutineStart.UNDISPATCHED) { sync.sync("t", id, "remote", refresh = true) }
            release.complete(Unit)
            first.await(); duplicate.await()
            assertEquals(1, posts)
        }
    }
}
