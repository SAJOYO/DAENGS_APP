package com.daengs.app.place

import com.daengs.app.location.GeoPoint
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.*

/** 시설 화면 전용 계약. Chat projection이나 클라이언트가 만든 검색 계획을 보내지 않는다. */
data class FacilityQuery(
    val query: String,
    val origin: GeoPoint,
    val radiusMeters: Int,
    val kinds: List<PlaceKind>,
    val parking: Boolean,
    val dogs: List<PlaceDogSnapshot>,
    val clientRequestId: String = UUID.randomUUID().toString(),
) {
    init {
        require(query.isNotBlank() && query.codePointCount(0, query.length) <= 1000)
        UUID.fromString(clientRequestId)
        require(kinds.size <= 6 && kinds.distinct().size == kinds.size)
        PlaceSearchRequest(origin, radiusMeters, kinds.ifEmpty { listOf(PlaceKind.CAFE) }, dogs = dogs)
    }

    fun toJson() = buildJsonObject {
        put("client_request_id", clientRequestId); put("query", query)
        put("spatial", buildJsonObject { put("lat", origin.latitude); put("lng", origin.longitude); put("radius_m", radiusMeters) })
        put("kinds", buildJsonArray { kinds.forEach { add(it.wire) } })
        put("preferences", buildJsonObject { put("parking", parking) })
        put("dogs", buildJsonArray { dogs.forEach { add(it.toJson()) } })
    }
}

sealed interface FacilityChoice {
    data class Confirm(val lensId: String) : FacilityChoice
    data class Refine(val signalId: String, val optionId: String) : FacilityChoice
    fun toJson() = buildJsonObject {
        when (val choice = this@FacilityChoice) {
            is Confirm -> { put("type", "confirm"); put("lens_id", choice.lensId) }
            is Refine -> { put("type", "refine"); put("signal_id", choice.signalId); put("option_id", choice.optionId) }
        }
    }
}

data class FacilityAction(
    val searchId: String,
    val expectedRevision: Int,
    val choice: FacilityChoice,
    val clientRequestId: String = UUID.randomUUID().toString(),
) {
    fun toJson() = buildJsonObject {
        put("client_request_id", clientRequestId); put("search_id", searchId)
        put("expected_revision", expectedRevision); put("action", choice.toJson())
    }
}

data class FacilityOption(val id: String, val label: String, val availability: String, val note: String)
data class FacilitySignal(
    val id: String, val label: String, val state: String, val required: Boolean, val note: String,
    val options: List<FacilityOption>, val selectedOptionId: String?,
)
data class FacilityFact(val label: String, val text: String, val severity: String)
data class FacilityPresentation(
    val key: PlaceKey, val summary: String, val facts: List<FacilityFact>,
    val notices: List<String>, val whyMatched: List<String>,
)
data class FacilityLens(
    val id: String, val label: String, val note: String, val kinds: List<PlaceKind>, val parking: Boolean,
    val search: PlaceSearchResponse, val presentations: List<FacilityPresentation>,
)
data class FacilityResponse(
    val searchId: String, val request: FacilityQuery, val outcome: String, val revision: Int,
    val expiresAt: Instant?, val lenses: List<FacilityLens>, val signals: List<FacilitySignal>,
    val notices: List<String>, val confirmedLensId: String?,
) {
    val confirmedLens: FacilityLens? get() = lenses.firstOrNull { it.id == confirmedLensId }
    fun canChoose(choice: FacilityChoice): Boolean = when (choice) {
        is FacilityChoice.Confirm -> lenses.any { it.id == choice.lensId }
        is FacilityChoice.Refine -> signals.any { signal ->
            signal.id == choice.signalId && signal.state == "needs_selection" &&
                signal.options.any { it.id == choice.optionId && it.availability == "proxy" }
        }
    }
}

/** 응답의 원래 질의·반려견·업종 및 후속 액션 echo를 확인한 뒤에만 화면에 전달한다. */
fun JsonObject.toFacilityResponse(expected: FacilityQuery, action: FacilityAction? = null): FacilityResponse {
    require(string("contract_version") == "facility-discovery-v1")
    val echoed = getValue("request").jsonObject.toFacilityQuery()
    require(echoed == expected) { "Facility query echo mismatch" }
    val searchId = string("search_id").also { UUID.fromString(it) }
    val revision = getValue("revision").jsonPrimitive.int.also { require(it >= 1) }
    val confirmed = optionalString("confirmed_lens_id")
    if (action == null) {
        require(revision == 1 && confirmed == null && (this["action_request"] == null || this["action_request"] == JsonNull))
    } else {
        require(searchId == action.searchId && revision == action.expectedRevision + 1)
        require(this["action_request"] == action.toJson()) { "Facility action echo mismatch" }
        if (action.choice is FacilityChoice.Confirm) require(confirmed == action.choice.lensId)
    }
    val lenses = objects("lenses").map { lens ->
        val applied = lens.getValue("applied").jsonObject
        val kinds = applied.getValue("kinds").jsonArray.map { PlaceKind.fromWire(it.jsonPrimitive.content) }
        require(expected.kinds.isEmpty() || kinds.all { it in expected.kinds })
        val search = lens.getValue("search").jsonObject.toPlaceSearchResponse()
        require(search.groups.map { it.kind } == kinds && kinds.isNotEmpty())
        search.requireDogEcho(PlaceSearchRequest(expected.origin, expected.radiusMeters, kinds, dogs = expected.dogs))
        val presentations = lens.objects("presentations").map { p ->
            val key = p.getValue("place_key").jsonObject
            FacilityPresentation(PlaceKey(key.string("source"), key.string("ref")), p.string("summary"),
                listOf("promoted_items", "core_items", "detail_items").flatMap { field ->
                    p.objects(field).map { FacilityFact(it.string("label"), it.string("display_text"), it.string("severity")) }
                }, p.messages("notices"), p.messages("why_matched"))
        }
        require(presentations.map { it.key } == search.groups.flatMap { it.results }.map { it.place.key })
        FacilityLens(lens.string("id"), lens.string("label"), lens.string("note"), kinds,
            applied.getValue("parking").jsonPrimitive.boolean, search, presentations)
    }
    require(lenses.size <= 3 && lenses.map { it.id }.distinct().size == lenses.size)
    require(confirmed == null || lenses.any { it.id == confirmed })
    val signals = objects("signals").map { signal ->
        FacilitySignal(signal.string("id"), signal.string("label"), signal.string("state"),
            signal.getValue("required").jsonPrimitive.boolean, signal.string("note"),
            signal.objects("options").map { FacilityOption(it.string("id"), it.string("label"), it.string("availability"), it.string("note")) },
            signal.optionalString("selected_option_id"))
    }
    require(signals.size <= 20 && signals.map { it.id }.distinct().size == signals.size)
    val outcome = string("outcome").also { require(it in setOf("results", "empty", "needs_clarification", "unsupported")) }
    return FacilityResponse(searchId, echoed, outcome, revision, optionalString("expires_at")?.let(Instant::parse),
        lenses, signals, messages("notices"), confirmed)
}

private fun JsonObject.toFacilityQuery(): FacilityQuery {
    val spatial = getValue("spatial").jsonObject
    return FacilityQuery(string("query"), GeoPoint(spatial.getValue("lat").jsonPrimitive.double, spatial.getValue("lng").jsonPrimitive.double),
        spatial.getValue("radius_m").jsonPrimitive.int,
        getValue("kinds").jsonArray.map { PlaceKind.fromWire(it.jsonPrimitive.content) },
        getValue("preferences").jsonObject.getValue("parking").jsonPrimitive.boolean,
        objects("dogs").map { dog -> PlaceDogSnapshot(dog.string("ref"), dog.optionalString("revision"),
            dog.optionalString("dog_size")?.let { size -> DogSize.entries.single { it.wire == size } },
            dog["dog_weight_kg"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.double,
            dog["dog_age_years"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.double) }, string("client_request_id"))
}

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.optionalString(key: String) = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.objects(key: String) = getValue(key).jsonArray.map { it.jsonObject }
private fun JsonObject.messages(key: String) = objects(key).map { it.string("message") }
