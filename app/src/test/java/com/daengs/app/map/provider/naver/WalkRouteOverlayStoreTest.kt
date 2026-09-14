package com.daengs.app.map.provider.naver

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.trail.TrailLayerState
import com.daengs.app.map.style.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class WalkRouteOverlayStoreTest {
    private val policy = WalkStylePolicy.parse(File("src/main/assets/walk-style-v1.json").readText())
    private val probe = WalkRouteProbe()
    private class Handle(var state: WalkRouteRenderState) : WalkRouteHandle {
        var updates = 0; var removals = 0
        override fun update(previous: WalkRouteRenderState, next: WalkRouteRenderState) {
            assertEquals(state, previous); state = next; updates++
        }
        override fun remove() { removals++ }
    }
    private val handles = mutableMapOf<WalkRouteKey, Handle>()
    private val store = WalkRouteOverlayStore({ state -> Handle(state).also { handles[state.key] = it } }, probe)
    private fun point(index: Int, segment: Int = 0) = WalkSpeedPoint(
        GeoPoint(37.5 + index * .00001, 127.0 + segment * .001), index * 1000L)
    private fun trail(vararg counts: Int) = TrailLayerState(speedPaths = counts.mapIndexed { segment, count ->
        List(count) { point(it, segment) }
    })
    private fun sync(trail: TrailLayerState, completed: CompletedRouteLayerState = CompletedRouteLayerState(),
        theme: String = "pink", dim: Boolean = false, style: WalkStylePolicy = policy) =
        store.sync(walkRouteSources(trail, completed), style, theme, dim)
    private fun key(index: Int, done: Boolean = false, speed: Boolean = true) = WalkRouteKey(done, index, speed)

    @Test fun `stroke only changes native appearance without preparing geometry again`() {
        val sources = walkRouteSources(trail(5), CompletedRouteLayerState())
        store.sync(sources, policy, "pink", false)
        val handle = handles.getValue(key(0))
        val parts = handle.state.parts
        probe.reset()
        val stroke = WalkRouteStroke(widthPx = 16, outlineWidthPx = 3)
        store.sync(sources, policy, "pink", false, stroke)
        assertSame(handle, handles.getValue(key(0)))
        assertSame(parts, handle.state.parts)
        assertEquals(stroke, handle.state.stroke)
        assertEquals(0, probe.prepared)
        assertEquals(0, probe.created)
        assertEquals(1, probe.updated)
    }

    @Test fun `ten thousand points append updates only the growing segment`() {
        val initial = trail(2500, 2500, 2500, 2500)
        sync(initial)
        val original = handles.toMap()
        val fixedParts = original.getValue(key(0)).state.parts
        probe.reset()
        repeat(20) { step ->
            // The upstream presenter may rebuild equal nested lists on every snapshot.
            val copy = initial.speedPaths.dropLast(1).map { it.toList() } +
                listOf(initial.speedPaths.last() + List(step + 1) { point(2500 + it, 3) })
            sync(TrailLayerState(speedPaths = copy))
        }
        assertEquals(0, probe.created); assertEquals(0, probe.removed)
        assertEquals(20, probe.updated); assertEquals(20, probe.prepared)
        assertEquals(160, probe.pointsPrepared)
        assertEquals(100, probe.edgesPainted)
        original.forEach { (key, handle) -> assertSame(handle, handles[key]) }
        assertSame(fixedParts, handles.getValue(key(0)).state.parts)
        assertEquals(0, original.getValue(key(0)).updates)
    }

    @Test fun `same snapshot and equal copies do no native or preparation work`() {
        val initial = trail(4, 5)
        val sources = walkRouteSources(initial, CompletedRouteLayerState())
        store.sync(sources, policy, "pink", false); probe.reset()
        repeat(60) { store.sync(sources, policy, "pink", false) }
        sync(initial.copy(speedPaths = initial.speedPaths.map { it.map { point -> point.copy() } }))
        assertEquals(0, probe.prepared); assertEquals(0, probe.updated)
        assertEquals(0, probe.created); assertEquals(0, probe.removed)
    }

    @Test fun `pause and GPS gaps add a separate line only when drawable`() {
        val initial = trail(4)
        sync(initial); val first = handles.getValue(key(0)); probe.reset()
        sync(trail(4, 1)); assertEquals(0, probe.created)
        sync(trail(4, 2)); assertEquals(1, probe.created)
        assertSame(first, handles[key(0)]); assertEquals(0, first.updates)
        assertEquals(listOf(point(0, 1).point, point(1, 1).point),
            listOf(handles.getValue(key(1)).state.parts.first().points.first(),
                handles.getValue(key(1)).state.parts.last().points.last()))
        assertFalse(handles.getValue(key(1)).state.parts.flatMap { it.points }.contains(point(3).point))
    }

    @Test fun `live and completed ordinal zero have independent lifetimes and dimming`() {
        val live = trail(4)
        val done = CompletedRouteLayerState(speedPaths = trail(5).speedPaths)
        sync(live, done); val liveHandle = handles.getValue(key(0)); val doneHandle = handles.getValue(key(0, true))
        val parts = doneHandle.state.parts; probe.reset()
        sync(live, done, dim = true)
        assertEquals(0, probe.prepared); assertEquals(1, probe.updated)
        assertFalse(liveHandle.state.dimmed); assertTrue(doneHandle.state.dimmed)
        assertSame(parts, doneHandle.state.parts)
        sync(live, done, dim = false); assertFalse(doneHandle.state.dimmed)
        sync(live); assertEquals(1, doneHandle.removals); assertEquals(0, liveHandle.removals)
    }

    @Test fun `theme and policy changes repaint existing handles with exact reference colors`() {
        val input = trail(5, 6); sync(input); val first = handles.getValue(key(0)); probe.reset()
        sync(input, theme = "blue")
        assertSame(first, handles[key(0)]); assertEquals(2, probe.prepared)
        assertEquals(paintWalkSpeedPath(input.speedPaths[0], policy, "blue"), first.state.parts)
        val changed = policy.copy(speedMax = .5)
        sync(input, theme = "blue", style = changed)
        assertEquals(paintWalkSpeedPath(input.speedPaths[0], changed, "blue"), first.state.parts)
        assertEquals(0, probe.created); assertEquals(0, probe.removed)
    }

    @Test fun `replacing or shortening a segment keeps no stale coordinates`() {
        sync(trail(5, 5)); val handle = handles.getValue(key(0)); val second = handles.getValue(key(1))
        val changed = trail(3).copy(speedPaths = listOf(List(3) { point(it, 8) }))
        sync(changed)
        assertSame(handle, handles[key(0)])
        assertEquals(paintWalkSpeedPath(changed.speedPaths.single(), policy, "pink"), handle.state.parts)
        assertEquals(1, second.removals)
    }

    @Test fun `empty and one point segments release their native handle and can return`() {
        sync(trail(3)); val old = handles.getValue(key(0))
        sync(trail(1)); assertEquals(1, old.removals)
        sync(trail(1)); assertEquals(1, old.removals)
        sync(trail(2)); assertNotSame(old, handles.getValue(key(0)))
        sync(TrailLayerState()); assertEquals(1, handles.getValue(key(0)).removals)
    }

    @Test fun `plain fallback uses unknown speed and changes overlay type cleanly`() {
        val plain = TrailLayerState(paths = listOf(listOf(point(0).point, point(1).point)))
        sync(plain); val handle = handles.getValue(key(0, speed = false))
        assertEquals(policy.unknownColor, handle.state.parts.single().color)
        sync(plain.copy(speedPaths = trail(1).speedPaths))
        assertEquals(1, handle.removals); assertEquals(1, handles.size) // no speed line until second point
        sync(plain.copy(speedPaths = trail(2).speedPaths))
        assertEquals(2, handles.size)
        sync(plain); assertEquals(1, handles.getValue(key(0)).removals)
    }

    @Test fun `zero distance and invalid timestamps keep reference display behavior`() {
        val invalid = listOf(point(0), point(1).copy(capturedAtMillis = 100))
        sync(TrailLayerState(speedPaths = listOf(invalid)))
        assertTrue(handles.getValue(key(0)).state.parts.all { it.color == policy.unknownColor })
        sync(TrailLayerState(speedPaths = listOf(listOf(point(0), point(0).copy(capturedAtMillis = 1000)))))
        assertEquals(1, handles.getValue(key(0)).removals)
    }

    @Test fun `clear on map disposal releases once and invalidates source cache`() {
        val sources = walkRouteSources(trail(3, 3), CompletedRouteLayerState())
        store.sync(sources, policy, "pink", false); val old = handles.values.toList()
        store.clear(); store.clear()
        assertTrue(old.all { it.removals == 1 }); probe.reset()
        store.sync(sources, policy, "pink", false)
        assertEquals(2, probe.created); assertEquals(2, probe.prepared)
        assertTrue(old.none { it in handles.values })
    }

    @Test fun `appending recomputes previous edge interpolation exactly without altering input`() {
        val initial = listOf(point(0), point(1), point(2))
        sync(TrailLayerState(speedPaths = listOf(initial)))
        val appended = initial + point(5).copy(capturedAtMillis = 3000)
        val copy = appended.toList()
        sync(TrailLayerState(speedPaths = listOf(appended)))
        assertEquals(paintWalkSpeedPath(appended, policy, "pink"), handles.getValue(key(0)).state.parts)
        assertEquals(copy, appended); assertEquals(3, initial.size)
    }
}
