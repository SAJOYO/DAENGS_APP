package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecordingJournalTest {
    private val epoch = RecordingEpoch("e", "s", "clock", 0, 100, 100_000_000, 0)
    private val fix = RecordedFix(0, 0, 150, 37.5, 127.0, 4f, true,
        ingressSeq = 0, sourceEpoch = "e", clockEpochId = "clock",
        elapsedRealtimeNanos = 150_000_000, receivedElapsedNanos = 200_000_000,
        receivedAtMillis = 200, speedMps = 1.4f, speedAccuracyMps = .3f,
        bearingDegrees = 90f, bearingAccuracyDegrees = 5f, provider = "gps", recordingEligible = false)

    @Test fun `raw round trip retains all fields and retry counts only once`() = withLog { log, dao ->
        log.saveRecordingEpoch(epoch)
        log.append("s", fix)
        log.append("s", fix)
        assertEquals(listOf(fix), log.fixes("s")) // Upload identity includes cached raw rows.
        assertEquals(listOf(fix), log.observationsAfter("s", -1, 1))
        assertTrue(log.observationsAfter("s", 0, 1).isEmpty())
        assertEquals(1L, log.recordingEpochs("s").single().persistedCount)
        assertTrue(runCatching { log.append("s", fix.copy(lat = 0.0)) }.isFailure)
        assertEquals(fix, log.fixes("s").single())
        assertEquals(1L, dao.recordingEpochs("s").single().persistedCount)
    }

    @Test fun `epoch mismatch rolls back and only verified stop creates diary work`() = withLog { log, dao ->
        log.saveRecordingEpoch(epoch)
        assertTrue(runCatching { log.append("s", fix.copy(clockEpochId = "other")) }.isFailure)
        assertEquals(0L, log.recordingEpochs("s").single().persistedCount)
        assertTrue(log.fixes("s").isEmpty())
        log.append("s", fix)
        assertTrue(runCatching { log.closeSession("s", 200) }.isFailure)
        assertNull(log.session("s")!!.endedAtMillis)
        assertNull(dao.diaryPublication("s"))
        log.saveRecordingEpoch(epoch.copy(endedAtMillis = 200, endedElapsedNanos = 200_000_000,
            endKind = "STOP", targetIngressSeq = 0, persistedCount = 1, drained = true))
        log.closeSession("s", 200)
        assertEquals(200L, log.session("s")!!.endedAtMillis)
        assertNotNull(dao.diaryPublication("s"))
        assertTrue(runCatching { log.append("s", fix.copy(clientSeq = 1, ingressSeq = 1)) }.isFailure)
    }

    private fun withLog(test: suspend (RoomWalkFixLog, WalkDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(),
            WalkDatabase::class.java).build()
        try {
            val log = RoomWalkFixLog(db.walkDao())
            log.openSession(RecordedSession("s", startedAtMillis = 100))
            test(log, db.walkDao())
        } finally { db.close() }
    }
}
