package com.daengs.app.ui.places

import com.daengs.app.journey.JourneyRepository
import com.daengs.app.journey.JourneyResponse
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.location.LocationUpdateConfig
import com.daengs.app.map.features.places.PlaceSearchState
import com.daengs.app.place.PlaceFailure
import com.daengs.app.place.PlaceSearchConditions
import com.daengs.app.place.PlaceSearchRequest
import com.daengs.app.place.PlaceSearchResponse
import com.daengs.app.place.PlaceSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModelTest {
    @Test
    fun `permission denial has distinct request and settings recovery states`() = runTest {
        val viewModel = viewModelAt(GeoPoint(37.5, 127.0), backgroundScope)

        viewModel.activate(permissionGranted = false)
        runCurrent()
        assertEquals(PlaceLocationState.PermissionRequired, viewModel.state.value.location)

        viewModel.updatePermission(granted = false, permanentlyDenied = true)
        runCurrent()
        assertEquals(
            PlaceLocationState.PermissionPermanentlyDenied,
            viewModel.state.value.location,
        )
    }

    @Test
    fun `revoking permission clears device results that can no longer be refreshed`() = runTest {
        val viewModel = viewModelAt(GeoPoint(37.5, 127.0), backgroundScope)

        runCurrent()
        viewModel.activate(permissionGranted = true)
        runCurrent()
        assertEquals(PlaceSearchState.Empty(emptyResponse()), viewModel.state.value.discovery.search)

        viewModel.updatePermission(granted = false, permanentlyDenied = true)
        runCurrent()

        assertEquals(PlaceSearchState.Idle, viewModel.state.value.discovery.search)
    }

    @Test
    fun `an overseas device location cannot crash or reach the place repository`() = runTest {
        var placeCalls = 0
        val repository = PlaceSearchRepository { _: PlaceSearchRequest ->
            placeCalls++
            emptyResponse()
        }
        val viewModel = viewModelAt(
            point = GeoPoint(37.7749, -122.4194),
            scope = backgroundScope,
            repository = repository,
        )

        runCurrent()
        viewModel.activate(permissionGranted = true)
        runCurrent()

        assertEquals(
            PlaceLocationState.Unsupported(GeoPoint(37.7749, -122.4194)),
            viewModel.state.value.location,
        )
        assertEquals(0, placeCalls)
        assertEquals(
            PlaceSearchState.Failed(PlaceFailure.UnsupportedLocation),
            viewModel.state.value.discovery.search,
        )
    }

    @Test
    fun `a device feed moving overseas invalidates a previous empty result`() = runTest {
        val source = StreamingLocationSource(GeoPoint(37.5, 127.0))
        val viewModel = PlacesViewModel(
            placeRepository = PlaceSearchRepository { emptyResponse() },
            journeyRepository = JourneyRepository { JourneyResponse("dog", emptyList()) },
            locationSource = source,
            externalScope = backgroundScope,
        )

        runCurrent()
        viewModel.activate(permissionGranted = true)
        runCurrent()
        assertEquals(PlaceSearchState.Empty(emptyResponse()), viewModel.state.value.discovery.search)

        source.emit(GeoPoint(37.7749, -122.4194))
        runCurrent()

        assertEquals(
            PlaceSearchState.Failed(PlaceFailure.UnsupportedLocation),
            viewModel.state.value.discovery.search,
        )
    }

    private fun viewModelAt(
        point: GeoPoint,
        scope: CoroutineScope,
        repository: PlaceSearchRepository = PlaceSearchRepository { emptyResponse() },
    ) = PlacesViewModel(
        placeRepository = repository,
        journeyRepository = JourneyRepository { JourneyResponse("dog", emptyList()) },
        locationSource = FixedLocationSource(point),
        externalScope = scope,
    )

    private class FixedLocationSource(private val point: GeoPoint) : LocationSource {
        override suspend fun currentLocation() = LocationSample(
            point = point,
            capturedAtMillis = 0,
        )

        override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> =
            emptyFlow()
    }

    private class StreamingLocationSource(private val current: GeoPoint) : LocationSource {
        private val updates = MutableSharedFlow<LocationSample>(extraBufferCapacity = 1)

        override suspend fun currentLocation() = sample(current)

        override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> = updates

        fun emit(point: GeoPoint) {
            check(updates.tryEmit(sample(point)))
        }

        private fun sample(point: GeoPoint) = LocationSample(
            point = point,
            capturedAtMillis = 0,
        )
    }

    companion object {
        private fun emptyResponse() = PlaceSearchResponse(
            conditions = PlaceSearchConditions(null, null, null),
            groups = emptyList(),
        )
    }
}
