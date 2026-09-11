package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.pin.ActionPin
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

const val WALK_BEHAVIOR_COMPARISON_VERSION = "walk-behavior-comparison-v1"

/** The behavior only selects a subset; it never changes the baseline walk selector. */
data class WalkBehaviorComparisonQuery(val spatial: SpatialDiaryQuery, val behavior: WalkMomentType) {
    init { require(behavior != WalkMomentType.NOTE) }
    fun toJson(): JSONObject = JSONObject().apply {
        put("comparison_version", WALK_BEHAVIOR_COMPARISON_VERSION)
        put("walk_selector", spatial.toJson().getJSONObject("walk_selector"))
        put("behavior_code", behavior.behaviorCode)
    }
    companion object {
        fun parse(json: JSONObject): WalkBehaviorComparisonQuery {
            require(json.getString("comparison_version") == WALK_BEHAVIOR_COMPARISON_VERSION)
            return WalkBehaviorComparisonQuery(SpatialDiaryQuery.parse(JSONObject()
                .put("view_version", SPATIAL_DIARY_VIEW_VERSION)
                .put("walk_selector", json.getJSONObject("walk_selector"))
                .put("field_metric", "walk_utilization")),
                WalkMomentType.entries.single { it.behaviorCode == json.getString("behavior_code") })
        }
    }
}

data class BehaviorWalkGroup(val walkIds: List<String>, val field: SpatialDiaryField) {
    init {
        require(walkIds.distinct().size == walkIds.size)
        require(field.metric == SpatialDiaryMetric.WALK_UTILIZATION && field.unit == "share")
        require(field.normalization == "equal_contributing_walks")
        require(field.denominator == walkIds.size.toDouble())
        require(walkIds.isNotEmpty() || field.cells.isEmpty())
    }
    companion object {
        fun parse(json: JSONObject) = BehaviorWalkGroup(json.getJSONArray("walk_ids").strings(),
            SpatialDiaryField.parse(json.getJSONObject("field")))
    }
}

data class BehaviorComparisonSummary(val selectedWalkCount: Int, val excludedEmptyWalkCount: Int,
    val entryCount: Int, val recordedDayCount: Int, val unlocatedEntryCount: Int)

data class BehaviorComparisonEvidence(val walkId: String, val clientSessionId: String?,
    val entryRevision: Int, val pinRevision: Int, val content: WalkEntry) {
    val key: String get() = "$walkId:${content.id}"
    // The server also projects legacy content into a pin. Raw GPS is never a display fallback.
    val point: GeoPoint? get() = content.pin?.point
    val locationLabel: String get() = content.pin?.label ?: "위치 없이 남긴 행동"
    companion object {
        fun parse(json: JSONObject): BehaviorComparisonEvidence {
            val walkId = json.getString("walk_id")
            return BehaviorComparisonEvidence(walkId,
                if (json.isNull("client_session_id")) null else json.getString("client_session_id"),
                json.getInt("entry_revision"), json.getInt("pin_revision"),
                WalkEntry.parse(json.getString("entry_id"), walkId, json).copy(
                    pin = json.optJSONObject("pin")?.let { ActionPin(it.toString()) }))
        }
    }
}

data class BehaviorComparisonReceipt(val sourceRevision: String, val viewAsOf: Instant,
    val paintFingerprint: String, val contextPolicyVersion: Int, val aggregationVersion: Int)

data class WalkBehaviorComparison(val query: WalkBehaviorComparisonQuery,
    val projection: SpatialDiaryProjection, val baseline: BehaviorWalkGroup, val matching: BehaviorWalkGroup,
    val summary: BehaviorComparisonSummary, val evidence: List<BehaviorComparisonEvidence>,
    val receipt: BehaviorComparisonReceipt) {
    init {
        require(baseline.walkIds.containsAll(matching.walkIds))
        require(summary.selectedWalkCount == baseline.walkIds.size + summary.excludedEmptyWalkCount)
        require(summary.excludedEmptyWalkCount >= 0 && summary.recordedDayCount >= 0)
        require(summary.entryCount == evidence.size && summary.unlocatedEntryCount == evidence.count { it.point == null })
        require(evidence.map { it.walkId to it.content.id }.distinct().size == evidence.size)
        require(evidence.map { it.walkId }.toSet() == matching.walkIds.toSet())
        require(evidence.all { it.content.petId == query.spatial.petId && it.content.type == query.behavior })
        require(receipt.paintFingerprint == projection.paintFingerprint && receipt.sourceRevision.isNotBlank())
        require(receipt.contextPolicyVersion == SPATIAL_DIARY_CONTEXT_POLICY_VERSION)
        require(receipt.aggregationVersion == SPATIAL_DIARY_AGGREGATION_VERSION)
    }
    val sameWalks: Boolean get() = baseline.walkIds.toSet() == matching.walkIds.toSet()
    companion object {
        fun parse(json: JSONObject): WalkBehaviorComparison {
            val summary = json.getJSONObject("summary")
            val receipt = json.getJSONObject("receipt")
            val evidence = json.getJSONArray("evidence")
            return WalkBehaviorComparison(WalkBehaviorComparisonQuery.parse(json.getJSONObject("spec")),
                SpatialDiaryProjection.parse(json.getJSONObject("projection")),
                BehaviorWalkGroup.parse(json.getJSONObject("baseline")), BehaviorWalkGroup.parse(json.getJSONObject("matching")),
                BehaviorComparisonSummary(summary.getInt("selected_walk_count"), summary.getInt("excluded_empty_walk_count"),
                    summary.getInt("entry_count"), summary.getInt("recorded_day_count"), summary.getInt("unlocated_entry_count")),
                (0 until evidence.length()).map { BehaviorComparisonEvidence.parse(evidence.getJSONObject(it)) },
                BehaviorComparisonReceipt(receipt.getString("source_revision"), Instant.parse(receipt.getString("view_as_of")),
                    receipt.getString("paint_fp"), receipt.getInt("context_policy_version"), receipt.getInt("aggregation_version")))
        }
    }
}

private fun JSONArray.strings() = (0 until length()).map(::getString)
