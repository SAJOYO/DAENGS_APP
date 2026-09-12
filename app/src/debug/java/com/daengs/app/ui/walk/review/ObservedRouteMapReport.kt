package com.daengs.app.ui.walk.review

import com.daengs.app.map.layers.completedroute.RecordPresentationPolicy
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import com.daengs.app.walk.trajectory.*
import org.json.JSONArray
import org.json.JSONObject

/** Describes the policy consumed by both real detail and review UI; legacy fields stay comparable. */
internal fun observedRouteMapReport(review: CompletedRouteReview, scenes: List<DiaryScene>) = JSONObject().apply {
    val policy = review.observed.assessment.policy
    put("format", "walk-observed-map-review-v1")
    put("display_policy", ObservedRouteDisplayPolicy.VERSION)
    put("style_policy", RecordPresentationPolicy.VERSION)
    put("connection_policy", JSONObject().put("version", policy.version).put("max_accuracy_m", policy.maxAccuracyMeters)
        .put("min_interval_ms", policy.minIntervalMillis).put("max_interval_ms", policy.maxIntervalMillis)
        .put("max_adjusted_speed_mps", policy.maxAdjustedSpeedMps).put("max_velocity_change_mps2", policy.maxVelocityChangeMps2)
        .put("min_consistent_edges", policy.minConsistentEdges).put("max_clock_difference_ms", policy.maxClockDifferenceMillis))
    put("direction_margin_m", ObservedRouteDisplayPolicy.DIRECTION_DISPLACEMENT_MARGIN_METERS)
    put("direction_context_ms", ObservedRouteDisplayPolicy.DIRECTION_CONTEXT_MILLIS)
    put("unsupported_reason", review.observed.assessment.unsupportedReason ?: JSONObject.NULL)
    put("sections", JSONArray().apply { review.observed.sections.forEach { put(it.report()) } })
    put("scenes", JSONArray().apply { scenes.forEachIndexed { i, scene ->
        val focus = review.recordSceneFocus(scene)
        put(JSONObject().put("scene", i + 1).put("relation", focus.relation.name)
            .put("walking_path_count", focus.paths.size).put("point_preserved", focus.point == scene.point)
            .put("event_at_ms", scene.atMillis).put("location_at_ms", focus.binding?.locationAtMillis ?: JSONObject.NULL)
            .put("observed_parts", JSONArray().apply { focus.observedParts.forEach { put(it.report()) } }))
    } })
}

private fun ObservedRouteSection.report() = JSONObject().put("id", id).put("walking_use", walkingUse.name)
    .put("source_seqs", JSONArray(fixes.map { it.clientSeq }))
    .put("started_at_ms", startedAtMillis).put("ended_at_ms", endedAtMillis)
    .put("directions", JSONArray().apply { directions.forEach {
        put(JSONObject().put("from_seq", it.fromSeq).put("to_seq", it.toSeq)
            .put("evidence_from_seq", it.evidenceFromSeq).put("evidence_to_seq", it.evidenceToSeq))
    } })
