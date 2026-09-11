package com.daengs.app.walk.diary

import com.daengs.app.auth.Session
import com.daengs.app.walk.WalkSyncState
import com.daengs.app.walk.store.WalkSessionRow
import com.daengs.app.walk.support.diarySlotFixture
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class DiarySlotPreviewTest {
    private val remoteId = "f5849cae-994a-4a99-aad8-bb8826eb5662"
    private val auth = Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private fun result() = DiarySlotPreview.parse(diarySlotFixture())
    private fun row() = WalkSessionRow(result().clientSessionId, 0, "owner", 300_000,
        syncState = WalkSyncState.DERIVED.storedValue, serverWalkId = remoteId)

    @Test fun `server sample preserves all parts and whitespace in base scene`() {
        val fixture = diarySlotFixture()
        val preview = DiarySlotPreview.parse(fixture)
        assertEquals("accepted", preview.modelStatus)
        assertEquals(5, preview.scenes.size)
        val scene = preview.scenes[1]
        assertEquals(3, scene.evidence.count { it.part == "space" })
        assertEquals(1, scene.evidence.count { it.part == "environment" })
        assertEquals(1, scene.evidence.count { it.part == "motion" })
        assertEquals(fixture.getJSONObject("preview").getJSONObject("base_board")
            .getJSONArray("scenes").getJSONObject(1).getString("body"), scene.baseBody)
        assertTrue(scene.body.endsWith("  벤치 옆에서 물을 마셨다.\n"))
        assertTrue(scene.citations.isNotEmpty())
    }

    @Test fun `location reference is separate from space slot count and can be cited`() {
        val fixture = diarySlotFixture()
        val preview = fixture.getJSONObject("preview")
        val stamp = preview.getJSONArray("stamps").getJSONObject(1)
        val address = JSONObject().put("id", "address").put("part", "space")
            .put("role", "scene_address_reference").put("facts", JSONObject().put("name", "테스트동"))
        stamp.put("evidence", JSONArray()).put("location_reference", address)
        preview.getJSONObject("citations").put(stamp.getString("scene_id"), JSONArray().put("address"))
        val scene = DiarySlotPreview.parse(fixture).scenes[1]
        assertTrue(scene.evidence.isEmpty())
        assertEquals("address", scene.locationReference!!.id)
        assertEquals(setOf("address"), scene.citations)
    }

    @Test fun `foreign scene citations and mismatched base board cannot be shown`() {
        val foreign = diarySlotFixture()
        val preview = foreign.getJSONObject("preview")
        preview.getJSONObject("citations").put(preview.getJSONArray("scenes").getJSONObject(0).getString("id"),
            JSONArray().put("foreign-evidence"))
        assertTrue(runCatching { DiarySlotPreview.parse(foreign) }.isFailure)
        val mismatch = diarySlotFixture()
        mismatch.getJSONObject("preview").getJSONObject("base_board").getJSONArray("scenes").getJSONObject(0).put("id", "foreign")
        assertTrue(runCatching { DiarySlotPreview.parse(mismatch) }.isFailure)
    }

    @Test fun `authenticated request uses server walk id and only preview options`() = runBlocking {
        val stub = Stub(200, diarySlotFixture().toString())
        try {
            val response = DiarySlotPreviewApi { stub.base }.generate("token", remoteId).getOrThrow()
            assertEquals("/app/walks/$remoteId/diary-slots/preview", stub.path)
            assertEquals("POST", stub.method)
            assertEquals("Bearer token", stub.authorization)
            assertEquals(setOf("target_scene_count", "generate"), stub.body.keys().asSequence().toSet())
            assertEquals(3, stub.body.getInt("target_scene_count"))
            assertTrue(stub.body.getBoolean("generate"))
            assertEquals(result(), response)
        } finally { stub.stop() }
    }

    @Test fun `disabled missing unfinished and expired requests have explicit errors`() = runBlocking {
        for ((status, detail, expected) in listOf(
            Triple(404, "산책 일기 미리보기가 꺼져 있습니다.", "아직 사용할 수 없어요"),
            Triple(404, "산책 기록을 찾을 수 없습니다.", "산책을 찾을 수 없어요"),
            Triple(409, "산책 입력을 준비할 수 없습니다.", "아직 준비되지 않았어요"),
            Triple(401, "", "로그인이 만료됐어요"),
        )) {
            val stub = Stub(status, JSONObject().put("detail", detail).toString())
            try {
                val failure = DiarySlotPreviewApi { stub.base }.generate("token", remoteId).exceptionOrNull() as DiarySlotPreviewException
                assertEquals(status, failure.statusCode)
                assertTrue(failure.message!!.contains(expected))
            } finally { stub.stop() }
        }
    }

    @Test fun `sync precedes request and an unsynced walk does not call model`() = runBlocking {
        val events = mutableListOf<String>()
        val loaded = loadDiarySlotPreview(row().id, { "owner" }, { auth }, { row() },
            { token, id -> assertEquals("token", token); assertEquals(row().id, id); events += "sync" },
            { _, id -> assertEquals(remoteId, id); events += "fetch"; Result.success(result()) })
        assertEquals(listOf("sync", "fetch"), events)
        assertEquals(result(), loaded)
        var fetched = false
        assertTrue(runCatching {
            loadDiarySlotPreview(row().id, { "owner" }, { auth },
                { row().copy(syncState = WalkSyncState.RAW_UPLOADED.storedValue) }, { _, _ -> },
                { _, _ -> fetched = true; Result.success(result()) })
        }.isFailure)
        assertFalse(fetched)
    }

    @Test fun `another owner and an ongoing walk are rejected before sync`() = runBlocking {
        for (invalid in listOf(row().copy(ownerId = "other"), row().copy(endedAtMillis = null))) {
            var synced = false
            assertTrue(runCatching { loadDiarySlotPreview(row().id, { "owner" }, { auth }, { invalid },
                { _, _ -> synced = true }, { _, _ -> error("must not fetch") }) }.isFailure)
            assertFalse(synced)
        }
    }

    @Test fun `account switch or deleted session after response discards its contents`() = runBlocking {
        for (changeOwner in listOf(true, false)) {
            var owner = "owner"
            var saved: WalkSessionRow? = row()
            assertTrue(runCatching { loadDiarySlotPreview(row().id, { owner }, { auth }, { saved }, { _, _ -> }, { _, _ ->
                if (changeOwner) owner = "other" else saved = null
                Result.success(result())
            }) }.isFailure)
        }
    }

    @Test fun `different client session and sync failure never appear as successful preview`() = runBlocking {
        assertTrue(runCatching { loadDiarySlotPreview(row().id, { "owner" }, { auth }, { row() }, { _, _ -> },
            { _, _ -> Result.success(result().copy(clientSessionId = "other")) }) }.isFailure)
        assertTrue(runCatching { loadDiarySlotPreview(row().id, { "owner" }, { auth }, { row() },
            { _, _ -> error("sync failed") }, { _, _ -> fail("must not fetch"); Result.success(result()) }) }.isFailure)
    }

    @Test fun `cancelled request cannot publish a late successful result`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var delivered = false
        val job = launch {
            loadDiarySlotPreview(row().id, { "owner" }, { auth }, { row() }, { _, _ -> }, { _, _ ->
                entered.complete(Unit)
                try { awaitCancellation() } catch (_: CancellationException) { Result.success(result()) }
            })
            delivered = true
        }
        entered.await(); job.cancelAndJoin()
        assertFalse(delivered)
    }

    private class Stub(status: Int, response: String) {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var path: String? = null
        var method: String? = null
        var authorization: String? = null
        lateinit var body: JSONObject
        init {
            server.createContext("/app/walks/") { exchange ->
                path = exchange.requestURI.path; method = exchange.requestMethod
                authorization = exchange.requestHeaders.getFirst("Authorization")
                body = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }
        fun stop() = server.stop(0)
    }
}
