package com.daengs.app.walk.records

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.map.layers.traces.TraceBrush
import com.daengs.app.ui.theme.WalkTraceShadow
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.daengs.app.map.layers.traces.WalkTraceProvenance
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.diary.SpatialDiaryHexGrid
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor
import kotlin.math.sqrt

class WalkRecordsTracesTest {
    private val traceStyle = com.daengs.app.map.features.records.TraceDisplayPolicy(WalkTraceShadow.RGB)
    @Test fun `changing scale invalidates opacity cache while changing pigment preserves geometry and alpha`() = runBlocking {
        val cells = (-3..3).flatMap { q -> (-3..3).map { r -> SpatialDiaryCellId(q, r) } }.toSet()
        val prepared = prepareWalkRecordsTraces(selection(record("a", cells), record("b", cells)))
        val baseline = prepared.compose(style = traceStyle)
        val changed = traceStyle.copy(density = com.daengs.app.map.features.records.TraceDensityScale(listOf(
            com.daengs.app.map.features.records.TraceDensityBand(1, .01f),
            com.daengs.app.map.features.records.TraceDensityBand(2, .4f))))
        val scaled = prepared.compose(style = changed)
        assertEquals(.4f, scaled.maxOf { it.alpha.max() }, .000001f)
        val recolored = prepared.compose(style = changed.copy(rgb = 0x123456))
        scaled.zip(recolored).forEach { (before, after) ->
            assertEquals(before.southWest, after.southWest)
            assertEquals(before.northEast, after.northEast)
            assertArrayEquals(before.alpha, after.alpha, 0f)
            assertTrue(after.rgb!!.all { it == 0x123456 })
        }
        assertSameTiles(baseline, prepared.compose(style = traceStyle))
        assertEquals(.01f, prepared.compose(hiddenIds = setOf("b"), style = changed).maxOf { it.alpha.max() }, .000001f)
    }

    @Test
    fun `hiding one of two walks recomposes fixed alpha and restoration preserves cached masks`() = runBlocking {
        val cells = (-3..3).flatMap { q -> (-3..3).map { r -> SpatialDiaryCellId(q, r) } }.toSet()
        val selection = selection(record("a", cells), record("b", cells))
        val prepared = prepareWalkRecordsTraces(selection)
        val bounds = prepared.bounds.toList()
        val original = prepared.composeForTest()
        val originalPixels = original.map { it.alpha.copyOf() }
        val one = prepared.composeForTest(setOf("b"))

        assertEquals(0.12f, original.maxOf { tile -> tile.alpha.max() }, 0.000001f)
        assertEquals(0.04f, one.maxOf { tile -> tile.alpha.max() }, 0.000001f)
        // Returned tiles belong to the composition, not to the cached per-walk masks.
        original.forEach { it.alpha.fill(0f) }
        val restored = prepared.composeForTest()
        restored.forEachIndexed { index, tile -> assertArrayEquals(originalPixels[index], tile.alpha, 0f) }
        assertEquals(setOf("a", "b"), prepared.availableWalkIds)
        assertEquals(bounds, prepared.bounds)
        assertEquals(listOf("a", "b"), selection.sessionIds)
        assertTrue(selection.records.all { it.trace!!.cells == cells })
    }

    @Test
    fun `hiding everything clears only the rendered tiles and unknown hidden IDs are ignored`() = runBlocking {
        val selection = selection(record("a", setOf(SpatialDiaryCellId(0, 0))))
        val prepared = prepareWalkRecordsTraces(selection)
        val original = prepared.composeForTest()
        val bounds = prepared.bounds.toList()

        assertSameTiles(original, prepared.composeForTest(setOf("unknown")))
        assertTrue(prepared.composeForTest(setOf("a", "unknown")).isEmpty())
        assertEquals(setOf("a"), prepared.availableWalkIds)
        assertEquals(bounds, prepared.bounds)
        assertEquals(1, selection.records.size)
        assertSameTiles(original, prepared.composeForTest())
    }

    @Test
    fun `bounds include trace support and valid routes without traces and never depend on visibility`() = runBlocking {
        val cell = SpatialDiaryCellId(0, 0)
        val route = listOf(GeoPoint(37.4, 127.0), GeoPoint(37.7, 127.2),
            GeoPoint(Double.NaN, 0.0), GeoPoint(91.0, 0.0), GeoPoint(0.0, 181.0))
        val traced = record("traced", setOf(cell))
        val missing = record("route-only", route = route)
        val prepared = prepareWalkRecordsTraces(selection(traced, missing))
        val support = SpatialDiaryHexGrid.boundary(cell, 8.0)
        val expected = listOf(GeoPoint(support.minOf { it.latitude }, support.minOf { it.longitude }), GeoPoint(37.7, 127.2))

        assertEquals(expected, prepared.bounds)
        assertEquals(setOf("traced"), prepared.availableWalkIds)
        prepared.composeForTest(setOf("traced"))
        assertEquals(expected, prepared.bounds)
        val noTraces = prepareWalkRecordsTraces(selection(missing))
        assertTrue(noTraces.availableWalkIds.isEmpty())
        assertTrue(noTraces.composeForTest().isEmpty())
        assertEquals(listOf(GeoPoint(37.4, 127.0), GeoPoint(37.7, 127.2)), noTraces.bounds)
        val noGeometry = prepareWalkRecordsTraces(selection(record("empty")))
        assertTrue(noGeometry.bounds.isEmpty())
        assertTrue(noGeometry.composeForTest().isEmpty())
    }

    @Test
    fun `selection and individual brush limits fail without returning a partial prepared layer`() = runBlocking {
        val many = (0..400).map { record("walk-$it", setOf(SpatialDiaryCellId(0, 0))) }
        val tooManyWalks = runCatching {
            prepareWalkRecordsTraces(WalkRecordsSelection(WalkRecordsQuery(), many))
        }.exceptionOrNull()
        assertTrue(tooManyWalks is IllegalArgumentException)
        val tooManyCells = runCatching {
            prepareWalkRecordsTraces(selection(record("too-many-cells", (0..5_000).map {
                SpatialDiaryCellId(it, 0)
            }.toSet())))
        }.exceptionOrNull()
        assertTrue(tooManyCells is IllegalArgumentException)
    }

    @Test
    fun `straight routes get a nonempty camera rectangle while single points keep the provider special case`() = runBlocking {
        val horizontal = record("horizontal", route = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.0, 128.0), GeoPoint(Double.NaN, 0.0)))
        val vertical = record("vertical", route = listOf(GeoPoint(37.0, 127.0), GeoPoint(38.0, 127.0)))
        val expectedHorizontal = listOf(GeoPoint(37.0 - 0.00001, 127.0), GeoPoint(37.0 + 0.00001, 128.0))
        val expectedVertical = listOf(GeoPoint(37.0, 127.0 - 0.00001), GeoPoint(38.0, 127.0 + 0.00001))

        assertEquals(expectedHorizontal, walkRecordFocusBounds(horizontal))
        assertEquals(expectedVertical, walkRecordFocusBounds(vertical))
        assertEquals(expectedHorizontal, prepareWalkRecordsTraces(selection(horizontal)).bounds)
        assertEquals(expectedVertical, prepareWalkRecordsTraces(selection(vertical)).bounds)
        val point = GeoPoint(37.0, 127.0)
        val single = record("single", route = listOf(point))
        assertEquals(listOf(point, point), walkRecordFocusBounds(single))
        assertEquals(listOf(point, point), prepareWalkRecordsTraces(selection(single)).bounds)
        assertTrue(walkRecordFocusBounds(record("empty")).isEmpty())
        for (latitude in listOf(-90.0, 90.0)) {
            val edge = walkRecordFocusBounds(record("latitude-edge", route = listOf(GeoPoint(latitude, 127.0), GeoPoint(latitude, 128.0))))
            assertTrue(edge.first().latitude < edge.last().latitude)
            assertTrue(edge.all { it.latitude in -90.0..90.0 })
        }
        for (longitude in listOf(-180.0, 180.0)) {
            val edge = walkRecordFocusBounds(record("longitude-edge", route = listOf(GeoPoint(37.0, longitude), GeoPoint(38.0, longitude))))
            assertTrue(edge.first().longitude < edge.last().longitude)
            assertTrue(edge.all { it.longitude in -180.0..180.0 })
        }
    }

    @Test
    fun `overlap thresholds and snapped hits retain original cell membership while hidden walks change only ink`() = runBlocking {
        val five = SpatialDiaryCellId(0, 0)
        val two = SpatialDiaryCellId(8, 0)
        val single = SpatialDiaryCellId(60, 0)
        val near = SpatialDiaryCellId(1, 0)
        val selected = selection(record("a", setOf(five, two, single, near)),
            record("b", setOf(five, two)), record("c", setOf(five)), record("d", setOf(five)), record("e", setOf(five)))
        val prepared = prepareWalkRecordsTraces(selected)
        val allIds = setOf("a", "b", "c", "d", "e")
        val atFive = SpatialDiaryHexGrid.center(five, 8.0)
        val atTwo = SpatialDiaryHexGrid.center(two, 8.0)
        assertNull(prepared.overlapUnavailableReason)
        listOf(2, 3, 5).forEach { assertTrue(prepared.hasOverlap(it)) }
        assertEquals(allIds, prepared.overlapWalkIds(5))
        assertEquals(setOf("a", "b"), prepared.hitTestOverlap(atTwo, 2, snapRadiusU = 0.0)!!.walkIds)
        assertNull(prepared.hitTestOverlap(atTwo, 3, snapRadiusU = 0.0))
        val snapped = prepared.hitTestOverlap(SpatialDiaryHexGrid.center(near, 8.0), 5)!!
        assertEquals(five, snapped.cell)
        assertEquals(atFive, snapped.point)
        assertEquals(allIds, snapped.walkIds)
        assertNull(prepared.hitTestOverlap(SpatialDiaryHexGrid.center(SpatialDiaryCellId(3, 0), 8.0), 5))
        assertNull(prepared.hitTestOverlap(GeoPoint(Double.NaN, 0.0), 2))
        assertEquals(allIds, prepared.hitTestOverlap(atFive, 5, hiddenIds = setOf("b", "c", "d", "e"))!!.walkIds)
        assertNull(prepared.hitTestOverlap(atFive, 5, hiddenIds = allIds))
        val original = prepared.composeForTest(minimumOverlapWalks = 2)
        assertTrue(prepared.composeForTest().any { it.tileX >= 3 })
        assertTrue(original.none { it.tileX >= 3 })
        assertTrue(original.all { tile -> tile.alpha.all { it <= 0.460001f } })
        val hidden = prepared.composeForTest(setOf("b", "c", "d", "e"), minimumOverlapWalks = 5)
        assertTrue(hidden.isNotEmpty())
        assertTrue(hidden.all { tile -> tile.alpha.all { it <= 0.040001f } })
        assertTrue(prepared.composeForTest(allIds, minimumOverlapWalks = 5).isEmpty())
        assertTrue(prepared.hasOverlap(5))
        assertEquals(allIds, prepared.overlapWalkIds(5))
        assertSameTiles(original, prepared.composeForTest(minimumOverlapWalks = 2))
        assertEquals(5, selected.records.size)
    }

    @Test
    fun `neighbour blur is not overlap evidence and different cell radii disable only overlap mode`() = runBlocking {
        val near = prepareWalkRecordsTraces(selection(record("a", setOf(SpatialDiaryCellId(0, 0))),
            record("b", setOf(SpatialDiaryCellId(1, 0)))))
        assertTrue(near.composeForTest().isNotEmpty())
        assertFalse(near.hasOverlap(2))
        assertTrue(near.composeForTest(minimumOverlapWalks = 2).isEmpty())
        assertNull(near.hitTestOverlap(GeoPoint(0.0, 0.0), 2))
        val b = record("b", setOf(SpatialDiaryCellId(0, 0)))
        val mixed = prepareWalkRecordsTraces(selection(record("a", setOf(SpatialDiaryCellId(0, 0))),
            b.copy(trace = b.trace!!.copy(radiusU = 16.0))))
        assertNotNull(mixed.overlapUnavailableReason)
        assertFalse(mixed.hasOverlap(2))
        assertTrue(mixed.composeForTest().isNotEmpty())
        assertNull(mixed.hitTestOverlap(GeoPoint(0.0, 0.0), 2))
        assertTrue(runCatching { mixed.composeForTest(minimumOverlapWalks = 2) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `overlap evidence limits return an explicit unavailable state without partial membership`() {
        val wide = (0..49).flatMap { q -> (0..99).map { r -> SpatialDiaryCellId(q, r) } }.toSet()
        val extra = setOf(SpatialDiaryCellId(200, 0), SpatialDiaryCellId(201, 0))
        val index = WalkTraceOverlap.create(listOf(WalkTraceSheet("a", cells = wide),
            WalkTraceSheet("b", cells = wide), WalkTraceSheet("c", cells = extra), WalkTraceSheet("d", cells = extra))) {}
        assertNotNull(index.unavailableReason)
        assertFalse(index.hasOverlap(2))
        assertTrue(index.walkIds(2).isEmpty())
    }

    @Test
    fun `sealed provenance permits matching policies across analyses and disables only mixed policy overlap`() = runBlocking {
        val cells = setOf(SpatialDiaryCellId(0, 0))
        val policy = WalkTraceProvenance("analysis-a", "sheet-a", "paint-a", 2, "hex-v1", "profile-a", 1.5)
        val a = record("a", cells).let { it.copy(trace = it.trace!!.copy(provenance = policy)) }
        val b = record("b", cells).let { it.copy(trace = it.trace!!.copy(provenance =
            policy.copy(analysisId = "analysis-b", sheetFingerprint = "sheet-b"))) }
        val matching = prepareWalkRecordsTraces(selection(a, b))
        assertTrue(matching.hasOverlap(2))
        assertEquals(setOf("a", "b"), matching.hitTestOverlap(GeoPoint(0.0, 0.0), 2)!!.walkIds)
        val original = matching.composeForTest(minimumOverlapWalks = 2)
        matching.composeForTest(setOf("b"), minimumOverlapWalks = 2)
        assertSameTiles(original, matching.composeForTest(minimumOverlapWalks = 2))

        for (different in listOf(null, policy.copy(paintFingerprint = "paint-b"),
            policy.copy(profileFingerprint = "profile-b"), policy.copy(sampleStepMeters = 3.0))) {
            val mixed = prepareWalkRecordsTraces(selection(a, b.copy(trace = b.trace!!.copy(provenance = different))))
            assertNotNull(mixed.overlapUnavailableReason)
            assertFalse(mixed.hasOverlap(2))
            assertTrue(mixed.composeForTest().isNotEmpty())
            assertNull(mixed.hitTestOverlap(GeoPoint(0.0, 0.0), 2))
            assertTrue(runCatching { mixed.composeForTest(minimumOverlapWalks = 2) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `neighbouring ink cannot revive an overlap area after all of its contributors are hidden`() = runBlocking {
        val a = SpatialDiaryCellId(0, 0)
        val neighbour = SpatialDiaryCellId(1, 0)
        val far = SpatialDiaryCellId(30, 0)
        val hidden = setOf("a", "b")
        val onlyHiddenArea = prepareWalkRecordsTraces(selection(record("a", setOf(a)),
            record("b", setOf(a)), record("c", setOf(neighbour))))
        assertTrue(onlyHiddenArea.hasOverlap(2))
        assertEquals(hidden, onlyHiddenArea.overlapWalkIds(2))
        assertTrue(onlyHiddenArea.composeForTest(hidden).isNotEmpty())
        assertTrue(onlyHiddenArea.composeForTest(hidden, minimumOverlapWalks = 2).isEmpty())
        assertNull(onlyHiddenArea.hitTestOverlap(GeoPoint(0.0, 0.0), 2, hiddenIds = hidden))

        // c is a legitimate contributor elsewhere, so filtering whole-walk IDs alone is not enough.
        val activeElsewhere = prepareWalkRecordsTraces(selection(record("a", setOf(a)), record("b", setOf(a)),
            record("c", setOf(neighbour, far)), record("d", setOf(far))))
        val original = activeElsewhere.composeForTest(minimumOverlapWalks = 2)
        val visible = activeElsewhere.composeForTest(hidden, minimumOverlapWalks = 2)
        assertTrue(visible.isNotEmpty())
        assertTrue(visible.all { it.tileX == 1 })
        assertEquals(setOf("a", "b", "c", "d"), activeElsewhere.overlapWalkIds(2))
        assertNull(activeElsewhere.hitTestOverlap(GeoPoint(0.0, 0.0), 2, hiddenIds = hidden))
        assertSameTiles(original, activeElsewhere.composeForTest(minimumOverlapWalks = 2))
    }

    @Test
    fun `shadow pigment stays neutral while distinct counts control strength and thresholds preserve evidence`() = runBlocking {
        val two = SpatialDiaryCellId(0, 0)
        val three = SpatialDiaryCellId(8, 0)
        val four = SpatialDiaryCellId(16, 0)
        val five = SpatialDiaryCellId(24, 0)
        val cells = setOf(two, three, four, five)
        val prepared = prepareWalkRecordsTraces(selection(record("a", cells), record("b", cells),
            record("c", setOf(three, four, five)), record("d", setOf(four, five)), record("e", setOf(five))))
        val overall = prepared.composeForTest()
        val colored = prepared.composeForTest(minimumOverlapWalks = 2)
        val byTile = colored.associateBy { it.tileX to it.tileY }
        val originalColors = byTile.mapValues { requireNotNull(it.value.rgb).copyOf() }
        assertTrue(overall.all { tile -> requireNotNull(tile.rgb).all { it == WalkTraceShadow.RGB } })
        mapOf(two to WalkTraceShadow.RGB, three to WalkTraceShadow.RGB,
            four to WalkTraceShadow.RGB, five to WalkTraceShadow.RGB).forEach { (cell, color) ->
            val (tile, index) = pixelAt(colored, cell)
            assertTrue(tile.alpha[index] > 0f)
            assertEquals(color, requireNotNull(tile.rgb)[index])
        }

        // Both display modes share the strength field; overlap only clips its exact eligibility support.
        val eligibility = TraceBrush.mask(WalkTraceSheet("eligibility", cells = cells)).tiles.associateBy { it.tileX to it.tileY }
        overall.forEach { tile ->
            val region = eligibility.getValue(tile.tileX to tile.tileY)
            val expectedAlpha = FloatArray(tile.alpha.size) { i -> tile.alpha[i] * region.alpha[i] }
            assertArrayEquals(expectedAlpha, byTile.getValue(tile.tileX to tile.tileY).alpha, 0f)
        }
        val hidden = prepared.composeForTest(setOf("b", "c", "d", "e"), minimumOverlapWalks = 2)
        assertTrue(hidden.all { tile -> tile.alpha.all { it <= 0.040001f } })
        hidden.forEach { tile -> assertArrayEquals(originalColors.getValue(tile.tileX to tile.tileY), requireNotNull(tile.rgb)) }
        val minimumFive = prepared.composeForTest(minimumOverlapWalks = 5)
        minimumFive.forEach { tile -> assertArrayEquals(originalColors.getValue(tile.tileX to tile.tileY), requireNotNull(tile.rgb)) }
        assertTrue(minimumFive.all { tile -> tile.alpha.all { it <= 0.460001f } })
        assertEquals(5, prepared.hitTestOverlap(SpatialDiaryHexGrid.center(five, 8.0), 5,
            hiddenIds = setOf("b", "c", "d", "e"))!!.walkIds.size)
        assertTrue(prepared.composeForTest(setOf("a", "b", "c", "d", "e"), minimumOverlapWalks = 2).isEmpty())

        colored.forEach { requireNotNull(it.rgb).fill(0) }
        prepared.composeForTest(minimumOverlapWalks = 2).forEach { tile ->
            assertArrayEquals(originalColors.getValue(tile.tileX to tile.tileY), requireNotNull(tile.rgb))
        }
    }

    @Test
    fun `shadow strength uses fixed count steps without saturating early or normalising sparse selections`() = runBlocking {
        val patch = (-3..3).flatMap { q -> (-3..3).map { r -> SpatialDiaryCellId(q, r) } }.toSet()
        for ((count, expected) in listOf(1 to .04f, 2 to .12f, 3 to .22f, 4 to .22f,
            5 to .34f, 7 to .34f, 8 to .46f, 12 to .46f)) {
            val records = (1..count).map { record("walk-$it", patch) }
            val prepared = prepareWalkRecordsTraces(selection(*records.toTypedArray()))
            val tiles = prepared.composeForTest()
            assertEquals("$count walks", expected, tiles.maxOf { it.alpha.max() }, .000001f)
            assertTrue(tiles.all { tile -> tile.alpha.all { it in 0f..(expected + .000001f) } })
            val single = prepared.composeForTest((2..count).map { "walk-$it" }.toSet())
            assertEquals(.04f, single.maxOf { it.alpha.max() }, .000001f)
        }
    }

    @Test
    fun `adjacent single walks never acquire overlap strength from their blurred edges`() = runBlocking {
        val prepared = prepareWalkRecordsTraces(selection(
            record("a", setOf(SpatialDiaryCellId(0, 0))),
            record("b", setOf(SpatialDiaryCellId(1, 0)))))
        assertFalse(prepared.hasOverlap(2))
        assertTrue(prepared.composeForTest().all { tile -> tile.alpha.all { it <= .040001f } })
        assertTrue(prepared.composeForTest(minimumOverlapWalks = 2).isEmpty())
    }

    @Test
    fun `incomparable trace policies show only the faint base strength`() = runBlocking {
        val a = record("a", setOf(SpatialDiaryCellId(0, 0)))
        val b = record("b", setOf(SpatialDiaryCellId(0, 0)))
        val prepared = prepareWalkRecordsTraces(selection(a, b.copy(trace = b.trace!!.copy(radiusU = 16.0))))
        assertNotNull(prepared.overlapUnavailableReason)
        assertTrue(prepared.composeForTest().all { tile ->
            tile.alpha.all { it <= .040001f } && requireNotNull(tile.rgb).all { it == WalkTraceShadow.RGB }
        })
    }

    private suspend fun PreparedWalkRecordsTraces.composeForTest(
        hiddenIds: Set<String> = emptySet(), minimumOverlapWalks: Int? = null,
    ) = compose(hiddenIds, minimumOverlapWalks, traceStyle)

    private fun pixelAt(tiles: List<TraceRasterTile>, cell: SpatialDiaryCellId): Pair<TraceRasterTile, Int> {
        val first = tiles.first()
        val x = floor(8.0 * sqrt(3.0) * (cell.q + cell.r / 2.0) / first.pixelU).toInt()
        val y = floor(8.0 * 1.5 * cell.r / first.pixelU).toInt()
        val tile = tiles.single { it.tileX == Math.floorDiv(x, first.size) && it.tileY == Math.floorDiv(y, first.size) }
        val row = (tile.tileY + 1) * tile.size - 1 - y
        val col = x - tile.tileX * tile.size
        return tile to row * tile.size + col
    }

    private fun record(id: String, cells: Set<SpatialDiaryCellId>? = null, route: List<GeoPoint> = emptyList()): WalkRecord {
        val segments = if (route.isEmpty()) emptyList() else listOf(route.mapIndexed { index, point ->
            LocationSample(point, index.toLong())
        })
        return WalkRecord(WalkSummary(id, emptyList(), 0L, 100L, null, 0.0, 0L, segments, null),
            trace = cells?.let { WalkTraceSheet(id, cells = it) })
    }

    private fun selection(vararg records: WalkRecord) = WalkRecordsSelection(WalkRecordsQuery(), records.toList())

    private fun assertSameTiles(expected: List<TraceRasterTile>, actual: List<TraceRasterTile>) {
        assertEquals(expected.map { it.tileX to it.tileY }, actual.map { it.tileX to it.tileY })
        expected.zip(actual).forEach { (before, after) ->
            assertArrayEquals(before.alpha, after.alpha, 0f)
            assertEquals(before.rgb == null, after.rgb == null)
            if (before.rgb != null) assertArrayEquals(before.rgb, requireNotNull(after.rgb))
        }
    }
}
