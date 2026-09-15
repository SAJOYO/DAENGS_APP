package com.daengs.app.walk.trajectory

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import org.junit.Assert.*
import org.junit.Test

class RecordContextReviewTest {
    private fun fix(i: Int, at: Long, x: Double = i * 4.0, chain: Int = 0) =
        RecordedFix(i, chain, at, 0.0, x / 111_195, 1f, false)
    private fun read(raw: List<RecordedFix>, end: Long = 90_000) =
        readCompletedRoute(RecordedSession("context", startedAtMillis = 0, endedAtMillis = end), raw)
    private fun walk() = (0..8).map { fix(it, 10_000 + it * 2_000L) }

    @Test fun `start and end retain event time separately from first last fix and walking endpoint`() {
        val detail = read(walk()); val review = CompletedRouteReview(detail).context
        val start = review.contexts.first(); val end = review.contexts.last()
        assertEquals(0L, start.fromMillis); assertEquals(10_000L, start.after!!.atMillis)
        assertNull(start.before)
        assertEquals(90_000L, end.fromMillis); assertEquals(26_000L, end.before!!.atMillis)
        assertEquals(detail.route.end, end.walkingEndpoint)
        assertEquals(90_000L, review.durationMillis)
        assertNull(review.frameAt(5_000).point); assertEquals(5_000L, review.frameAt(5_000).recordedAtMillis)
        assertNotNull(review.frameAt(13_000).point); assertNull(review.frameAt(80_000).point)
        assertTrue(review.frameAt(13_000).derivedSpeedMetersPerSecond!! > 0)
        assertNull(review.frameAt(80_000).derivedSpeedMetersPerSecond)
    }
    @Test fun `chain gap retains two source endpoints without replaying the relation`() {
        val raw = walk() + (9..17).map { fix(it, 60_000 + (it - 9) * 2_000L, 200.0 + it * 4, 1) }
        val detail = read(raw); val review = CompletedRouteReview(detail)
        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        assertEquals(8, gap.fromSeq); assertEquals(9, gap.toSeq)
        assertEquals(34_000L, gap.durationMillis)
        assertEquals(RecordMovement.WALKING, gap.beforeMovement); assertEquals(RecordMovement.WALKING, gap.afterMovement)
        assertEquals(2, gap.locations.size); assertNull(review.context.frameAt(40_000).point)
        assertEquals(detail.summary, summarizeLegacy(RecordedSession("context", startedAtMillis = 0, endedAtMillis = 90_000), raw, Int.MAX_VALUE))
    }
    @Test fun `invalid endpoint is not replaced by a guessed faraway position`() {
        val raw = walk() + fix(9, 40_000).copy(lat = Double.NaN, accuracyM = 100f)
        val review = CompletedRouteReview(read(raw)).context
        val gap = review.contexts.single { it.kind == RecordContextKind.GAP }
        assertNotNull(gap.before); assertNull(gap.after)
        assertEquals(8, review.contexts.last().before!!.seq)
    }
    @Test fun `missing all locations still leaves start end and selectable record time`() {
        val review = CompletedRouteReview(read(emptyList())).context
        assertEquals(listOf(RecordContextKind.START, RecordContextKind.END), review.contexts.map { it.kind })
        assertTrue(review.contexts.all { it.locations.isEmpty() })
        assertNull(review.frameAt(45_000).point); assertEquals(45_000L, review.frameAt(45_000).recordedAtMillis)
    }
    @Test fun `epoch and reversed clocks never fabricate a duration or automatic cursor`() {
        for (suffix in listOf(walk().map { it.copy(clientSeq = it.clientSeq + 9, sourceEpoch = "next", atMillis = it.atMillis + 40_000) },
            walk().map { it.copy(clientSeq = it.clientSeq + 9) })) {
            val review = CompletedRouteReview(read(walk() + suffix)).context
            assertNull(review.durationMillis); assertNull(review.frameAt(50_000).point)
            assertTrue(review.contexts.any { it.fromSeq != null && it.toSeq != null && it.durationMillis == null })
            assertNull(review.gapAt(40_000))
        }
    }
    @Test fun `unlocated scene retains temporal neighbors without gaining a coordinate`() {
        val detail = read(walk()); val review = CompletedRouteReview(detail)
        val scene = DiaryScene("note", "context", 15_000, "메모", "", null, "")
        val neighbors = review.context.temporalNeighbors(scene)!!
        assertEquals(14_000L, neighbors.first!!.atMillis); assertEquals(16_000L, neighbors.second!!.atMillis)
        assertNull(review.recordSceneFocus(scene).point); assertTrue(review.recordSceneFocus(scene).paths.isEmpty())
        assertNull(review.context.temporalNeighbors(scene.copy(sessionId = "other")))
    }
    @Test fun `adopted excluded movement can replay while unresolved jitter cannot`() {
        val fast = (0..12).map { fix(it, 10_000 + it * 1_000L, it * 12.0) }
        val fastReview = CompletedRouteReview(read(fast)).context
        assertNotNull(fastReview.frameAt(15_500).point)
        val jitter = (0..12).map { fix(it, 10_000 + it * 1_000L, if (it % 2 == 0) 0.0 else .5) }
        assertNull(CompletedRouteReview(read(jitter)).context.frameAt(15_500).point)
    }
    @Test fun `changed read and unsupported input cannot borrow contexts`() {
        val detail = read(walk()); val observed = ObservedRouteReview(detail)
        assertTrue(RecordContextReview(detail.copy(observations = emptyList()), observed).contexts.isEmpty())
        assertTrue(CompletedRouteReview(detail.copy(legacyRouteEvidence = null)).context.contexts.isEmpty())
    }
    @Test fun `walking to stationary unresolved observations creates a transition without splitting accounting`() {
        val raw = walk() + (9..18).map { fix(it, 26_000 + (it - 8) * 1_000L, 32.0 + if (it % 2 == 0) .5 else 0.0) }
        val detail = read(raw); val review = CompletedRouteReview(detail)
        val transition = review.context.contexts.single { it.kind == RecordContextKind.TRANSITION }
        assertEquals(RecordMovement.WALKING, transition.beforeMovement)
        assertEquals(RecordMovement.UNRESOLVED, transition.afterMovement)
        assertEquals(8, transition.fromSeq); assertEquals(transition.before, transition.after)
        assertEquals(summarizeLegacy(RecordedSession("context", startedAtMillis = 0, endedAtMillis = 90_000), raw, Int.MAX_VALUE).distanceMeters,
            detail.summary.distanceMeters, 0.0)
    }
}
