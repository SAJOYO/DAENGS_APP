package com.daengs.app.map.provider.naver

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.territory.*
import com.daengs.app.territory.TerritoryProximityRange
import org.junit.Assert.*
import org.junit.Test

class TerritoryOverlayStoreTest {
    private class Handle(var state: TerritoryRenderState) : TerritoryOverlayHandle {
        var updates = 0
        var removed = 0
        var frame = TerritoryFeedbackFrame()
        override fun update(previous: TerritoryRenderState, next: TerritoryRenderState) {
            assertEquals(state, previous)
            state = next; updates++
        }
        override fun frame(frame: TerritoryFeedbackFrame, marked: Boolean) { this.frame = frame }
        override fun remove() { removed++ }
    }
    private val handles = mutableMapOf<String, Handle>()
    private val probe = TerritoryOverlayProbe()
    private val store = TerritoryOverlayStore({ state -> Handle(state).also { handles[state.id] = it } }, probe)
    private fun site(id: String) = TerritorySiteMarkerState(id, GeoPoint(37.5, 127.0), occupancyKnown = true)
    private fun sync(sites: List<TerritorySiteMarkerState>) = store.sync(sites.map { it.renderState() })

    @Test fun `500 markers survive selection and only old and new selection change`() {
        val sites = List(500) { site("$it") }
        sync(sites)
        val originals = handles.toMap()
        probe.reset()
        sync(sites.map { it.copy(selected = it.id == "0", radiusMeters = 20.0.takeIf { _ -> it.id == "0" }) })
        assertEquals(1, probe.updated)
        probe.reset()
        sync(sites.map { it.copy(selected = it.id == "1", radiusMeters = 20.0.takeIf { _ -> it.id == "1" }) })
        assertEquals(2, probe.updated)
        assertEquals(0, probe.created)
        assertEquals(0, probe.removed)
        originals.forEach { (id, handle) -> assertSame(handle, handles[id]) }
        assertNull(handles.getValue("0").state.radius)
        assertEquals(20.0, handles.getValue("1").state.radius!!, 0.0)
    }

    @Test fun `ready unused label and proximity changes with no ring do not update SDK handle`() {
        val original = site("0")
        sync(listOf(original)); probe.reset()
        sync(listOf(original.copy(ready = true, label = "new hidden label", proximity = TerritoryProximityRange.IN_RANGE)))
        assertEquals(0, probe.updated)
        assertEquals(0, probe.created)
        assertTrue(probe.syncNanos.isEmpty())
    }

    @Test fun `membership and certification update one retained object and unknown becomes neutral`() {
        val other = site("0").copy(occupancy = TerritoryMarkerOccupancy.UNVERIFIED)
        sync(listOf(other, site("1")))
        val retained = handles.getValue("0")
        probe.reset()
        sync(listOf(other.copy(isMine = true, occupancy = TerritoryMarkerOccupancy.VERIFIED), site("1")))
        assertSame(retained, handles["0"])
        assertEquals(TerritoryPoleStyle.MINE_VERIFIED, retained.state.style)
        assertEquals("내 인증", retained.state.caption)
        assertEquals(1, probe.updated)
        sync(listOf(other.copy(occupancyKnown = false, isMine = true), site("1")))
        assertEquals(TerritoryPoleStyle.NEUTRAL, retained.state.style)
        assertEquals("확인 전", retained.state.caption)
        assertEquals(.55f, retained.state.alpha)
    }

    @Test fun `reorder keeps all objects and add remove and clear have exact lifetimes`() {
        sync(listOf(site("a"), site("b")))
        val a = handles.getValue("a"); val b = handles.getValue("b")
        probe.reset()
        sync(listOf(site("b"), site("a")))
        assertEquals(0, probe.created + probe.removed + probe.updated)
        sync(listOf(site("b"), site("c")))
        assertEquals(1, probe.created); assertEquals(1, probe.removed)
        assertEquals(1, a.removed); assertSame(b, handles["b"])
        store.clear(); store.clear()
        assertEquals(1, b.removed); assertEquals(1, handles.getValue("c").removed)
        sync(listOf(site("b")))
        assertNotSame(b, handles["b"])
    }

    @Test fun `range move change and blank tap update retained object and remove auxiliary displays`() {
        val selected = site("a").copy(selected = true, radiusMeters = 20.0,
            feedback = TerritoryFeedback(1, "a", TerritoryFeedbackKind.VERIFIED, 0))
        sync(listOf(selected))
        val handle = handles.getValue("a")
        assertTrue(handle.state.paw)
        val moved = selected.copy(point = GeoPoint(37.51, 127.01), radiusMeters = 10.0,
            proximity = TerritoryProximityRange.IN_RANGE)
        sync(listOf(moved))
        assertEquals(moved.point, handle.state.point)
        assertEquals("범위 안", handle.state.range!!.label)
        sync(listOf(moved.copy(selected = false, radiusMeters = null, feedback = null)))
        assertFalse(handle.state.paw)
        assertNull(handle.state.range)
        assertTrue(handle.state.hideCollisions)
        assertSame(handle, handles["a"])
    }

    @Test fun `feedback cancellation resets retained marker and fresh id does not recreate it`() {
        val feedback = TerritoryFeedback(1, "a", TerritoryFeedbackKind.VERIFIED, 0)
        val a = site("a").copy(selected = true, feedback = feedback)
        sync(listOf(a, site("b")))
        val retained = handles.getValue("a")
        store.frame(feedback, .5f)
        assertTrue(retained.frame.markerScale > 1f)
        assertEquals(1f, handles.getValue("b").frame.markerScale)
        probe.reset()
        sync(listOf(a.copy(feedback = feedback.copy(id = 2, startedNanos = 10)), site("b")))
        assertEquals(0, probe.created + probe.updated + probe.removed)
        sync(listOf(a.copy(selected = false, feedback = null), site("b").copy(selected = true)))
        store.frame(null, 1f)
        assertEquals(TerritoryFeedbackFrame(), retained.frame)
        assertSame(retained, handles["a"])
    }
}
