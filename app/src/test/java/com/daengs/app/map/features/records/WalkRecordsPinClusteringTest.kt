package com.daengs.app.map.features.records

import org.junit.Assert.*
import org.junit.Test

class WalkRecordsPinClusteringTest {
    @Test fun `adjacent pins never chain the whole street and order does not affect membership`() {
        val points = (0..59).map { RecordPinScreenPoint("$it", 0.0, it * 10.0) }
        val groups = clusterRecordPins(points)
        assertTrue(groups.size >= 12)
        assertEquals(points.map { it.id }.toSet(), groups.flatMap { it.points }.map { it.id }.toSet())
        groups.forEach { g -> assertTrue(g.points.all { a -> g.points.all { a.distance(it) <= 44 } }) }
        assertEquals(groups, clusterRecordPins(points.reversed()))
    }
    @Test fun `parallel paths separate when projected distance grows while exact repeats stay together`() {
        val points = (0..9).flatMap { y -> (0..1).map { lane -> RecordPinScreenPoint("$y-$lane", lane * 25.0, y * 10.0) } }
        assertTrue(clusterRecordPins(points).any { g -> g.points.map { it.x }.distinct().size == 2 })
        assertTrue(clusterRecordPins(points.map { it.copy(x = it.x * 3, y = it.y * 3) }).all { g -> g.points.map { it.x }.distinct().size == 1 })
        val repeats = (0..20).map { RecordPinScreenPoint("$it", 137.0, 46.0) }
        assertEquals(21, clusterRecordPins(repeats).single().points.size)
    }
    @Test fun `badge layout keeps icons apart and anchors remain original points`() {
        val points = (0..19).map { RecordPinScreenPoint("$it", (it % 4) * 30.0, (it / 4) * 31.0) }
        val groups = clusterRecordPins(points, 32.0)
        val placements = placeRecordPinBadges(groups)
        assertTrue(groups.all { it.anchor in it.points })
        placements.forEachIndexed { i, p -> placements.drop(i+1).forEach { assertTrue(p.distance(it) >= 44.0) } }
    }
    @Test fun `invalid projection does not form a marker and widely separated records are not lost`() {
        val points = (0..2000).map { RecordPinScreenPoint("$it", it*100.0, -it*100.0) }
        assertEquals(points.size, clusterRecordPins(points + RecordPinScreenPoint("bad", Double.NaN, 3.0)).size)
    }
}
