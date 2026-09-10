package com.daengs.app.walk.records

import com.daengs.app.map.layers.traces.WalkTraceProvenance
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.walk.diary.SpatialDiaryCellId
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

data class WalkRecordSheetResult(val trace: WalkTraceSheet?, val state: WalkTraceState)

/**
 * Reads sealed per-walk inputs, never the aggregate spatial-diary field or a repainted GPS route.
 * The request's local/server mapping is captured before suspension and must match every result.
 * Unsupported paint policies are per-walk states; malformed or misattributed responses fail whole.
 */
fun parseWalkRecordSheets(
    response: JSONObject,
    expectedWalkIds: Map<String, String>,
    checkCancelled: () -> Unit = {},
): Map<String, WalkRecordSheetResult> {
    checkCancelled()
    val expected = expectedWalkIds.toMap()
    require(expected.size in 1..400 && expected.values.distinct().size == expected.size)
    expected.forEach { (local, server) -> canonicalUuid(local); canonicalUuid(server) }
    response.requireKeys("schema_version", "items")
    require(response.integer("schema_version") == 1) { "지원하지 않는 산책 흔적 응답이에요." }
    val items = response.getJSONArray("items")
    require(items.length() == expected.size) { "요청한 산책과 흔적 응답 개수가 달라요." }
    val result = linkedMapOf<String, WalkRecordSheetResult>()
    var cellBudget = 100_000L
    expected.entries.forEachIndexed { index, (localId, serverId) ->
        checkCancelled()
        val item = items.getJSONObject(index)
        item.requireKeys("client_session_id", "walk_id", "status", "analysis_id", "sheet_fingerprint", "sheet")
        require(canonicalUuid(item.text("client_session_id")) == localId) { "요청한 산책과 응답 순서가 달라요." }
        val walkId = item.nullableText("walk_id")?.let(::canonicalUuid)
        require(walkId == null || walkId == serverId) { "다른 산책의 흔적이 반환됐어요." }
        val analysisId = item.nullableText("analysis_id")?.let(::canonicalUuid)
        val fingerprint = item.nullableText("sheet_fingerprint")
        val state = item.text("status")
        result[localId] = when (state) {
            "pending", "unavailable" -> {
                require(item.isNull("sheet") && fingerprint == null && analysisId == null)
                require(if (state == "pending") walkId != null else walkId == null)
                WalkRecordSheetResult(null, if (state == "pending") WalkTraceState.ANALYSIS_PENDING else WalkTraceState.NOT_UPLOADED)
            }
            "ready" -> {
                require(walkId != null && analysisId != null && fingerprint != null)
                require(SHEET_FINGERPRINT.matches(fingerprint)) { "원판 출처가 올바르지 않아요." }
                val sheet = item.getJSONObject("sheet")
                val cells = sheet.getJSONArray("cells")
                cellBudget -= cells.length()
                require(cellBudget >= 0) { "한 번에 읽을 산책 흔적이 너무 많아요." }
                parseSheet(sheet, localId, serverId, analysisId, fingerprint, checkCancelled)
            }
            else -> throw IllegalArgumentException("알 수 없는 산책 흔적 상태예요.")
        }
    }
    return result.toMap()
}

private fun parseSheet(
    sheet: JSONObject, localId: String, serverId: String, analysisId: String, fingerprint: String,
    checkCancelled: () -> Unit,
): WalkRecordSheetResult {
    sheet.requireKeys("v", "walk_id", "at", "paint", "cols", "cell_count", "cells")
    require(sheet.integer("v") == 1) { "지원하지 않는 산책 원판 형식이에요." }
    require(canonicalUuid(sheet.text("walk_id")) == serverId) { "원판과 산책의 ID가 달라요." }
    Instant.parse(sheet.text("at"))
    val paint = sheet.getJSONObject("paint")
    paint.requireKeys("paint_version", "grid_version", "radius_u", "profile", "profile_fp", "sample_step_m", "paint_fp")
    val paintVersion = paint.integer("paint_version").also { require(it > 0) }
    val gridVersion = paint.text("grid_version").also { require(it.isNotBlank()) }
    val radius = paint.number("radius_u").also { require(it > 0) }
    require(paint.text("profile").isNotBlank())
    val profileFingerprint = paint.text("profile_fp").also { require(PAINT_FINGERPRINT.matches(it)) }
    val sampleStep = paint.number("sample_step_m").also { require(it > 0) }
    val paintFingerprint = paint.text("paint_fp").also { require(PAINT_FINGERPRINT.matches(it)) }
    val columns = sheet.getJSONArray("cols")
    require(columns.length() == 4 && (0..3).map { columns.get(it) } == listOf("q", "r", "occupancy_s", "peak"))
    val rows = sheet.getJSONArray("cells")
    require(sheet.integer("cell_count") == rows.length()) { "원판 셀 개수가 맞지 않아요." }
    val supported = paintVersion == 2 && gridVersion == "hex-v1" && radius in 4.0..64.0 && rows.length() <= 5_000
    val cells = linkedSetOf<SpatialDiaryCellId>()
    var previous: SpatialDiaryCellId? = null
    for (index in 0 until rows.length()) {
        if (index % 128 == 0) checkCancelled()
        val row = rows.getJSONArray(index)
        require(row.length() == 4)
        val cell = SpatialDiaryCellId(strictInteger(row.get(0)), strictInteger(row.get(1)))
        require(previous?.let { cell.q > it.q || (cell.q == it.q && cell.r > it.r) } != false) {
            "원판 셀이 중복되거나 정렬 순서가 달라요."
        }
        previous = cell
        require(strictNumber(row.get(2)) >= 0)
        val peak = strictNumber(row.get(3))
        require(peak > 0 && peak <= 1)
        if (gridVersion == "hex-v1") {
            val x = radius * sqrt(3.0) * (cell.q + cell.r / 2.0)
            val y = radius * 1.5 * cell.r
            require(abs(x) + radius < WORLD_EDGE && abs(y) + radius < WORLD_EDGE) {
                "원판 셀이 지도 좌표 범위를 벗어났어요."
            }
        }
        // Positive peak defines support. Zero occupancy is valid and must remain in the trace.
        if (supported) cells += cell
    }
    if (!supported) return WalkRecordSheetResult(null, WalkTraceState.UNSUPPORTED)
    // DEV validates both fingerprints against the stored payload before serving it. Preserve
    // their original identities: Python's canonical float JSON is not JSONObject.toString().
    val provenance = WalkTraceProvenance(analysisId, fingerprint, paintFingerprint, paintVersion,
        gridVersion, profileFingerprint, sampleStep)
    val trace = WalkTraceSheet(localId, radius, cells.toSet(), provenance)
    return WalkRecordSheetResult(trace, if (cells.isEmpty()) WalkTraceState.EMPTY else WalkTraceState.READY)
}

private fun JSONObject.requireKeys(vararg names: String) = require(keys().asSequence().toSet() == names.toSet()) {
    "산책 흔적 응답 필드가 계약과 달라요."
}

private fun JSONObject.text(key: String): String = get(key).let { require(it is String); it }
private fun JSONObject.nullableText(key: String): String? = if (isNull(key)) null else text(key)
private fun JSONObject.integer(key: String): Int = strictInteger(get(key))
private fun JSONObject.number(key: String): Double = strictNumber(get(key))

private fun strictInteger(value: Any): Int {
    // JSONObject.getInt coerces strings and truncates fractions; neither is a cell coordinate.
    require(value is Byte || value is Short || value is Int || value is Long)
    val number = (value as Number).toLong()
    require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
    return number.toInt()
}

private fun strictNumber(value: Any): Double {
    require(value is Number)
    return value.toDouble().also { require(it.isFinite()) }
}

private fun canonicalUuid(value: String): String = UUID.fromString(value).toString().also {
    require(it == value) { "산책 ID 형식이 올바르지 않아요." }
}

private val SHEET_FINGERPRINT = Regex("sha256:[0-9a-f]{64}")
private val PAINT_FINGERPRINT = Regex("[0-9a-f]{12}")
private const val WORLD_EDGE = PI * 6_378_137.0
