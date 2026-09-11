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
    require(candidate.keys == original.keys)
    for (field in listOf("lat", "lng", "dogs")) require(normalized(candidate.getValue(field)) == normalized(original.getValue(field)))
    val radius = candidate["radius_m"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int
    require(radius == null || (radius in 100..20000 && origin != null))
    val kinds = candidate.getValue("kinds").jsonArray.map { value -> PlaceKind.entries.first { it.wire == value.jsonPrimitive.content } }
    require(kinds.toSet().size == kinds.size)
    return copy(kinds = kinds.toSet(), name = candidate.getValue("name_query").jsonPrimitive.content,
        radiusMeters = radius, parkingFirst = candidate.getValue("parking").jsonPrimitive.boolean,
        requiredConditions = candidate.getValue("hard").jsonObject.takeUnless {
            it.getValue("all").jsonArray.isEmpty() && it.getValue("any").jsonArray.isEmpty()
        })
}
