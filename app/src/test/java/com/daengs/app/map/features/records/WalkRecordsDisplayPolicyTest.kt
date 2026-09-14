package com.daengs.app.map.features.records

import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.shell.WalkLayerStack
import com.daengs.app.map.style.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WalkRecordsDisplayPolicyTest {
    private val speed = WalkStylePolicy.parse(File("src/main/assets/walk-style-v1.json").readText())
    private val policy = WalkRecordsDisplayPolicy(TraceDisplayPolicy(0x29252B), WalkRouteAppearance(speed, "pink"))

    @Test fun `density and legend use the same distinct walk bands`() {
        val scale = policy.trace.density
        assertEquals(listOf(0f, .04f, .12f, .22f, .22f, .34f, .34f, .34f, .46f, .46f), (0..9).map(scale::alpha))
        assertEquals(listOf("1회", "2회", "3–4회", "5–7회", "8회 이상"), scale.legend().map { it.label })
        assertEquals(scale.bands.map { scale.alpha(it.minimum) }, scale.legend().map { it.opacity })
    }

    @Test fun `density cache key cannot change through caller owned list`() {
        val bands = mutableListOf(TraceDensityBand(1, .04f), TraceDensityBand(2, .12f))
        val scale = TraceDensityScale(bands)
        val cache = mapOf(scale to "cached")
        bands[1] = TraceDensityBand(2, .6f)
        assertEquals(.12f, scale.alpha(2), 0f)
        assertEquals("cached", cache[TraceDensityScale(listOf(TraceDensityBand(1, .04f), TraceDensityBand(2, .12f)))])
        assertTrue(runCatching { (scale.bands as MutableList).clear() }.isFailure)
    }

    @Test fun `invalid scales fail before preparing tiles`() {
        listOf(emptyList(), listOf(TraceDensityBand(2, .1f)),
            listOf(TraceDensityBand(1, Float.NaN)), listOf(TraceDensityBand(1, 1.1f)),
            listOf(TraceDensityBand(1, .2f), TraceDensityBand(2, .1f)),
            listOf(TraceDensityBand(1, .1f), TraceDensityBand(1, .2f))).forEach {
            assertTrue(runCatching { TraceDensityScale(it) }.isFailure)
        }
    }

    @Test fun `records scene carries the resolved speed style and semantic stack even without traces`() {
        val route = CompletedRouteLayerState()
        val plan = composeWalkRecordsMapScene(policy, route = route)
        assertSame(route, plan.scene.completedRoute)
        assertSame(policy.route, plan.scene.walkPresentation!!.route)
        assertEquals(WalkLayerStack.RECORDS, plan.scene.walkPresentation!!.stack)
        assertEquals(policy.trace.density.legend(), plan.traceLegend)
        assertEquals(speed.speedScaleStops("pink"), plan.routeLegend)
    }

    @Test fun `route theme does not affect shadow legend and density does not affect route style`() {
        val baseline = composeWalkRecordsMapScene(policy)
        val recolored = composeWalkRecordsMapScene(policy.copy(route = policy.route.copy(themeId = "blue")))
        assertEquals(baseline.traceLegend, recolored.traceLegend)
        assertNotEquals(baseline.routeLegend, recolored.routeLegend)
        val scaled = composeWalkRecordsMapScene(policy.copy(trace = policy.trace.copy(
            density = TraceDensityScale(listOf(TraceDensityBand(1, .01f), TraceDensityBand(2, .4f))))))
        assertEquals(baseline.routeLegend, scaled.routeLegend)
        assertSame(baseline.scene.walkPresentation!!.route, scaled.scene.walkPresentation!!.route)
    }

    @Test fun `records never silently render missing pigment with the legacy pink fallback`() {
        val tile = com.daengs.app.map.layers.traces.TraceRasterTile(0, 0, 1, 2.0, floatArrayOf(.04f),
            com.daengs.app.location.GeoPoint(37.5, 127.0), com.daengs.app.location.GeoPoint(37.6, 127.1))
        assertTrue(runCatching { composeWalkRecordsMapScene(policy, listOf(tile)) }.isFailure)
        assertEquals(1, composeWalkRecordsMapScene(policy, listOf(tile.copy(rgb = intArrayOf(policy.trace.rgb)))).scene.traceTiles.size)
    }
}
