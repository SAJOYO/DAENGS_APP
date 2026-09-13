package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.*
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.routeexplorer.*
import com.daengs.app.walk.diary.DiaryWalk
import org.junit.Assert.*
import org.junit.Test

class DiaryMapNavigationTest {
    @Test fun `measured observed scenes respect map camera and ambiguous photos keep their independent location`() {
        for (repeated in listOf(false, true)) {
            val detail = measuredObservedDetail(repeatedWallTimes = repeated)
            val original = measuredScene(detail, 9)
            val scene = if (repeated) original.copy(source = null, photo = WalkPhoto("observed-photo",
                detail.summary.sessionId, original.atMillis, original.point!!, java.io.File("synthetic-photo"))) else original
            val route = PreparedDiaryRoute(detail); val focus = route.review.recordSceneFocus(scene)
            assertEquals(if (repeated) SceneRouteRelation.AMBIGUOUS else SceneRouteRelation.OBSERVED_EXCLUDED, focus.relation)
            assertTrue(focus.paths.isEmpty())
            assertEquals(repeated, focus.observedParts.isEmpty())
            val read = WalkDiaryReadView(route, DiaryWalk(detail.summary, listOf(scene), ""), mapOf(scene.id to focus))
            val navigation = DiaryMapNavigation(); navigation.initialize(detail.route.bounds)
            val initial = navigation.camera
            assertTrue(navigation.selectScene(read, scene, fromMap = true))
            assertEquals(initial, navigation.camera)
            assertTrue(navigation.selectScene(read, scene))
            assertEquals(scene.point, navigation.camera.center)
            val selected = navigation.camera
            assertFalse(navigation.selectScene(read, scene.copy(body = "stale")))
            assertEquals(selected, navigation.camera)
        }
    }

    private fun point(x: Double) = GeoPoint(37.5, 127.0 + x / 88_000)
    private fun record() = readCompletedRoute(RecordedSession("overview", startedAtMillis = 0, endedAtMillis = 300_000),
        (0..17).map { i -> val p = point(if (i < 9) i * 4.0 else 1_000 + (i - 9) * 4.0)
            RecordedFix(i, if (i < 9) 0 else 1, if (i < 9) 10_000 + i * 2_000L else 200_000 + (i - 9) * 2_000L,
                p.latitude, p.longitude, 1f, false) })
    private fun scene(id: String, point: GeoPoint?) = DiaryScene(id, "overview", 20_000, id, "", point, "")

    @Test fun `current photo remains navigable when its walking visit is ambiguous`() {
        val detail = measuredSceneDetail(secondVisit = true); val original = measuredScene(detail, 7)
        val photo = WalkPhoto("photo", detail.summary.sessionId, original.atMillis, original.point!!, java.io.File("synthetic-photo"))
        val scene = original.copy(source = null, photo = photo)
        val route = PreparedDiaryRoute(detail); val focus = route.review.recordSceneFocus(scene)
        assertEquals(SceneRouteRelation.AMBIGUOUS, focus.relation)
        assertTrue(focus.paths.isEmpty())
        val read = WalkDiaryReadView(route, DiaryWalk(detail.summary, listOf(scene), ""), mapOf(scene.id to focus))
        val navigation = DiaryMapNavigation(); navigation.initialize(detail.route.bounds)
        val initial = navigation.camera
        assertTrue(navigation.selectScene(read, scene, fromMap = true))
        assertEquals(initial, navigation.camera)
        assertTrue(navigation.selectScene(read, scene))
        assertEquals(photo.point, navigation.camera.center)
        assertNull(navigation.camera.minZoom)
        val selected = navigation.camera
        assertFalse(navigation.selectScene(read.copy(sceneFocus = emptyMap()), scene))
        assertFalse(navigation.selectScene(read, scene.copy(body = "옛 본문")))
        assertEquals(selected, navigation.camera)
        listOf(scene.copy(photo = photo.copy(sessionId = "other")),
            scene.copy(photo = photo.copy(capturedAtMillis = photo.capturedAtMillis + 1)),
            scene.copy(photo = photo.copy(point = point(20_000.0)))).forEach { mismatched ->
            val rejected = route.review.recordSceneFocus(mismatched)
            assertNull(rejected.point)
            assertTrue(rejected.paths.isEmpty())
        }
    }

    @Test fun `measured scene navigation rejects pending and stale bindings and map clicks keep camera`() {
        val detail = measuredSceneDetail(); val scene = measuredScene(detail)
        val route = PreparedDiaryRoute(detail)
        val read = WalkDiaryReadView(route, DiaryWalk(detail.summary, listOf(scene), ""),
            mapOf(scene.id to route.review.recordSceneFocus(scene)))
        val navigation = DiaryMapNavigation()
        navigation.initialize(detail.route.bounds)
        val initial = navigation.camera
        assertFalse(navigation.selectScene(read.copy(sceneFocus = emptyMap()), scene))
        assertFalse(navigation.selectScene(read, scene.copy(body = "이전 본문")))
        assertTrue(navigation.selectScene(read, scene, fromMap = true))
        assertEquals(initial, navigation.camera)
        assertTrue(navigation.selectScene(read, scene))
        assertEquals(scene.point, navigation.camera.center)
        assertEquals(SCENE_ROUTE_MIN_ZOOM, navigation.camera.minZoom)
        val selected = navigation.camera
        val next = read.copy(route = PreparedDiaryRoute(measuredSceneDetail("measurement-b")))
        assertFalse(navigation.selectScene(next, scene))
        navigation.initialize(next.route.detail.route.bounds)
        assertEquals(selected, navigation.camera)
        val ambiguous = measuredSceneDetail(secondVisit = true)
        val noSource = measuredScene(ambiguous).copy(source = null)
        val review = PreparedDiaryRoute(ambiguous)
        val unbound = WalkDiaryReadView(review, DiaryWalk(ambiguous.summary, listOf(noSource), ""),
            mapOf(noSource.id to review.review.recordSceneFocus(noSource)))
        assertTrue(navigation.selectScene(unbound, noSource)) // Still readable, without a borrowed position.
        assertEquals(selected, navigation.camera)
    }

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

    @Test fun `settings below the toolbar only hide badges fully covered at that offset`() {
        val targets = listOf("above" to 20.0, "covered" to 90.0, "below" to 150.0, "partial" to 115.0)
            .map { (id, y) -> MapLocationTarget(id, listOf(GeoPoint(90.0, y))) }
        val query = MapVisibilityQuery("r", targets, bottomOcclusionPx = 0,
            topRightCoverPx = 40, topRightCoverTopPx = 70)
        assertEquals(setOf("above", "below", "partial"), visibleMapLocationIds(query, 100, 200, 1f,
            badgeSize = { MapScreenSize(10f, 10f) }, project = { MapScreenPoint(it.latitude.toFloat(), it.longitude.toFloat()) }))
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
