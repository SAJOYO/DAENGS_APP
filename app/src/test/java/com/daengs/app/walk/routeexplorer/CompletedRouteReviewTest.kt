package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import org.junit.Assert.*
import org.junit.Test

class CompletedRouteReviewTest {
    @Test fun `retained monotonic time identifies the correct source after wall time and position repeat`() {
        val detail = reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L),
            listOf(0.0 to 10_000L, -10.0 to 20_000L))
        val fixes = detail.observations.map { it.copy(elapsedRealtimeNanos = (it.clientSeq + 1) * 1_000_000_000L) }
        val summary = detail.summary.copy(measurementVersion = "motion-v1",
            segments = detail.summary.segments.mapIndexed { segment, samples -> samples.mapIndexed { index, sample ->
                sample.copy(elapsedRealtimeNanos = fixes[segment * 2 + index].elapsedRealtimeNanos)
            } })
        val clocked = detail.copy(summary = summary, route = summary.toSessionRoute(), observations = fixes)
        val anchor = StoryboardObservation(2, 1, 10_000, reviewPoint(0.0))
        val focus = CompletedRouteReview(clocked).sceneFocus(reviewScene(10_000, 0.0).copy(source = source(anchor)))
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals(listOf(reviewPoint(0.0), reviewPoint(-10.0)), focus.paths.single())
    }

    @Test fun `legacy reader may omit the monotonic clock only when the raw address is still unique`() {
        val detail = reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L))
        val clocked = detail.copy(observations = detail.observations.map { it.copy(elapsedRealtimeNanos = it.atMillis * 1_000_000) })
        val scene = reviewScene(10_000, 0.0).copy(source = source(StoryboardObservation(0, 0, 10_000, reviewPoint(0.0))))
        assertEquals(SceneRouteRelation.CONNECTED, CompletedRouteReview(clocked).sceneFocus(scene).relation)
        val duplicate = clocked.copy(observations = clocked.observations + clocked.observations.first().copy(clientSeq = 9))
        assertEquals(SceneRouteRelation.AMBIGUOUS, CompletedRouteReview(duplicate).sceneFocus(scene).relation)
        val measured = clocked.copy(summary = clocked.summary.copy(measurementVersion = "motion-v1"))
        assertEquals(SceneRouteRelation.NO_ROUTE, CompletedRouteReview(measured).sceneFocus(scene).relation)
    }

    @Test fun `local entry last known position and late pin changes cannot claim a current passage`() {
        val review = CompletedRouteReview(reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L)))
        val scene = reviewScene(15_000, 5.0).copy(entryId = "note")
        val entry = WalkEntry("note", "s", WalkMomentType.NOTE, 15_000, reviewPoint(5.0), 10_000, note = "메모")
        assertEquals(SceneRouteRelation.NO_ROUTE, review.sceneFocus(scene).relation)
        assertEquals(SceneRouteRelation.EARLIER_LOCATION, review.sceneFocus(scene, entry).relation)
        val current = entry.copy(locationCapturedAtMillis = 15_000)
        assertEquals(SceneRouteRelation.CONNECTED, review.sceneFocus(scene, current).relation)
        assertEquals(SceneRouteRelation.NO_ROUTE, review.sceneFocus(scene, current.copy(point = reviewPoint(200.0))).relation)
        val pin = com.daengs.app.walk.pin.ActionPin("""{"method":"last_known","state":"resolved"}""")
        assertEquals(SceneRouteRelation.EARLIER_LOCATION, review.sceneFocus(scene, current.copy(pin = pin)).relation)
    }

    @Test fun `an exact timestamp is still ambiguous if another visit straddles that wall time`() {
        val review = CompletedRouteReview(reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L),
            listOf(-5.0 to 5_000L, 5.0 to 15_000L)))
        assertEquals(SceneRouteRelation.AMBIGUOUS, review.sceneFocus(reviewScene(10_000, 0.0)).relation)
    }

    @Test fun `separate far sections retain their own geometry without a connector or recomputed metric`() {
        val detail = reviewDetail(listOf(0.0 to 0L, 10.0 to 10_000L),
            listOf(2_000.0 to 40_000L, 2_010.0 to 50_000L))
        val review = CompletedRouteReview(detail)
        assertEquals(2, review.sections.size)
        assertEquals(detail.route.segments.map { it.points.map(WalkRoutePoint::point) }, review.sections.map { it.path })
        assertEquals(777.0, review.summary.distanceMeters, 0.0)
        assertEquals(0L, review.summary.startedAtMillis)
        assertEquals(70_000L, review.summary.endedAtMillis)
        assertTrue(review.sceneFocus(reviewScene(30_000, 1_000.0)).paths.isEmpty())
    }

    @Test fun `same location on the return visit highlights only that visit direction`() {
        val detail = reviewDetail(listOf(-10.0 to 10_000L, 0.0 to 20_000L, 10.0 to 30_000L),
            listOf(10.0 to 40_000L, 0.0 to 50_000L, -10.0 to 60_000L))
        val review = CompletedRouteReview(detail)
        val outward = review.sceneFocus(reviewScene(20_000, 0.0))
        val returning = review.sceneFocus(reviewScene(50_000, 0.0))
        assertEquals(SceneRouteRelation.CONNECTED, returning.relation)
        assertEquals(outward.paths.single().asReversed(), returning.paths.single())
        assertEquals(detail.route.segments[1].points.map { it.point }, returning.paths.single())
    }

    @Test fun `a photo between nearby timed points uses event time without snapping to another road`() {
        val review = CompletedRouteReview(reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L)))
        val focus = review.sceneFocus(reviewScene(15_000, 5.0))
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals(reviewPoint(5.0).longitude, focus.point!!.longitude, 1e-9)
        assertEquals(SceneRouteRelation.NO_ROUTE, review.sceneFocus(reviewScene(15_000, 200.0)).relation)
    }

    @Test fun `before first fix after last fix gaps and unknown positions never create a path`() {
        val review = CompletedRouteReview(reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L),
            listOf(10.0 to 50_000L, 20.0 to 60_000L)))
        for (time in listOf(5_000L, 35_000L, 65_000L)) {
            val focus = review.sceneFocus(reviewScene(time, 10.0))
            assertEquals(SceneRouteRelation.NO_ROUTE, focus.relation)
            assertTrue(focus.paths.isEmpty()); assertNull(focus.point)
        }
        assertEquals(SceneRouteRelation.UNLOCATED, review.sceneFocus(reviewScene(15_000, 5.0).copy(point = null)).relation)
    }

    @Test fun `an excluded raw scene does not borrow an accepted edge even when time and space are nearby`() {
        val detail = reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L))
        val excluded = RecordedFix(9, 0, 15_000, reviewPoint(5.0).latitude, reviewPoint(5.0).longitude, 3f, false)
        val review = CompletedRouteReview(detail.copy(observations = detail.observations + excluded))
        val anchor = StoryboardObservation(9, 0, 15_000, reviewPoint(5.0))
        val scene = reviewScene(15_000, 5.0).copy(source = source(anchor))
        assertEquals(SceneRouteRelation.NO_ROUTE, review.sceneFocus(scene).relation)
        assertTrue(review.sceneFocus(scene).paths.isEmpty())
    }

    @Test fun `observation identity must agree with session time chain coordinates and raw source`() {
        val detail = reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L))
        val review = CompletedRouteReview(detail)
        val anchor = StoryboardObservation(0, 0, 10_000, reviewPoint(0.0))
        val scene = reviewScene(10_000, 0.0).copy(source = source(anchor))
        assertEquals(SceneRouteRelation.CONNECTED, review.sceneFocus(scene).relation)
        val changed = listOf(scene.copy(sessionId = "another"), scene.copy(atMillis = 11_000),
            scene.copy(source = source(anchor.copy(chainIndex = 1))),
            scene.copy(source = source(anchor.copy(clientSeq = 100))),
            scene.copy(source = source(anchor.copy(point = reviewPoint(500.0)))))
        changed.forEach { assertTrue(review.sceneFocus(it).paths.isEmpty()) }
        val mocked = detail.copy(observations = detail.observations.map { it.copy(isMock = true) })
        assertTrue(CompletedRouteReview(mocked).sceneFocus(scene).paths.isEmpty())
    }

    @Test fun `location evidence survives the display model and last known is not current passage`() {
        val review = CompletedRouteReview(reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L)))
        val scene = reviewScene(15_000, 5.0)
        for (method in listOf("observed", "last_known")) {
            val content = DiarySceneContent("", "note", locationLabel = "", locationMethod = method,
                locationAtMillis = 10_000, positionState = "resolved")
            assertEquals(SceneRouteRelation.EARLIER_LOCATION, review.sceneFocus(scene.copy(content = content)).relation)
        }
        assertTrue(review.sceneFocus(scene.copy(content = DiarySceneContent("", "note", locationLabel = "",
            positionState = "provisional"))).paths.isEmpty())
    }

    @Test fun `ambiguous wall time cannot silently select the first visit but monotonic observation can`() {
        val detail = reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L),
            listOf(10.0 to 10_000L, 0.0 to 20_000L))
        val review = CompletedRouteReview(detail)
        assertEquals(SceneRouteRelation.AMBIGUOUS, review.sceneFocus(reviewScene(10_000, 0.0)).relation)
        val anchor = StoryboardObservation(0, 0, 10_000, reviewPoint(0.0))
        assertEquals(SceneRouteRelation.CONNECTED, review.sceneFocus(reviewScene(10_000, 0.0).copy(source = source(anchor))).relation)
    }

    @Test fun `single points and long time gaps are not highlighted as a traversal`() {
        val one = CompletedRouteReview(reviewDetail(listOf(0.0 to 10_000L)))
        assertTrue(one.sections.isEmpty())
        assertTrue(one.sceneFocus(reviewScene(10_000, 0.0)).paths.isEmpty())
        val gap = CompletedRouteReview(reviewDetail(listOf(0.0 to 10_000L, 10.0 to 50_000L)))
        assertTrue(gap.sceneFocus(reviewScene(30_000, 5.0)).paths.isEmpty())
        assertTrue(gap.sceneFocus(reviewScene(10_000, 0.0)).paths.isEmpty())
    }

    private fun source(anchor: StoryboardObservation) = StoryboardScene("observed", anchor.atMillis,
        "장면", "", "", "revision-1", observation = anchor)
}

internal fun reviewPoint(x: Double) = GeoPoint(37.5, 127.0 + x / 88_200)
internal fun reviewScene(at: Long, x: Double) = DiaryScene("s/$at", "s", at, "장면", "남긴 기록", reviewPoint(x), "")
internal fun reviewDetail(vararg segments: List<Pair<Double, Long>>): WalkSessionDetail {
    var seq = 0
    val fixes = segments.flatMapIndexed { chain, samples -> samples.map { (x, at) ->
        RecordedFix(seq++, chain, at, reviewPoint(x).latitude, reviewPoint(x).longitude, 3f, false)
    } }
    val summary = WalkSummary("s", emptyList(), 0, 70_000, null, 777.0, 70_000,
        segments.map { points -> points.map { (x, at) -> LocationSample(reviewPoint(x), at, accuracyMeters = 3f) } },
        fixes.firstOrNull()?.let { GeoPoint(it.lat, it.lng) }, activeElapsedAtMillis = fixes.associate { it.atMillis to it.atMillis })
    return WalkSessionDetail(summary, summary.toSessionRoute(), emptyList(), observations = fixes)
}
