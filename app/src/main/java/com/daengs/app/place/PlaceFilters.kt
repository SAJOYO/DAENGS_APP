package com.daengs.app.place

import kotlinx.serialization.json.*
import java.util.UUID

const val PLACE_FILTER_VERSION = "place-filter-v1"

/** IDs belong to the condition, so editing its value does not create a new condition. */
data class PlaceFilterAtom(val id: String, val capability: String, val op: String, val value: JsonElement) {
    fun toJson() = buildJsonObject { put("id", id); put("capability", capability); put("op", op); put("value", value) }
}

data class PlaceFilterBranch(val id: String = UUID.randomUUID().toString(), val all: List<PlaceFilterAtom> = emptyList()) {
    fun toJson() = buildJsonObject { put("id", id); put("all", JsonArray(all.map { it.toJson() })) }
}

data class PlaceFilterPreference(val atom: PlaceFilterAtom, val scopeKinds: List<PlaceKind>) {
    fun toJson() = JsonObject(atom.toJson() + ("scope_kinds" to JsonArray(scopeKinds.map { JsonPrimitive(it.wire) })))
}

/** Shared manual/LLM condition tree. Spatial/name/dog inputs are supplied by the search session. */
data class PlaceFilterCriteria(
    val kinds: List<PlaceKind>,
    val all: List<PlaceFilterAtom> = emptyList(),
    val any: List<PlaceFilterBranch> = emptyList(),
    val preferences: List<PlaceFilterPreference> = emptyList(),
    val showUncertain: Boolean = false,
) {
    fun validationMessage(): String? = when {
        kinds.isEmpty() || kinds.size > 6 || kinds.distinct() != kinds -> "업종을 1~6개 선택해 주세요."
        any.size > 4 -> "대안 묶음은 4개까지 만들 수 있어요."
        any.any { it.all.isEmpty() } -> "빈 대안 묶음에 조건을 넣거나 묶음을 삭제해 주세요."
        preferences.any { it.scopeKinds.isEmpty() || it.scopeKinds.any { k -> k !in kinds } } -> "주차 우선의 적용 업종을 다시 선택해 주세요."
        (all + any.flatMap { it.all }).filter { it.capability == "purpose.kind" }.any { atom ->
            atom.value.jsonArray.any { value -> kinds.none { it.wire == value.jsonPrimitive.content } }
        } -> "대안 묶음의 업종도 검색 업종에 포함해 주세요."
        else -> null // Server performs authoritative cross-branch contradiction validation.
    }

    fun state(request: PlaceSearchRequest): JsonObject {
        require(validationMessage() == null) { validationMessage().orEmpty() }
        require(request.kinds == kinds)
        require(request.dogSize == null && request.dogWeightKg == null && request.dogAgeYears == null)
        return buildJsonObject {
            put("contract_version", PLACE_FILTER_VERSION)
            put("candidate_kinds", JsonArray(kinds.map { JsonPrimitive(it.wire) }))
            put("spatial", buildJsonObject {
                put("lat", request.origin.latitude); put("lng", request.origin.longitude); put("radius_m", request.radiusMeters)
            })
            put("name_query", request.nameQuery.trim())
            put("hard", buildJsonObject {
                put("all", JsonArray(all.map { it.toJson() })); put("any", JsonArray(any.map { it.toJson() }))
            })
            put("preferences", JsonArray(preferences.map { it.toJson() }))
            put("unknown_policy", if (showUncertain) "separate" else "exclude")
            put("result_policy", buildJsonObject {
                put("limit_per_kind", request.limitPerKind ?: 50); put("uncertain_limit_per_kind", if (showUncertain) 20 else 0)
            })
            // Include null defaults so a full normalized echo can be compared without dropping fields.
            put("dogs", JsonArray(request.dogs.map { dog -> buildJsonObject {
                put("ref", dog.ref); put("revision", dog.revision?.let(::JsonPrimitive) ?: JsonNull)
                put("dog_size", dog.size?.wire?.let(::JsonPrimitive) ?: JsonNull)
                put("dog_weight_kg", dog.weightKg?.let(::JsonPrimitive) ?: JsonNull)
                put("dog_age_years", dog.ageYears?.let(::JsonPrimitive) ?: JsonNull)
            } }))
        }
    }
}

fun List<PlaceFilterAtom>.setBoolean(capability: String, value: Boolean?): List<PlaceFilterAtom> {
    val previous = firstOrNull { it.capability == capability }
    val remaining = filterNot { it.capability == capability }
    return if (value == null) remaining else remaining + PlaceFilterAtom(
        previous?.id ?: UUID.randomUUID().toString(), capability, "eq", JsonPrimitive(value),
    )
}

data class PlaceFilterRequest(val request: PlaceSearchRequest, val criteria: PlaceFilterCriteria, val revision: Long,
                              val requestId: String = UUID.randomUUID().toString()) {
    val state: JsonObject = criteria.state(request)
    fun toJson() = buildJsonObject { put("revision", revision); put("search_request_id", requestId); put("state", state) }
}

data class PlaceFilterCapabilities(val document: JsonObject) {
    val kinds: List<PlaceKind> = document.getValue("candidate_kinds").jsonArray.map { PlaceKind.fromWire(it.jsonPrimitive.content) }
    init {
        require(document["contract_version"] == JsonPrimitive(PLACE_FILTER_VERSION))
        require(document.getValue("max_candidate_kinds").jsonPrimitive.int >= 6)
        val specs = document.getValue("capabilities").jsonArray.map { it.jsonObject }.associateBy { it.getValue("id").jsonPrimitive.content }
        require(listOf("purpose.kind", "operations.parking", "pet_access.exclusive").all { it in specs })
        require(specs.getValue("purpose.kind").getValue("operators").jsonArray.contains(JsonPrimitive("in")))
        listOf("operations.parking", "pet_access.exclusive").forEach {
            require(specs.getValue(it).getValue("operators").jsonArray.contains(JsonPrimitive("eq")))
        }
        require(specs.getValue("operations.parking").getValue("prefer_values").jsonArray.contains(JsonPrimitive(true)))
        require(document.getValue("unknown_policies").jsonArray.contains(JsonPrimitive("separate")))
    }
}

/** Keep both buckets and all evidence; legacy card projection never becomes the stored result. */
data class PlaceFilterResponse(val document: JsonObject, val request: PlaceFilterRequest) {
    init {
        require(document["revision"]?.jsonPrimitive?.long == request.revision)
        require(document["search_request_id"] == JsonPrimitive(request.requestId))
        require(document["execution_status"] == JsonPrimitive("complete"))
        require(document["ranking_version"] == JsonPrimitive("distance-band-500-v1"))
        require(document.getValue("evaluated_at").jsonPrimitive.content.isNotBlank())
        require(sameFilterJson(document.getValue("applied_state"), request.state)) { "Server changed the requested filters" }
        require(document.getValue("groups").jsonArray.map { it.jsonObject.getValue("kind").jsonPrimitive.content } == request.criteria.kinds.map { it.wire })
        document.getValue("groups").jsonArray.forEach { group ->
            listOf("matched" to "true", "uncertain" to "unknown").forEach { (bucket, expected) ->
                val rows = group.jsonObject.getValue(bucket).jsonArray
                val limit = if (bucket == "matched") request.request.limitPerKind ?: 50 else if (request.criteria.showUncertain) 20 else 0
                require(rows.size <= limit)
                rows.forEach { require(it.jsonObject.getValue("filter_evaluation").jsonObject["state"] == JsonPrimitive(expected)) }
            }
        }
        results().requireDogEcho(request.request)
        results(uncertain = true).requireDogEcho(request.request)
    }

    fun results(uncertain: Boolean = false): PlaceSearchResponse = PlaceSearchResponse(null,
        document.getValue("groups").jsonArray.map { value ->
            val group = value.jsonObject
            val bucket = if (uncertain) "uncertain" else "matched"
            PlaceSearchGroup(PlaceKind.fromWire(group.getValue("kind").jsonPrimitive.content),
                PlaceSort(PlaceSortType.DISTANCE, emptyList(), emptyList(), null, emptyMap()),
                if (uncertain) 20 else request.request.limitPerKind ?: 50,
                group.getValue("${bucket}_truncated").jsonPrimitive.boolean,
                group.getValue(bucket).jsonArray.map { it.jsonObject.toPlaceSearchHit() })
        }, request.request.dogs)

    fun evidence(key: PlaceKey): JsonObject? = document.getValue("groups").jsonArray.asSequence()
        .flatMap { g -> (g.jsonObject.getValue("matched").jsonArray + g.jsonObject.getValue("uncertain").jsonArray).asSequence() }
        .map { it.jsonObject }.firstOrNull { it.toPlaceSearchHit().place.key == key }?.get("filter_evaluation")?.jsonObject
}

/** JSON numbers may return as 127.0 instead of 127; keys, arrays, booleans and nulls stay exact. */
internal fun sameFilterJson(left: JsonElement, right: JsonElement): Boolean = when {
    left is JsonObject && right is JsonObject -> left.keys == right.keys && left.all { (k, v) -> sameFilterJson(v, right.getValue(k)) }
    left is JsonArray && right is JsonArray -> left.size == right.size && left.zip(right).all { (a, b) -> sameFilterJson(a, b) }
    left is JsonPrimitive && right is JsonPrimitive && !left.isString && !right.isString && left.doubleOrNull != null && right.doubleOrNull != null -> left.double == right.double
    else -> left == right
}
