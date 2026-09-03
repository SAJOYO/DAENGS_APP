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

    private class FakeLog : WalkFixLog {
        val sessions = mutableListOf<RecordedSession>()
        val fixes = mutableMapOf<String, List<RecordedFix>>()

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
        val remote = mutableListOf<RemoteWalkDetail>()
        var uploadFails = false
        var appendFails = false
        var failFor: String? = null
        var finalizeFails = false
        var uploadCalls = 0
        var finalizeCalls = 0
        val finalized = mutableListOf<Pair<String, WalkFinalizeManifest>>()
        var detailCalls = 0

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
            return Result.success("server-" + session.id)
        }

        override suspend fun appendPoints(
            token: String,
            walkId: String,
            fixes: List<RecordedFix>,
        ): Result<Unit> {
            appended += fixes
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
            return Result.success(Unit)
        }

        override suspend fun list(token: String): Result<List<RemoteWalk>> =
            Result.success(remote.map { it.walk })

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
