package com.daengs.app.walk.motion

import org.junit.Assert.*
import org.junit.Test

class MotionPolicyEngineTest {
    @Test fun `high speed then zero cannot reconnect excluded vehicle distance`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 0.0, 0f))
        val high = h.observe(fix(1, 10.0, 1.0, 10f))
        assertTrue(MotionReason.HIGH_SPEED in high.decision!!.reasons)
        val firstStop = h.observe(fix(2, 20.0, 2.0, 0f))
        assertTrue(MotionReason.REENTRY_PENDING in firstStop.decision!!.reasons)
        val reentry = h.observe(fix(3, 20.0, 4.0, 0f))
        assertEquals(Connection.START_NEW, reentry.decision!!.connection)
        assertEquals(0.0, reentry.snapshot.eligibleDistanceM, 0.0)
        val walking = h.observe(fix(4, 24.0, 8.0, 1f))
        assertEquals(4.0, walking.snapshot.eligibleDistanceM, 0.0001)
        assertEquals(3L, walking.decision!!.fromRef!!.ingressSeq)
        assertEquals(2L, walking.snapshot.segmentCount)
        assertEquals(0, walking.decision.toRef!!.chainIndex) // policy segments do not rewrite pause chains
    }

    @Test fun `instantaneous zero does not override a physically fast segment`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 0.0, 0f))
        val result = h.observe(fix(1, 30.0, 2.0, 0f))
        assertEquals(Movement.STILL, result.estimate!!.movement)
        assertTrue(MotionReason.HIGH_SPEED in result.decision!!.reasons)
        assertEquals(0.0, result.snapshot.eligibleDistanceM, 0.0)
    }

    @Test fun `poor position still exposes trusted speed and erects high speed barrier`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 0.0))
        val result = h.observe(fix(1, 50.0, 1.0, 20f, accuracy = 100f))
        assertEquals(PositionQuality.UNCERTAIN, result.estimate!!.positionQuality)
        assertEquals(SpeedQuality.TRUSTED, result.estimate.speedQuality)
        assertEquals(20.0, result.estimate.speedMps!!, 0.0)
        assertTrue(MotionReason.HIGH_SPEED in result.decision!!.reasons)
        assertEquals(0.0, h.observe(fix(2, 50.0, 3.0, 0f)).snapshot.eligibleDistanceM, 0.0)
    }

    @Test fun `one poor position does not automatically cut a plausible short gap`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 0.0))
        h.observe(fix(1, 100.0, 1.0, accuracy = 100f))
        val result = h.observe(fix(2, 6.0, 4.0))
        assertEquals(Connection.CONTINUE, result.decision!!.connection)
        assertEquals(6.0, result.snapshot.eligibleDistanceM, 0.0001)
    }

    @Test fun `sustained poor positions create gap even with regular callbacks`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 0.0))
        for (seq in 1..25) h.observe(fix(seq, 100.0, seq.toDouble(), accuracy = 100f))
        val result = h.observe(fix(26, 30.0, 26.0))
        assertEquals(Connection.START_NEW, result.decision!!.connection)
        assertTrue(MotionReason.OBSERVATION_GAP in result.decision.reasons)
        assertEquals(0.0, result.snapshot.eligibleDistanceM, 0.0)
    }

    @Test fun `small steps accumulate against a separate noise anchor`() {
        val h = EngineHarness()
        for (i in 0..10) h.observe(fix(i, i.toDouble(), i * 2.0))
        assertTrue(h.engine.snapshot().eligibleDistanceM >= 8.9)
        assertTrue(h.engine.snapshot().eligibleDistanceM <= 10.0)
        assertEquals(1L, h.engine.snapshot().segmentCount)
    }

    @Test fun `stationary uncertainty is not manufactured as measured zero`() {
        val h = EngineHarness()
        for (i in 0..20) {
            val step = h.observe(fix(i, (i % 2).toDouble(), i.toDouble(), speed = null, accuracy = 5f))
            assertNull(step.estimate!!.speedMps)
            assertEquals(Movement.UNKNOWN, step.estimate.movement)
        }
        assertEquals(0.0, h.engine.snapshot().eligibleDistanceM, 0.0)
    }

    @Test fun `coordinate speed window does not depend on last distance acceptance`() {
        val h = EngineHarness(MotionConfig(minDistanceM = 100.0, windowSize = 2))
        for (i in 0..5) h.observe(fix(i, i * 2.0, i * 2.0, speed = null, accuracy = 0f))
        val estimate = h.steps.last().estimate!!
        assertEquals(SpeedSource.COORDINATE_WINDOW, estimate.speedSource)
        assertEquals(1.0, estimate.speedMps!!, 0.00001)
        assertEquals(0.0, h.engine.snapshot().eligibleDistanceM, 0.0)
        assertEquals(2, h.engine.snapshot().retainedObservationCount)
    }

    @Test fun `missing speed accuracy is unverified and checked against coordinate history`() {
        val h = EngineHarness()
        val first = h.observe(fix(0, 0.0, 0.0, speedAccuracy = null))
        assertEquals(SpeedQuality.UNVERIFIED, first.estimate!!.speedQuality)
        assertEquals(1.0, first.estimate.speedMps!!, 0.0)
        val conflict = h.observe(fix(1, 4.0, 4.0, speed = 20f, speedAccuracy = null))
        assertTrue(MotionReason.SPEED_CONFLICT in conflict.estimate!!.reasons)
        assertEquals(SpeedSource.COORDINATE_WINDOW, conflict.estimate.speedSource)
        assertEquals(1.0, conflict.estimate.speedMps!!, 0.00001)
    }

    @Test fun `invalid speed can fall back to coordinates without replacing raw`() {
        for (invalid in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val h = EngineHarness()
            h.observe(fix(0, 0.0, 0.0, speed = null))
            val raw = fix(1, 6.0, 3.0, speed = invalid)
            val result = h.observe(raw)
            assertEquals(SpeedSource.COORDINATE_WINDOW, result.estimate!!.speedSource)
            assertTrue(MotionReason.SPEED_INVALID in result.estimate.reasons)
            assertEquals(invalid, raw.speedMps)
        }
    }

    @Test fun `invalid speed accuracy is independent from valid position`() {
        for (accuracy in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, 50f)) {
            val h = EngineHarness()
            val result = h.observe(fix(0, 0.0, 0.0, speedAccuracy = accuracy))
            assertEquals(PositionQuality.USABLE, result.estimate!!.positionQuality)
            assertNull(result.estimate.speedMps)
            assertTrue(MotionReason.SPEED_UNCERTAIN in result.estimate.reasons)
        }
    }

    @Test fun `invalid coordinate and mock samples never add distance`() {
        val invalids = listOf(fix(0, 0.0, 0.0).copy(lat = Double.NaN),
            fix(0, 0.0, 0.0).copy(lat = 91.0), fix(0, 0.0, 0.0).copy(lng = Double.POSITIVE_INFINITY),
            fix(0, 0.0, 0.0, accuracy = -1f), fix(0, 0.0, 0.0, accuracy = Float.NaN),
            fix(0, 0.0, 0.0).copy(isMock = true))
        for (raw in invalids) {
            val result = EngineHarness().observe(raw)
            assertEquals(Connection.SKIP, result.decision!!.connection)
            assertEquals(0.0, result.snapshot.eligibleDistanceM, 0.0)
            if (raw.isMock) assertNull(result.estimate!!.speedMps)
            else assertEquals(SpeedQuality.TRUSTED, result.estimate!!.speedQuality)
        }
    }

    @Test fun `antipodal coordinates do not produce NaN distance`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 0.0).copy(lat = 45.0, lng = 15.0))
        val result = h.observe(fix(1, 0.0, 1.0).copy(lat = -45.0, lng = -165.0))
        assertTrue(result.decision!!.estimatedSegmentSpeedMps!!.isFinite())
        assertEquals(0.0, result.snapshot.eligibleDistanceM, 0.0)
    }

    @Test fun `out of order and duplicate samples do not rewind measurement history`() {
        val h = EngineHarness()
        val a = fix(0, 0.0, 10.0)
        h.observe(a)
        val duplicate = h.observe(a.copy(clientSeq = 1, ingressSeq = 1, receivedElapsedNanos = nanos(20.0), receivedAtMillis = 20000))
        assertTrue(MotionReason.DUPLICATE in duplicate.decision!!.reasons)
        val conflict = h.observe(fix(2, 100.0, 10.0))
        assertTrue(MotionReason.SAME_TIME_CONFLICT in conflict.decision!!.reasons)
        val earlier = h.observe(fix(3, 100.0, 9.0))
        assertTrue(MotionReason.OUT_OF_ORDER in earlier.decision!!.reasons)
        assertEquals(4.0, h.observe(fix(4, 4.0, 14.0)).snapshot.eligibleDistanceM, 0.0001)
    }

    @Test fun `old delivery with increasing measurement time remains valid for path`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 1.0).copy(receivedElapsedNanos = nanos(1000.0)))
        val result = h.observe(fix(1, 5.0, 5.0).copy(receivedElapsedNanos = nanos(1001.0)))
        assertEquals(5.0, result.snapshot.eligibleDistanceM, 0.0001)
    }

    @Test fun `cached and invalid times cannot contaminate next valid anchor`() {
        val h = EngineHarness()
        val cached = h.observe(fix(0, 100.0, 1.0, 20f).copy(recordingEligible = false))
        assertTrue(MotionReason.OUTSIDE_ACTIVE_INTERVAL in cached.decision!!.reasons)
        h.observe(fix(1, 0.0, 2.0))
        val invalid = h.observe(fix(2, 100.0, 3.0).copy(receivedElapsedNanos = nanos(1.0)))
        assertTrue(MotionReason.INVALID_TIME in invalid.decision!!.reasons)
        val next = h.observe(fix(3, 100.0, 4.0))
        assertEquals(Connection.START_NEW, next.decision!!.connection)
        assertEquals(0.0, next.snapshot.eligibleDistanceM, 0.0)
    }

    @Test fun `source clock and chain contamination cannot connect path`() {
        val bad = listOf(fix(1, 10.0, 2.0, source = "old"), fix(1, 10.0, 2.0, clock = "old"), fix(1, 10.0, 2.0, chain = 8))
        for (raw in bad) {
            val h = EngineHarness()
            h.observe(fix(0, 0.0, 1.0))
            assertEquals(Connection.SKIP, h.observe(raw).decision!!.connection)
            assertEquals(Connection.START_NEW, h.observe(fix(2, 10.0, 3.0)).decision!!.connection)
            assertEquals(0.0, h.engine.snapshot().eligibleDistanceM, 0.0)
        }
    }

    @Test fun `pause resume changes source and may change clock without subtracting domains`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 100.0))
        h.send(MotionEvent.End(0, nanos(102.0), EndKind.PAUSE))
        h.send(MotionEvent.Begin(MotionEpoch("e2", "c2", 1, 0, 1)))
        val result = h.observe(fix(1, 500.0, 1.0, source = "e2", clock = "c2", chain = 1))
        assertTrue(MotionReason.CLOCK_DOMAIN_CHANGED in result.decision!!.reasons)
        assertEquals(Connection.START_NEW, result.decision.connection)
        h.send(MotionEvent.End(1, nanos(2.0), EndKind.STOP))
        assertEquals(nanos(104.0), h.engine.snapshot().closedRecordingDurationNanos)
        assertEquals(0.0, h.engine.snapshot().eligibleDistanceM, 0.0)
    }

    @Test fun `known loss and missing sequence break distance without inventing observations`() {
        for (reason in LossReason.entries) {
            val h = EngineHarness()
            h.observe(fix(0, 0.0, 0.0))
            h.send(MotionEvent.Loss(reason))
            val next = h.observe(fix(2, 10.0, 2.0))
            assertEquals(Connection.START_NEW, next.decision!!.connection)
            assertTrue(next.snapshot.hasKnownLoss)
            assertEquals(2L, next.snapshot.processedObservationCount)
            assertEquals(0.0, next.snapshot.eligibleDistanceM, 0.0)
        }
    }

    @Test fun `missing tail at end stays visible as loss`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 0.0))
        val end = h.send(MotionEvent.End(2, nanos(4.0), EndKind.STOP))
        assertTrue(end.snapshot.hasKnownLoss)
        assertEquals(1L, end.snapshot.processedObservationCount)
        assertEquals(2L, end.snapshot.lastIngressSeq)
    }

    @Test fun `invalid journal session and ingress commands do not mutate state`() {
        val h = EngineHarness()
        h.observe(fix(0, 0.0, 0.0))
        val before = h.engine.snapshot()
        val bad = listOf(MotionJournalEntry(20, MotionEvent.Loss(LossReason.STORAGE_FAILURE)),
            MotionJournalEntry(2, MotionEvent.Observation("other", fix(1, 1.0, 1.0))),
            MotionJournalEntry(2, MotionEvent.Observation("s", fix(0, 1.0, 1.0))),
            MotionJournalEntry(2, MotionEvent.Observation("s", fix(1, 1.0, 1.0).copy(ingressSeq = null))),
            MotionJournalEntry(2, MotionEvent.End(0, -1, EndKind.STOP)))
        bad.forEach { assertThrows(IllegalArgumentException::class.java) { h.engine.step(it) }; assertEquals(before, h.engine.snapshot()) }
    }

    @Test fun `single and arbitrary batch partitions produce identical steps`() {
        val h = EngineHarness()
        for (i in 0..50) h.observe(fix(i, i * 2.0, i * 2.0))
        h.send(MotionEvent.End(50, nanos(102.0), EndKind.STOP))
        for (size in listOf(1, 3, 16, 128)) {
            val engine = MotionPolicyEngine(MotionPolicies.freeze("s"))
            val results = mutableListOf<MotionStep>()
            h.entries.chunked(size).forEach { engine.consume(it, results::add) }
            assertEquals(h.steps, results)
        }
    }

    @Test fun `long recording retains bounded history and cumulative distance`() {
        val engine = MotionPolicyEngine(MotionPolicies.freeze("s"))
        engine.step(MotionJournalEntry(0, MotionEvent.Begin(MotionEpoch("e", "c", 0, 0, 0))))
        for (i in 0 until 10_000) {
            val step = engine.step(MotionJournalEntry(i + 1L, MotionEvent.Observation("s", fix(i, i * 4.0, i * 4.0))))
            assertTrue(step.snapshot.retainedObservationCount <= 8)
        }
        assertEquals(39_996.0, engine.snapshot().eligibleDistanceM, 0.01)
        assertEquals(10_000L, engine.snapshot().processedObservationCount)
    }
}
