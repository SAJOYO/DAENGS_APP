package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos

internal fun explorerPoint(x: Double, y: Double = 0.0) = GeoPoint(37.5 + y / 111_195,
    127.0 + x / (111_195 * cos(Math.toRadians(37.5))))
internal fun explorerRoute(vararg paths: List<Pair<Double, Double>>): WalkSessionRoute {
    var sequence = 0
    return WalkSessionRoute(paths.mapIndexed { segment, path ->
        WalkRouteSegment(segment, path.mapIndexed { index, (x, y) ->
            val time = sequence++ * 1_000L
            WalkRoutePoint(explorerPoint(x, y), 10_000 + time, 3f, time, 0.0, null, segment, index)
        })
    })
}
internal fun straightExplorerPath(y: Double = 0.0) = (-10..10).map { it * 5.0 to y }

class RouteExplorerIndexTest {
    @Test fun `fast playback saturates at the end without overflowing or running backward`() {
        assertEquals(Long.MAX_VALUE, advanceRoutePlayback(Long.MAX_VALUE - 5, Long.MAX_VALUE,
            Long.MAX_VALUE, RoutePlaybackSpeed.SIXTEEN))
        assertEquals(900L, advanceRoutePlayback(900, -10, 1_000, RoutePlaybackSpeed.FOUR))
        assertEquals(1_000L, advanceRoutePlayback(900, 100, 1_000, RoutePlaybackSpeed.SIXTEEN))
        assertEquals(0L, advanceRoutePlayback(100, 100, -1, RoutePlaybackSpeed.TWO))
    }

    @Test fun `many nearby observations count as one passage and a turnaround as two`() {
        val line = straightExplorerPath()
        assertEquals(1, RouteExplorerIndex(explorerRoute(line)).passagesAt(explorerPoint(0.0)).passes.size)
        val result = RouteExplorerIndex(explorerRoute(line + line.asReversed().drop(1))).passagesAt(explorerPoint(0.0))
        assertEquals(2, result.passes.size)
        assertNotEquals(result.passes[0].reverse, result.passes[1].reverse)
        assertTrue(result.passes[0].endedAtMillis < result.passes[1].startedAtMillis)
    }
    @Test fun `leaving the corridor and returning in the same direction creates another pass`() {
        val line = straightExplorerPath()
        val loop = line + listOf(50.0 to 30.0, -50.0 to 30.0) + line
        val result = RouteExplorerIndex(explorerRoute(loop)).passagesAt(explorerPoint(0.0))
        assertEquals(2, result.passes.size)
        assertEquals(result.passes[0].reverse, result.passes[1].reverse)
    }
    @Test fun `crossing diagonals are not two visits to the same road`() {
        val first = (-10..10).map { it * 5.0 to it * 5.0 }
        val second = (-10..10).map { it * 5.0 to -it * 5.0 }
        assertEquals(1, RouteExplorerIndex(explorerRoute(first, second)).passagesAt(explorerPoint(0.0)).passes.size)
    }
    @Test fun `nearby parallel tracks are uncertain and stationary drift is not a pass`() {
        val parallel = RouteExplorerIndex(explorerRoute(straightExplorerPath(), straightExplorerPath(3.0)))
            .passagesAt(explorerPoint(0.0))
        assertTrue(parallel.uncertain)
        assertTrue(parallel.passes.isEmpty())
        val jitter = List(30) { if (it % 2 == 0) -2.0 to 0.0 else 2.0 to 0.0 }
        assertTrue(RouteExplorerIndex(explorerRoute(jitter)).passagesAt(explorerPoint(0.0)).passes.isEmpty())
    }
    @Test fun `pause split at a location does not manufacture two crossings`() {
        val route = explorerRoute((-10..-1).map { it * 5.0 to 0.0 }, (1..10).map { it * 5.0 to 0.0 })
        assertTrue(RouteExplorerIndex(route).passagesAt(explorerPoint(0.0)).passes.size <= 1)
    }
    @Test fun `replay interpolates short observed edges but never segments or long gaps`() {
        val route = explorerRoute(listOf(0.0 to 0.0, 10.0 to 0.0), listOf(20.0 to 0.0, 30.0 to 0.0))
        val index = RouteExplorerIndex(route)
        assertEquals(explorerPoint(0.0), index.frameAt(0).point)
        assertEquals(explorerPoint(5.0).longitude, index.frameAt(500).point!!.longitude, .0000001)
        assertEquals(10_500L, index.frameAt(500).recordedAtMillis)
        assertTrue(index.frameAt(1_500).inGap)
        assertNull(index.frameAt(1_500).point)
        assertEquals(explorerPoint(20.0), index.frameAt(2_000).point)
        val longGap = route.segments.first().let { segment ->
            segment.copy(points = listOf(segment.points.first(), segment.points.last().copy(
                activeElapsedMillis = 30_000, capturedAtMillis = 40_000)))
        }
        assertTrue(RouteExplorerIndex(WalkSessionRoute(listOf(longGap))).frameAt(10_000).inGap)
        assertTrue(RouteExplorerIndex(WalkSessionRoute(emptyList())).frameAt(0).inGap)
    }
    @Test fun `replay clock stops at the end and ignores negative time deltas`() {
        assertEquals(1_000L, advanceRoutePlayback(950, 100, 1_000))
        assertEquals(500L, advanceRoutePlayback(500, -100, 1_000))
        assertEquals(0L, advanceRoutePlayback(0, 500, 0))
    }

    @Test fun `tap distance selects the nearby edge without enlarging the passage corridor`() {
        val index = RouteExplorerIndex(explorerRoute(straightExplorerPath()))
        val near = index.passagesAt(explorerPoint(0.0, 19.9))
        assertFalse(near.uncertain)
        assertEquals(1, near.passes.size)
        assertEquals(explorerPoint(0.0).latitude, near.anchor.latitude, .00000001)
        val far = index.passagesAt(explorerPoint(0.0, 20.1))
        assertTrue(far.uncertain)
        assertTrue(far.passes.isEmpty())
    }

    @Test fun `passage accuracy accepts twelve meters but rejects invalid fixes without changing replay`() {
        assertEquals(1, RouteExplorerIndex(twoPointRoute(accuracy = 12f)).passagesAt(explorerPoint(0.0)).passes.size)
        for (accuracy in listOf(null, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 12.01f)) {
            val index = RouteExplorerIndex(twoPointRoute(accuracy = accuracy))
            assertTrue("accuracy=$accuracy", index.passagesAt(explorerPoint(0.0)).uncertain)
            // Legacy replay represents recorded positions; passage eligibility must not filter it.
            assertFalse("accuracy=$accuracy", index.frameAt(500).inGap)
        }
    }

    @Test fun `passage sample gap is inclusive and independent of the active replay clock`() {
        val accepted = RouteExplorerIndex(twoPointRoute(wallGap = 15_000, activeGap = 30_000))
        assertEquals(1, accepted.passagesAt(explorerPoint(0.0)).passes.size)
        assertTrue(accepted.frameAt(500).inGap)
        for (gap in listOf(0L, -1L, 15_001L)) {
            val result = RouteExplorerIndex(twoPointRoute(wallGap = gap)).passagesAt(explorerPoint(0.0))
            assertTrue("wall gap=$gap", result.uncertain)
            assertTrue(result.passes.isEmpty())
        }
    }

    @Test fun `replay requires both clocks within fifteen seconds but preserves exact observations`() {
        val valid = RouteExplorerIndex(twoPointRoute(wallGap = 15_000, activeGap = 15_000))
        val halfway = valid.frameAt(7_500)
        assertFalse(halfway.inGap)
        assertEquals(explorerPoint(0.0).longitude, halfway.point!!.longitude, .00000001)
        assertEquals(17_500L, halfway.recordedAtMillis)
        for ((wall, active) in listOf(15_001L to 15_000L, 15_000L to 15_001L, 0L to 15_000L)) {
            val index = RouteExplorerIndex(twoPointRoute(wallGap = wall, activeGap = active))
            assertTrue("wall=$wall active=$active", index.frameAt(7_500).inGap)
            assertNull(index.frameAt(7_500).point)
            assertEquals(explorerPoint(30.0), index.frameAt(active).point)
            assertEquals(10_000L + wall, index.frameAt(active).recordedAtMillis)
        }
    }

    @Test fun `a traversal must cover eighteen meters and reach both sides of the anchor`() {
        fun passes(from: Double, to: Double) = RouteExplorerIndex(
            explorerRoute(listOf(from to 0.0, to to 0.0))).passagesAt(explorerPoint(0.0)).passes.size
        assertEquals(1, passes(-9.1, 9.1))
        assertEquals(0, passes(-8.9, 8.9))
        assertEquals(0, passes(-5.9, 12.5))
    }

    private fun twoPointRoute(wallGap: Long = 1_000, activeGap: Long = 1_000,
        accuracy: Float? = 3f): WalkSessionRoute {
        val segment = explorerRoute(listOf(-30.0 to 0.0, 30.0 to 0.0)).segments.single()
        return WalkSessionRoute(listOf(segment.copy(points = segment.points.mapIndexed { index, point ->
            point.copy(capturedAtMillis = 10_000 + index * wallGap, activeElapsedMillis = index * activeGap,
                accuracyMeters = accuracy)
        })))
    }
}
