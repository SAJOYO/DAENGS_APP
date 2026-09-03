package com.daengs.app.ui.places

import com.daengs.app.journey.JourneyItem
import com.daengs.app.journey.JourneyRepository
import com.daengs.app.journey.JourneyResponse
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.journey.PlaceJourneyState
import com.daengs.app.map.features.places.PlaceOriginMode
import com.daengs.app.map.features.places.PlaceSearchState
import com.daengs.app.map.layers.places.FacilityIconGroup
import com.daengs.app.place.PlaceFacts
import com.daengs.app.place.PlaceFailure
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceMatch
import com.daengs.app.place.PlaceResult
import com.daengs.app.place.PlaceSearchConditions
import com.daengs.app.place.PlaceSearchRequest
import com.daengs.app.place.PlaceSearchResponse
import com.daengs.app.place.PlaceSearchRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSessionCoordinatorTest {
    @Test
    fun `a late device resolution cannot replace a newer pinned intent`() = runTest {
        val requests = mutableListOf<PlaceSearchRequest>()
        val session = session(
            repository = PlaceSearchRepository { request ->
                requests += request
                emptyResponse()
            },
        )
        val pinned = GeoPoint(37.51, 127.01)
        val pending = session.requestDeviceSearch(PlaceKind.CAFE, preferParking = false)

        session.searchAt(pinned, PlaceKind.RESTAURANT, preferParking = false)
        runCurrent()
        session.resolveDeviceSearch(pending, GeoPoint(37.5, 127.0))
        runCurrent()

        assertEquals(1, requests.size)
        assertEquals(pinned, requests.single().origin)
        assertEquals(listOf(PlaceKind.RESTAURANT), requests.single().kinds)
        assertEquals(PlaceOriginMode.PINNED, session.state.value.discovery.originMode)
        assertEquals(
            PlaceSearchOrigin.PinnedMap(pinned),
            session.state.value.latestIntent?.origin,
        )
    }

    @Test
    fun `a category change keeps the pinned origin instead of the device position`() = runTest {
        val requests = mutableListOf<PlaceSearchRequest>()
        val session = session(
            repository = PlaceSearchRepository { request ->
                requests += request
                emptyResponse()
            },
        )
        val pinned = GeoPoint(35.1796, 129.0756)
        session.searchAt(pinned, PlaceKind.CAFE, preferParking = false)
        runCurrent()
        requests.clear()

        val pending = session.searchAtCurrentOrigin(
            kind = PlaceKind.RESTAURANT,
            preferParking = true,
            devicePosition = GeoPoint(37.5, 127.0),
        )
        runCurrent()

        assertNull(pending)
        assertEquals(1, requests.size)
        assertEquals(pinned, requests.single().origin)
        assertEquals(listOf(PlaceKind.RESTAURANT), requests.single().kinds)
        assertEquals(PlaceOriginMode.PINNED, session.state.value.discovery.originMode)
    }

    @Test
    fun `an unsupported feed resolves a pending device intent and rejects its late fix`() = runTest {
        val requests = mutableListOf<PlaceSearchRequest>()
        val session = session(
            repository = PlaceSearchRepository { request ->
                requests += request
                emptyResponse()
            },
        )
        val pending = session.requestDeviceSearch(PlaceKind.CAFE, preferParking = false)
        val overseas = GeoPoint(37.7749, -122.4194)

        session.replaceUnsupportedDeviceOrigin(overseas)
        session.resolveDeviceSearch(pending, GeoPoint(37.5, 127.0))
        runCurrent()

        assertEquals(0, requests.size)
        assertEquals(
            PlaceSearchState.Failed(PlaceFailure.UnsupportedLocation),
            session.state.value.discovery.search,
        )
        assertEquals(
            PlaceSearchOrigin.DeviceSnapshot(overseas),
            session.state.value.latestIntent?.origin,
        )
    }

    @Test
    fun `deactivation cannot leave a cancelled device intent over pinned results`() = runTest {
        val requests = mutableListOf<PlaceSearchRequest>()
        val session = session(
            PlaceSearchRepository { request ->
                requests += request
                emptyResponse()
            },
        )
        val pinned = GeoPoint(37.51, 127.01)
        session.searchAt(pinned, PlaceKind.CAFE, preferParking = false)
        runCurrent()
        session.requestDeviceSearch(PlaceKind.HOSPITAL, preferParking = false)

        session.deactivate()
        session.replaceUnsupportedDeviceOrigin(GeoPoint(37.7749, -122.4194))
        runCurrent()

        assertEquals(1, requests.size)
        assertEquals(pinned, session.state.value.discovery.origin)
        assertEquals(PlaceOriginMode.PINNED, session.state.value.discovery.originMode)
        assertEquals(
            PlaceSearchOrigin.PinnedMap(pinned),
            session.state.value.latestIntent?.origin,
        )
    }

    @Test
    fun `selecting another place clears journey state for the previous destination`() = runTest {
        val session = session(PlaceSearchRepository { emptyResponse() })
        val first = place("hospital-7", GeoPoint(37.5145, 127.0316))
        val second = place("hospital-8", GeoPoint(37.5150, 127.0320))
        session.loadJourney(origin = null, place = first)
        runCurrent()
        assertEquals(first.key, session.state.value.journey.destinationKey)

        session.selectPlace(second.key)
        runCurrent()

        assertEquals(second.key, session.state.value.discovery.selectedPlaceKey)
        assertEquals(PlaceJourneyState(), session.state.value.journey)
    }

    @Test
    fun `leaving the screen invalidates an in flight journey response`() = runTest {
        val response = CompletableDeferred<JourneyResponse>()
        val session = PlaceSessionCoordinator(
            placeRepository = PlaceSearchRepository { emptyResponse() },
            journeyRepository = JourneyRepository { response.await() },
            scope = backgroundScope,
        )
        val destination = place("hospital-7", GeoPoint(37.5145, 127.0316))
        session.loadJourney(GeoPoint(37.4979, 127.0276), destination)
        runCurrent()
        assertEquals(destination.key, session.state.value.journey.destinationKey)

        session.deactivate()
        runCurrent()
        assertEquals(PlaceJourneyState(), session.state.value.journey)
        response.complete(
            JourneyResponse(
                companion = "dog",
                items = listOf(
                    JourneyItem(
                        destination = destination.point,
                        name = destination.name,
                        straightMeters = 0,
                        modePriority = emptyList(),
                        legs = emptyMap(),
                    ),
                ),
            ),
        )
        runCurrent()

        assertEquals(PlaceJourneyState(), session.state.value.journey)
    }

    private fun TestScope.session(repository: PlaceSearchRepository) = PlaceSessionCoordinator(
        placeRepository = repository,
        journeyRepository = JourneyRepository { JourneyResponse("dog", emptyList()) },
        scope = backgroundScope,
    )

    private fun place(ref: String, point: GeoPoint): PlaceResult {
        val key = PlaceKey("medical", ref)
        return PlaceResult(
            key = key,
            aliases = emptyList(),
            name = "댕스동물병원",
            point = point,
            distanceMeters = 0,
            match = PlaceMatch(key, PlaceKind.HOSPITAL),
            classifications = emptyList(),
            facts = PlaceFacts(
                address = null,
                phone = null,
                homepage = null,
                hoursText = null,
                closedDays = null,
                parking = null,
                indoor = null,
                outdoor = null,
                petAccess = null,
                medical = null,
            ),
            fieldSources = emptyMap(),
            iconGroup = FacilityIconGroup.MEDICAL,
        )
    }

    private companion object {
        fun emptyResponse() = PlaceSearchResponse(
            conditions = PlaceSearchConditions(null, null, null),
            groups = emptyList(),
        )
    }
}
