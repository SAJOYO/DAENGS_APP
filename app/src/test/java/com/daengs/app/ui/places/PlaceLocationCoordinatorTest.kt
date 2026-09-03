package com.daengs.app.ui.places

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.location.LocationUpdateConfig
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceLocationCoordinatorTest {
    @Test
    fun `a non cancellable late fix cannot escape after deactivation`() = runTest {
        val source = DelayedLocationSource()
        val coordinator = PlaceLocationCoordinator(source, backgroundScope)
        var accepted: LocationSample? = null

        coordinator.activate(granted = true)
        coordinator.locate { accepted = it }
        runCurrent()
        coordinator.deactivate()

        source.complete(sample(GeoPoint(37.5, 127.0)))
        runCurrent()

        assertEquals(null, accepted)
    }

    @Test
    fun `a late fix cannot restore location after permission revocation`() = runTest {
        val source = DelayedLocationSource()
        val coordinator = PlaceLocationCoordinator(source, backgroundScope)

        coordinator.activate(granted = true)
        coordinator.locate {}
        runCurrent()
        coordinator.updatePermission(granted = false, permanentlyDenied = true)

        source.complete(sample(GeoPoint(37.5, 127.0)))
        runCurrent()

        assertEquals(PlaceLocationState.PermissionPermanentlyDenied, coordinator.state.value)
    }

    @Test
    fun `stream fixes are classified without knowing about place search`() = runTest {
        val source = StreamingLocationSource()
        val coordinator = PlaceLocationCoordinator(source, backgroundScope)

        coordinator.activate(granted = true)
        runCurrent()
        source.emit(sample(GeoPoint(37.5, 127.0)))
        runCurrent()
        assertEquals(
            PlaceLocationState.Ready(GeoPoint(37.5, 127.0)),
            coordinator.state.value,
        )

        source.emit(sample(GeoPoint(37.5, 127.0), isMock = true))
        runCurrent()
        assertEquals(
            PlaceLocationState.Failed(
                PlaceLocationFailure.MOCK_LOCATION,
                lastKnown = GeoPoint(37.5, 127.0),
            ),
            coordinator.state.value,
        )
    }

    private class DelayedLocationSource : LocationSource {
        private var continuation: Continuation<LocationSample>? = null

        override suspend fun currentLocation(): LocationSample = suspendCoroutine {
            check(continuation == null)
            continuation = it
        }

        override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> =
            kotlinx.coroutines.flow.emptyFlow()

        fun complete(value: LocationSample) {
            val pending = checkNotNull(continuation)
            continuation = null
            pending.resume(value)
        }
    }

    private class StreamingLocationSource : LocationSource {
        private val updates = MutableSharedFlow<LocationSample>(extraBufferCapacity = 2)

        override suspend fun currentLocation(): LocationSample = error("not used")

        override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> = updates

        fun emit(value: LocationSample) {
            check(updates.tryEmit(value))
        }
    }

    private companion object {
        fun sample(point: GeoPoint, isMock: Boolean = false) = LocationSample(
            point = point,
            capturedAtMillis = 0,
            isMock = isMock,
        )
    }
}
