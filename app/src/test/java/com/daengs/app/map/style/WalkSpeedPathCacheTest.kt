package com.daengs.app.map.style

import com.daengs.app.location.GeoPoint
import java.io.File
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class WalkSpeedPathCacheTest {
    private val policy = WalkStylePolicy.parse(File("src/main/assets/walk-style-v1.json").readText())
    private fun point(index: Int, millis: Long = index * 1000L) = WalkSpeedPoint(
        GeoPoint(37.5 + index * .00001, 127.0), millis)
    private fun check(cache: WalkSpeedPathCache, points: List<WalkSpeedPoint>,
        theme: String = "pink", style: WalkStylePolicy = policy): List<WalkSpeedPart> {
        val snapshot = points.toList()
        val actual = cache.paint(points, style, theme)
        assertEquals(paintWalkSpeedPath(points, style, theme), actual)
        assertEquals(snapshot, points)
        return actual
    }

    @Test fun `five thousand point append paints only a bounded tail and shares stable parts`() {
        val calls = mutableListOf<Int>()
        val cache = WalkSpeedPathCache { calls += it }
        val path = List(5000) { point(it) }
        val before = check(cache, path); val original = before.toList(); calls.clear()
        val after = check(cache, path + point(5000))
        assertEquals(listOf(4, 2, 2), calls)
        assertEquals(5, calls.sumOf { (it - 1).coerceAtLeast(0) })
        assertSame(before.first(), after.first())
        assertEquals(original, before)
    }

    @Test fun `empty one point and batch appends match full painting`() {
        val cache = WalkSpeedPathCache()
        for (size in listOf(0, 1, 2, 3, 12, 13, 21, 51)) check(cache, List(size) { point(it) })
    }

    @Test fun `previous terminal edge color changes when a new fast edge is appended`() {
        val cache = WalkSpeedPathCache()
        val path = listOf(point(0), point(1), point(2))
        val before = check(cache, path)
        val after = check(cache, path + point(5, 3000))
        assertNotEquals(before.last().color, after[before.lastIndex].color)
    }

    @Test fun `zero length edges unknown times and speed limits keep exact boundaries`() {
        val cache = WalkSpeedPathCache()
        val path = listOf(point(0, 0), point(0, 1000), point(1, 1000), point(2, 1100),
            point(3, 2100), point(3, 3100), point(4, 20000), point(60, 21000),
            point(61, 20000), point(62, 22000), point(62, 23000), point(63, 24000))
        for (size in 0..path.size) check(cache, path.take(size))
    }

    @Test fun `equal copied paths reuse output and do no painting`() {
        val calls = mutableListOf<Int>(); val cache = WalkSpeedPathCache { calls += it }
        val path = List(50) { point(it) }; val result = check(cache, path); calls.clear()
        assertSame(result, check(cache, path.map { it.copy() }))
        assertTrue(calls.isEmpty())
    }

    @Test fun `prefix trimming replacement and timestamp correction reset before further appends`() {
        val cache = WalkSpeedPathCache(); var path = List(40) { point(it) }
        check(cache, path)
        path = path.drop(10) + point(40); check(cache, path)
        path = path + point(41); check(cache, path)
        path = path.mapIndexed { index, p -> if (index == 10) p.copy(capturedAtMillis = p.capturedAtMillis + 999) else p }
        check(cache, path); check(cache, path + point(42))
        path = List(20) { point(it + 100) }; check(cache, path)
        check(cache, path + point(120)); check(cache, emptyList()); check(cache, path)
    }

    @Test fun `theme and policy invalidation then append matches reference`() {
        val cache = WalkSpeedPathCache(); val path = List(20) { point(it) }
        check(cache, path)
        for (theme in policy.themes) {
            check(cache, path, theme.id)
            check(cache, path + point(20), theme.id)
        }
        val changed = policy.copy(speedMax = .5, unknownColor = 0xff123456.toInt())
        check(cache, path, "blue", changed)
        check(cache, path + point(20), "blue", changed)
    }

    @Test fun `deterministic mixed paths and append batches match every theme`() {
        val random = Random(431)
        var time = 0L; var latitude = 37.5; var longitude = 127.0
        val path = List(180) {
            time += listOf(0L, 100L, 500L, 1000L, 2000L, 11000L)[random.nextInt(6)]
            latitude += random.nextInt(0, 15) * .000002
            longitude += random.nextInt(-4, 5) * .000002
            WalkSpeedPoint(GeoPoint(latitude, longitude), time)
        }
        for (theme in policy.themes) {
            val cache = WalkSpeedPathCache()
            for (size in 0..path.size step 3) check(cache, path.take(size), theme.id)
            check(cache, path.drop(30), theme.id)
            check(cache, path.drop(30) + point(500, time + 1000), theme.id)
        }
    }
}
