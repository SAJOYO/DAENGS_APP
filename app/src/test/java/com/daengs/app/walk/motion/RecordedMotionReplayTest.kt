package com.daengs.app.walk.motion

import com.daengs.app.walk.RecordingEpoch
import com.daengs.app.walk.RecordedSession
import org.junit.Assert.*
import org.junit.Test

class RecordedMotionReplayTest {
    @Test fun `comparison uses frozen settings and shows boundary differences without rewriting legacy summary`() {
        for (distance in listOf(49.5, 50.5)) for (end in listOf(59.999, 60.0)) {
            val session = RecordedSession("s", startedAtMillis = 0, endedAtMillis = (end * 1000).toLong(),
                motionPolicyJson = MotionPolicies.encode(MotionPolicies.freeze("s")))
            val raw = (0..12).map { fix(it, distance * it / 12, .5 + 59.0 * it / 12) }
            val legacy = com.daengs.app.walk.summarize(session, raw)
            val input = RecordedMotionInput(session, listOf(epoch(end = end, count = 13)), raw)
            val result = compareRecordedMotion(input) as RecordedMotionComparison.Ready
            assertEquals(legacy.distanceMeters, result.legacyDistanceM, .00001)
            assertEquals(distance, result.candidate.eligibleDistanceM, .00001)
            assertFalse(result.legacyCountsAsWalk) // Fix-to-fix time is under 60s.
            assertEquals(distance > 50 && end >= 60, result.candidateCountsAsWalk)
            assertEquals(legacy, com.daengs.app.walk.summarize(session, raw))
        }
        val session = RecordedSession("s", startedAtMillis = 0, endedAtMillis = 5000,
            motionPolicyJson = MotionPolicies.encode(MotionPolicies.freeze("s", MotionConfig(minDistanceM = 20.0))))
        val result = compareRecordedMotion(RecordedMotionInput(session, listOf(epoch()),
            listOf(fix(0, 0.0, 1.0), fix(1, 8.0, 4.0)))) as RecordedMotionComparison.Ready
        assertEquals(8.0, result.legacyDistanceM, .00001)
        assertEquals(-8.0, result.distanceDeltaM, .00001)
    }

    @Test fun `comparison distinguishes legacy unsupported and incomplete journals without a guessed result`() {
        val policy = MotionPolicies.encode(MotionPolicies.freeze("s"))
        val session = RecordedSession("s", startedAtMillis = 0, endedAtMillis = 5000, motionPolicyJson = policy)
        val input = RecordedMotionInput(session, listOf(epoch()), listOf(fix(0, 0.0, 1.0), fix(1, 4.0, 4.0)))
        for ((json, reason) in listOf(null to "LEGACY_POLICY", "broken" to "POLICY_ENVELOPE",
            policy.replace("motion-v1", "future") to "POLICY_VERSION")) {
            assertEquals(RecordedMotionComparison.Unavailable(reason),
                compareRecordedMotion(input.copy(session = session.copy(motionPolicyJson = json))))
        }
        for (bad in listOf(input.copy(session = session.copy(endedAtMillis = null)),
            input.copy(session = session.copy(endedAtMillis = 6000)), input.copy(epochs = emptyList()),
            input.copy(epochs = listOf(epoch(kind = "PAUSE"))),
            input.copy(epochs = listOf(epoch().copy(drained = false))))) {
            assertEquals(RecordedMotionComparison.Unavailable("INCOMPLETE_RECORDING"), compareRecordedMotion(bad))
        }
        for (bad in listOf(input.copy(fixes = input.fixes.reversed()), input.copy(fixes = input.fixes.take(1)),
            input.copy(fixes = input.fixes + fix(2, 8.0, 4.5)))) {
            assertEquals(RecordedMotionComparison.Unavailable("INVALID_RECORDING"), compareRecordedMotion(bad))
        }
    }

    private fun epoch(id: String = "e", chain: Int = 0, start: Double = 0.0, end: Double = 5.0,
        first: Long = 0, count: Long = 2, kind: String = "STOP") =
        RecordingEpoch(id, "s", "c", chain, (start * 1000).toLong(), nanos(start), first,
            endedAtMillis = (end * 1000).toLong(), endedElapsedNanos = nanos(end), endKind = kind,
            targetIngressSeq = first + count - 1, persistedCount = count, drained = true)

    @Test fun `drained receipt replay matches individually consumed journal including empty stop`() {
        val epochs = listOf(epoch(kind = "PAUSE"), epoch("e2", 1, 7.0, 7.0, 2, 0))
        val raw = listOf(fix(0, 0.0, 1.0), fix(1, 4.0, 4.0))
        val policy = MotionPolicies.freeze("s")
        val actual = mutableListOf<MotionStep>()
        val result = replayRecordedMotion(policy, epochs, raw.asSequence(), actual::add)
        val entries = listOf(
            MotionEvent.Begin(MotionEpoch("e", "c", 0, 0, 0, nanos(5.0))),
            MotionEvent.Observation("s", raw[0]), MotionEvent.Observation("s", raw[1]),
            MotionEvent.End(1, nanos(5.0), EndKind.PAUSE),
            MotionEvent.Begin(MotionEpoch("e2", "c", 1, nanos(7.0), 2, nanos(7.0))),
            MotionEvent.End(1, nanos(7.0), EndKind.STOP))
        val expected = mutableListOf<MotionStep>()
        val engine = MotionPolicyEngine(policy)
        entries.forEachIndexed { index, event -> expected += engine.step(MotionJournalEntry(index.toLong(), event)) }
        assertEquals(expected, actual)
        assertEquals(engine.snapshot(), result)
        assertEquals(nanos(5.0), result.closedRecordingDurationNanos)
        assertEquals(4.0, result.eligibleDistanceM, 0.00001)
    }

    @Test fun `recording intervals are half open and do not count boundary or cached positions`() {
        val raw = listOf(fix(0, 100.0, 0.0).copy(recordingEligible = false),
            fix(1, 0.0, 2.0), fix(2, 8.0, 5.0))
        val steps = mutableListOf<MotionStep>()
        val result = replayRecordedMotion(MotionPolicies.freeze("s"), listOf(epoch(start = 1.0, count = 3)), raw.asSequence(), steps::add)
        assertEquals(0.0, result.eligibleDistanceM, 0.0)
        assertEquals(2, steps.count { it.decision?.reasons?.contains(MotionReason.OUTSIDE_ACTIVE_INTERVAL) == true })
        assertEquals(nanos(4.0), result.closedRecordingDurationNanos)
    }

    @Test fun `paused gap is excluded and actual journal identities remain unchanged`() {
        val epochs = listOf(epoch(kind = "PAUSE"), epoch("e2", 1, 20.0, 25.0, 2, 2))
        val raw = listOf(fix(0, 0.0, 1.0), fix(1, 4.0, 4.0),
            fix(2, 500.0, 21.0, source = "e2", chain = 1), fix(3, 504.0, 24.0, source = "e2", chain = 1))
        val before = raw.toList()
        val result = replayRecordedMotion(MotionPolicies.freeze("s"), epochs, raw.asSequence())
        assertEquals(8.0, result.eligibleDistanceM, 0.0001)
        assertEquals(2L, result.segmentCount)
        assertEquals(before, raw)
    }

    @Test fun `missing interrupted failed and undrained receipts cannot produce a final replay`() {
        val cases = listOf(emptyList(), listOf(epoch(kind = "PAUSE")), listOf(epoch(kind = "INTERRUPTED")),
            listOf(epoch().copy(drained = false)), listOf(epoch().copy(failureReason = "disk")))
        cases.forEach { epochs -> assertThrows(IllegalStateException::class.java) {
            replayRecordedMotion(MotionPolicies.freeze("s"), epochs, emptySequence())
        } }
    }

    @Test fun `missing extra reordered cross session and legacy observations fail explicitly`() {
        val raw = listOf(fix(0, 0.0, 1.0), fix(1, 4.0, 4.0))
        val cases = listOf(raw.take(1), raw + fix(2, 6.0, 6.0), raw.reversed(),
            listOf(raw[0].copy(sourceEpoch = "other"), raw[1]),
            listOf(raw[0].copy(ingressSeq = null), raw[1]),
            listOf(raw[0].copy(clientSeq = 40), raw[1]))
        cases.forEach { fixes -> assertThrows(IllegalArgumentException::class.java) {
            replayRecordedMotion(MotionPolicies.freeze("s"), listOf(epoch()), fixes.asSequence())
        } }
        assertThrows(IllegalArgumentException::class.java) {
            replayRecordedMotion(MotionPolicies.freeze("other"), listOf(epoch()), raw.asSequence())
        }
    }

    @Test fun `fresh engine from persisted descriptor reproduces every decision`() {
        val original = MotionPolicies.freeze("s", MotionConfig(minDistanceM = 1.0, maxWalkingSpeedMps = 5.0))
        val restored = (MotionPolicies.resolve("s", original.stored) as MotionPolicySelection.Supported).policy
        val raw = listOf(fix(0, 0.0, 1.0), fix(1, 4.0, 4.0))
        val first = mutableListOf<MotionStep>()
        val second = mutableListOf<MotionStep>()
        replayRecordedMotion(original, listOf(epoch()), raw.asSequence(), first::add)
        replayRecordedMotion(restored, listOf(epoch()), raw.asSequence(), second::add)
        assertEquals(first, second)
    }
}
