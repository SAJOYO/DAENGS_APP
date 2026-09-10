package com.daengs.app.map.layers.traces

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.diary.SpatialDiaryCellId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.math.PI
import kotlin.math.pow

class TraceBrushTest {
    @Test
    fun `repeated passes in one walk preserve the sheet and do not add opacity`() {
        val route = (0..12).map { SpatialDiaryCellId(it, 0) }
        val once = WalkTraceSheet("walk", cells = route.toSet())
        val repeated = once.copy(cells = (route + route.reversed() + route).toSet())
        val first = TraceBrush.mask(once)
        val second = TraceBrush.mask(repeated)
        val firstPixels = pixels(first.tiles)
        val secondPixels = pixels(second.tiles)

        assertEquals(firstPixels.keys, secondPixels.keys)
        firstPixels.forEach { (key, alpha) -> assertEquals(alpha, secondPixels.getValue(key)) }
        assertEquals(route.toSet(), once.cells)
        assertTrue(TraceBrush.compose(listOf(second)).all { tile -> tile.alpha.all { it <= 0.14f } })
    }

    @Test
    fun `only distinct walks add source over alpha and hiding restores the original`() {
        val original = solidTile()
        val sheets = (1..3).map { WalkTraceMask("walk-$it", listOf(original)) }
        val three = TraceBrush.compose(sheets)
        val two = TraceBrush.compose(sheets.take(2))
        val one = TraceBrush.compose(sheets.take(1))

        assertEquals((1 - 0.86.pow(3)).toFloat(), three.single().alpha.first(), 0.000001f)
        assertEquals(0.2604f, two.single().alpha.first(), 0.000001f)
        assertEquals(0.14f, one.single().alpha.first(), 0.000001f)
        val many = TraceBrush.compose((1..20).map { WalkTraceMask("many-$it", listOf(original)) })
        assertTrue(many.single().alpha.all { it == 0.40f })
        assertTrue(original.alpha.all { it == 1f })
        assertArrayEquals(three.single().alpha, TraceBrush.compose(sheets.reversed()).single().alpha, 0f)
        val unrelated = WalkTraceMask("other-place", listOf(solidTile(tileX = 1_000)))
        assertArrayEquals(one.single().alpha, TraceBrush.compose(sheets.take(1) + unrelated).first().alpha, 0f)
        assertThrows(IllegalArgumentException::class.java) { TraceBrush.compose(sheets + sheets.first()) }
    }

    @Test
    fun `single islands and one cell wide trails survive with smooth edges and a filled interior`() {
        val island = TraceBrush.mask(WalkTraceSheet("island", cells = setOf(SpatialDiaryCellId(0, 0))))
        assertTrue(island.tiles.any { tile -> tile.alpha.any { it > 0.1f } })
        assertTrue(island.tiles.any { tile -> tile.alpha.any { it > 0f && it < 1f } })
        val tiny = TraceBrush.mask(WalkTraceSheet("tiny", radiusU = 2.0,
            cells = setOf(SpatialDiaryCellId(0, 0))), TraceBrushPolicy(insetU = 1.0))
        assertTrue(tiny.tiles.any { tile -> tile.alpha.any { it > 0f } })
        val thin = TraceBrush.mask(WalkTraceSheet("thin", cells = (0..20).map { SpatialDiaryCellId(it, 0) }.toSet()))
        assertEquals(1, componentCount(pixels(thin.tiles).filterValues { it > 0 }.keys))
        val filled = TraceBrush.mask(WalkTraceSheet("filled", cells = (-4..4).flatMap { q ->
            (-4..4).map { r -> SpatialDiaryCellId(q, r) }
        }.toSet()))
        assertEquals(1f, pixels(filled.tiles).getValue(0 to 0), 0f)
    }

    @Test
    fun `nearby disconnected support does not acquire a bridge from added blur tails`() {
        val a = SpatialDiaryCellId(0, 0)
        val b = SpatialDiaryCellId(1, 1)
        val together = TraceBrush.mask(WalkTraceSheet("two-islands", cells = setOf(a, b)))

        assertEquals(2, componentCount(pixels(together.tiles).filterValues { it > 0 }.keys))
    }

    @Test
    fun `raster coverage is independent of tile partition on both sides of world origin`() {
        val cells = (-30..30).map { SpatialDiaryCellId(it, it / 3) }.toSet()
        val sheet = WalkTraceSheet("boundary", cells = cells)
        val smallTiles = TraceBrush.mask(sheet, TraceBrushPolicy(tileSize = 64)).tiles
        val largeTiles = TraceBrush.mask(sheet, TraceBrushPolicy(tileSize = 128)).tiles
        val small = pixels(smallTiles)
        val large = pixels(largeTiles)

        assertEquals(small.filterValues { it > 0 }.keys, large.filterValues { it > 0 }.keys)
        small.filterValues { it > 0 }.forEach { (key, value) ->
            assertEquals("pixel $key", value, large.getValue(key), 0.000001f)
        }
        val pigment = cells.associateWith { if (it.q < 0) TraceOverlapPalette.TEAL_RGB else TraceOverlapPalette.ORANGE_RGB }
        val smallColors = colors(smallTiles, pigment)
        val largeColors = colors(largeTiles, pigment)
        small.filterValues { it > 0 }.keys.forEach { key ->
            assertEquals("colour $key", smallColors.getValue(key), largeColors.getValue(key))
        }
        assertTrue(smallColors.values.any { it != 0 && it != TraceOverlapPalette.TEAL_RGB && it != TraceOverlapPalette.ORANGE_RGB })
    }

    @Test
    fun `colour interpolation preserves the pigment at soft edges without reading or changing alpha`() {
        val cell = SpatialDiaryCellId(0, 0)
        val tiles = TraceBrush.mask(WalkTraceSheet("island", cells = setOf(cell))).tiles
        val sourcePixels = pixels(tiles)
        val pigment = mapOf(cell to TraceOverlapPalette.TEAL_RGB)
        val painted = colors(tiles, pigment)

        sourcePixels.filterValues { it > 0 }.keys.forEach { key ->
            assertEquals(TraceOverlapPalette.TEAL_RGB, painted.getValue(key))
        }
        assertEquals(sourcePixels, pixels(tiles))
        val first = tiles.first()
        assertArrayEquals(TraceBrush.colors(first, pigment, 8.0),
            TraceBrush.colors(first.copy(alpha = FloatArray(first.alpha.size)), pigment, 8.0))
    }

    @Test
    fun `distant walks use sparse world tiles instead of a giant bounding rectangle`() {
        val mask = TraceBrush.mask(WalkTraceSheet("distant", cells = setOf(
            SpatialDiaryCellId(0, 0), SpatialDiaryCellId(100_000, 0),
        )))

        assertTrue(mask.tiles.size <= 8)
        assertTrue(mask.tiles.maxOf { it.tileX } - mask.tiles.minOf { it.tileX } > 5_000)
        assertEquals(2, componentCount(pixels(mask.tiles).filterValues { it > 0 }.keys))
    }

    @Test
    fun `northern pixels and tile bounds agree with web mercator placement`() {
        val mask = TraceBrush.mask(WalkTraceSheet("north", cells = setOf(SpatialDiaryCellId(1, 16))))
        val tile = mask.tiles.single { it.tileX == 0 && it.tileY == 0 }

        assertTrue(tile.alpha[31 * tile.size + 62] > 0)
        assertEquals(0f, tile.alpha[95 * tile.size + 62], 0f)
        assertEquals(0.0, tile.southWest.latitude, 0.0000001)
        assertEquals(0.0, tile.southWest.longitude, 0.0000001)
        assertEquals(256 / 6_378_137.0 * 180 / PI, tile.northEast.longitude, 0.0000001)
        assertTrue(tile.northEast.latitude > tile.southWest.latitude)
    }

    @Test
    fun `invalid grids and resource limits fail explicitly before making a partial layer`() {
        val cells = (0..2).map { SpatialDiaryCellId(it, 0) }.toSet()
        assertThrows(IllegalArgumentException::class.java) {
            TraceBrush.mask(WalkTraceSheet("too-many", cells = cells), TraceBrushPolicy(maxCells = 2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceBrush.mask(WalkTraceSheet("too-wide", cells = cells), TraceBrushPolicy(maxTiles = 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceBrush.mask(WalkTraceSheet("too-scattered", cells = cells), TraceBrushPolicy(maxRasterTiles = 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceBrush.mask(WalkTraceSheet("bad-coordinate", cells = setOf(SpatialDiaryCellId(Int.MAX_VALUE, 0))))
        }
        assertThrows(IllegalArgumentException::class.java) { TraceBrushPolicy(pixelU = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) {
            TraceBrush.compose(listOf(WalkTraceMask("a", listOf(solidTile())),
                WalkTraceMask("b", listOf(solidTile().copy(pixelU = 1.0)))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceBrush.compose(listOf(WalkTraceMask("a", listOf(solidTile(), solidTile()))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceBrush.colors(solidTile(), cells.associateWith { 0xFFFFFF }, 8.0,
                TraceBrushPolicy(tileSize = 32, maxCells = 2))
        }
        assertThrows(IllegalArgumentException::class.java) { solidTile().copy(rgb = intArrayOf(0)) }
    }

    @Test
    fun `rendering and composition cooperate with cancellation without mutating cached input`() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            TraceBrush.mask(WalkTraceSheet("cancelled", cells = (0..30).map {
                SpatialDiaryCellId(it, 0)
            }.toSet())) { if (++checks == 20) throw CancellationException() }
        }
        assertEquals(20, checks)
        val source = solidTile()
        checks = 0
        assertThrows(CancellationException::class.java) {
            TraceBrush.compose(listOf(WalkTraceMask("cancelled", listOf(source)))) {
                if (++checks == 3) throw CancellationException()
            }
        }
        assertTrue(source.alpha.all { it == 1f })
        checks = 0
        assertThrows(CancellationException::class.java) {
            TraceBrush.colors(source, mapOf(SpatialDiaryCellId(0, 0) to TraceOverlapPalette.TEAL_RGB), 8.0) {
                if (++checks == 4) throw CancellationException()
            }
        }
        assertEquals(4, checks)
        assertTrue(source.alpha.all { it == 1f })
    }

    private fun solidTile(tileX: Int = 0) = TraceRasterTile(tileX, 0, 32, 2.0,
        FloatArray(32 * 32) { 1f }, GeoPoint(0.0, 0.0), GeoPoint(0.001, 0.001))

    private fun pixels(tiles: List<TraceRasterTile>): Map<Pair<Int, Int>, Float> = buildMap {
        tiles.forEach { tile ->
            tile.alpha.forEachIndexed { index, alpha ->
                val x = tile.tileX * tile.size + index % tile.size
                val y = (tile.tileY + 1) * tile.size - 1 - index / tile.size
                put(x to y, alpha)
            }
        }
    }

    private fun colors(tiles: List<TraceRasterTile>, cells: Map<SpatialDiaryCellId, Int>): Map<Pair<Int, Int>, Int> = buildMap {
        tiles.forEach { tile ->
            TraceBrush.colors(tile, cells, 8.0).forEachIndexed { index, rgb ->
                val x = tile.tileX * tile.size + index % tile.size
                val y = (tile.tileY + 1) * tile.size - 1 - index / tile.size
                put(x to y, rgb)
            }
        }
    }

    private fun componentCount(points: Set<Pair<Int, Int>>): Int {
        val remaining = points.toMutableSet()
        var count = 0
        while (remaining.isNotEmpty()) {
            val queue = ArrayDeque<Pair<Int, Int>>()
            queue.add(remaining.first().also { remaining.remove(it) })
            count++
            while (queue.isNotEmpty()) {
                val (x, y) = queue.removeFirst()
                for (dx in -1..1) for (dy in -1..1) {
                    val next = x + dx to y + dy
                    if (remaining.remove(next)) queue.add(next)
                }
            }
        }
        return count
    }
}
