package com.daengs.app.walk.sync

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.*
import com.daengs.app.walk.motion.*
import org.json.JSONObject
import kotlin.math.abs

/** Reject the entire candidate on an input, page, source edge or metric mismatch. */
internal object WalkMeasurementContract {
    const val VERSION = "walk-measurement-v1"
    private val badTimes = setOf(MotionReason.INVALID_TIME, MotionReason.OUTSIDE_ACTIVE_INTERVAL,
        MotionReason.OUT_OF_ORDER, MotionReason.DUPLICATE, MotionReason.SAME_TIME_CONFLICT)
    fun hash(text: String) = MotionPolicies.hash(text)
    private fun integer(o: JSONObject, key: String): Long {
        val n = o.get(key); require(n is Int || n is Long)
        return (n as Number).toLong()
    }
    private fun near(a: Double, b: Double) {
        require(a.isFinite() && b.isFinite() && abs(a - b) <= maxOf(1e-7, abs(b) * 1e-10))
    }
    private fun objects(o: JSONObject, key: String) = o.getJSONArray(key).let { a ->
        (0 until a.length()).map(a::getJSONObject)
    }

    fun summary(text: String, plan: WalkPrecisionContract.Plan, account: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size <= 1_000_000)
        val s = JSONObject(text)
        val base = plan.base
        require(s.getString("version") == VERSION && s.getString("walk_id") == base.input.session.serverWalkId)
        require(s.getString("base_evidence_fingerprint") == base.evidenceHash && s.getString("precision_fingerprint") == plan.evidenceHash)
        require(s.get("device_result_verified") == false && s.getString("observation_policy_status") == "experimental")
        val m = s.getJSONObject("measurement"); val scope = m.getJSONObject("scope"); val key = m.getJSONObject("key")
        require(scope.getString("owner_id") == account && scope.getString("session_id") == base.input.session.id)
        require(m.getString("measurement_id").matches(Regex("shadow-[0-9a-f]{64}")))
        require(m.getString("result_digest").matches(Regex("[0-9a-f]{64}")))
        require(key.getString("schema_version") == "trajectory-contract-v2" && key.getString("coordinate_basis") == "device-fix-bits-v1")
        require(key.getString("precision_fingerprint") == plan.evidenceHash.removePrefix("sha256:"))
        require(key.getString("journal_fingerprint") == base.evidenceHash.removePrefix("sha256:"))
        val policy = base.manifest.getJSONObject("policy")
        require(key.getString("motion_policy_version") == policy.getString("version") && key.getString("config_hash") == policy.getString("config_hash"))
        require(key.getString("clock_mapping_version") == "motion-sample-support-v1" && key.getString("engine_version") == "motion-v1-trajectory-adapter-v1")
        val chunks = objects(s, "required_route_chunks")
        require(chunks.size <= 2000)
        var points = 0L
        chunks.forEachIndexed { i, c ->
            require(integer(c, "index") == i.toLong() && integer(c, "byte_size") in 1..512_000)
            require(c.getString("sha256").matches(Regex("[0-9a-f]{64}")))
            val count = integer(c, "point_count"); require(count in 1..256); points += count
        }
        require(points == integer(s, "route_point_count") && points <= base.input.fixes.size.toLong() * 4)
        return s
    }

    fun adopt(text: String, pages: List<String>, plan: WalkPrecisionContract.Plan, account: String,
        local: WalkSessionDetail): WalkSessionDetail {
        val s = summary(text, plan, account)
        val input = plan.base.input
        require(local.summary.sessionId == input.session.id && local.observations == input.fixes)
        val m = s.getJSONObject("measurement"); val id = m.getString("measurement_id")
        val descriptors = objects(s, "required_route_chunks")
        require(pages.size == descriptors.size)
        val points = pages.flatMapIndexed { i, raw ->
            val c = descriptors[i]
            require(raw.toByteArray(Charsets.UTF_8).size.toLong() == integer(c, "byte_size") && hash(raw) == c.getString("sha256"))
            val page = JSONObject(raw)
            require(page.getString("version") == VERSION && page.getString("measurement_id") == id && integer(page, "chunk_index") == i.toLong())
            objects(page, "points").also { require(it.size.toLong() == integer(c, "point_count")) }
        }
        val fixes = input.fixes.associateBy { it.clientSeq }
        val byRef = input.fixes.associateBy { Triple(it.sourceEpoch, it.clockEpochId, it.ingressSeq) }
        fun seq(ref: MotionRef) = requireNotNull(byRef[Triple(ref.sourceEpoch, ref.clockEpochId, ref.ingressSeq)]).clientSeq
        val expectedEdges = linkedMapOf<Pair<Int, Int>, Double>()
        val usable = linkedSetOf<Int>()
        val policy = (MotionPolicies.resolveJson(input.session.id, input.session.motionPolicyJson) as MotionPolicySelection.Supported).policy
        val replay = replayRecordedMotion(policy, input.epochs, input.fixes.asSequence()) { step ->
            step.estimate?.let { e -> if (e.positionQuality == PositionQuality.USABLE && e.observedElapsedNanos != null && e.reasons.none { it in badTimes })
                usable += seq(requireNotNull(e.ref)) }
            step.decision?.let { d -> if (d.connection == Connection.CONTINUE) {
                require(d.distanceUse == DistanceUse.INCLUDE)
                expectedEdges[seq(requireNotNull(d.fromRef)) to seq(requireNotNull(d.toRef))] = d.distanceDeltaM
            } }
        }
        val metrics = s.getJSONObject("metrics")
        near(metrics.getDouble("walking_distance_m"), replay.eligibleDistanceM)
        require(integer(s, "motion_recording_duration_ns") == replay.closedRecordingDurationNanos)
        fun fix(ref: JSONObject): RecordedFix {
            require(ref.getString("session_id") == input.session.id && ref.isNull("control_kind"))
            val n = integer(ref, "ingress_seq"); require(n in 0..Int.MAX_VALUE)
            val f = requireNotNull(fixes[n.toInt()])
            require(ref.getString("source_epoch") == f.sourceEpoch && ref.getString("clock_epoch_id") == f.clockEpochId)
            return f
        }
        // Wire order is walking sections first, then observed runs; a group cannot reappear.
        val groups = linkedMapOf<Pair<String, Int>, MutableList<JSONObject>>()
        var previous: Pair<String, Int>? = null
        points.forEach { p ->
            val kind = p.getString("kind"); require(kind in setOf("walking_section", "observed_run"))
            val index = integer(p, "section_index"); require(index in 0..100_000)
            val group = kind to index.toInt()
            if (group != previous) require(group !in groups)
            val list = groups.getOrPut(group) { mutableListOf() }
            require(integer(p, "point_index") == list.size.toLong())
            val f = fix(p.getJSONObject("ref"))
            require(f.clientSeq in usable && p.getDouble("lat") == f.lat && p.getDouble("lng") == f.lng)
            require(integer(p, "wall_time_millis") == f.atMillis && integer(p, "elapsed_ns") == f.elapsedRealtimeNanos)
            require(p.getDouble("walking_distance_m").isFinite() && p.getDouble("walking_distance_m") >= 0)
            if (list.isNotEmpty()) require(p.getString("section_id") == list.first().getString("section_id"))
            list += p; previous = group
        }
        val seen = linkedMapOf<Pair<Int, Int>, Double>()
        var cumulative = 0.0
        val offsets = mutableMapOf<String, Long>(); var closed = 0L
        input.epochs.forEach { offsets[it.id] = closed; closed = Math.addExact(closed,
            requireNotNull(it.endedElapsedNanos) - it.startedElapsedNanos) }
        val epochs = input.epochs.associateBy { it.id }
        val routes = mutableListOf<WalkRouteSegment>()
        val samples = mutableListOf<List<LocationSample>>()
        val observed = mutableListOf<List<RecordedFix>>()
        groups.forEach { (group, list) ->
            val walking = group.first == "walking_section"
            require(list.size >= 2 && group.second == if (walking) routes.size else observed.size)
            require(walking || routes.size.toLong() == integer(s, "walking_section_count"))
            val raw = list.map { fix(it.getJSONObject("ref")) }
            require(list.first().getDouble("walking_distance_m") == 0.0)
            for (i in 1 until raw.size) {
                val a = raw[i-1]; val b = raw[i]
                require(a.clientSeq < b.clientSeq && a.sourceEpoch == b.sourceEpoch && a.clockEpochId == b.clockEpochId)
                require(requireNotNull(b.elapsedRealtimeNanos) > requireNotNull(a.elapsedRealtimeNanos))
                if (walking) {
                    val edge = a.clientSeq to b.clientSeq
                    require(edge !in seen)
                    val distance = list[i].getDouble("walking_distance_m")
                    near(distance, requireNotNull(expectedEdges[edge])); seen[edge] = distance
                }
            }
            if (walking) {
                val route = raw.mapIndexed { i, f ->
                    cumulative += list[i].getDouble("walking_distance_m")
                    val nanos = requireNotNull(f.elapsedRealtimeNanos)
                    val elapsed = (offsets.getValue(requireNotNull(f.sourceEpoch)) + nanos - epochs.getValue(f.sourceEpoch).startedElapsedNanos) / 1_000_000
                    val speed = if (i == 0) null else list[i].getDouble("walking_distance_m") / ((nanos - requireNotNull(raw[i-1].elapsedRealtimeNanos)) / 1e9)
                    WalkRoutePoint(GeoPoint(f.lat, f.lng), f.atMillis, f.accuracyM, elapsed, cumulative, speed, group.second, i, nanos)
                }
                routes += WalkRouteSegment(group.second, route)
                samples += route.map { LocationSample(it.point, it.capturedAtMillis, accuracyMeters = it.accuracyMeters, elapsedRealtimeNanos = it.elapsedRealtimeNanos) }
            } else observed += raw
        }
        require(seen.keys == expectedEdges.keys)
        near(cumulative, replay.eligibleDistanceM)
        require(routes.size.toLong() == integer(s, "walking_section_count") && observed.size.toLong() == integer(s, "observed_run_count"))
        val wallTimes = objects(s, "boundary_wall_times")
        val boundaries = s.getJSONObject("boundaries")
        val names = listOf("record_start", "record_end", "first_observed", "last_observed", "first_walking", "last_walking")
        val expectedSeqs = mapOf("first_observed" to usable.firstOrNull(), "last_observed" to usable.lastOrNull(),
            "first_walking" to seen.keys.firstOrNull()?.first, "last_walking" to seen.keys.lastOrNull()?.second)
        val ends = names.associateWith { name ->
            if (boundaries.isNull(name)) {
                require(name in expectedSeqs && expectedSeqs[name] == null); null
            } else {
                val ref = boundaries.getJSONObject(name)
                val t = wallTimes.single { listOf("session_id", "source_epoch", "clock_epoch_id", "ingress_seq", "control_kind").all { key -> it.getJSONObject("ref").get(key) == ref.get(key) } }
                val at = integer(t, "original_wall_time_millis")
                if (name in expectedSeqs) {
                    val f = fix(ref); require(f.clientSeq == expectedSeqs[name] && at == f.atMillis)
                    WalkMeasurementBoundary(requireNotNull(f.sourceEpoch), requireNotNull(f.clockEpochId), f.clientSeq, null, at, GeoPoint(f.lat, f.lng))
                } else {
                    val start = name == "record_start"; val epoch = if (start) input.epochs.first() else input.epochs.last()
                    require(ref.getString("session_id") == input.session.id && ref.isNull("ingress_seq") &&
                        ref.getString("source_epoch") == epoch.id && ref.getString("clock_epoch_id") == epoch.clockEpochId &&
                        ref.getString("control_kind") == if (start) "epoch_start" else "epoch_end")
                    require(at == if (start) epoch.startedAtMillis else epoch.endedAtMillis)
                    WalkMeasurementBoundary(epoch.id, epoch.clockEpochId, null, ref.getString("control_kind"), at, null)
                }
            }
        }
        val route = WalkSessionRoute(routes)
        return local.copy(summary = local.summary.copy(distanceMeters = metrics.getDouble("walking_distance_m"),
            activeDurationMillis = replay.closedRecordingDurationNanos / 1_000_000, segments = samples, anchor = route.start?.point),
            route = route, legacyRouteEvidence = null,
            measurement = WalkMeasurementDetail(id, m.getString("result_digest"), ends, observed))
    }
}
