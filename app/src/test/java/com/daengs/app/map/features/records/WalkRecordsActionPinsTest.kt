package com.daengs.app.map.features.records

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.pin.ActionPin
import com.daengs.app.walk.records.*
import org.junit.Assert.*
import org.junit.Test

class WalkRecordsActionPinsTest {
    private val point = GeoPoint(37.5, 127.0)
    private fun record(id: String, vararg entries: WalkEntry) = WalkRecord(
        WalkSummary(id, listOf("a", "b"), 0, 900000L, null, 400.0, 900000L, emptyList(), null), entries = entries.toList())
    private fun entry(id: String, session: String = "walk", type: WalkMomentType = WalkMomentType.SNIFFING,
        dog: String? = "a") = WalkEntry(id, session, type, 1000L, point = point, locationCapturedAtMillis = 1000L, petId = dog)

    @Test fun `all action kinds share a coordinate marker without merging their records`() {
        val records = listOf(record("walk", entry("sniff"), entry("bark", type = WalkMomentType.BARKING),
            entry("note", type = WalkMomentType.NOTE)), record("other", entry("poop", "other", WalkMomentType.EXCRETION)))
        val selected = WalkRecordsSelection(WalkRecordsQuery(), records)
        val pins = walkRecordsActionPins(selected)
        assertEquals(3, pins.records.size)
        assertEquals(1, pins.groups.size)
        val marker = pins.groups.single().marker(pins.records.last().key)
        assertEquals("3", marker.sequenceLabel)
        assertEquals(RECORD_ACTION_TYPES, marker.behaviors)
        assertTrue(marker.selected)
        assertTrue(marker.aboveRouteEndpoints)
        assertEquals(pins.groups.single().id, walkRecordsActionPins(WalkRecordsSelection(selected.query, records.reversed())).groups.single().id)
    }

    @Test fun `display kinds and hiding never reduce the baseline walks or remove list evidence`() {
        val selected = WalkRecordsSelection(WalkRecordsQuery(), listOf(record("walk", entry("sniff")),
            record("other", entry("bark", "other", WalkMomentType.BARKING)), record("empty")))
        assertEquals(1, walkRecordsActionPins(selected, setOf(WalkMomentType.BARKING)).records.size)
        val hidden = walkRecordsActionPins(selected, hiddenIds = setOf("walk"))
        assertEquals(2, hidden.records.size)
        assertEquals(1, hidden.visibleCount)
        assertEquals(listOf("walk", "other", "empty"), selected.sessionIds)
        val off = walkRecordsActionPins(selected, enabled = false)
        assertEquals(hidden.records, off.records)
        assertTrue(off.groups.isEmpty())
    }

    @Test fun `selected dog applies to the action owner and unspecified dog only appears in all dogs`() {
        val walk = record("walk", entry("a"), entry("b", dog = "b"), entry("none", dog = null))
        assertEquals(listOf("a"), walkRecordsActionPins(WalkRecordsSelection(WalkRecordsQuery(dogIds = setOf("a")), listOf(walk))).records.map { it.entry.id })
        assertEquals(3, walkRecordsActionPins(WalkRecordsSelection(WalkRecordsQuery(), listOf(walk))).records.size)
    }

    @Test fun `explicit estimated and unlocated pins own display over the legacy GPS point`() {
        val estimate = entry("estimate").copy(pin = ActionPin("""{"state":"resolved","method":"estimated","point":{"lat":37.6,"lng":127.1}}"""))
        val unlocated = entry("unlocated").copy(pin = ActionPin("""{"state":"unlocated","method":"none","point":null}"""))
        val missing = entry("missing").copy(point = null, locationCapturedAtMillis = null)
        val pins = walkRecordsActionPins(WalkRecordsSelection(WalkRecordsQuery(), listOf(record("walk", estimate, unlocated, missing))))
        assertEquals(GeoPoint(37.6, 127.1), pins.groups.single().point)
        assertEquals(2, pins.unlocatedCount)
        assertEquals(point, pins.records.first { it.entry.id == "estimate" }.entry.point)
        assertEquals("추정 위치", pins.records.first { it.entry.id == "estimate" }.locationLabel)
        assertEquals(3, pins.records.size)
    }
}
