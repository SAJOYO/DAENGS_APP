package com.daengs.app.walk.sync

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.motion.*
import java.math.RoundingMode
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** Original IEEE bits supplement, rather than replace, the six-decimal raw backup. */
internal object WalkPrecisionContract {
    const val VERSION = "gps-motion-precision-v1"
    const val CALCULATION = "gps-motion-calculation-v1"
    private val manifestKeys = listOf("version", "client_session_id", "base_evidence_fingerprint", "point_count")
    private val pointKeys = listOf("client_seq", "lat_bits", "lng_bits", "accuracy_bits")

    data class Plan(val base: WalkMotionContract.Plan, val manifest: JSONObject, val points: List<JSONObject>) {
        val manifestHash = manifestDigest(manifest)
        val chunks = points.chunked(256)
        val chunkHashes = chunks.map(::chunkDigest)
        val evidenceHash = digest(JSONArray().put(VERSION).put(manifestHash).put(JSONArray(chunkHashes)))
        fun receipt() = JSONObject().put("manifest_fingerprint", manifestHash).put("evidence_fingerprint", evidenceHash)
        fun chunk(i: Int) = JSONObject().put("manifest_fingerprint", manifestHash).put("points", JSONArray(chunks[i]))
    }

    fun create(base: WalkMotionContract.Plan): Plan = read(base,
        JSONObject().put("version", VERSION).put("client_session_id", base.input.session.id)
            .put("base_evidence_fingerprint", base.evidenceHash).put("point_count", base.input.fixes.size),
        base.input.fixes.map { p -> JSONObject().put("client_seq", p.clientSeq)
            .put("lat_bits", p.lat.toRawBits().toULong().toString(16).padStart(16, '0'))
            .put("lng_bits", p.lng.toRawBits().toULong().toString(16).padStart(16, '0'))
            .put("accuracy_bits", p.accuracyM?.toRawBits()?.toUInt()?.toString(16)?.padStart(8, '0') ?: JSONObject.NULL) })

    fun read(base: WalkMotionContract.Plan, manifest: JSONObject, points: List<JSONObject>): Plan {
        require(manifest.keys().asSequence().toSet() == manifestKeys.toSet())
        require(manifest.getString("version") == VERSION && manifest.getString("client_session_id") == base.input.session.id)
        require(manifest.getString("base_evidence_fingerprint") == base.evidenceHash)
        require(integer(manifest, "point_count") == base.input.fixes.size.toLong() && points.size == base.input.fixes.size)
        fun rounded(v: Double) = v.toBigDecimal().setScale(6, RoundingMode.HALF_EVEN)
        val fixes = points.mapIndexed { i, p ->
            require(p.keys().asSequence().toSet() == pointKeys.toSet() && integer(p, "client_seq") == i.toLong())
            fun double(key: String) = p.getString(key).also { require(it.matches(Regex("[0-9a-f]{16}"))) }
                .let { Double.fromBits(it.toULong(16).toLong()) }
            val lat = double("lat_bits"); val lng = double("lng_bits")
            require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0)
            val accuracy = if (p.get("accuracy_bits") == JSONObject.NULL) null else
                p.getString("accuracy_bits").also { require(it.matches(Regex("[0-9a-f]{8}"))) }
                    .let { Float.fromBits(it.toUInt(16).toInt()) }.also { require(it.isFinite() && it >= 0f) }
            val raw = base.input.fixes[i]
            require(rounded(lat) == rounded(raw.lat) && rounded(lng) == rounded(raw.lng))
            require((accuracy == null) == (raw.accuracyM == null) &&
                (accuracy == null || accuracy.toDouble() == raw.accuracyM!!.toDouble()))
            raw.copy(lat = lat, lng = lng, accuracyM = accuracy)
        }
        return Plan(base.copy(input = base.input.copy(fixes = fixes)), manifest, points)
    }

    fun validateStatus(value: JSONObject, plan: Plan, complete: Boolean): Set<Int> {
        require(value.getString("version") == VERSION && value.getString("manifest_fingerprint") == plan.manifestHash)
        val m = value.getJSONObject("manifest")
        require(m.keys().asSequence().toSet() == manifestKeys.toSet() && manifestDigest(m) == plan.manifestHash)
        val a = value.getJSONArray("received_chunks")
        val indices = (0 until a.length()).map { exact(a.get(it)).also { n -> require(n in plan.chunks.indices.map(Int::toLong)) }.toInt() }
        require(indices == indices.distinct().sorted())
        val state = value.getString("state")
        require(state in setOf("collecting", "complete"))
        if (complete || state == "complete") require(state == "complete" && indices == plan.chunks.indices.toList() &&
            value.getString("evidence_fingerprint") == plan.evidenceHash)
        else require(value.has("evidence_fingerprint") && value.isNull("evidence_fingerprint"))
        return indices.toSet()
    }

    /** Strict structural equality, except for documented floating point distance tolerance. */
    fun verify(plan: Plan, server: JSONObject): JSONObject {
        val base = plan.base
        val policy = base.manifest.getJSONObject("policy")
        require(server.getString("version") == CALCULATION && server.getString("coordinate_basis") == "device-fix-bits-v1")
        require(server.get("device_result_verified") == false)
        val identities = mapOf("walk_id" to requireNotNull(base.input.session.serverWalkId),
            "client_session_id" to base.input.session.id, "policy_version" to policy.getString("version"),
            "measurement_version" to policy.getString("measurement_version"), "config_hash" to policy.getString("config_hash"),
            "manifest_fingerprint" to base.manifestHash, "evidence_fingerprint" to base.evidenceHash,
            "precision_fingerprint" to plan.evidenceHash)
        identities.forEach { (key, value) -> require(server.getString(key) == value) }
        val expected = replay(base)
        for (key in listOf("recording_duration_nanos", "active_duration_millis", "point_count", "segment_count"))
            require(integer(server, key) == integer(expected, key))
        val segments = server.getJSONArray("segments"); val wanted = expected.getJSONArray("segments")
        require(segments.length() == wanted.length())
        for (i in 0 until segments.length()) {
            val a = segments.getJSONArray(i); val b = wanted.getJSONArray(i)
            require(a.length() == b.length())
            for (j in 0 until a.length()) require(exact(a.get(j)) == exact(b.get(j)))
        }
        val reasons = server.getJSONObject("reason_counts"); val expectedReasons = expected.getJSONObject("reason_counts")
        require(reasons.keys().asSequence().toSet() == expectedReasons.keys().asSequence().toSet())
        reasons.keys().forEach { require(integer(reasons, it) == integer(expectedReasons, it)) }
        require(server.get("distance_m") is Number)
        val distance = server.getDouble("distance_m"); val local = expected.getDouble("distance_m")
        require(distance.isFinite() && abs(distance - local) <= maxOf(1e-7, abs(local) * 1e-10)) { "서버 GPS 계산이 기기와 달라요." }
        // Keep only a compact receipt. Full segment arrays can exceed SQLite's CursorWindow.
        return JSONObject(identities).put("version", CALCULATION).put("coordinate_basis", "device-fix-bits-v1")
            .put("distance_m", local).put("recording_duration_nanos", expected.getLong("recording_duration_nanos"))
            .put("point_count", expected.getLong("point_count")).put("segment_count", expected.getLong("segment_count"))
    }

    fun replay(base: WalkMotionContract.Plan): JSONObject {
        val policy = (MotionPolicies.resolveJson(base.input.session.id, base.input.session.motionPolicyJson) as MotionPolicySelection.Supported).policy
        val paths = mutableListOf<MutableList<Long>>()
        val reasons = sortedMapOf<String, Int>()
        val result = replayRecordedMotion(policy, base.input.epochs, base.input.fixes.asSequence()) { step ->
            step.decision?.let { d ->
                d.reasons.forEach { reasons[it.name] = (reasons[it.name] ?: 0) + 1 }
                if (d.connection == Connection.START_NEW) paths.add(mutableListOf(requireNotNull(d.toRef).ingressSeq))
                if (d.connection == Connection.CONTINUE) paths.last().add(requireNotNull(d.toRef).ingressSeq)
            }
        }
        return JSONObject().put("distance_m", result.eligibleDistanceM).put("recording_duration_nanos", result.closedRecordingDurationNanos)
            .put("active_duration_millis", result.closedRecordingDurationNanos / 1_000_000).put("point_count", result.processedObservationCount)
            .put("segment_count", result.segmentCount).put("segments", JSONArray(paths.map { JSONArray(it) })).put("reason_counts", JSONObject(reasons))
    }

    fun manifestDigest(m: JSONObject) = digest(JSONArray(manifestKeys.map { m.get(it) }))
    fun chunkDigest(points: List<JSONObject>) = digest(JSONArray(points.map { p -> JSONArray(pointKeys.map { p.get(it) }) }))
    private fun digest(a: JSONArray) = "sha256:" + MotionPolicies.hash(a.toString())
    private fun integer(o: JSONObject, key: String) = exact(o.get(key))
    private fun exact(value: Any): Long {
        require(value is Int || value is Long)
        return (value as Number).toLong().also { require(it >= 0) }
    }
}
