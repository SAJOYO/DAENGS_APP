package com.daengs.app.place.bookmarks

import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceDogSnapshot
import com.daengs.app.ui.places.PlaceBrowseFilters
import kotlinx.serialization.json.*

/** Interpretation may change conditions, never the origin or the selected dog snapshots. */
internal fun PlaceBrowseFilters.withSavedPlan(candidate: JsonObject, dogs: List<PlaceDogSnapshot>): PlaceBrowseFilters {
    fun normalized(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.filterValues { it != JsonNull }.mapValues { normalized(it.value) })
        is JsonArray -> JsonArray(value.map(::normalized))
        else -> value
    }
    val original = savedQuery(dogs)
    require(candidate.keys - "excluded_keys" == original.keys - "excluded_keys")
    val excluded = candidate["excluded_keys"]?.jsonArray?.map { it.jsonObject.savedKey() }?.toSet().orEmpty()
    require(excluded.size <= 120)
    for (field in listOf("lat", "lng", "dogs")) require(normalized(candidate.getValue(field)) == normalized(original.getValue(field)))
    val radius = candidate["radius_m"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int
    require(radius == null || (radius in 100..20000 && origin != null))
    val kinds = candidate.getValue("kinds").jsonArray.map { value -> PlaceKind.entries.first { it.wire == value.jsonPrimitive.content } }
    require(kinds.toSet().size == kinds.size)
    return copy(kinds = kinds.toSet(), name = candidate.getValue("name_query").jsonPrimitive.content,
        radiusMeters = radius, excludedKeys = excluded, parkingFirst = candidate.getValue("parking").jsonPrimitive.boolean,
        requiredConditions = candidate.getValue("hard").jsonObject.takeUnless {
            it.getValue("all").jsonArray.isEmpty() && it.getValue("any").jsonArray.isEmpty()
        })
}

/** A normal-search handoff keeps saved origin/dogs and every hard condition. */
internal fun PlaceBrowseFilters.withSearchPlan(candidate: JsonObject, dogs: List<PlaceDogSnapshot>): PlaceBrowseFilters {
    require(candidate.keys == setOf("contract_version", "candidate_kinds", "spatial", "name_query",
        "hard", "preferences", "unknown_policy", "result_policy", "dogs"))
    require(candidate.getValue("contract_version").jsonPrimitive.content == "place-filter-v1")
    require(candidate.getValue("unknown_policy").jsonPrimitive.content == "exclude")
    val policy = candidate.getValue("result_policy").jsonObject
    require(policy.getValue("limit_per_kind").jsonPrimitive.int in 1..20)
    require(policy.getValue("uncertain_limit_per_kind").jsonPrimitive.int == 0)
    val kinds = candidate.getValue("candidate_kinds").jsonArray
    require(kinds.size in 1..6)
    val preferences = candidate.getValue("preferences").jsonArray
    require(preferences.size <= 1)
    preferences.forEach {
        val p = it.jsonObject
        require(p.getValue("capability").jsonPrimitive.content == "operations.parking")
        require(p.getValue("op").jsonPrimitive.content == "eq" && p.getValue("value").jsonPrimitive.boolean)
        require(p.getValue("scope_kinds").jsonArray.toSet() == kinds.toSet())
    }
    val spatial = candidate.getValue("spatial").jsonObject
    require(spatial.keys == setOf("lat", "lng", "radius_m") && spatial.getValue("radius_m") != JsonNull)
    return withSavedPlan(buildJsonObject {
        spatial.forEach { (key, value) -> put(key, value) }
        put("kinds", kinds); put("name_query", candidate.getValue("name_query"))
        put("hard", candidate.getValue("hard")); put("parking", preferences.isNotEmpty())
        put("dogs", candidate.getValue("dogs"))
        if (excludedKeys.isNotEmpty()) put("excluded_keys", JsonArray(excludedKeys.map { it.savedJson() }))
    }, dogs)
}
