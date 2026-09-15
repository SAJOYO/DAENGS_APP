package com.daengs.app.map.features.records

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.*
import com.daengs.app.walk.records.*
import org.junit.Assert.*
import org.junit.Test

class WalkRecordsDetachedPinsTest {
    private val point = GeoPoint(37.5, 127.0)
    private val nearby = GeoPoint(37.50001, 127.0)
    private fun walk(id: String, type: WalkMomentType, at: GeoPoint? = point) = WalkRecord(
        WalkSummary(id, listOf("dog"), 0, 900000L, null, 400.0, 900000L, emptyList(), null),
        entries = listOf(WalkEntry("same-entry-id", id, type, 1000L, point = at, petId = "dog")))
    private fun pins(vararg walks: WalkRecord) = walkRecordsActionPins(WalkRecordsSelection(WalkRecordsQuery(), walks.toList()))

    @Test fun `selection preserves mixed actions and count at the original coordinate`() {
        val pins = pins(walk("one", WalkMomentType.SNIFFING), walk("two", WalkMomentType.BARKING))
        val original = pins.detachedMarkers(null, emptyList()).single()
        val selected = pins.detachedMarkers(pins.records.first().key, emptyList()).single()
        assertEquals(setOf(WalkMomentType.SNIFFING, WalkMomentType.BARKING), selected.behaviors)
        assertEquals(2, selected.recordPin!!.count)
        assertFalse(selected.recordPin.background)
        assertTrue(selected.selected)
        assertEquals(original.copy(selected = true), selected)
        assertEquals(point, selected.point)
        assertEquals(2, pins.records.map { it.key }.distinct().size)
    }

    @Test fun `native cluster resolves original sessions and coordinates without duplication`() {
        val pins = pins(walk("one", WalkMomentType.SNIFFING), walk("two", WalkMomentType.EXCRETION, nearby),
            walk("unlocated", WalkMomentType.BARKING, null))
        val ids = pins.groups.map { it.id }
        val group = requireNotNull(pins.resolveGroup(ids + ids.first() + "stale-id"))
        assertEquals(setOf("one", "two"), group.records.map { it.walk.summary.sessionId }.toSet())
        assertEquals(setOf(point, nearby), group.records.map { it.point }.toSet())
        assertEquals(2, group.records.size)
        assertTrue(group.records.all { record -> pins.records.any { it === record } })
        assertEquals(1, pins.unlocatedCount)
        assertNull(pins.resolveGroup(listOf("stale-id")))
    }

    @Test fun `action filter does not leak unmatched background into detached pins`() {
        val selection = WalkRecordsSelection(WalkRecordsQuery(), listOf(
            walk("one", WalkMomentType.SNIFFING), walk("two", WalkMomentType.BARKING)))
        val pins = walkRecordsActionPins(selection, setOf(WalkMomentType.SNIFFING))
        assertEquals(1, pins.backgroundGroups.size)
        val marker = pins.detachedMarkers(null, emptyList()).single()
        assertEquals(setOf(WalkMomentType.SNIFFING), marker.behaviors)
        assertEquals(1, marker.recordPin!!.count)
        assertEquals(listOf("one"), pins.resolveGroup(listOf(marker.id))!!.records.map { it.walk.summary.sessionId })
        assertEquals(2, selection.records.size)
    }

    @Test fun `route obstacles retain segment gaps and omit hidden or missing routes`() {
        val segments = listOf(listOf(point, nearby), listOf(GeoPoint(37.6,127.1), GeoPoint(37.60001,127.1)))
        val original = walk("one", WalkMomentType.SNIFFING)
        val routed = original.copy(summary = original.summary.copy(segments = segments.map { path ->
            path.mapIndexed { i, p -> LocationSample(p, i * 1000L) }
        }))
        val hidden = routed.copy(summary = routed.summary.copy(sessionId = "hidden"),
            entries = routed.entries.map { it.copy(sessionId = "hidden") })
        assertEquals(segments, recordPinAvoidancePaths(listOf(routed, hidden, original), setOf("hidden")))
        assertEquals(segments, routed.summary.segments.map { it.map { sample -> sample.point } })
        assertTrue(recordPinAvoidancePaths(listOf(original), emptySet()).isEmpty())
    }
}
