package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.*
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import org.junit.Assert.*
import org.junit.Test

class DiaryMapNavigationTest {
    private fun point(x: Double) = GeoPoint(37.5, 127.0 + x / 88_000)
    private fun record() = readCompletedRoute(RecordedSession("overview", startedAtMillis = 0, endedAtMillis = 300_000),
        (0..17).map { i -> val p = point(if (i < 9) i * 4.0 else 1_000 + (i - 9) * 4.0)
            RecordedFix(i, if (i < 9) 0 else 1, if (i < 9) 10_000 + i * 2_000L else 200_000 + (i - 9) * 2_000L,
                p.latitude, p.longitude, 1f, false) })
    private fun scene(id: String, point: GeoPoint?) = DiaryScene(id, "overview", 20_000, id, "", point, "")

    @Test fun `walking view includes distant valid restart while whole record also includes distant photos`() {
        val detail = record(); val review = CompletedRouteReview(detail)
        val scenes = listOf(scene("far", point(20_000.0)), scene("no-location", null))
        val walking = diaryWalkingBounds(detail, review, scenes)
        val whole = diaryWholeRecordBounds(detail, review, scenes)
        assertEquals(2, detail.route.segments.size)
        assertEquals(detail.route.bounds.maxOf { it.longitude }, walking.last().longitude, 0.0)
        assertTrue(walking.last().longitude > point(1_000.0).longitude)
        assertEquals(point(20_000.0).longitude, whole.last().longitude, 0.0)
        val noisyAnchor = detail.copy(summary = detail.summary.copy(anchor = point(-50_000.0)))
        assertEquals(whole, diaryWholeRecordBounds(noisyAnchor, CompletedRouteReview(noisyAnchor), scenes))
        assertEquals(detail.summary.distanceMeters, review.summary.distanceMeters, 0.0)
    }

    @Test fun `map selection gestures and late data never replace an explicit camera request`() {
        val state = DiaryMapNavigation()
        state.initialize(listOf(point(0.0), point(40.0)))
        state.fit(listOf(point(0.0), point(20_000.0)), DiaryMapView.WHOLE)
        val whole = state.camera
        state.gesture(); state.locate(point(40.0), fromMap = true)
        state.initialize(listOf(point(0.0), point(30_000.0)))
        assertEquals(whole, state.camera)
        assertEquals(DiaryMapView.CUSTOM, state.view)
        state.locate(point(40.0), minZoom = 18.0)
        assertEquals(whole.revision + 1, state.camera.revision)
        val selected = state.camera
        state.locate(null); state.fit(emptyList())
        assertEquals(selected, state.camera)
        state.fit(listOf(point(0.0), point(40.0)), DiaryMapView.WALKING)
        assertNull(state.camera.center); assertFalse(state.camera.expandedContext)
    }

    @Test fun `visibility uses exposed badges and all locations of a unique scene`() {
        fun p(x: Int, y: Int) = GeoPoint(x / 100.0, y / 100.0)
        val query = MapVisibilityQuery("r1", listOf(
            MapLocationTarget("multiple", listOf(p(10, 10), p(80, 70))),
            MapLocationTarget("under-controls", listOf(p(10, 30))),
            MapLocationTarget("under-sheet", listOf(p(80, 120))),
            MapLocationTarget("partially-exposed", listOf(p(70, 90))),
            MapLocationTarget("off-map", listOf(p(150, 60)))), 20, 50, 40)
        fun project(p: GeoPoint) = MapScreenPoint((p.latitude * 100).toFloat(), (p.longitude * 100).toFloat())
        assertEquals(setOf("multiple", "partially-exposed"), visibleMapLocationIds(query, 100, 100, 1f, project = ::project))
        assertNull(visibleMapLocationIds(query, 0, 100, 1f, project = ::project))
        assertNull(visibleMapLocationIds(query.copy(bottomOcclusionPx = 100), 100, 100, 1f, project = ::project))
        // A shared ordinal badge may be wider than a single number; partial exposure still counts.
        val wide = MapVisibilityQuery("wide", listOf(MapLocationTarget("1-2-3", listOf(p(-25, 60)))), 0)
        assertEquals(setOf("1-2-3"), visibleMapLocationIds(wide, 100, 100, 1f,
            badgeSize = { MapScreenSize(80f, 37f) }, project = ::project))
    }

    @Test fun `shared pins count each scene once and unlocated scenes are never offscreen`() {
        val scenes = listOf(scene("one", point(0.0)), scene("two", point(0.0)),
            scene("two", point(2_000.0)), scene("unlocated", null))
        val targets = diaryVisibilityTargets(scenes)
        assertEquals(listOf(point(0.0), point(2_000.0)), targets.single { it.id == "two" }.points)
        val query = MapVisibilityQuery("r", targets, 0)
        assertEquals(listOf("one"), diaryOffscreenScenes(scenes, MapVisibilityResult(query, setOf("two"))).map { it.id })
        assertTrue(diaryOffscreenScenes(scenes, MapVisibilityResult(query, null)).isEmpty())
        assertEquals(2, diaryOffscreenScenes(scenes, MapVisibilityResult(query, emptySet())).size)
    }
}
