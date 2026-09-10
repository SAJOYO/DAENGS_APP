package com.daengs.app.walk.records

import com.daengs.app.map.layers.traces.TraceBrush
import com.daengs.app.walk.diary.SpatialDiaryCellId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WalkRecordSheetsTest {
    @Test fun `native server serializer fixture preserves zero occupancy support provenance and per walk states`() {
        val response = fixture()
        val original = response.toString()
        val parsed = parseWalkRecordSheets(response, expected)
        val first = parsed.getValue(uuid(101))
        val trace = requireNotNull(first.trace)

        assertEquals(expected.keys.toList(), parsed.keys.toList())
        assertEquals(listOf(WalkTraceState.READY, WalkTraceState.EMPTY, WalkTraceState.ANALYSIS_PENDING,
            WalkTraceState.NOT_UPLOADED), parsed.values.map { it.state })
        assertEquals(uuid(101), trace.walkId)
        assertEquals(8.0, trace.radiusU, 0.0)
        assertEquals(setOf(SpatialDiaryCellId(10, -3), SpatialDiaryCellId(11, -3)), trace.cells)
        assertTrue(TraceBrush.mask(trace).tiles.any { tile -> tile.alpha.any { it > 0f } })
        val source = requireNotNull(trace.provenance)
        assertEquals(uuid(201), source.analysisId)
        assertEquals(response.item().getString("sheet_fingerprint"), source.sheetFingerprint)
        assertEquals("3bb02412877d", source.paintFingerprint)
        assertEquals("903fab223ba8", source.profileFingerprint)
        assertEquals(2, source.paintVersion)
        assertEquals("hex-v1", source.gridVersion)
        assertEquals(1.5, source.sampleStepMeters, 0.0)
        assertTrue(requireNotNull(parsed.getValue(uuid(102)).trace).cells.isEmpty())
        assertNull(parsed.getValue(uuid(103)).trace)
        assertNull(parsed.getValue(uuid(104)).trace)
        assertEquals(original, response.toString())
        response.sheet().getJSONArray("cells").getJSONArray(0).put(0, 999)
        assertTrue(SpatialDiaryCellId(10, -3) in trace.cells)
    }

    @Test fun `unsupported paint grid radius and display size affect only their own record`() {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONObject("paint").put("paint_version", 3) },
            { it.getJSONObject("paint").put("grid_version", "hex-v2") },
            { it.getJSONObject("paint").put("radius_u", 2.0) },
            { it.getJSONObject("paint").put("radius_u", 128.0) },
            { sheet ->
                sheet.put("cells", JSONArray().apply {
                    (0..5_000).forEach { put(JSONArray(listOf(it, 0, 0.0, 0.45))) }
                }).put("cell_count", 5_001)
            },
        )
        changes.forEachIndexed { index, change ->
            val response = fixture()
            change(response.sheet())
            val parsed = parseWalkRecordSheets(response, expected)
            assertEquals("unsupported variant $index", WalkTraceState.UNSUPPORTED, parsed.getValue(uuid(101)).state)
            assertNull(parsed.getValue(uuid(101)).trace)
            assertEquals(WalkTraceState.EMPTY, parsed.getValue(uuid(102)).state)
            assertEquals(WalkTraceState.ANALYSIS_PENDING, parsed.getValue(uuid(103)).state)
            assertEquals(WalkTraceState.NOT_UPLOADED, parsed.getValue(uuid(104)).state)
        }
    }

    @Test fun `malformed native cells and metadata are rejected rather than truncated or silently dropped`() {
        val changes: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "fractional coordinate" to { it.rows().getJSONArray(0).put(0, 10.5) },
            "floating coordinate" to { it.rows().getJSONArray(0).put(0, 10.0) },
            "string coordinate" to { it.rows().getJSONArray(0).put(0, "10") },
            "boolean coordinate" to { it.rows().getJSONArray(0).put(0, true) },
            "overflow coordinate" to { it.rows().getJSONArray(0).put(0, Int.MAX_VALUE.toLong() + 1) },
            "outside Mercator" to { it.rows().getJSONArray(1).put(0, Int.MAX_VALUE) },
            "negative occupancy" to { it.rows().getJSONArray(0).put(2, -1.0) },
            "nonfinite occupancy" to { it.rows().getJSONArray(0).put(2, "NaN") },
            "zero peak" to { it.rows().getJSONArray(0).put(3, 0) },
            "oversized peak" to { it.rows().getJSONArray(0).put(3, 1.01) },
            "duplicate cell" to { it.rows().put(1, JSONArray(it.rows().getJSONArray(0).toString())) },
            "unordered cells" to { sheet ->
                val rows = sheet.rows()
                val first = rows.getJSONArray(0)
                rows.put(0, rows.getJSONArray(1)).put(1, first)
            },
            "wrong row width" to { it.rows().getJSONArray(0).put(4, 1) },
            "wrong count" to { it.put("cell_count", 3) },
            "fractional count" to { it.put("cell_count", 2.5) },
            "wrong columns" to { it.getJSONArray("cols").put(2, "value") },
            "unknown native schema" to { it.put("v", 2) },
            "naive timestamp" to { it.put("at", "2026-09-10T00:00:00") },
            "zero radius" to { it.getJSONObject("paint").put("radius_u", 0) },
            "missing policy" to { it.getJSONObject("paint").remove("profile_fp") },
            "bad fingerprint" to { it.getJSONObject("paint").put("paint_fp", "unknown") },
            "wrong native walk" to { it.put("walk_id", uuid(99)) },
        )
        changes.forEach { (label, change) ->
            val response = fixture()
            change(response.sheet())
            assertRejected(label, response)
        }
        // A future paint policy does not give malformed cell rows a validation bypass.
        val unsupportedAndBroken = fixture()
        unsupportedAndBroken.sheet().getJSONObject("paint").put("paint_version", 3)
        unsupportedAndBroken.sheet().rows().getJSONArray(0).put(3, 0)
        assertRejected("unsupported malformed cell", unsupportedAndBroken)
    }

    @Test fun `membership mapping versions and state metadata must match the captured request exactly`() {
        val changes: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "unknown envelope" to { it.put("schema_version", 2) },
            "coerced envelope version" to { it.put("schema_version", "1") },
            "extra envelope field" to { it.put("unexpected", true) },
            "missing response" to { it.getJSONArray("items").remove(3) },
            "extra response" to { it.getJSONArray("items").put(it.item()) },
            "duplicate local ID" to { it.item(1).put("client_session_id", uuid(101)) },
            "other local ID" to { it.item().put("client_session_id", uuid(199)) },
            "invalid local UUID" to { it.item().put("client_session_id", "1-1-1-1-1") },
            "wrong server ID" to { it.item().put("walk_id", uuid(99)) },
            "ready missing server ID" to { it.item().put("walk_id", JSONObject.NULL) },
            "ready missing analysis" to { it.item().put("analysis_id", JSONObject.NULL) },
            "ready invalid fingerprint" to { it.item().put("sheet_fingerprint", "sha256:no") },
            "unknown state" to { it.item().put("status", "missing") },
            "pending retains analysis" to { it.item(2).put("analysis_id", uuid(201)) },
            "pending has no server ID" to { it.item(2).put("walk_id", JSONObject.NULL) },
            "unavailable retains server ID" to { it.item(3).put("walk_id", uuid(4)) },
            "unavailable retains analysis" to { it.item(3).put("analysis_id", uuid(204)) },
            "unavailable retains sheet" to { it.item(3).put("sheet", it.sheet()) },
            "reordered response" to { response ->
                val items = response.getJSONArray("items")
                val first = items.getJSONObject(0)
                items.put(0, items.getJSONObject(1)).put(1, first)
            },
        )
        changes.forEach { (label, change) ->
            val response = fixture()
            change(response)
            assertRejected(label, response)
        }
        assertThrows(IllegalArgumentException::class.java) { parseWalkRecordSheets(fixture(), emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) {
            parseWalkRecordSheets(fixture(), expected + (uuid(102) to uuid(1)))
        }
    }

    private fun assertRejected(label: String, response: JSONObject) {
        assertNotNull(label, runCatching { parseWalkRecordSheets(response, expected) }.exceptionOrNull())
    }

    // Generated by DEV's Cellophane -> encode_cellophane -> response model_dump_json,
    // including a valid zero-occupancy/positive-peak cell. Do not hand-recreate this payload.
    private fun fixture() = JSONObject(requireNotNull(javaClass.getResource("/record-sheet-contract.json")).readText())
    private fun JSONObject.item(index: Int = 0) = getJSONArray("items").getJSONObject(index)
    private fun JSONObject.sheet() = item().getJSONObject("sheet")
    private fun JSONObject.rows() = getJSONArray("cells")
    private fun uuid(value: Long) = UUID(0, value).toString()
    private val expected = (1L..4L).associate { uuid(it + 100) to uuid(it) }
}
