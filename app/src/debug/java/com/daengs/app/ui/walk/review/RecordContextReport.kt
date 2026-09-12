package com.daengs.app.ui.walk.review

import com.daengs.app.ui.walk.recordContextLayer
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import com.daengs.app.walk.trajectory.ConfirmedRecordLocation
import org.json.JSONArray
import org.json.JSONObject

internal fun recordContextReport(review: CompletedRouteReview) = JSONObject().apply {
    put("format", "record-context-review-v1"); put("available", review.context.available)
    put("record_duration_ms", review.context.durationMillis ?: JSONObject.NULL)
    put("contexts", JSONArray().apply { review.context.contexts.forEach { value ->
        put(JSONObject().put("id", value.id).put("kind", value.kind.name)
            .put("from_seq", value.fromSeq ?: JSONObject.NULL).put("to_seq", value.toSeq ?: JSONObject.NULL)
            .put("from_ms", value.fromMillis).put("to_ms", value.toMillis)
            .put("duration_ms", value.durationMillis ?: JSONObject.NULL)
            .put("before", value.before.json()).put("after", value.after.json())
            .put("before_movement", value.beforeMovement?.name ?: JSONObject.NULL)
            .put("after_movement", value.afterMovement?.name ?: JSONObject.NULL)
            .put("reasons", JSONArray(value.reasons.map { it.name }.sorted()))
            .put("walking_at_ms", value.walkingEndpoint?.capturedAtMillis ?: JSONObject.NULL)
            .put("has_selected_guide", recordContextLayer(review, value)?.selectedGapGuide != null))
    } })
}
private fun ConfirmedRecordLocation?.json(): Any = if (this == null) JSONObject.NULL else
    JSONObject().put("seq", seq).put("at_ms", atMillis)
