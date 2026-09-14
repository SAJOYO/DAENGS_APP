package com.daengs.app.walk.sync

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkFixLog
import com.daengs.app.walk.WalkSyncState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기기와 서버를 맞추는 규칙.
 *
 * 서버도 Room 도 안 쓴다 — 여기서 보는 것은 **무엇을 올리고 무엇을 내려받는가**,
 * 그리고 **실패했을 때 무엇을 안 하는가**다.
 */
class WalkSyncTest {

    @Test fun `v1 release keeps uploading to an old server without losing local GPS eligibility`() = runBlocking {
        for (state in WalkSyncState.entries) {
            val log = FakeLog()
            val api = FakeApi()
            log.sessions += session("gps", ended = true, state = state,
                serverWalkId = if (state == WalkSyncState.LOCAL_ONLY) null else "server-gps")
            val original = fix(0).copy(recordingEligible = false)
            log.append("gps", original)
            var consumers = 0
            val sync = WalkSync(log, api, { NOW },
                photoSync = { _, _, _ -> consumers++ },
                storyboardSync = { _, _, _ -> consumers++ },
                recording = WalkRecordingSync { _, path, _, _ ->
                    assertEquals("/entry-capabilities", path)
                    throw WalkHttpException(404, "old server")
                }, requireRecordingSupport = false, warn = { _, _ -> })
            sync.syncPendingSession("token", "gps")
            assertEquals(WalkSyncState.DERIVED, log.session("gps")!!.syncState)
            assertEquals(listOf(original), log.fixes("gps"))
            assertEquals(if (state == WalkSyncState.LOCAL_ONLY) 1 else 0, api.uploadCalls)
            if (state == WalkSyncState.LOCAL_ONLY) assertEquals(listOf(original), api.uploadedPoints)
            assertEquals(if (state == WalkSyncState.DERIVED) 0 else 1, api.finalizeCalls)
            assertEquals(2, consumers)
        }
    }

    @Test fun `GPS evidence readiness gates new uploaded and already derived sessions`() = runBlocking {
        for (state in WalkSyncState.entries) {
            val log = FakeLog()
            val api = FakeApi()
            log.sessions += session("gps", ended = true, state = state,
                serverWalkId = if (state == WalkSyncState.LOCAL_ONLY) null else "server-gps")
            log.append("gps", fix(0).copy(recordingEligible = false))
            var probes = 0
            var consumers = 0
            val sync = WalkSync(log, api, { NOW },
                photoSync = { _, _, _ -> consumers++ },
                storyboardSync = { _, _, _ -> consumers++ },
                recording = WalkRecordingSync { _, _, _, _ -> probes++; throw java.io.IOException("unsupported") },
                warn = { _, _ -> })
            assertTrue(runCatching { sync.syncPendingSession("token", "gps") }.isFailure)
            assertEquals(1, probes)
            assertEquals(0, api.uploadCalls)
            assertEquals(0, api.finalizeCalls)
            assertEquals(0, consumers)
            assertEquals(state, log.session("gps")!!.syncState)
            assertEquals(false, log.fixes("gps").single().recordingEligible)
        }
    }

    @Test fun `manual diary refresh synchronizes photos without generating twice`() = runBlocking {
        val log = FakeLog()
        val api = FakeApi()
        log.sessions += session("photos", ended = true)
        var photos = 0; var scenes = 0
        val sync = WalkSync(log, api, { NOW }, photoSync = { _, _, _ -> photos++ },
            storyboardSync = { _, _, _ -> scenes++ }, warn = { _, _ -> })
        sync.syncPendingSession("token", "photos", includeStoryboard = false)
        assertEquals(1, photos); assertEquals(0, scenes)
        assertEquals(1, api.finalizeCalls)
    }

    @Test fun `photo delivery retries before scene synchronization without reuploading GPS`() = runBlocking {
        val log = FakeLog()
        val api = FakeApi()
        log.sessions += session("photos", ended = true)
        var photos = 0
        var scenes = 0
        val sync = WalkSync(log, api, { NOW }, photoSync = { _, _, _ ->
            photos++
            if (photos == 1) throw java.io.IOException("offline")
        }, storyboardSync = { _, _, _ -> scenes++ }, warn = { _, _ -> })
        assertTrue(runCatching { sync.syncPendingSession("token", "photos") }.isFailure)
        assertEquals(0, scenes)
        sync.syncPendingSession("token", "photos")
        assertEquals(2, photos)
        assertEquals(1, scenes)
        assertEquals(1, api.uploadCalls)
        assertEquals(1, api.finalizeCalls)
    }

    @Test fun `worker analyzes scenes only after GPS finalization and retries without reupload`() = runBlocking {
        val log = FakeLog()
        val api = FakeApi()
        log.sessions += session("done", ended = true)
        var attempts = 0
        val sync = WalkSync(log, api, { NOW }, storyboardSync = { _, id, remote ->
            assertEquals(WalkSyncState.DERIVED, log.session(id)!!.syncState)
            assertEquals("server-done", remote)
            attempts++
            if (attempts == 1) throw java.io.IOException("analysis offline")
        }, warn = { _, _ -> })
        assertTrue(runCatching { sync.syncPendingSession("token", "done") }.isFailure)
        sync.syncPendingSession("token", "done")
        assertEquals(2, attempts)
        assertEquals(1, api.uploadCalls)
        assertEquals(1, api.finalizeCalls)
    }

    private val log = FakeLog()
    private val api = FakeApi()
    private val warnings = mutableListOf<String>()

    // 로그는 가로챈다 — JVM 테스트에서 android.util.Log 가 안 돈다.
    private val sync = WalkSync(log, api, { NOW }) { message, _ -> warnings += message }

    @Test
    fun `끝났고 안 올라간 것만 올린다`() = runBlocking {
        log.sessions += session("done", ended = true)
        log.sessions += session("open", ended = false)
        log.sessions += session(
            "already",
            ended = true,
            state = WalkSyncState.DERIVED,
            synced = 1L,
        )

        sync.syncOnce("token")

        assertEquals(listOf("done"), api.uploaded.map { it.id })
    }

    /** 올린 뒤 표시가 남아야 다음에 또 올리지 않는다. */
    @Test
    fun `올리고 나면 표시가 남는다`() = runBlocking {
        log.sessions += session("done", ended = true)

        sync.syncOnce("token")

        assertEquals(NOW, log.sessions.single { it.id == "done" }.syncedAtMillis)
        assertEquals(WalkSyncState.DERIVED, log.sessions.single { it.id == "done" }.syncState)
        assertEquals(listOf("server-done"), api.finalized.map { it.first })
    }

    /**
     * **실패하면 표시하지 않는다.**
     *
     * 표시해 버리면 다시 올릴 기회가 없어져서 그 산책은 영영 기기에만 남는다.
     */
    @Test
    fun `올리기에 실패하면 표시하지 않는다`() = runBlocking {
        log.sessions += session("done", ended = true)
        api.uploadFails = true

        sync.syncOnce("token")

        assertNull(log.sessions.single().syncedAtMillis)
        assertEquals(WalkSyncState.LOCAL_ONLY, log.sessions.single().syncState)
    }

    @Test
    fun `worker용 한 건 동기화는 실패를 호출자에게 돌려준다`() = runBlocking {
        log.sessions += session("done", ended = true)
        api.uploadFails = true

        val failure = runCatching {
            sync.syncPendingSession("token", "done")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(WalkSyncState.LOCAL_ONLY, log.sessions.single().syncState)
    }

    @Test
    fun `worker가 이미 끝난 세션을 다시 받아도 요청하지 않는다`() = runBlocking {
        log.sessions += session(
            "done",
            ended = true,
            state = WalkSyncState.DERIVED,
            synced = NOW,
        )

        sync.syncPendingSession("token", "done")

        assertEquals(0, api.uploadCalls)
        assertEquals(0, api.finalizeCalls)
    }

    /** 한 건이 막혀도 나머지는 시도한다 — 큰 산책 하나에 다른 기록이 볼모가 되면 안 된다. */
    @Test
    fun `한 건이 실패해도 나머지는 올라간다`() = runBlocking {
        log.sessions += session("bad", ended = true)
        log.sessions += session("good", ended = true)
        api.failFor = "bad"

        sync.syncOnce("token")

        assertEquals(listOf("good"), api.uploaded.map { it.id })
        assertNull(log.sessions.single { it.id == "bad" }.syncedAtMillis)
        assertEquals(NOW, log.sessions.single { it.id == "good" }.syncedAtMillis)
        assertEquals(WalkSyncState.DERIVED, log.sessions.single { it.id == "good" }.syncState)
    }

    /** 긴 산책은 나눠 보낸다. 순번이 서버 PK 라 나눠도 중복이 안 생긴다. */
    @Test
    fun `좌표가 많으면 나눠 보낸다`() = runBlocking {
        log.sessions += session("long", ended = true)
        log.fixes["long"] = (0 until 4_500).map { fix(it) }

        sync.syncOnce("token")

        // 첫 요청 2000 + 이어붙이기 2000 + 500
        assertEquals(2_000, api.uploadedPoints.size)
        assertEquals(listOf(2_000, 500), api.appended.map { it.size })
        assertEquals(NOW, log.sessions.single().syncedAtMillis)
        assertEquals(4_500, api.finalized.single().second.expectedPointCount)
        assertEquals(4_499, api.finalized.single().second.terminalClientSeq)
    }

    @Test
    fun `마지막 chunk가 실패하면 raw uploaded나 finalize로 넘어가지 않는다`() = runBlocking {
        log.sessions += session("long", ended = true)
        log.fixes["long"] = (0 until 2_500).map { fix(it) }
        api.appendFails = true

        sync.syncOnce("token")

        assertEquals(WalkSyncState.LOCAL_ONLY, log.sessions.single().syncState)
        assertEquals(0, api.finalizeCalls)
    }

    @Test fun `청크 응답을 잃으면 같은 세션과 범위로 재전송한 뒤 봉인한다`() = runBlocking {
        log.sessions += session("long", ended = true)
        log.fixes["long"] = (0 until 4_500).map { fix(it) }
        api.appendFails = true

        assertTrue(runCatching { sync.syncPendingSession("token", "long") }.isFailure)
        assertEquals(WalkSyncState.LOCAL_ONLY, log.sessions.single().syncState)
        assertNull(log.sessions.single().serverWalkId)
        assertEquals(0, api.finalizeCalls)

        api.appendFails = false
        sync.syncPendingSession("token", "long")

        assertEquals(listOf("long", "long"), api.uploaded.map { it.id })
        assertEquals(listOf(2_000, 2_000, 4_000), api.appended.map { it.first().clientSeq })
        assertEquals(listOf("server-long" to "long", "server-long" to "long", "server-long" to "long"), api.appendedIds)
        assertEquals(WalkFinalizeManifest(4_500, 4_499), api.finalized.single().second)
        assertEquals(WalkSyncState.DERIVED, log.sessions.single().syncState)
    }

    @Test fun `첫 업로드나 청크 응답 중 계정이 바뀌면 다음 전송과 완료 표시를 멈춘다`() = runBlocking {
        for (duringCreate in listOf(true, false)) {
            val log = FakeLog().apply {
                owner = "first"
                sessions += session("long", ended = true)
                sessions += session("next", ended = true)
                fixes["long"] = (0 until 4_500).map { fix(it) }
            }
            val api = FakeApi()
            if (duringCreate) api.afterUpload = { log.owner = "second" }
            else api.afterAppend = { log.owner = "second" }

            WalkSync(log, api, warn = { _, _ -> }).syncOnce("old-token")

            assertEquals(1, api.uploadCalls)
            assertEquals(if (duringCreate) 0 else 1, api.appended.size)
            assertEquals(0, api.finalizeCalls)
            assertEquals(0, api.listCalls)
            assertTrue(log.sessions.all { it.syncState == WalkSyncState.LOCAL_ONLY && it.serverWalkId == null })
        }
    }

    @Test fun `봉인 응답 중 계정이 바뀌면 로컬 완료나 후속 업로드를 기록하지 않는다`() = runBlocking {
        log.owner = "first"
        log.sessions += session("done", ended = true)
        api.afterFinalize = { log.owner = "second" }
        var consumers = 0
        val sync = WalkSync(log, api, photoSync = { _, _, _ -> consumers++ }, warn = { _, _ -> })

        assertTrue(runCatching { sync.syncPendingSession("old-token", "done") }.isFailure)

        assertEquals(WalkSyncState.RAW_UPLOADED, log.sessions.single().syncState)
        assertEquals(0, consumers)
    }

    @Test fun `청크 취소는 조용한 동기화에서도 다음 산책으로 넘어가지 않는다`() = runBlocking {
        log.sessions += session("long", ended = true)
        log.sessions += session("next", ended = true)
        log.fixes["long"] = (0 until 2_500).map { fix(it) }
        api.afterAppend = { throw kotlinx.coroutines.CancellationException("cancelled upload") }

        val failure = runCatching { sync.syncOnce("token") }.exceptionOrNull()

        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertEquals(1, api.uploadCalls)
        assertEquals(0, api.finalizeCalls)
        assertEquals(0, api.listCalls)
        assertTrue(log.sessions.all { it.syncState == WalkSyncState.LOCAL_ONLY })
    }

    /** finalize만 실패하면 원본은 다시 보내지 않고 다음 실행에서 finalize부터 잇는다. */
    @Test
    fun `계산 응답을 못 받으면 raw uploaded에서 finalize만 다시 시도한다`() = runBlocking {
        log.sessions += session("done", ended = true)
        api.finalizeFails = true

        sync.syncOnce("token")

        val waiting = log.sessions.single()
        assertEquals(WalkSyncState.RAW_UPLOADED, waiting.syncState)
        assertEquals("server-done", waiting.serverWalkId)
        assertEquals(1, api.uploadCalls)

        api.finalizeFails = false
        sync.syncOnce("token")

        assertEquals(1, api.uploadCalls)
        assertEquals(2, api.finalizeCalls)
        assertEquals(WalkSyncState.DERIVED, log.sessions.single().syncState)
    }

    /** v4에서 올라온 기록은 raw_uploaded지만 서버 id가 없어 create로 id만 다시 얻는다. */
    @Test
    fun `예전 업로드 기록은 서버 id를 다시 얻어 finalize한다`() = runBlocking {
        log.sessions += session(
            "legacy",
            ended = true,
            state = WalkSyncState.RAW_UPLOADED,
            synced = 1L,
        )

        sync.syncOnce("token")

        assertEquals(1, api.uploadCalls)
        assertEquals("server-legacy", api.finalized.single().first)
        assertEquals(WalkSyncState.DERIVED, log.sessions.single().syncState)
    }

    @Test
    fun `서버에만 있는 산책을 내려받는다`() = runBlocking {
        api.remote += remote("from-server")

        sync.syncOnce("token")

        val restored = log.sessions.single()
        assertEquals("from-server", restored.id)
        // 목록에는 분석 상태가 없으므로 원본 업로드까지만 확실한 것으로 기록한다.
        assertEquals(NOW, restored.syncedAtMillis)
        assertEquals(WalkSyncState.RAW_UPLOADED, restored.syncState)
        assertEquals("server-from-server", restored.serverWalkId)
        assertEquals(2, log.fixes["from-server"]?.size)
    }

    /** 이미 있는 것은 **덮어쓰지 않는다.** 끝난 기록은 바뀌지 않으므로 받을 이유가 없다. */
    @Test
    fun `이미 있는 산책은 다시 안 받는다`() = runBlocking {
        log.sessions += session(
            "mine",
            ended = true,
            state = WalkSyncState.DERIVED,
            synced = 1L,
        )
        api.remote += remote("mine")

        sync.syncOnce("token")

        assertTrue("좌표를 덮어쓰면 안 된다", log.fixes["mine"].isNullOrEmpty())
        assertEquals(0, api.detailCalls)
    }

    /**
     * 로그인 안 했으면 아무것도 안 한다.
     *
     * 산책은 로그인 없이도 되고, 그때는 기기에만 남는 것이 맞다.
     */
    @Test
    fun `토큰이 없으면 아무 일도 안 한다`() = runBlocking {
        log.sessions += session("done", ended = true)

        sync.syncOnce(null)

        assertTrue(api.uploaded.isEmpty())
        assertNull(log.sessions.single().syncedAtMillis)
    }

    // -- 대역 -------------------------------------------------------------

    private fun session(
        id: String,
        ended: Boolean,
        state: WalkSyncState = WalkSyncState.LOCAL_ONLY,
        serverWalkId: String? = null,
        synced: Long? = null,
    ) = RecordedSession(
        id = id,
        dogIds = listOf("dog-1"),
        startedAtMillis = 1_000L,
        endedAtMillis = if (ended) 2_000L else null,
        weather = RecordedWeather(weatherCode = 61, isDay = true, temperatureC = 18.5f),
        syncState = state,
        serverWalkId = serverWalkId,
        syncedAtMillis = synced,
    )

    private fun fix(seq: Int) = RecordedFix(
        clientSeq = seq,
        chainIndex = 0,
        atMillis = 1_000L + seq,
        lat = 37.5,
        lng = 127.0,
        accuracyM = 5f,
        isMock = false,
    )

    private fun remote(sessionId: String) = RemoteWalkDetail(
        walk = RemoteWalk(
            id = "server-$sessionId",
            clientSessionId = sessionId,
            dogIds = emptyList(),
            startedAtMillis = 1_000L,
            endedAtMillis = 2_000L,
            weather = null,
        ),
        fixes = listOf(fix(0), fix(1)),
    )


    /**
     * 액세스 토큰은 **JWE** 다 — 앱이 열 수 없다.
     *
     * 예전에는 세 조각 JWT 로 보고 `parts[1]` 을 payload 로 파싱해 `sub` 를 계정과
     * 맞춰 봤다. JWE 는 다섯 조각이고 그 자리는 `alg="dir"` 에서 빈 문자열이라 늘
     * 실패했고, 관문이 항상 닫혀 **올리기도 되찾기도 통째로 멈춰 있었다.** 로그도
     * 안 남아 "기록이 없다" 와 구분되지 않았다.
     *
     * 이 판이 없으면 같은 일이 또 조용히 지나간다 — 다른 판들은 계정이 `null`(경계
     * 없음)이라 관문을 그냥 지나가기 때문이다.
     */
    @Test fun `열 수 없는 JWE 토큰이어도 계정이 맞으면 동기화한다`() = runBlocking {
        // 헤더.빈칸.iv.본문.태그 — 실기기에서 받은 것과 같은 모양이다.
        val jwe = "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2R0NNIn0..DcQCyIhK2QNc.Zm9vYmFy.Xy1abc"
        log.owner = "de5f95e5-f2b9-4b13-8565-65626454cfa4"
        log.sessions += session("done", ended = true)

        sync.syncOnce(jwe)

        assertEquals(1, api.uploadCalls)
        assertEquals(WalkSyncState.DERIVED, log.sessions.single().syncState)
    }

    /** 로그인 전(빈 계정)에는 아무 것도 올리지 않는다. 아직 누구의 기록도 아니다. */
    @Test fun `계정이 비어 있으면 올리지 않는다`() = runBlocking {
        log.owner = ""
        log.sessions += session("done", ended = true)

        sync.syncOnce("token")

        assertEquals(0, api.uploadCalls)
    }

    private class FakeLog : WalkFixLog {
        val sessions = mutableListOf<RecordedSession>()
        val fixes = mutableMapOf<String, List<RecordedFix>>()

        /**
         * 로그인한 계정. **기본은 `null` — 계정 경계 없음**이라 대부분의 테스트는
         * 이걸 안 본다. 그래서 이 값이 실제로 채워진 판을 하나 두지 않으면,
         * 계정 관문이 통째로 막혀 있어도 테스트가 전부 초록으로 남는다.
         */
        var owner: String? = null
        override val ownerId: String? get() = owner

        override suspend fun openSession(session: RecordedSession) {
            sessions += session
        }

        override suspend fun append(sessionId: String, fix: RecordedFix) {
            fixes[sessionId] = fixes[sessionId].orEmpty() + fix
        }

        override suspend fun appendAction(action: com.daengs.app.walk.RecordedWalkAction) = Unit

        override suspend fun closeSession(sessionId: String, endedAtMillis: Long) = Unit

        override suspend fun stampWeather(sessionId: String, weather: RecordedWeather) = Unit

        override suspend fun deleteSession(sessionId: String) = Unit

        override suspend fun forgetDog(dogId: String) = Unit

        override suspend fun forgetEverything() = Unit

        override suspend fun unfinishedSessions(): List<RecordedSession> =
            sessions.filter { it.endedAtMillis == null }

        override suspend fun finishedSessions(): List<RecordedSession> =
            sessions.filter { it.endedAtMillis != null }

        override suspend fun sessionsPendingAnalysis(): List<RecordedSession> =
            sessions.filter {
                it.endedAtMillis != null && it.syncState != WalkSyncState.DERIVED
            }

        override suspend fun markRawUploaded(
            sessionId: String,
            serverWalkId: String,
            changedAtMillis: Long,
        ) {
            val index = sessions.indexOfFirst { it.id == sessionId }
            sessions[index] = sessions[index].copy(
                syncState = WalkSyncState.RAW_UPLOADED,
                serverWalkId = serverWalkId,
                syncedAtMillis = changedAtMillis,
            )
        }

        override suspend fun markDerived(sessionId: String, changedAtMillis: Long) {
            val index = sessions.indexOfFirst { it.id == sessionId }
            sessions[index] = sessions[index].copy(
                syncState = WalkSyncState.DERIVED,
                syncedAtMillis = changedAtMillis,
            )
        }

        override suspend fun session(sessionId: String): RecordedSession? =
            sessions.firstOrNull { it.id == sessionId }

        override suspend fun fixes(sessionId: String): List<RecordedFix> =
            fixes[sessionId].orEmpty()

        override suspend fun actions(
            sessionId: String,
        ): List<com.daengs.app.walk.RecordedWalkAction> = emptyList()
    }

    private class FakeApi : WalkApiClient {
        override val configured: Boolean = true

        val uploaded = mutableListOf<RecordedSession>()
        val uploadedPoints = mutableListOf<RecordedFix>()
        val appended = mutableListOf<List<RecordedFix>>()
        val appendedIds = mutableListOf<Pair<String, String>>()
        var afterUpload: () -> Unit = {}
        var afterAppend: () -> Unit = {}
        var afterFinalize: () -> Unit = {}
        val remote = mutableListOf<RemoteWalkDetail>()
        var uploadFails = false
        var appendFails = false
        var failFor: String? = null
        var finalizeFails = false
        var uploadCalls = 0
        var finalizeCalls = 0
        val finalized = mutableListOf<Pair<String, WalkFinalizeManifest>>()
        var detailCalls = 0
        var listCalls = 0

        override suspend fun upload(
            token: String,
            session: RecordedSession,
            fixes: List<RecordedFix>,
        ): Result<String> {
            uploadCalls++
            if (uploadFails || session.id == failFor) {
                return Result.failure(IllegalStateException("서버에 닿지 못했어요."))
            }
            uploaded += session
            uploadedPoints += fixes
            afterUpload()
            return Result.success("server-" + session.id)
        }

        override suspend fun appendPoints(
            token: String,
            walkId: String,
            clientSessionId: String,
            fixes: List<RecordedFix>,
        ): Result<Unit> {
            appended += fixes
            appendedIds += walkId to clientSessionId
            afterAppend()
            if (appendFails) {
                return Result.failure(IllegalStateException("이어붙이지 못했습니다."))
            }
            return Result.success(Unit)
        }

        override suspend fun finalize(
            token: String,
            walkId: String,
            manifest: WalkFinalizeManifest,
        ): Result<Unit> {
            finalizeCalls++
            if (finalizeFails) {
                return Result.failure(IllegalStateException("응답을 잃었습니다."))
            }
            finalized += walkId to manifest
            afterFinalize()
            return Result.success(Unit)
        }

        override suspend fun list(token: String): Result<List<RemoteWalk>> {
            listCalls++
            return Result.success(remote.map { it.walk })
        }

        override suspend fun detail(token: String, walkId: String): Result<RemoteWalkDetail> {
            detailCalls++
            return remote.firstOrNull { it.walk.id == walkId }
                ?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("없음"))
        }
    }

    private companion object {
        const val NOW = 9_999L
    }
}
