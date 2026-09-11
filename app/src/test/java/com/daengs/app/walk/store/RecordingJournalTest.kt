package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.motion.*
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

    @Test fun `policy and completed raw survive reopening without replacement or cross owner comparison`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "motion-policy-roundtrip.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, WalkDatabase::class.java, name).build()
        try {
            var log = RoomWalkFixLog(db.walkDao(), owner = { "owner" })
            val policy = MotionPolicies.freeze("s", MotionConfig(minDistanceM = 7.0))
            val saved = RecordedSession("s", ownerId = "owner", startedAtMillis = 0,
                motionPolicyJson = MotionPolicies.encode(policy))
            log.openSession(saved)
            val originalEpoch = RecordingEpoch("e", "s", "c", 0, 0, 0, 0)
            log.saveRecordingEpoch(originalEpoch)
            val raw = listOf(com.daengs.app.walk.motion.fix(0, 0.0, 1.0), com.daengs.app.walk.motion.fix(1, 8.0, 4.0))
            raw.forEach { log.append("s", it) }
            log.saveRecordingEpoch(originalEpoch.copy(endedAtMillis = 5000, endedElapsedNanos = nanos(5.0),
                endKind = "STOP", targetIngressSeq = 1, persistedCount = 2, drained = true))
            // Simulate process recovery after the durable STOP but before closing the session row.
            db.close()
            db = Room.databaseBuilder(context, WalkDatabase::class.java, name).build()
            log = RoomWalkFixLog(db.walkDao(), owner = { "owner" })
            assertEquals(saved, log.unfinishedSessions().single())
            log.closeSession("s", 5000)
            log.openSession(saved.copy(motionPolicyJson = MotionPolicies.encode(MotionPolicies.freeze("s"))))
            log.restoreSession(saved.copy(motionPolicyJson = null))
            val restored = log.session("s")!!
            assertEquals(saved.motionPolicyJson, restored.motionPolicyJson)
            assertEquals(raw, log.fixes("s"))
            val comparison = log.compareMotion("s") as RecordedMotionComparison.Ready
            assertEquals(policy.stored.configHash, comparison.candidate.configHash)
            assertEquals(8.0, comparison.candidate.eligibleDistanceM, .00001)
            assertEquals(3000L, comparison.legacyActiveDurationMillis)
            assertEquals(5000L, comparison.candidateRecordingDurationMillis)
            assertEquals(comparison, log.compareMotion("s"))
            assertEquals(restored, log.session("s")) // Comparison must not activate or write any candidate.
            assertNull(RoomWalkFixLog(db.walkDao(), owner = { "other" }).compareMotion("s"))

            log.restoreSession(RecordedSession("restored", ownerId = "owner", startedAtMillis = 0,
                endedAtMillis = 5000, serverWalkId = "server"))
            assertNull(log.session("restored")!!.motionPolicyJson)
            assertEquals(RecordedMotionComparison.Unavailable("LEGACY_POLICY"), log.compareMotion("restored"))
            log.openSession(RecordedSession("bad", startedAtMillis = 0, motionPolicyJson = "future-or-corrupt"))
            assertEquals("future-or-corrupt", log.session("bad")!!.motionPolicyJson)
            assertEquals(RecordedMotionComparison.Unavailable("POLICY_ENVELOPE"), log.compareMotion("bad"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
