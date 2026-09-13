package com.daengs.app.walk.routeexplorer

import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Test

class MeasurementSceneReviewTest {
    @Test fun `source-less event uses a unique timed interval and keeps the source range explicit`() {
        val detail = measuredSceneDetail()
        val scene = measuredScene(detail, 1).copy(source = null, atMillis = 13_000)
        val focus = CompletedRouteReview(detail).recordSceneFocus(scene)
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals(1, focus.binding!!.sourceStart!!.clientSeq)
        assertEquals(2, focus.binding.sourceEnd!!.clientSeq)
        assertNull(focus.binding.locationSource)
        assertEquals(13_000L, focus.binding.locationAtMillis)
        val invalid = measuredScene(detail).let { it.copy(source = it.source!!.copy(
            observation = it.source.observation!!.copy(point = com.daengs.app.location.GeoPoint(Double.NaN, 127.0)))) }
        assertTrue(CompletedRouteReview(detail).recordSceneFocus(invalid).paths.isEmpty())
    }

    @Test fun `source identity selects the correct visit when time position and elapsed clock all repeat`() {
        val detail = measuredSceneDetail(secondVisit = true)
        val scene = measuredScene(detail, 7)
        val focus = CompletedRouteReview(detail).recordSceneFocus(scene)
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals("epoch-1", focus.binding!!.locationSource!!.sourceEpoch)
        assertEquals("section-1", focus.binding.sectionId)
        assertEquals(7, focus.binding.observationSeq)
        assertEquals(detail.measurement!!.id, focus.key!!.measurementId)
        assertEquals(SceneRouteRelation.AMBIGUOUS, CompletedRouteReview(detail).recordSceneFocus(scene.copy(source = null)).relation)
        assertEquals(123.0, detail.summary.distanceMeters, 0.0)
    }

    @Test fun `an omitted observation binds to its final interval without inventing a display vertex`() {
        val detail = measuredSceneDetail(retained = listOf(0, 2, 4))
        val scene = measuredScene(detail, 1)
        val focus = CompletedRouteReview(detail).recordSceneFocus(scene)
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals(0, focus.binding!!.sourceStart!!.clientSeq)
        assertEquals(2, focus.binding.sourceEnd!!.clientSeq)
        assertEquals(1, focus.binding.locationSource!!.clientSeq)
        assertEquals(scene.point, focus.point)
        assertEquals(detail.route.bounds, focus.paths.single())
        val excluded = detail.copy(measurement = detail.measurement!!.copy(
            usableSources = detail.measurement.usableSources.filterNot { it.clientSeq == 1 }.toSet()))
        assertTrue(CompletedRouteReview(excluded).recordSceneFocus(scene).paths.isEmpty())
    }

    @Test fun `clock correction uses anchored source time and never enables wall clock interpolation`() {
        val base = measuredSceneDetail()
        // Global wall boundaries can be reversed after a correction; source addresses still resolve.
        val detail = base.copy(summary = base.summary.copy(startedAtMillis = 80_000, endedAtMillis = 5_000))
        assertEquals(SceneRouteRelation.CONNECTED, CompletedRouteReview(detail).recordSceneFocus(measuredScene(detail)).relation)
        val altered = base.copy(route = base.route.copy(segments = base.route.segments.map { segment ->
            segment.copy(points = segment.points.map { it.copy(elapsedRealtimeNanos = 3) })
        }))
        assertTrue(CompletedRouteReview(altered).recordSceneFocus(measuredScene(base)).paths.isEmpty())
    }

    @Test fun `control event and first observation keep separate addresses and timestamps`() {
        val detail = measuredSceneDetail(); val first = measuredScene(detail, 0)
        val scene = first.copy(atMillis = 0, source = first.source!!.copy(id = "start", atMillis = 0))
        val focus = CompletedRouteReview(detail).recordSceneFocus(scene)
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals(0L, focus.binding!!.eventAtMillis)
        assertEquals(10_000L, focus.binding.locationAtMillis)
        assertEquals("epoch_start", focus.binding.eventSource!!.controlKind)
        assertEquals(0, focus.binding.locationSource!!.clientSeq)
        val borrowed = measuredScene(detail, 2).let { it.copy(atMillis = 0, source = it.source!!.copy(id = "start", atMillis = 0)) }
        assertTrue(CompletedRouteReview(detail).recordSceneFocus(borrowed).paths.isEmpty())
    }

    @Test fun `text edit changes scene revision while event revision and measured path remain stable`() {
        val detail = measuredSceneDetail(); val original = measuredScene(detail)
        val review = CompletedRouteReview(detail); val first = review.recordSceneFocus(original)
        val edited = review.recordSceneFocus(original.copy(title = "고친 제목", body = "고친 본문"))
        assertEquals(first.key!!.eventRevision, edited.key!!.eventRevision)
        assertNotEquals(first.key.sceneRevision, edited.key.sceneRevision)
        assertEquals(first.paths, edited.paths)
        val moved = review.recordSceneFocus(measuredScene(detail, 3))
        assertNotEquals(first.key.eventRevision, moved.key!!.eventRevision)
        val again = CompletedRouteReview(detail.copy()).recordSceneFocus(original.copy())
        assertEquals(first, again)
        val next = detail.copy(measurement = detail.measurement!!.copy(id = "measurement-b"))
        assertNotEquals(first.key, CompletedRouteReview(next).recordSceneFocus(original).key)
    }

    @Test fun `missing changed or foreign entry cannot borrow a current route`() {
        val detail = measuredSceneDetail(); val source = measuredScene(detail)
        val scene = source.copy(entryId = "note")
        val entry = WalkEntry("note", "measured", WalkMomentType.NOTE, scene.atMillis, scene.point, scene.atMillis, note = "기록")
        val review = CompletedRouteReview(detail)
        assertEquals(SceneRouteRelation.CONNECTED, review.recordSceneFocus(scene, entry).relation)
        assertTrue(review.recordSceneFocus(scene).paths.isEmpty())
        assertTrue(review.recordSceneFocus(scene, entry.copy(sessionId = "other")).paths.isEmpty())
        val earlier = entry.copy(recordedAtMillis = scene.atMillis + 1_000)
        val oldLocation = scene.copy(atMillis = earlier.recordedAtMillis, source = scene.source!!.copy(atMillis = earlier.recordedAtMillis))
        val focus = review.recordSceneFocus(oldLocation, earlier)
        assertEquals(SceneRouteRelation.EARLIER_LOCATION, focus.relation)
        assertTrue(focus.paths.isEmpty())
        assertNotEquals(focus.binding!!.eventAtMillis, focus.binding.locationAtMillis)
    }
}
