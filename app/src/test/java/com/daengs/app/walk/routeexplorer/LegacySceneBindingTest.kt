package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.StoryboardObservation
import com.daengs.app.walk.diary.StoryboardScene
import org.junit.Assert.*
import org.junit.Test

class LegacySceneBindingTest {
    private val session = RecordedSession("s", startedAtMillis = 0, endedAtMillis = 100_000)
    private fun fix(seq: Int, x: Double, at: Long = 10_000L + seq * 2_000L) =
        RecordedFix(seq, 0, at, reviewPoint(x).latitude, reviewPoint(x).longitude, 3f, false)
    private val normal get() = listOf(fix(0, 0.0), fix(1, 1.0), fix(2, 4.0))
    private fun scene(fix: RecordedFix) = reviewScene(fix.atMillis, 0.0).copy(
        point = GeoPoint(fix.lat, fix.lng),
        source = StoryboardScene("observed", fix.atMillis, "장면", "", "", "revision-1",
            observation = StoryboardObservation(fix.clientSeq, fix.chainIndex, fix.atMillis, GeoPoint(fix.lat, fix.lng))))

    @Test fun `normal omitted source anchors to its accepted edge without moving the pin or metrics`() {
        val raw = normal
        val baseline = summarize(session, raw, Int.MAX_VALUE)
        val read = readCompletedRoute(session, raw)
        assertEquals(baseline, read.summary)
        assertEquals(baseline.toSessionRoute(), read.route)
        val selected = scene(raw[1])
        val review = CompletedRouteReview(read)
        assertEquals(SceneRouteRelation.CONNECTED, review.sceneFocus(selected.copy(source = null)).relation)
        val focus = review.sceneFocus(selected)
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals(selected.point, focus.point)
        assertEquals(read.route.bounds, focus.paths.single())
        assertEquals(0, focus.binding!!.fromSeq); assertEquals(2, focus.binding.toSeq)
        assertEquals(raw[1].atMillis, focus.binding.eventAtMillis)
        assertEquals(raw[1].atMillis, focus.binding.locationAtMillis)
    }

    @Test fun `many small omissions belong to one accepted edge without adding vertices`() {
        val raw = listOf(fix(0, 0.0), fix(1, 0.5), fix(2, 1.0), fix(3, 2.0), fix(4, 4.0))
        val read = readCompletedRoute(session, raw)
        val review = CompletedRouteReview(read)
        assertEquals(2, read.route.points.size)
        for (fix in raw.subList(1, 4)) {
            val focus = review.sceneFocus(scene(fix))
            assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
            assertEquals(0, focus.binding!!.fromSeq); assertEquals(4, focus.binding.toSeq)
        }
    }

    @Test fun `no later accepted point keeps position but cannot invent a walking edge`() {
        val raw = normal.take(2)
        val focus = CompletedRouteReview(readCompletedRoute(session, raw)).sceneFocus(scene(raw[1]))
        assertEquals(SceneRouteRelation.NO_ROUTE, focus.relation)
        assertEquals(scene(raw[1]).point, focus.point)
        assertTrue(focus.paths.isEmpty()); assertNull(focus.binding!!.toSeq)
    }

    @Test fun `frequent small observations over a long display interval are not a GPS gap`() {
        val raw = listOf(fix(0, 0.0)) + (1..24).map { fix(it, 1.0) } + fix(25, 4.0)
        val read = readCompletedRoute(session, raw)
        assertEquals(2, read.route.points.size)
        val focus = CompletedRouteReview(read).sceneFocus(scene(raw[15]))
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals(0, focus.binding!!.fromSeq); assertEquals(25, focus.binding.toSeq)
        assertEquals(read.route.bounds, focus.paths.single())
        val gap = raw.filter { it.clientSeq !in 2..20 }.mapIndexed { i, fix -> fix.copy(clientSeq = i) }
        assertTrue(CompletedRouteReview(readCompletedRoute(session, gap)).sceneFocus(scene(gap[1])).paths.isEmpty())
    }

    @Test fun `omitted cause alone cannot cross excluded or invalid observations`() {
        val middle = fix(2, 2.0)
        val barriers = listOf(middle.copy(accuracyM = 100f), fix(2, 1000.0),
            middle.copy(recordingEligible = false), middle.copy(isMock = true),
            middle.copy(accuracyM = Float.NaN), middle.copy(accuracyM = -1f),
            middle.copy(atMillis = 12_000), middle.copy(atMillis = 11_000),
            middle.copy(sourceEpoch = "restart"), middle.copy(clockEpochId = "restart"),
            middle.copy(elapsedRealtimeNanos = 100L))
        for (barrier in barriers) {
            val raw = listOf(fix(0, 0.0), fix(1, 1.0), barrier, fix(3, 8.0))
            val read = readCompletedRoute(session, raw)
            val focus = CompletedRouteReview(read).sceneFocus(scene(raw[1]))
            assertTrue("barrier $barrier", focus.paths.isEmpty())
            assertEquals(summarize(session, raw, Int.MAX_VALUE), read.summary)
        }
    }

    @Test fun `duplicate or missing source addresses cannot supply an edge`() {
        for (raw in listOf(listOf(fix(0, 0.0), fix(1, 1.0), fix(3, 4.0)), normal + normal[1])) {
            val focus = CompletedRouteReview(readCompletedRoute(session, raw)).sceneFocus(scene(raw[1]))
            assertTrue(focus.paths.isEmpty())
        }
    }

    @Test fun `chain boundaries and long gaps do not borrow the next accepted point`() {
        for (end in listOf(normal.last().copy(chainIndex = 1), normal.last().copy(atMillis = 50_000))) {
            val raw = normal.take(2) + end
            assertTrue(CompletedRouteReview(readCompletedRoute(session, raw)).sceneFocus(scene(raw[1])).paths.isEmpty())
        }
    }

    @Test fun `source and route changes cannot reuse an earlier read basis`() {
        val read = readCompletedRoute(session, normal)
        val changed = listOf(read.copy(summary = read.summary.copy(distanceMeters = 900.0)),
            read.copy(route = WalkSessionRoute(emptyList())),
            read.copy(observations = normal.map { it.copy(sourceEpoch = "changed") }),
            read.copy(legacyRouteEvidence = null))
        changed.forEach { assertTrue(CompletedRouteReview(it).sceneFocus(scene(normal[1])).paths.isEmpty()) }
        assertEquals(SceneRouteRelation.CONNECTED,
            CompletedRouteReview(read.copy(moments = emptyList())).sceneFocus(scene(normal[1])).relation)
    }

    @Test fun `return visit uses its own source range and retains reverse direction`() {
        val raw = normal + listOf(fix(3, 4.0, 40_000).copy(chainIndex = 1),
            fix(4, 3.0, 42_000).copy(chainIndex = 1), fix(5, 0.0, 44_000).copy(chainIndex = 1))
        val review = CompletedRouteReview(readCompletedRoute(session, raw))
        val outward = review.sceneFocus(scene(raw[1]))
        val returning = review.sceneFocus(scene(raw[4]))
        assertEquals(outward.paths.single().asReversed(), returning.paths.single())
        assertEquals(3, returning.binding!!.fromSeq); assertEquals(5, returning.binding.toSeq)
    }

    @Test fun `recovered highlight does not extend over an adjacent exclusion even if legacy draws that edge`() {
        val raw = normal + listOf(fix(3, 1000.0), fix(4, 8.0))
        val read = readCompletedRoute(session, raw)
        assertEquals(3, read.route.points.size) // Preserve the historical bridge, but do not borrow it.
        val review = CompletedRouteReview(read)
        assertEquals(read.route.bounds.take(2), review.sceneFocus(scene(raw[1])).paths.single())
        assertTrue(review.sceneFocus(scene(raw[3])).paths.isEmpty())
    }

    @Test fun `monotonic reversal inside a wall-clock ordered span cannot be repaired by scene matching`() {
        for (nanos in listOf(listOf(10L, 20L, 30L), listOf(10L, 5L, 30L))) {
            val raw = normal.mapIndexed { i, fix -> fix.copy(elapsedRealtimeNanos = nanos[i], clockEpochId = "e") }
            val focus = CompletedRouteReview(readCompletedRoute(session, raw)).sceneFocus(scene(raw[1]))
            assertEquals(nanos[1] > nanos[0], focus.relation == SceneRouteRelation.CONNECTED)
        }
    }

    @Test fun `decision output and batch boundaries preserve recorder results and sample identities`() {
        val samples = listOf(fix(0, 0.0), fix(1, 1.0), fix(2, 4.0), fix(3, 1000.0),
            fix(4, 6.0).copy(accuracyM = 100f), fix(5, 8.0)).map {
            LocationSample(GeoPoint(it.lat, it.lng), it.atMillis, accuracyMeters = it.accuracyM)
        }
        fun run(batch: Boolean, observe: Boolean): Pair<TrailSnapshot, List<TrailDecision>> {
            val recorder = TrailRecorder()
            val decisions = mutableListOf<TrailDecision>()
            if (observe) recorder.onDecision = { decisions += it }
            recorder.start()
            if (batch) samples.chunked(2).forEach { recorder.addAll(it) } else samples.forEach { recorder.add(it) }
            recorder.pause(); recorder.resume()
            recorder.addAll(listOf(samples.last().copy(point = reviewPoint(1000.0), capturedAtMillis = 60_000)))
            return recorder.stop() to decisions
        }
        val baseline = run(false, false).first
        for (batch in listOf(true, false)) {
            assertEquals(baseline, run(batch, false).first)
            val (snapshot, decisions) = run(batch, true)
            assertEquals(baseline, snapshot)
            assertEquals(listOf(TrailDisposition.RETAINED, TrailDisposition.BELOW_MIN_DISTANCE,
                TrailDisposition.RETAINED, TrailDisposition.TOO_FAST, TrailDisposition.LOW_ACCURACY,
                TrailDisposition.RETAINED, TrailDisposition.RETAINED), decisions.map { it.disposition })
            assertSame(samples[0], decisions[1].previousAccepted)
            assertSame(samples[2], decisions[5].previousAccepted)
            assertTrue(decisions.last().startsSegment)
        }
    }
}
