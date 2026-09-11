package com.daengs.app.ui.places

import com.daengs.app.place.*
import com.daengs.app.place.support.filteredConversationFixture
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AppliedPlaceFiltersTest {
    @Test fun mandatoryParkingAndOrAlternativesRemainDistinctFromParkingPreference() {
        val result = filteredConversationFixture().toConversationResult()
        val shown = result.appliedPlaceFilters()
        assertFalse(result.parkingFirst)
        assertEquals("주차 가능한 곳만", shown.all.single().label)
        assertEquals(2, shown.count)
        assertEquals(listOf("shop", "pet"), shown.any.map { it.id })
        assertEquals(listOf("shop-kind", "pet-kind"), shown.any.flatMap { it.conditions }.map { it.id })
    }

    @Test fun negativeValuesAndKindExclusionsAreNotShownAsPositiveConditions() {
        val body = filteredConversationFixture()
        val all = Json.parseToJsonElement("""
            [{"id":"parking","capability":"operations.parking","op":"eq","value":false},
             {"id":"exclusive","capability":"pet_access.exclusive","op":"eq","value":false},
             {"id":"kind","capability":"purpose.kind","op":"not_in","value":["shopping"]}]
        """)
        val result = JsonObject(body + ("filters" to JsonObject(body.getValue("filters").jsonObject +
            ("hard" to buildJsonObject { put("all", all); put("any", JsonArray(emptyList())) })))).toConversationResult()
        val labels = result.appliedPlaceFilters().all.map { it.label }
        assertEquals("주차 불가로 확인된 곳만", labels[0])
        assertEquals("반려동물 전용 시설 제외", labels[1])
        assertTrue(labels[2].endsWith(" 제외"))
    }
}
