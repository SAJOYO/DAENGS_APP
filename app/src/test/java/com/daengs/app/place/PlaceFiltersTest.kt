package com.daengs.app.place

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PlaceFiltersTest {
    @Test fun pythonApiGoldenResponseAcceptsTheExactKotlinRequestAndDogSnapshots() {
        val fixture = Json.parseToJsonElement(javaClass.getResource("/place_filter_wire.json")!!.readText()).jsonObject
        val request = filterRequestFixture()
        assertTrue(sameFilterJson(request.toJson(), fixture.getValue("request")))
        val result = PlaceFilterResponse(fixture.getValue("response").jsonObject, request)
        PlaceFilterCapabilities(fixture.getValue("capabilities").jsonObject)
        assertEquals("주차 확인 카페", result.results().groups.single().results.single().place.name)
        assertEquals("주차 미상 카페", result.results(true).groups.single().results.single().place.name)
        assertEquals(2, result.results().groups.single().results.single().evaluations.dogs.size)
    }
    @Test fun arbitraryManualKindCombinationsDoNotRequireAnExistingPurposeGroup() {
        val kinds = listOf(PlaceKind.CAFE, PlaceKind.HOSPITAL)
        assertEquals(PlaceCategorySelection.Custom(kinds), PlaceCategorySelection.fromKinds(kinds))
    }
    @Test fun falseAndClearAreDifferentAndEditsKeepTheAtomId() {
        val atom = PlaceFilterAtom("parking", "operations.parking", "eq", JsonPrimitive(true))
        val off = listOf(atom).setBoolean("operations.parking", false)
        assertEquals("parking", off.single().id)
        assertEquals(JsonPrimitive(false), off.single().value)
        assertTrue(off.setBoolean("operations.parking", null).isEmpty())
    }

    @Test fun hardIntersectionOrBranchesPreferencesAndUnknownPolicySurviveSerialization() {
        val criteria = PlaceFilterCriteria(listOf(PlaceKind.CAFE, PlaceKind.RESTAURANT),
            all = listOf(PlaceFilterAtom("p", "operations.parking", "eq", JsonPrimitive(false))),
            any = listOf(PlaceFilterBranch("b", listOf(PlaceFilterAtom("k", "purpose.kind", "in", JsonArray(listOf(JsonPrimitive("cafe"))))))),
            showUncertain = true)
        val request = filterRequestFixture(criteria)
        assertEquals(JsonPrimitive(false), request.state.getValue("hard").jsonObject.getValue("all").jsonArray.single().jsonObject["value"])
        assertEquals("b", request.state.getValue("hard").jsonObject.getValue("any").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals(request.state, filterResponseFixture(request).document["applied_state"])
    }

    @Test fun changedEchoOrRequestIdOrRevisionOrIncompleteResultsAreRejected() {
        val request = filterRequestFixture()
        val result = filterResponseFixture(request)
        val mutations = listOf(
            "revision" to JsonPrimitive(8), "search_request_id" to JsonPrimitive("old"),
            "execution_status" to JsonPrimitive("incomplete"),
            "applied_state" to JsonObject(request.state + ("hard" to buildJsonObject { put("all", JsonArray(emptyList())); put("any", JsonArray(emptyList())) })),
            "applied_state" to JsonObject(request.state + ("dogs" to JsonArray(emptyList()))),
        )
        mutations.forEach { (field, value) -> assertThrows(IllegalArgumentException::class.java) {
            PlaceFilterResponse(JsonObject(result.document + (field to value)), request)
        } }
    }

    @Test fun groupsAndUnknownBucketRemainSeparateWithoutLocalReranking() {
        val result = filterResponseFixture(filterRequestFixture(), withHits = true)
        assertEquals("먼 주차 카페", result.results().groups.single().results.single().place.name)
        assertEquals("가까운 정보 미상 카페", result.results(true).groups.single().results.single().place.name)
        assertEquals(result.request.request.dogs, result.results(true).dogs)
    }

    @Test fun missingCapabilityAndUnknownVersionDoNotEnableTheEditor() {
        val valid = filterCapabilitiesFixture().document
        assertThrows(IllegalArgumentException::class.java) { PlaceFilterCapabilities(JsonObject(valid + ("contract_version" to JsonPrimitive("v2")))) }
        assertThrows(IllegalArgumentException::class.java) { PlaceFilterCapabilities(JsonObject(valid + ("capabilities" to JsonArray(emptyList())))) }
    }

    @Test fun numbersCanNormalizeButBooleanFalseIsNotZero() {
        assertTrue(sameFilterJson(JsonPrimitive(127), JsonPrimitive(127.0)))
        assertFalse(sameFilterJson(JsonPrimitive(false), JsonPrimitive(0)))
        assertFalse(sameFilterJson(JsonPrimitive("127"), JsonPrimitive(127)))
    }
}
