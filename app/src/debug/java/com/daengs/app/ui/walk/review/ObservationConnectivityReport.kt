package com.daengs.app.ui.walk.review

import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.StoryboardObservationIndex
import com.daengs.app.walk.distanceTo
import com.daengs.app.walk.trajectory.*
import org.json.JSONArray
import org.json.JSONObject

/** Diagnostic only. Reports candidate correspondence without changing production scene bindings. */
internal fun observationConnectivityReport(detail: WalkSessionDetail, scenes: List<DiaryScene>): JSONObject {
    val policy = LegacyConnectionPolicy()
    val result = evaluateLegacyObservationConnections(detail, policy)
    val basis = detail.legacyRouteEvidence
    val observationIndex = StoryboardObservationIndex(detail.summary, detail.observations)
    val qualities = result.observations.associateBy { it.fix.clientSeq }
    val variations = listOf(
        "accuracy_15m" to policy.copy(maxAccuracyMeters = 15.0),
        "accuracy_35m" to policy.copy(maxAccuracyMeters = 35.0),
        "interval_5s" to policy.copy(maxIntervalMillis = 5_000),
        "interval_15s" to policy.copy(maxIntervalMillis = 15_000),
        "adjusted_speed_25mps" to policy.copy(maxAdjustedSpeedMps = 25.0),
        "adjusted_speed_45mps" to policy.copy(maxAdjustedSpeedMps = 45.0),
        "velocity_change_4mps2" to policy.copy(maxVelocityChangeMps2 = 4.0),
        "velocity_change_12mps2" to policy.copy(maxVelocityChangeMps2 = 12.0),
        "support_2edges" to policy.copy(minConsistentEdges = 2),
        "support_4edges" to policy.copy(minConsistentEdges = 4),
    )
    return JSONObject().apply {
        put("format", "walk-observation-connectivity-review-v1")
        put("stage", result.stage)
        put("input_scope", "provided_legacy_observations")
        put("speed_source", "adjacent_raw_coordinates")
        put("unsupported_reason", result.unsupportedReason ?: JSONObject.NULL)
        put("reader_version", basis?.readerVersion ?: JSONObject.NULL)
        put("policy", policy.json())
        put("summary", result.counts())
        put("observations", JSONArray().apply { result.observations.forEach { assessed ->
            val fix = assessed.fix
            put(JSONObject().put("seq", fix.clientSeq).put("chain", fix.chainIndex).put("at_ms", fix.atMillis)
                .put("position_quality", assessed.quality.name).put("position_reasons", assessed.reasons.names())
                .put("recorder_disposition", basis?.disposition(fix.clientSeq)?.name ?: JSONObject.NULL)
                .put("recorder_previous_accepted_seq", basis?.previousAcceptedSeq(fix.clientSeq) ?: JSONObject.NULL))
        } })
        put("owners", JSONArray().apply { result.owners.forEach { owner ->
            put(JSONObject().put("from_seq", owner.edge.fromSeq).put("to_seq", owner.edge.toSeq)
                .put("segment", owner.edge.after.segmentIndex).put("ending_point_index", owner.edge.after.pointIndex)
                .put("distance_contribution_m", owner.distanceContributionMeters.takeIf { it.isFinite() } ?: JSONObject.NULL)
                .put("distance_contribution_valid", owner.distanceContributionMeters.isFinite()))
        } })
        put("intervals", JSONArray().apply { result.intervals.forEach { interval ->
            put(JSONObject().put("from_seq", interval.fromSeq).put("to_seq", interval.toSeq)
                .put("connection", interval.connection.name).put("clock", interval.clock.name)
                .put("connection_reasons", interval.reasons.names()).put("walking_use", interval.walkingUse.name)
                .put("owner_to_seq", interval.ownerToSeq ?: JSONObject.NULL)
                .put("accounting_reasons", interval.accountingReasons.names()).put("auxiliary_use", interval.auxiliaryUse.name)
                .put("kinematics", interval.kinematics?.let { k -> JSONObject().put("seconds", k.seconds)
                    .put("displacement_m", k.displacementMeters).put("coordinate_speed_mps", k.coordinateSpeedMps)
                    .put("accuracy_allowance_m", k.accuracyAllowanceMeters).put("adjusted_speed_mps", k.adjustedSpeedMps)
                } ?: JSONObject.NULL))
        } })
        put("neighbor_checks", JSONArray().apply { result.neighbors.forEach { n ->
            put(JSONObject().put("from_seq", n.fromSeq).put("through_seq", n.throughSeq).put("to_seq", n.toSeq)
                .put("velocity_change_mps", n.velocityChangeMps).put("allowance_mps", n.allowanceMps).put("consistent", n.consistent))
        } })
        put("auxiliary_runs", result.runsJson())
        put("transitions", JSONArray().apply { result.transitions.forEach { transition ->
            put(JSONObject().put("at_seq", transition.atSeq)
                .put("before_connection", transition.beforeConnection.name).put("after_connection", transition.afterConnection.name)
                .put("before_walking_use", transition.beforeWalkingUse.name).put("after_walking_use", transition.afterWalkingUse.name)
                .put("before_auxiliary_use", transition.beforeAuxiliaryUse.name).put("after_auxiliary_use", transition.afterAuxiliaryUse.name))
        } })
        put("scenes", JSONArray().apply { scenes.forEachIndexed { i, scene ->
            val anchor = scene.source?.observation
            val verified = observationIndex.resolve(anchor)?.let { source ->
                scene.sessionId == detail.summary.sessionId && scene.point?.distanceTo(source)?.let { it <= 1.0 } == true
            } == true
            val relation = when {
                !verified || anchor == null -> "UNRESOLVED"
                anchor.atMillis < scene.atMillis -> "BEFORE_EVENT"
                anchor.atMillis > scene.atMillis -> "AFTER_EVENT"
                else -> "AT_EVENT"
            }
            put(JSONObject().put("scene", i + 1).put("event_at_ms", scene.atMillis)
                .put("observation_seq", anchor?.clientSeq ?: JSONObject.NULL)
                .put("location_at_ms", anchor?.atMillis ?: JSONObject.NULL).put("source_verified", verified)
                .put("location_relation_to_event", relation)
                .put("position_quality", qualities[anchor?.clientSeq]?.quality?.name ?: JSONObject.NULL)
                .put("incident_intervals_to_seq", JSONArray(result.intervals.filter {
                    verified && (it.fromSeq == anchor?.clientSeq || it.toSeq == anchor?.clientSeq)
                }.map { it.toSeq }))
                .put("candidate_run_indexes", JSONArray(result.auxiliaryRuns.withIndex().filter {
                    verified && anchor?.clientSeq in it.value.sourceSeqs
                }.map { it.index })))
        } })
        put("sensitivity", JSONArray().apply { variations.forEach { (name, variant) ->
            val comparison = evaluateLegacyObservationConnections(detail, variant)
            val changes = result.intervals.zip(comparison.intervals).filter { (a, b) ->
                a.connection != b.connection || a.auxiliaryUse != b.auxiliaryUse
            }
            put(JSONObject().put("variation", name).put("policy", variant.json()).put("summary", comparison.counts())
                .put("changed_interval_count", changes.size).put("first_changed_from_seq", changes.firstOrNull()?.first?.fromSeq ?: JSONObject.NULL)
                .put("auxiliary_runs", comparison.runsJson()))
        } })
    }
}

private fun LegacyConnectionPolicy.json() = JSONObject().put("version", version)
    .put("max_accuracy_m", maxAccuracyMeters).put("min_interval_ms", minIntervalMillis).put("max_interval_ms", maxIntervalMillis)
    .put("max_adjusted_speed_mps", maxAdjustedSpeedMps).put("max_velocity_change_mps2", maxVelocityChangeMps2)
    .put("min_consistent_edges", minConsistentEdges).put("max_clock_difference_ms", maxClockDifferenceMillis)

private fun LegacyConnectivityReview.counts() = JSONObject().apply {
    put("observations", observations.size); put("intervals", intervals.size); put("owners", owners.size)
    put("auxiliary_runs", auxiliaryRuns.size)
    ObservationConnection.entries.forEach { state -> put(state.name, intervals.count { it.connection == state }) }
    AuxiliaryUse.entries.forEach { use -> put(use.name, intervals.count { it.auxiliaryUse == use }) }
}

private fun LegacyConnectivityReview.runsJson() = JSONArray().apply { auxiliaryRuns.forEach { run ->
    put(JSONObject().put("from_seq", run.fromSeq).put("to_seq", run.toSeq).put("walking_use", run.walkingUse.name)
        .put("source_seqs", JSONArray(run.sourceSeqs)))
} }

private fun List<ConnectionReason>.names() = JSONArray(map { it.name })
