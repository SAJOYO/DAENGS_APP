package com.daengs.app.walk.sync

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkFixLog
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
        log.sessions += session("already", ended = true, synced = 1L)

        sync.syncOnce("token")

        assertEquals(listOf("done"), api.uploaded.map { it.id })
    }

    /** 올린 뒤 표시가 남아야 다음에 또 올리지 않는다. */
    @Test
    fun `올리고 나면 표시가 남는다`() = runBlocking {
        log.sessions += session("done", ended = true)

        sync.syncOnce("token")

        assertEquals(NOW, log.sessions.single { it.id == "done" }.syncedAtMillis)
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
    }

    @Test
    fun `서버에만 있는 산책을 내려받는다`() = runBlocking {
        api.remote += remote("from-server")

        sync.syncOnce("token")

        val restored = log.sessions.single()
        assertEquals("from-server", restored.id)
        // 되찾은 것은 이미 서버에 있으므로 올린 것으로 표시된다 — 다시 올리지 않는다.
        assertEquals(NOW, restored.syncedAtMillis)
        assertEquals(2, log.fixes["from-server"]?.size)
    }

    /** 이미 있는 것은 **덮어쓰지 않는다.** 끝난 기록은 바뀌지 않으므로 받을 이유가 없다. */
    @Test
    fun `이미 있는 산책은 다시 안 받는다`() = runBlocking {
        log.sessions += session("mine", ended = true, synced = 1L)
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

    private fun session(id: String, ended: Boolean, synced: Long? = null) = RecordedSession(
        id = id,
        dogIds = listOf("dog-1"),
        startedAtMillis = 1_000L,
        endedAtMillis = if (ended) 2_000L else null,
        weather = RecordedWeather(weatherCode = 61, isDay = true, temperatureC = 18.5f),
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

        override suspend fun closeSession(sessionId: String, endedAtMillis: Long) = Unit

        override suspend fun stampWeather(sessionId: String, weather: RecordedWeather) = Unit

        override suspend fun deleteSession(sessionId: String) = Unit

        override suspend fun forgetDog(dogId: String) = Unit

        override suspend fun forgetEverything() = Unit

        override suspend fun unfinishedSessions(): List<RecordedSession> =
            sessions.filter { it.endedAtMillis == null }

        override suspend fun finishedSessions(): List<RecordedSession> =
            sessions.filter { it.endedAtMillis != null }

        override suspend fun unsyncedSessions(): List<RecordedSession> =
            sessions.filter { it.endedAtMillis != null && it.syncedAtMillis == null }

        override suspend fun markSynced(sessionId: String, syncedAtMillis: Long) {
            val index = sessions.indexOfFirst { it.id == sessionId }
            sessions[index] = sessions[index].copy(syncedAtMillis = syncedAtMillis)
        }

        override suspend fun session(sessionId: String): RecordedSession? =
            sessions.firstOrNull { it.id == sessionId }

        override suspend fun fixes(sessionId: String): List<RecordedFix> =
            fixes[sessionId].orEmpty()
    }

    private class FakeApi : WalkApiClient {
        val uploaded = mutableListOf<RecordedSession>()
        val uploadedPoints = mutableListOf<RecordedFix>()
        val appended = mutableListOf<List<RecordedFix>>()
        val remote = mutableListOf<RemoteWalkDetail>()
        var uploadFails = false
        var failFor: String? = null
        var detailCalls = 0

        override suspend fun upload(
            token: String,
            session: RecordedSession,
            fixes: List<RecordedFix>,
        ): Result<String> {
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
