package com.daengs.app.place

import com.daengs.app.location.GeoPoint
import kotlinx.serialization.json.*

fun filterCapabilitiesFixture() = PlaceFilterCapabilities(Json.parseToJsonElement("""
    {"contract_version":"place-filter-v1","candidate_kinds":["cafe","restaurant","hospital"],"max_candidate_kinds":6,
     "unknown_policies":["exclude","separate"],"capabilities":[
       {"id":"purpose.kind","operators":["in","not_in"]},
       {"id":"operations.parking","operators":["eq"],"prefer_values":[true]},
       {"id":"pet_access.exclusive","operators":["eq"]}]}
""").jsonObject)

fun filterRequestFixture(criteria: PlaceFilterCriteria = PlaceFilterCriteria(listOf(PlaceKind.CAFE),
    all = listOf(PlaceFilterAtom("parking", "operations.parking", "eq", JsonPrimitive(true))), showUncertain = true)) =
    PlaceFilterRequest(PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = criteria.kinds, limitPerKind = 50,
        dogs = listOf(PlaceDogSnapshot("a", "v1", weightKg = 10.0), PlaceDogSnapshot("unknown"))), criteria, 7, "search-7")

fun filterResponseFixture(request: PlaceFilterRequest, withHits: Boolean = false): PlaceFilterResponse {
    val legacy = Json.parseToJsonElement(object {}.javaClass.getResource("/place_search_response.json")!!.readText()).jsonObject
    val hits = legacy.getValue("groups").jsonArray.first().jsonObject.getValue("results").jsonArray
    fun bucket(index: Int, state: String) = if (!withHits) JsonArray(emptyList()) else JsonArray(listOf(JsonObject(
        hits[index].jsonObject + ("evaluations" to buildJsonObject {
            put("dogs", JsonArray(request.request.dogs.map { dog -> buildJsonObject {
                put("ref", dog.ref); put("dog_access", buildJsonObject { put("state", "unknown"); put("reason", "test") }); put("restrictions", JsonObject(emptyMap()))
            } }))
        }) + ("filter_evaluation" to buildJsonObject { put("state", state); put("atoms", JsonArray(emptyList())) }))))
    return PlaceFilterResponse(buildJsonObject {
        put("revision", request.revision); put("search_request_id", request.requestId)
        put("execution_status", "complete"); put("evaluated_at", "2026-09-09T00:00:00Z"); put("ranking_version", "distance-band-500-v1")
        put("applied_state", request.state)
        put("groups", JsonArray(request.criteria.kinds.map { kind -> buildJsonObject {
            put("kind", kind.wire); put("matched", bucket(0, "true")); put("uncertain", if (request.criteria.showUncertain) bucket(1, "unknown") else JsonArray(emptyList()))
            put("matched_truncated", false); put("uncertain_truncated", false); put("total", JsonNull)
        } }))
    }, request)
}
