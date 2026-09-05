package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.location.LocationUpdateConfig
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.pet.Pet
import com.daengs.app.territory.NearbyTerritorySitesRequest
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

        // 두 마리면 **아무도 안 골라진 채로 시작한다** (`WalkDogPick.defaultWalkDogs`).
        // 그래서 여기서 누른 한 마리만 명령에 실린다.
        viewModel.updatePets(listOf(first, second))
        runCurrent()
        viewModel.onAction(WalkAction.ToggleDog(second.id))
        viewModel.onAction(WalkAction.StartConfirmed)

        assertEquals(listOf(second.id), controller.startedDogIds)
    }

    /**
     * **두 마리 이상이면 아무도 안 골라져 있어야 한다.**
     *
     * 예전에는 전부 골라진 채로 시작했다. 화면은 "누구와 나갈까요?" 라고 묻고 골라진
     * 표시는 연분홍/흰색 차이뿐이라, 데려갈 아이를 고르려고 누른 것이 **빼는 동작**이
     * 되어 나머지 아이들과 다녀온 것으로 기록됐다 (비공개 테스트에서 실제로 났다).
     */
    @Test
    fun `two dogs start with nothing selected`() = runTest {
        val viewModel = viewModel(FakeWalkController(), CountingLocationSource())

        viewModel.updatePets(listOf(pet("dog-1"), pet("dog-2")))
        runCurrent()

        assertEquals(emptySet<String>(), viewModel.state.value.selection.selectedDogIds)
    }

    /** 한 마리면 고를 것이 없다. 매번 누르게 하면 탭만 는다. */
    @Test
    fun `one dog is selected for you`() = runTest {
        val viewModel = viewModel(FakeWalkController(), CountingLocationSource())
        val only = pet("dog-1")

        viewModel.updatePets(listOf(only))
        runCurrent()

        assertEquals(setOf(only.id), viewModel.state.value.selection.selectedDogIds)
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

    @Test
    fun `leaving while locating cancels loading and allows a fresh request on return`() = runTest {
        val source = SuspendingLocationSource()
        val viewModel = viewModel(FakeWalkController(), source)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        assertEquals(true, viewModel.state.value.location.locating)
        assertEquals(1, source.currentLocationCalls)

        viewModel.deactivate()
        runCurrent()
        assertEquals(false, viewModel.state.value.location.locating)
        assertEquals(1, source.cancellations)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        assertEquals(true, viewModel.state.value.location.locating)
        assertEquals(2, source.currentLocationCalls)
    }

    @Test
    fun `service takeover cancels an in flight screen location request`() = runTest {
        val source = SuspendingLocationSource()
        val controller = FakeWalkController()
        val viewModel = viewModel(controller, source)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        controller.publish(
            WalkTrackingState(
                trail = TrailSnapshot(state = TrackingState.RECORDING),
                lastSample = LocationSample(GeoPoint(37.51, 127.01), capturedAtMillis = 1),
            ),
        )
        runCurrent()

        assertEquals(1, source.cancellations)
        assertEquals(false, viewModel.state.value.location.locating)
        assertEquals(WalkLocationOwner.TRACKING_SERVICE, viewModel.state.value.location.owner)
        assertEquals(GeoPoint(37.51, 127.01), viewModel.state.value.location.currentPosition)
    }

    @Test
    fun `revoking permission clears cached location and blocks stale territory reload`() = runTest {
        val territory = CountingTerritoryRepository()
        val viewModel = viewModel(
            controller = FakeWalkController(),
            source = CountingLocationSource(),
            territoryRepository = territory,
        )

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        viewModel.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY))
        runCurrent()
        assertEquals(1, territory.calls)

        viewModel.updatePermission(granted = false, precise = false)
        runCurrent()
        assertEquals(null, viewModel.state.value.location.currentPosition)
        assertEquals(null, viewModel.state.value.toMapPresentation { "" }.scene.currentPosition)

        viewModel.deactivate()
        viewModel.activate(permissionGranted = false, precisePermission = false)
        runCurrent()
        assertEquals(1, territory.calls)
    }

    @Test
    fun `granting permission while visible immediately locates the device`() = runTest {
        val source = CountingLocationSource()
        val viewModel = viewModel(FakeWalkController(), source)

        viewModel.activate(permissionGranted = false, precisePermission = false)
        runCurrent()
        assertEquals(0, source.currentLocationCalls)

        viewModel.updatePermission(granted = true, precise = true)
        runCurrent()

        assertEquals(1, source.currentLocationCalls)
        assertEquals(GeoPoint(37.5, 127.0), viewModel.state.value.location.currentPosition)
    }

    private fun TestScope.viewModel(
        controller: FakeWalkController,
        source: LocationSource,
        territoryRepository: TerritorySiteRepository = TerritorySiteRepository {
            TerritorySitePage(count = 0, truncated = false, sites = emptyList())
        },
    ) = WalkViewModel(
        walkController = controller,
        history = WalkHistory(EmptyWalkFixLog),
        locationSource = source,
        territoryRepository = territoryRepository,
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

    private class SuspendingLocationSource : LocationSource {
        var currentLocationCalls = 0
        var cancellations = 0

        override suspend fun currentLocation(): LocationSample {
            currentLocationCalls++
            try {
                awaitCancellation()
            } finally {
                cancellations++
            }
        }

        override fun locationUpdates(
            config: LocationUpdateConfig,
        ): Flow<LocationSample> = emptyFlow()
    }

    private class CountingTerritoryRepository : TerritorySiteRepository {
        var calls = 0

        override suspend fun nearby(
            request: NearbyTerritorySitesRequest,
        ): TerritorySitePage {
            calls++
            return TerritorySitePage(count = 0, truncated = false, sites = emptyList())
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
