package com.daengs.app.walk

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkFixWriterTest {
    @Test
    fun `commands land in submission order so a fix cannot outrun its session`() {
        val log = RecordingLog()

        withWriter(log) { writer ->
            writer.openSession(session("s1"))
            writer.append("s1", fix(0))
            writer.append("s1", fix(1))
            writer.closeSession("s1", 100L)
        }

        assertEquals(listOf("open:s1", "append:0", "append:1", "close:s1"), log.calls)
    }

    @Test
    fun `행동도 세션을 연 뒤 닫기 전에 같은 큐에 저장된다`() {
        val log = RecordingLog()

        withWriter(log) { writer ->
            writer.openSession(session("s1"))
            writer.appendAction(action("a1"))
            writer.closeSession("s1", 100L)
        }

        assertEquals(listOf("open:s1", "action:a1", "close:s1"), log.calls)
    }

    @Test
    fun `행동 저장 실패는 성공 완료가 되지 않고 flush도 실패한다`() = runTest {
        val writer = WalkFixWriter(RecordingLog(failAction = true), backgroundScope)

        val stored = writer.appendAction(action("a1"))
        runCurrent()

        assertEquals("action disk full", runCatching { stored.await() }.exceptionOrNull()?.message)
        assertEquals("action disk full", runCatching { writer.flush() }.exceptionOrNull()?.message)
    }

    @Test
    fun `행동 완료 신호는 실제 저장이 끝날 때까지 기다린다`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = WalkFixWriter(
            RecordingLog(blockAction = true, entered = entered, release = release),
            backgroundScope,
        )

        val stored = writer.appendAction(action("a1"))
        runCurrent()

        assertTrue(entered.isCompleted)
        assertFalse(stored.isCompleted)
        release.complete(Unit)
        stored.await()
        assertTrue(stored.isCompleted)
    }

    @Test
    fun `a failed write is reported and does not stop later writes`() {
        val log = RecordingLog(failOnSeq = 0)
        var reported: String? = null

        withWriter(log) { writer ->
            writer.openSession(session("s1"))
            writer.append("s1", fix(0))
            writer.append("s1", fix(1))
            reported = writer.failure.value
        }

        assertNotNull(reported)
        assertEquals(listOf("open:s1", "append:1"), log.calls)
    }

    @Test
    fun `no failure is reported when every write lands`() {
        val log = RecordingLog()
        var reported: String? = "unset"

        withWriter(log) { writer ->
            writer.openSession(session("s1"))
            writer.append("s1", fix(0))
            reported = writer.failure.value
        }

        assertNull(reported)
    }

    @Test
    fun `flush waits for every earlier write`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val log = RecordingLog(blockOnSeq = 0, entered = entered, release = release)
        val writer = WalkFixWriter(log, backgroundScope)

        writer.append("s1", fix(0))
        val flushing = async { writer.flush() }
        runCurrent()

        assertTrue(entered.isCompleted)
        assertFalse(flushing.isCompleted)
        release.complete(Unit)
        flushing.await()
        assertEquals(listOf("append:0"), log.calls)
    }

    @Test
    fun `failure can be cleared before a new recording`() {
        val log = RecordingLog(failOnSeq = 0)

        withWriter(log) { writer ->
            writer.append("s1", fix(0))
            assertNotNull(writer.failure.value)
            writer.clearFailure()
            assertNull(writer.failure.value)
        }
    }

    private fun withWriter(log: WalkFixLog, block: (WalkFixWriter) -> Unit) {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        try {
            block(WalkFixWriter(log, scope))
        } finally {
            scope.cancel()
        }
    }

    private fun session(id: String) = RecordedSession(id = id, dogIds = emptyList(), startedAtMillis = 0L)

    private fun fix(seq: Int) = RecordedFix(
        clientSeq = seq,
        chainIndex = 0,
        atMillis = seq.toLong(),
        lat = 37.0,
        lng = 127.0,
        accuracyM = 5f,
        isMock = false,
    )

    private fun action(id: String) = RecordedWalkAction(
        id = id,
        sessionId = "s1",
        type = WalkMomentType.SNIFFING,
        recordedAtMillis = 10L,
        locationCapturedAtMillis = 9L,
        point = com.daengs.app.location.GeoPoint(37.0, 127.0),
        accuracyMeters = 5f,
    )

    private class RecordingLog(
        private val failOnSeq: Int? = null,
        private val failAction: Boolean = false,
        private val blockAction: Boolean = false,
        private val blockOnSeq: Int? = null,
        private val entered: CompletableDeferred<Unit>? = null,
        private val release: CompletableDeferred<Unit>? = null,
    ) : WalkFixLog {
        val calls = mutableListOf<String>()

        override suspend fun openSession(session: RecordedSession) {
            calls += "open:${session.id}"
        }

        override suspend fun append(sessionId: String, fix: RecordedFix) {
            if (fix.clientSeq == failOnSeq) error("disk full")
            if (fix.clientSeq == blockOnSeq) {
                entered?.complete(Unit)
                release?.await()
            }
            calls += "append:${fix.clientSeq}"
        }

        override suspend fun appendAction(action: RecordedWalkAction) {
            if (failAction) error("action disk full")
            if (blockAction) {
                entered?.complete(Unit)
                release?.await()
            }
            calls += "action:${action.id}"
        }

        override suspend fun closeSession(sessionId: String, endedAtMillis: Long) {
            calls += "close:$sessionId"
        }

        override suspend fun stampWeather(sessionId: String, weather: RecordedWeather) {
            calls += "weather:$sessionId"
        }

        override suspend fun deleteSession(sessionId: String) {
            calls += "delete:$sessionId"
        }

        override suspend fun forgetDog(dogId: String) {
            calls += "forget:$dogId"
        }

        override suspend fun forgetEverything() {
            calls += "forgetAll"
        }

        override suspend fun unfinishedSessions(): List<RecordedSession> = emptyList()

        override suspend fun finishedSessions(): List<RecordedSession> = emptyList()

        override suspend fun sessionsPendingAnalysis(): List<RecordedSession> = emptyList()

        override suspend fun markRawUploaded(
            sessionId: String,
            serverWalkId: String,
            changedAtMillis: Long,
        ) {
            calls += "raw:$sessionId"
        }

        override suspend fun markDerived(sessionId: String, changedAtMillis: Long) {
            calls += "derived:$sessionId"
        }

        override suspend fun session(sessionId: String): RecordedSession? = null

        override suspend fun fixes(sessionId: String): List<RecordedFix> = emptyList()

        override suspend fun actions(sessionId: String): List<RecordedWalkAction> = emptyList()
    }
}
