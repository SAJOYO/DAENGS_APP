package com.daengs.app.ui.places

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.places.*
import com.daengs.app.pet.Pet
import com.daengs.app.place.*
import com.daengs.app.ui.places.lab.*
import com.daengs.app.ui.theme.DaengsTheme
import java.time.LocalDate
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaceSearchRegressionTest {
    @get:Rule val compose = createComposeRule()
    private fun response() = javaClass.getResourceAsStream("/place_search_lab_sample.json")!!.bufferedReader().use {
        Json.parseToJsonElement(it.readText()).jsonObject.toPlaceSearchResponse()
    }
    @Test fun completedYesterdaySnapshotStillShowsResultsAfterMidnight() {
        val dog = Pet("a", "콩이", "mix", null, null, 9f, LocalDate.now().minusYears(3), Pet.BirthDateKind.BIRTHDAY, isPrimary = true)
        val profiles = PlaceProfiles().receive("owner", listOf(dog), false, null).toggle("a")
            .copy(snapshotDate = LocalDate.now().minusDays(1))
        val response = response().copy(dogs = profiles.snapshots())
        assertNotEquals(response.dogs, profiles.snapshots(LocalDate.now()))
        val state = PlacesUiState(location = PlaceLocationState.Ready(GeoPoint(37.54, 127.05)), profiles = profiles,
            discovery = PlaceDiscoveryState(requestedKinds = listOf(PlaceKind.CAFE), search = PlaceSearchState.Content(response)))
        assertFalse(state.discovery.loading)
        assertTrue(response.groups.any { it.results.isNotEmpty() })
        assertEquals(LabPhase.RESULTS, state.toConnectedSearchState("", false, null, null).phase)
        val refreshed = profiles.receive("owner", listOf(dog), false, null)
        assertNotEquals(response.dogs, refreshed.snapshots())
        assertEquals(LabPhase.LOADING, state.copy(profiles = refreshed).toConnectedSearchState("", false, null, null).phase)
        val updated = state.copy(profiles = refreshed, discovery = state.discovery.copy(
            search = PlaceSearchState.Content(response.copy(dogs = refreshed.snapshots()))))
        assertEquals(LabPhase.RESULTS, updated.toConnectedSearchState("", false, null, null).phase)
    }
    @Test fun explicitDogProhibitionOverridesGeneralPetPermission() {
        val original = response().groups.flatMap { it.results }.first { it.place.facts.petAccess != null }
        val hit = original.copy(place = original.place.copy(facts = original.place.facts.copy(
            petAccess = original.place.facts.petAccess!!.copy(allowed = true, dogOk = false))))
        val expanded = mutableStateOf(false)
        compose.setContent { DaengsTheme {
            if (expanded.value) androidx.compose.foundation.layout.Column { PlaceDetailContent(hit) }
            else PlaceResultRow(hit, false, { expanded.value = true })
        } }
        compose.onNodeWithText("× 동반 불가 등록").assertExists()
        compose.onNodeWithContentDescription("동반 불가 등록").assertExists()
        compose.onNodeWithText("✓ 동반 가능 등록").assertDoesNotExist()
        compose.onNodeWithContentDescription("동반 불가 등록").performClick()
        compose.onNodeWithText("× 동반 불가 등록").assertExists()
    }
}
