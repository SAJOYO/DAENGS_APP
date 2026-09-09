package com.daengs.app.place

import kotlinx.serialization.json.*

fun filterEditFixture(query: PlaceFilterEditQuery = PlaceFilterEditQuery("주차 조건 빼줘", filterRequestFixture()), confirmation: Boolean = false, unresolved: Boolean = false): PlaceFilterEditResponse {
    val next = query.base.criteria.copy(all = emptyList()).state(query.base.request)
    return PlaceFilterEditResponse(buildJsonObject {
        put("contract_version", "place-filter-edit-v1"); put("search_id", "00000000-0000-0000-0000-000000000123"); put("revision", 1)
        put("request", query.toJson()); put("expires_at", "2026-09-09T10:00:00Z"); put("action_request", JsonNull); put("result", JsonNull)
        put("compiled", buildJsonObject {
            put("status", if (confirmation || unresolved) "needs_resolution" else "ready")
            put("base_state", query.base.state); put("proposed_state", if (unresolved) JsonNull else next); put("requires_confirmation", confirmation)
            put("issues", JsonArray(if (unresolved) listOf(buildJsonObject { put("code", "unsupported"); put("message", "지원하지 않는 조건이에요."); put("target_ids", JsonArray(emptyList())) }) else emptyList()))
            put("proposal", buildJsonObject { put("edits", JsonArray(emptyList())); put("unresolved", JsonArray(emptyList())) })
        })
    }, query)
}

fun appliedFilterEditFixture(action: PlaceFilterEditAction): PlaceFilterEditResponse {
    val previous = action.previous
    val state = previous.proposed!!
    val criteria = state.filterCriteria()
    val request = previous.query.base.request.copy(kinds = criteria.kinds, nameQuery = state.getValue("name_query").jsonPrimitive.content, preferParking = criteria.preferences.isNotEmpty())
    return PlaceFilterEditResponse(JsonObject(previous.document + mapOf(
        "revision" to JsonPrimitive(2), "action_request" to action.toJson(),
        "result" to filterResponseFixture(PlaceFilterRequest(request, criteria, previous.query.base.revision + 1, action.id)).document,
    )), previous.query, action)
}
