package com.daengs.app.walk.trajectory

import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Test

class LegacyObservationEvaluatorTest {
    private val session = RecordedSession("review", startedAtMillis = 0, endedAtMillis = 300_000)
    private fun fix(i: Int, x: Double, at: Long = 10_000L + i * 1_000L) =
        RecordedFix(i, 0, at, 0.0, x / 111_195, 1f, false)
    private fun evaluate(raw: List<RecordedFix>, policy: LegacyConnectionPolicy = LegacyConnectionPolicy()) =
        evaluateLegacyObservationConnections(readCompletedRoute(session, raw), policy)
    private fun fast(count: Int = 9) = (0 until count).map { fix(it, it * 12.0) }

    @Test fun `stable fast observations can form an excluded review run without walking distance`() {
        val raw = fast()
        val read = readCompletedRoute(session, raw)
        val before = read.summary
        val result = evaluateLegacyObservationConnections(read)
        assertEquals("review_candidate", result.stage)
        assertTrue(result.matches(read)); assertNull(result.unsupportedReason)
        assertTrue(result.intervals.all { it.connection == ObservationConnection.CONNECTED })
        assertTrue(result.intervals.all { it.walkingUse == LegacyWalkingUse.EXCLUDED })
        assertEquals((0..8).toList(), result.auxiliaryRuns.single().sourceSeqs)
        assertEquals(0.0, read.summary.distanceMeters, 0.0)
        assertEquals(before, read.summary); assertEquals(summarize(session, raw), read.summary)
    }

    @Test fun `normal walking is reserved once and raw legs do not duplicate the owner contribution`() {
        val raw = (0..8).map { fix(it, it * 2.0) }
        val read = readCompletedRoute(session, raw)
        val result = evaluateLegacyObservationConnections(read)
        assertEquals(4, result.owners.size)
        assertEquals(8, result.intervals.size)
        assertEquals(read.summary.distanceMeters, result.owners.sumOf { it.distanceContributionMeters }, 0.0)
        assertTrue(result.intervals.all { it.walkingUse == LegacyWalkingUse.INCLUDED })
        assertTrue(result.intervals.all { it.auxiliaryUse == AuxiliaryUse.RESERVED_BY_WALKING })
        assertTrue(result.auxiliaryRuns.isEmpty())
    }

    @Test fun `legacy bridge containing excluded fixes is a diagnostic conflict and never a second route`() {
        val raw = (0..8).map { fix(it, it * 2.0) } + (9..11).map { fix(it, 16.0 + (it - 8) * 12.0) } +
            (12..20).map { fix(it, 52.0 + (it - 11) * 2.0) }
        val read = readCompletedRoute(session, raw)
        val result = evaluateLegacyObservationConnections(read)
        val conflicts = result.intervals.filter { ConnectionReason.OWNER_CONTAINS_REJECTED_OBSERVATION in it.accountingReasons }
        assertTrue(conflicts.isNotEmpty())
        assertTrue(conflicts.all { it.auxiliaryUse == AuxiliaryUse.OWNERSHIP_CONFLICT && it.ownerToSeq != null })
        assertTrue(result.auxiliaryRuns.isEmpty())
        assertEquals(read.summary.distanceMeters, result.owners.sumOf { it.distanceContributionMeters }, 0.0)
        assertEquals(summarize(session, raw).segments, read.summary.segments)
    }

    @Test fun `pause separates excluded travel from a distant normal resumption`() {
        val raw = fast() + (9..17).map { fix(it, 500.0 + (it - 9) * 2).copy(chainIndex = 1) }
        val result = evaluate(raw)
        assertEquals((0..8).toList(), result.auxiliaryRuns.single().sourceSeqs)
        val boundary = result.intervals.first { it.fromSeq == 8 }
        assertEquals(ObservationConnection.BROKEN, boundary.connection)
        assertTrue(ConnectionReason.CHAIN_BOUNDARY in boundary.reasons)
        assertTrue(result.intervals.filter { it.fromSeq >= 9 }.all { it.ownerToSeq != null })
        assertTrue(result.transitions.any { it.atSeq == 8 })
    }

    @Test fun `a plausible instantaneous jump and return fails neighboring velocity consistency`() {
        val result = evaluate(listOf(0.0, 0.0, 20.0, 0.0, 0.0).mapIndexed { i, x -> fix(i, x) })
        assertTrue(result.intervals.all { it.kinematics!!.adjustedSpeedMps < result.policy.maxAdjustedSpeedMps })
        assertTrue(result.neighbors.any { !it.consistent })
        assertTrue(result.intervals.any { ConnectionReason.NEIGHBOR_VELOCITY_CHANGE in it.reasons })
        assertTrue(result.auxiliaryRuns.isEmpty())
    }

    @Test fun `large jump and later recovery never bridge the unknown space`() {
        val raw = fast(5) + (5..10).map { fix(it, 1_000.0 + (it - 5) * 12) }
        val result = evaluate(raw)
        val jump = result.intervals.first { it.fromSeq == 4 }
        assertEquals(ObservationConnection.UNRESOLVED, jump.connection)
        assertTrue(ConnectionReason.SPEED_BEYOND_REVIEW_LIMIT in jump.reasons)
        assertTrue(result.intervals.filter { it.fromSeq >= 5 }.all { it.connection == ObservationConnection.CONNECTED })
        assertTrue(result.auxiliaryRuns.none { 4 in it.sourceSeqs && 5 in it.sourceSeqs })
    }

    @Test fun `a small displacement across a time gap is still not a recorded path`() {
        val raw = fast(5) + (5..9).map { fix(it, 48.0 + (it - 5) * 2, 100_000L + (it - 5) * 1_000) }
        val result = evaluate(raw)
        val gap = result.intervals.first { it.fromSeq == 4 }
        assertTrue(ConnectionReason.OBSERVATION_GAP in gap.reasons)
        assertEquals(ObservationConnection.UNRESOLVED, gap.connection)
        assertTrue(result.auxiliaryRuns.none { 4 in it.sourceSeqs && 5 in it.sourceSeqs })
    }

    @Test fun `quality controls epochs and source holes block only the affected range`() {
        val base = fast(12)
        val changed = listOf(
            base[5].copy(accuracyM = 80f), base[5].copy(accuracyM = null), base[5].copy(accuracyM = Float.NaN),
            base[5].copy(lat = Double.NaN), base[5].copy(lng = 181.0), base[5].copy(isMock = true),
            base[5].copy(recordingEligible = false), base[5].copy(sourceEpoch = "new"),
            base[5].copy(clockEpochId = "new"), base[5].copy(chainIndex = 1),
            base[5].copy(atMillis = base[4].atMillis), base[5].copy(atMillis = base[4].atMillis - 1))
        for (change in changed) {
            val result = evaluate(base.toMutableList().also { it[5] = change })
            assertTrue("$change", result.intervals.filter { it.toSeq == 5 || it.fromSeq == 5 }
                .none { it.connection == ObservationConnection.CONNECTED })
            assertTrue(result.intervals.filter { it.fromSeq >= 6 }.all { it.connection == ObservationConnection.CONNECTED })
        }
        val missing = evaluate(base.filter { it.clientSeq != 5 })
        assertTrue(ConnectionReason.MISSING_SEQUENCE in missing.intervals.first { it.fromSeq == 4 }.reasons)
    }

    @Test fun `single and uncorroborated points stay available without a path and tiny movement is not exclusion`() {
        val one = evaluate(listOf(fix(0, 0.0)))
        assertEquals(ObservationQuality.USABLE, one.observations.single().quality)
        assertTrue(one.intervals.isEmpty()); assertTrue(one.auxiliaryRuns.isEmpty())
        assertTrue(evaluate(fast(2)).intervals.all { ConnectionReason.INSUFFICIENT_NEIGHBORS in it.reasons })
        val small = evaluate((0..5).map { fix(it, 1.0) })
        assertTrue(small.intervals.all { it.walkingUse == LegacyWalkingUse.UNRESOLVED })
        assertTrue(small.intervals.all { it.kinematics?.displacementMeters == 0.0 })
    }

    @Test fun `scoped monotonic time must agree and unscoped or incomplete clocks are not invented`() {
        val raw = fast().map { it.copy(clockEpochId = "boot", sourceEpoch = "epoch", elapsedRealtimeNanos = it.atMillis * 1_000_000) }
        assertTrue(evaluate(raw).intervals.all { it.clock == ObservationClock.SCOPED_MONOTONIC && it.connection == ObservationConnection.CONNECTED })
        val variations = listOf(raw.map { it.copy(clockEpochId = null) },
            raw.mapIndexed { i, it -> if (i == 4) it.copy(elapsedRealtimeNanos = null) else it },
            raw.mapIndexed { i, it -> if (i == 4) it.copy(atMillis = it.atMillis + 2_000) else it })
        variations.forEach { assertTrue(evaluate(it).intervals.first { edge -> edge.toSeq == 4 }.connection != ObservationConnection.CONNECTED) }
    }

    @Test fun `unsupported or stale read cannot lend accounting or a candidate route`() {
        val raw = fast()
        val read = readCompletedRoute(session, raw)
        for (changed in listOf(read.copy(legacyRouteEvidence = null), read.copy(summary = read.summary.copy(distanceMeters = 5.0)),
            read.copy(observations = raw.map { it.copy(accuracyM = 2f) }), read.copy(route = WalkSessionRoute(emptyList())),
            read.copy(summary = read.summary.copy(measurementVersion = "motion-measurement-v1")))) {
            val result = evaluateLegacyObservationConnections(changed)
            assertNotNull(result.unsupportedReason); assertFalse(result.matches(changed)); assertTrue(result.auxiliaryRuns.isEmpty())
        }
        assertEquals("duplicate_source_address", evaluate(raw + raw[4]).unsupportedReason)
    }

    @Test fun `final source regrouping and input order do not change neighbor and accounting results`() {
        val raw = fast(20)
        val expected = evaluate(raw)
        for (size in listOf(1, 2, 3, 7, 20)) {
            val actual = evaluate(raw.chunked(size).reversed().flatten())
            assertEquals(expected.intervals, actual.intervals)
            assertEquals(expected.neighbors, actual.neighbors)
            assertEquals(expected.auxiliaryRuns, actual.auxiliaryRuns)
            assertEquals(expected.owners, actual.owners)
            assertEquals(expected.transitions, actual.transitions)
        }
    }

    @Test fun `policy bounds are explicit and invalid numeric settings fail early`() {
        for (block in listOf<() -> Unit>({ LegacyConnectionPolicy(maxAccuracyMeters = Double.NaN) },
            { LegacyConnectionPolicy(minIntervalMillis = 0) }, { LegacyConnectionPolicy(maxAdjustedSpeedMps = Double.POSITIVE_INFINITY) },
            { LegacyConnectionPolicy(maxVelocityChangeMps2 = -1.0) }, { LegacyConnectionPolicy(minConsistentEdges = 1) })) {
            assertThrows(IllegalArgumentException::class.java, block)
        }
        assertEquals(ObservationConnection.CONNECTED, evaluate(fast(3), LegacyConnectionPolicy(minConsistentEdges = 2)).intervals.first().connection)
        assertEquals(ObservationConnection.UNRESOLVED, evaluate(fast(3)).intervals.first().connection)
    }

    @Test fun `invalid source addresses and reversed record windows cannot claim a candidate`() {
        assertEquals("invalid_source_address", evaluate(fast().map { it.copy(clientSeq = it.clientSeq - 1) }).unsupportedReason)
        assertEquals("invalid_source_address", evaluate(fast().map { it.copy(chainIndex = -1) }).unsupportedReason)
        val reversed = readCompletedRoute(session.copy(startedAtMillis = 30_000, endedAtMillis = 20_000), fast())
        assertEquals("invalid_record_window", evaluateLegacyObservationConnections(reversed).unsupportedReason)
    }
}
