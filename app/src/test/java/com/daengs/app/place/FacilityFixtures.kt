package com.daengs.app.place

import com.daengs.app.location.GeoPoint
import kotlinx.serialization.json.*

internal fun facilityQuery() = FacilityQuery("주차하기 좋은 카페", GeoPoint(37.54, 127.05), 3000,
    listOf(PlaceKind.CAFE), false, emptyList(), "b6706abc-6dca-41ba-8274-4e7616e35093")

internal fun facilityJson(query: FacilityQuery = facilityQuery(), action: FacilityAction? = null): JsonObject {
    val sample = Json.parseToJsonElement(FacilityQuery::class.java.getResourceAsStream("/place_search_lab_sample.json")!!
        .bufferedReader().use { it.readText() }).jsonObject
    val group = sample.getValue("groups").jsonArray.first().jsonObject
    return buildJsonObject {
        put("contract_version", "facility-discovery-v1")
        put("search_id", "0e2af292-5cfa-436c-a733-811f4264ebde")
        put("request", query.toJson()); put("outcome", "results")
        put("revision", action?.expectedRevision?.plus(1) ?: 1)
        put("expires_at", "2099-01-01T00:00:00Z")
        put("confirmed_lens_id", (action?.choice as? FacilityChoice.Confirm)?.lensId?.let(::JsonPrimitive) ?: JsonNull)
        put("action_request", action?.toJson() ?: JsonNull)
        put("lenses", buildJsonArray { add(buildJsonObject {
            put("id", "lens:cafe"); put("label", "카페"); put("note", "등록된 주차 정보를 우선 참고했어요.")
            put("applied", buildJsonObject { put("kinds", buildJsonArray { add("cafe") }); put("parking", true) })
            put("search", JsonObject(sample + ("groups" to JsonArray(listOf(group)))))
            put("presentations", buildJsonArray { group.getValue("results").jsonArray.forEach { hit -> add(buildJsonObject {
                put("place_key", hit.jsonObject.getValue("place").jsonObject.getValue("key"))
                put("summary", "주차 정보를 확인해 주세요.")
                put("core_items", buildJsonArray { add(buildJsonObject {
                    put("label", "주차"); put("display_text", "확인되지 않았어요."); put("severity", "warning")
                }) }); put("promoted_items", JsonArray(emptyList())); put("detail_items", JsonArray(emptyList()))
                put("notices", JsonArray(emptyList())); put("why_matched", JsonArray(emptyList()))
            }) } })
        }) })
        put("signals", JsonArray(emptyList()))
        put("notices", buildJsonArray { add(buildJsonObject { put("message", "선택한 지도 위치와 반경으로 검색했어요.") }) })
    }
}

internal fun facilityResponse(query: FacilityQuery = facilityQuery()) = facilityJson(query).toFacilityResponse(query)
