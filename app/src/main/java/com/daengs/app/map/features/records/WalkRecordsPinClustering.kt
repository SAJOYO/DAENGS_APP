package com.daengs.app.map.features.records

import kotlin.math.*

/** Coordinates are projected screen dp, not geographic distance or inferred road membership. */
data class RecordPinScreenPoint(val id: String, val x: Double, val y: Double) {
    fun distance(other: RecordPinScreenPoint) = hypot(x - other.x, y - other.y)
}
data class RecordPinScreenGroup(val points: List<RecordPinScreenPoint>, val anchor: RecordPinScreenPoint)

/** Complete-link bound prevents A-near-B-near-C from merging an entire street. */
fun clusterRecordPins(points: List<RecordPinScreenPoint>, maximumDiameter: Double = 44.0): List<RecordPinScreenGroup> {
    require(maximumDiameter > 0 && maximumDiameter.isFinite())
    val groups = mutableListOf<MutableList<RecordPinScreenPoint>>()
    val buckets = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    fun cell(p: RecordPinScreenPoint) = floor(p.x / maximumDiameter).toInt() to floor(p.y / maximumDiameter).toInt()
    points.filter { it.x.isFinite() && it.y.isFinite() }.sortedWith(compareBy<RecordPinScreenPoint> { it.y }.thenBy { it.x }.thenBy { it.id }).forEach { point ->
        val (x, y) = cell(point)
        val candidates = (-1..1).flatMap { dx -> (-1..1).flatMap { dy -> buckets[x + dx to y + dy].orEmpty() } }
        val nearest = candidates.filter { index -> groups[index].all { it.distance(point) <= maximumDiameter } }
            .minByOrNull { index -> groups[index].first().distance(point) }
        if (nearest == null) {
            buckets.getOrPut(x to y) { mutableListOf() }.add(groups.size)
            groups.add(mutableListOf(point))
        } else groups[nearest].add(point)
    }
    return groups.map { group ->
        val x = group.sumOf { it.x } / group.size; val y = group.sumOf { it.y } / group.size
        RecordPinScreenGroup(group.toList(), group.minBy { hypot(it.x - x, it.y - y) })
    }
}

/** Small displacement keeps badges readable; provider draws a leader to the real anchor. */
fun placeRecordPinBadges(groups: List<RecordPinScreenGroup>): List<RecordPinScreenPoint> {
    val placed = mutableListOf<RecordPinScreenPoint>()
    return groups.map { group ->
        val anchor = group.anchor
        val candidates = sequence {
            yield(anchor)
            for (radius in 12..120 step 12) for (step in 0..7) {
                val angle = step * PI / 4
                yield(anchor.copy(x = anchor.x + cos(angle) * radius, y = anchor.y + sin(angle) * radius))
            }
        }
        (candidates.firstOrNull { p -> placed.all { it.distance(p) >= 44.0 } } ?: anchor).also(placed::add)
    }
}
