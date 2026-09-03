package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.location.LocationUpdateConfig
import com.daengs.app.pet.Pet
import com.daengs.app.territory.TerritorySitePage
import com.daengs.app.territory.TerritorySiteRepository
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWalkAction
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.TrailSnapshot
import com.daengs.app.walk.WalkEvent
import com.daengs.app.walk.WalkFixLog
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkTrackingController
import com.daengs.app.walk.WalkTrackingState
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkViewModelTest {
    @Test
    fun `screen location feed stops while tracking service owns the walk`() = runTest {
        val source = CountingLocationSource()
        val controller = FakeWalkController()
        val viewModel = viewModel(controller, source)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        assertEquals(WalkLocationOwner.SCREEN, viewModel.state.value.location.owner)
        assertEquals(1, source.starts)

        controller.publish(
            WalkTrackingState(trail = TrailSnapshot(state = TrackingState.RECORDING)),
        )
        runCurrent()
        assertEquals(WalkLocationOwner.TRACKING_SERVICE, viewModel.state.value.location.owner)
        assertEquals(1, source.stops)

        controller.publish(WalkTrackingState())
        runCurrent()
        assertEquals(WalkLocationOwner.SCREEN, viewModel.state.value.location.owner)
        assertEquals(2, source.starts)
    }

    @Test
    fun `start action freezes the selected dog ids into the service command`() = runTest {
        val controller = FakeWalkController()
        val viewModel = viewModel(controller, CountingLocationSource())
        val first = pet("dog-1")
        val second = pet("dog-2")

        viewModel.updatePets(listOf(first, second))
        runCurrent()
        viewModel.onAction(WalkAction.ToggleDog(second.id))
        viewModel.onAction(WalkAction.StartConfirmed)

        assertEquals(listOf(first.id), controller.startedDogIds)
    }

    @Test
    fun `opening the screen during a walk does not request a second location fix`() = runTest {
        val source = CountingLocationSource()
        val controller = FakeWalkController().apply {
            publish(
                WalkTrackingState(
                    trail = TrailSnapshot(state = TrackingState.RECORDING),
                    lastSample = LocationSample(GeoPoint(37.5, 127.0), capturedAtMillis = 0),
                ),
            )
        }
        val viewModel = viewModel(controller, source)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()

        assertEquals(WalkLocationOwner.TRACKING_SERVICE, viewModel.state.value.location.owner)
        assertEquals(0, source.currentLocationCalls)
        assertEquals(0, source.starts)
    }

    private fun TestScope.viewModel(
        controller: FakeWalkController,
        source: LocationSource,
    ) = WalkViewModel(
        walkController = controller,
        history = WalkHistory(EmptyWalkFixLog),
        locationSource = source,
        territoryRepository = TerritorySiteRepository {
            TerritorySitePage(count = 0, truncated = false, sites = emptyList())
        },
        externalScope = backgroundScope,
    )

    private class CountingLocationSource : LocationSource {
        var starts = 0
        var stops = 0
        var currentLocationCalls = 0

        override suspend fun currentLocation(): LocationSample {
            currentLocationCalls++
            return LocationSample(
                point = GeoPoint(37.5, 127.0),
                capturedAtMillis = 0,
            )
        }

        override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> = flow {
            starts++
            try {
                awaitCancellation()
            } finally {
                stops++
            }
        }
    }

    private class FakeWalkController : WalkTrackingController {
        private val mutableState = MutableStateFlow(WalkTrackingState())
        override val state = mutableState.asStateFlow()
        private val mutableEvents = MutableSharedFlow<WalkEvent>(extraBufferCapacity = 4)
        override val events = mutableEvents.asSharedFlow()
        var startedDogIds: List<String>? = null

        fun publish(state: WalkTrackingState) {
            mutableState.value = state
        }

        override fun start(dogIds: List<String>) {
            startedDogIds = dogIds
        }

        override fun pause() = Unit
        override fun resume() = Unit
        override fun stop() = Unit
        override fun recordMoment(type: WalkMomentType) = Unit
        override fun dismissCompletion() = Unit
    }

    private fun pet(id: String) = Pet(
        id = id,
        name = id,
        breed = "maltese",
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = LocalDate.of(2020, 1, 1),
        birthDateKind = Pet.BirthDateKind.BIRTHDAY,
        isPrimary = id == "dog-1",
    )
}

private data object EmptyWalkFixLog : WalkFixLog {
    override suspend fun openSession(session: RecordedSession) = Unit
    override suspend fun append(sessionId: String, fix: RecordedFix) = Unit
    override suspend fun appendAction(action: RecordedWalkAction) = Unit
    override suspend fun closeSession(sessionId: String, endedAtMillis: Long) = Unit
    override suspend fun stampWeather(sessionId: String, weather: RecordedWeather) = Unit
    override suspend fun deleteSession(sessionId: String) = Unit
    override suspend fun unfinishedSessions(): List<RecordedSession> = emptyList()
    override suspend fun finishedSessions(): List<RecordedSession> = emptyList()
    override suspend fun sessionsPendingAnalysis(): List<RecordedSession> = emptyList()
    override suspend fun markRawUploaded(
        sessionId: String,
        serverWalkId: String,
        changedAtMillis: Long,
    ) = Unit

    override suspend fun markDerived(sessionId: String, changedAtMillis: Long) = Unit
    override suspend fun forgetDog(dogId: String) = Unit
    override suspend fun forgetEverything() = Unit
    override suspend fun session(sessionId: String): RecordedSession? = null
    override suspend fun fixes(sessionId: String): List<RecordedFix> = emptyList()
    override suspend fun actions(sessionId: String): List<RecordedWalkAction> = emptyList()
}
