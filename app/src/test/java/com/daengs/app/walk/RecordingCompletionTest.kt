package com.daengs.app.walk

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecordingCompletionTest {
    private fun receipt(kind: String = "STOP") = RecordingEpoch("e", "s", "clock", 0, 100, 100, 0,
        endedAtMillis = 200, endedElapsedNanos = 200, endKind = kind, targetIngressSeq = -1, drained = true)

    @Test fun `recovery closes only explicit drained stops and finishes pins first`() = runTest {
        val log = RecoveryLog(listOf(receipt()), entries = true)
        recoverDrainedRecordings(log) { id, cutoff -> log.calls += "pins:$id:$cutoff" }
        assertEquals(listOf("pins:s:200", "close:s:200"), log.calls)
    }

    @Test fun `recovery retains unknown paused interrupted failed and unowned sessions`() = runTest {
        val cases = listOf(emptyList(), listOf(receipt("PAUSE")), listOf(receipt("INTERRUPTED")),
            listOf(receipt().copy(drained = false)), listOf(receipt().copy(failureReason = "disk")))
        for (epochs in cases) {
            val log = RecoveryLog(epochs, entries = true)
            recoverDrainedRecordings(log) { _, _ -> error("must not finish pins") }
            assertTrue(log.calls.isEmpty())
        }
        val other = RecoveryLog(listOf(receipt()), entries = true, sessionOwner = "other")
        recoverDrainedRecordings(other) { _, _ -> error("wrong owner") }
        assertTrue(other.calls.isEmpty())
    }

    @Test fun `legitimate empty stop is discarded but never an incomplete tail`() = runTest {
        val log = RecoveryLog(listOf(receipt()), entries = false)
        recoverDrainedRecordings(log) { _, _ -> }
        assertEquals(listOf("delete:s"), log.calls)
    }

    private class RecoveryLog(private val epochs: List<RecordingEpoch>, private val entries: Boolean,
        private val sessionOwner: String = "owner") : WalkFixLog {
        val calls = mutableListOf<String>()
        override val ownerId = "owner"
        override suspend fun recordingEpochs(sessionId: String) = epochs
        override suspend fun unfinishedSessions() = listOf(RecordedSession("s", ownerId = sessionOwner, startedAtMillis = 100))
        override suspend fun hasEntries(sessionId: String) = entries
        override suspend fun fixes(sessionId: String) = emptyList<RecordedFix>()
        override suspend fun closeSession(sessionId: String, endedAtMillis: Long) { calls += "close:$sessionId:$endedAtMillis" }
        override suspend fun deleteSession(sessionId: String) { calls += "delete:$sessionId" }
        override suspend fun openSession(session: RecordedSession) = Unit
        override suspend fun append(sessionId: String, fix: RecordedFix) = Unit
        override suspend fun appendAction(action: RecordedWalkAction) = Unit
        override suspend fun stampWeather(sessionId: String, weather: RecordedWeather) = Unit
        override suspend fun finishedSessions() = emptyList<RecordedSession>()
        override suspend fun sessionsPendingAnalysis() = emptyList<RecordedSession>()
        override suspend fun markRawUploaded(sessionId: String, serverWalkId: String, changedAtMillis: Long) = Unit
        override suspend fun markDerived(sessionId: String, changedAtMillis: Long) = Unit
        override suspend fun forgetDog(dogId: String) = Unit
        override suspend fun forgetEverything() = Unit
        override suspend fun session(sessionId: String): RecordedSession? = null
        override suspend fun actions(sessionId: String) = emptyList<RecordedWalkAction>()
    }
}
