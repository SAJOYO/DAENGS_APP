package com.daengs.app.walk.sync

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordingEpoch
import com.daengs.app.walk.motion.*
import org.json.JSONArray
import org.json.JSONObject

/** DEV #449 wire contract. Array order and Float bits are shared with its golden fixture. */
internal object WalkMotionContract {
    const val VERSION = "gps-motion-backup-v1"
    const val CHUNK_SIZE = 256
    const val MAX_POINTS = 100_000
    val epochKeys = listOf("source_epoch", "clock_epoch_id", "chain_index", "started_at_millis",
        "started_elapsed_nanos", "first_ingress_seq", "ended_at_millis", "ended_elapsed_nanos",
        "target_ingress_seq", "persisted_count", "end_kind", "drained", "failure_reason", "first_failed_seq")
    val pointKeys = listOf("client_seq", "source_epoch", "clock_epoch_id", "chain_index",
        "elapsed_realtime_nanos", "received_elapsed_nanos", "received_at_millis", "speed_mps_bits",
        "speed_accuracy_mps_bits", "bearing_degrees_bits", "bearing_accuracy_degrees_bits", "provider", "recording_eligible")

    data class Plan(val input: RecordedMotionInput, val manifest: JSONObject, val points: List<JSONObject>) {
        val manifestHash = manifestDigest(manifest)
        val chunks = points.chunked(CHUNK_SIZE)
        val chunkHashes = chunks.map(::chunkDigest)
        val evidenceHash = digest(JSONArray().put(VERSION).put(manifestHash).put(JSONArray(chunkHashes)))
        fun receipt() = JSONObject().put("manifest_fingerprint", manifestHash).put("evidence_fingerprint", evidenceHash)
        fun chunk(index: Int) = JSONObject().put("manifest_fingerprint", manifestHash).put("points", JSONArray(chunks[index]))
    }

    fun measured(session: RecordedSession): Boolean = when (val choice = MotionPolicies.resolveJson(session.id, session.motionPolicyJson)) {
        MotionPolicySelection.Legacy -> false
        is MotionPolicySelection.Supported -> choice.policy.stored.measurementVersion != null
        is MotionPolicySelection.Unsupported -> error("GPS 측정 정책을 해석하지 못했어요: ${choice.reason}")
    }

    fun create(input: RecordedMotionInput): Plan {
        val policy = (MotionPolicies.resolveJson(input.session.id, input.session.motionPolicyJson) as? MotionPolicySelection.Supported)?.policy
            ?: error("측정 정책이 없어요.")
        require(policy.stored.measurementVersion == MotionPolicies.MEASUREMENT_VERSION)
        val stored = policy.stored
        val manifest = JSONObject().put("version", VERSION).put("client_session_id", input.session.id)
            .put("raw_input_fingerprint", WalkRecordingContract.rawFingerprint(input.fixes))
            .put("point_count", input.fixes.size).put("policy", JSONObject()
                .put("version", stored.version).put("observation_schema_version", stored.observationSchemaVersion)
                .put("measurement_version", stored.measurementVersion).put("config_json", stored.configJson).put("config_hash", stored.configHash))
            .put("epochs", JSONArray(input.epochs.map { e -> objectOf(epochKeys, listOf(e.id, e.clockEpochId,
                e.chainIndex, e.startedAtMillis, e.startedElapsedNanos, e.firstIngressSeq, e.endedAtMillis,
                e.endedElapsedNanos, e.targetIngressSeq, e.persistedCount, e.endKind, e.drained, e.failureReason, e.firstFailedSeq)) }))
        val points = input.fixes.map { p ->
            require(p.ingressSeq == p.clientSeq.toLong()) { "GPS 접수 번호가 달라요." }
            objectOf(pointKeys, listOf(p.clientSeq, p.sourceEpoch, p.clockEpochId, p.chainIndex,
                p.elapsedRealtimeNanos, p.receivedElapsedNanos, p.receivedAtMillis, bits(p.speedMps),
                bits(p.speedAccuracyMps), bits(p.bearingDegrees), bits(p.bearingAccuracyDegrees), p.provider, p.recordingEligible))
        }
        return read(input.session, input.fixes, manifest, points)
    }

    fun read(session: RecordedSession, raw: List<RecordedFix>, manifest: JSONObject, points: List<JSONObject>): Plan {
        require(manifest.keySet() == setOf("version", "client_session_id", "raw_input_fingerprint", "point_count", "policy", "epochs"))
        require(manifest.getString("version") == VERSION && manifest.getString("client_session_id") == session.id)
        require(session.id.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
        require(raw.size <= MAX_POINTS && manifest.long("point_count") == raw.size.toLong() && points.size == raw.size)
        require(raw.map { it.clientSeq } == raw.indices.toList())
        require(manifest.getString("raw_input_fingerprint") == WalkRecordingContract.rawFingerprint(raw)) { "서버 GPS 원본 지문이 달라요." }
        val p = manifest.getJSONObject("policy")
        require(p.keySet() == setOf("version", "observation_schema_version", "measurement_version", "config_json", "config_hash"))
        require(p.long("observation_schema_version") == 15L && p.getString("config_json").length <= 8192)
        val stored = StoredMotionPolicy(p.getString("version"), 15, p.getString("config_json"), p.getString("config_hash"), p.getString("measurement_version"))
        val policy = (MotionPolicies.resolve(session.id, stored) as? MotionPolicySelection.Supported)?.policy ?: error("미지원 측정 정책이에요.")
        require(stored.measurementVersion == MotionPolicies.MEASUREMENT_VERSION)
        val epochArray = manifest.getJSONArray("epochs")
        require(epochArray.length() in 1..1024)
        val epochs = (0 until epochArray.length()).map { i ->
            val e = epochArray.getJSONObject(i)
            require(e.keySet() == epochKeys.toSet() && e.isNull("failure_reason") && e.isNull("first_failed_seq"))
            require(e.get("drained") == true)
            RecordingEpoch(e.identity("source_epoch"), session.id, e.identity("clock_epoch_id"), e.seq("chain_index"),
                e.long("started_at_millis"), e.long("started_elapsed_nanos"), e.seq("first_ingress_seq").toLong(),
                e.long("ended_at_millis"), e.long("ended_elapsed_nanos"), e.getString("end_kind"),
                e.long("target_ingress_seq", -1).also { require(it <= MAX_POINTS) }, e.seq("persisted_count").toLong(), drained = true)
        }
        require(epochs.last().endedAtMillis == session.endedAtMillis)
        require(epochs.map { it.id }.distinct().size == epochs.size && epochs.map { it.clockEpochId }.distinct().size == 1)
        var next = 0L
        var duration = 0L
        epochs.forEachIndexed { i, e ->
            require(e.firstIngressSeq == next && e.targetIngressSeq == next + e.persistedCount - 1)
            require(e.endKind == if (i == epochs.lastIndex) "STOP" else "PAUSE")
            val end = requireNotNull(e.endedElapsedNanos)
            require(end >= e.startedElapsedNanos)
            if (i > 0) require(e.chainIndex > epochs[i-1].chainIndex && e.startedElapsedNanos >= requireNotNull(epochs[i-1].endedElapsedNanos))
            duration = Math.addExact(duration, end - e.startedElapsedNanos)
            next += e.persistedCount
        }
        require(next == raw.size.toLong())
        val fixes = points.mapIndexed { i, q ->
            require(q.keySet() == pointKeys.toSet())
            require(q.seq("client_seq") == i && q.seq("chain_index") == raw[i].chainIndex)
            require(q.get("recording_eligible") is Boolean && raw[i].recordingEligible == q.get("recording_eligible"))
            val provider = q.nullString("provider")?.also { require(it.matches(Regex("[a-zA-Z0-9_.-]{0,64}"))) }
            raw[i].copy(ingressSeq = i.toLong(), sourceEpoch = q.identity("source_epoch"), clockEpochId = q.identity("clock_epoch_id"),
                elapsedRealtimeNanos = q.nullLong("elapsed_realtime_nanos"), receivedElapsedNanos = q.nullLong("received_elapsed_nanos"),
                receivedAtMillis = q.nullLong("received_at_millis"), speedMps = q.floatBits("speed_mps_bits"),
                speedAccuracyMps = q.floatBits("speed_accuracy_mps_bits"), bearingDegrees = q.floatBits("bearing_degrees_bits"),
                bearingAccuracyDegrees = q.floatBits("bearing_accuracy_degrees_bits"), provider = provider)
        }
        replayRecordedMotion(policy, epochs, fixes.asSequence())
        return Plan(RecordedMotionInput(session.copy(motionPolicyJson = MotionPolicies.encode(policy)), epochs, fixes), manifest, points)
    }

    fun validateStatus(value: JSONObject, plan: Plan, complete: Boolean): List<Int> {
        require(value.getString("version") == VERSION && value.get("calculation_verified") == false)
        require(value.getString("manifest_fingerprint") == plan.manifestHash)
        require(manifestDigest(value.getJSONObject("manifest")) == plan.manifestHash)
        require(value.getJSONObject("manifest").getJSONObject("policy").getString("config_json") == plan.manifest.getJSONObject("policy").getString("config_json"))
        val array = value.getJSONArray("received_chunks")
        val indices = (0 until array.length()).map { index -> exactLong(array.get(index), 0).also { require(it < plan.chunks.size) }.toInt() }
        require(indices == indices.distinct().sorted())
        val state = value.getString("state")
        require(state == "collecting" || state == "complete")
        if (complete || state == "complete") {
            require(state == "complete" && indices == plan.chunks.indices.toList() && value.getString("evidence_fingerprint") == plan.evidenceHash)
        } else require(value.has("evidence_fingerprint") && value.isNull("evidence_fingerprint"))
        return indices
    }

    fun manifestDigest(m: JSONObject): String {
        val p = m.getJSONObject("policy")
        val epochs = m.getJSONArray("epochs")
        return digest(JSONArray(listOf(m.getString("version"), m.getString("client_session_id"), m.getString("raw_input_fingerprint"),
            m.long("point_count"), p.getString("version"), p.long("observation_schema_version"), p.getString("measurement_version"),
            p.getString("config_hash"), JSONArray((0 until epochs.length()).map { row(epochs.getJSONObject(it), epochKeys) }))))
    }
    fun chunkDigest(points: List<JSONObject>) = digest(JSONArray(points.map { row(it, pointKeys) }))
    private fun row(value: JSONObject, keys: List<String>) = JSONArray(keys.map { value.get(it) })
    private fun objectOf(keys: List<String>, values: List<Any?>) = JSONObject().apply { keys.zip(values).forEach { (k,v) -> put(k,v ?: JSONObject.NULL) } }
    private fun bits(value: Float?) = value?.toRawBits()?.toUInt()?.toString(16)?.padStart(8, '0')
    private fun digest(value: JSONArray) = "sha256:" + MotionPolicies.hash(value.toString())
    private fun JSONObject.keySet() = keys().asSequence().toSet()
    private fun JSONObject.identity(key: String) = getString(key).also { require(it.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) }
    private fun JSONObject.seq(key: String) = long(key).also { require(it <= MAX_POINTS) }.toInt()
    internal fun JSONObject.long(key: String, minimum: Long = 0) = exactLong(get(key), minimum)
    private fun exactLong(value: Any, minimum: Long): Long {
        require(value is Int || value is Long) { "GPS 정수 정밀도가 손실됐어요." }
        return (value as Number).toLong().also { require(it >= minimum) }
    }
    private fun JSONObject.nullLong(key: String) = if (get(key) == JSONObject.NULL) null else long(key)
    private fun JSONObject.nullString(key: String) = if (get(key) == JSONObject.NULL) null else getString(key)
    private fun JSONObject.floatBits(key: String): Float? = nullString(key)?.let {
        require(it.matches(Regex("[0-9a-f]{8}"))); Float.fromBits(it.toLong(16).toInt())
    }
}
