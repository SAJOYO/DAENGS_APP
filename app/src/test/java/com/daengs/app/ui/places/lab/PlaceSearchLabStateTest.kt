package com.daengs.app.ui.places.lab

import com.daengs.app.place.toPlaceSearchResponse
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PlaceSearchLabStateTest {
    private fun hits() = javaClass.getResourceAsStream("/place_search_lab_sample.json")!!.bufferedReader().use {
        Json.parseToJsonElement(it.readText()).jsonObject.toPlaceSearchResponse().groups.flatMap { group -> group.results }
    }
    @Test fun selectionDoesNotExpandAndOnlyOneCardExpands() {
        val hits = hits()
        val state = PlaceSearchLabState().results(hits)
        val first = hits[0].place.key
        val second = hits[1].place.key
        assertNull(state.select(first).expanded)
        val opened = state.toggle(first).toggle(second)
        assertEquals(second, opened.expanded)
        assertEquals(second, opened.selected)
        assertNull(opened.toggle(second).expanded)
    }
    @Test fun resultsKeepStableSelectionAndRemoveMissingExpandedCard() {
        val hits = hits()
        val state = PlaceSearchLabState().results(hits).toggle(hits[0].place.key)
        assertEquals(state.selected, state.results(hits.reversed()).selected)
        val next = state.results(hits.drop(1))
        assertNull(next.expanded)
        assertEquals(hits[1].place.key, next.selected)
        assertNull(next.results(emptyList()).selected)
    }
    @Test fun recordedRestrictionsAndUnknownEvaluationSurviveParsing() {
        val rooftop = hits().first { it.place.name == "구욱희씨" }
        assertEquals("루프탑 외 입장 불가", rooftop.place.facts.restrictions!!["raw"]!!.jsonPrimitive.content)
        assertEquals("partial", rooftop.place.facts.restrictions!!["parse_state"]!!.jsonPrimitive.content)
        assertEquals("unknown", rooftop.evaluations.restrictions!!["state"]!!.jsonPrimitive.content)
    }
    @Test fun typingAndModeChangesDoNotChangeAppliedCriteriaOrProfiles() {
        val state = PlaceSearchLabState(selectedDogIds = setOf("one", "two"))
        val typed = state.copy(draft = "카페", aiMode = true)
        assertEquals(state.applied, typed.applied)
        assertEquals(state.selectedDogIds, typed.selectedDogIds)
    }
}
