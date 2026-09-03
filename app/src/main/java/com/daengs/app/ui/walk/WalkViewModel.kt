package com.daengs.app.ui.walk

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daengs.app.BuildConfig
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.location.LocationSource
import com.daengs.app.map.features.territory.TerritoryBoardController
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.pet.Pet
import com.daengs.app.territory.HttpTerritorySiteRepository
import com.daengs.app.territory.TerritorySiteApi
import com.daengs.app.territory.TerritorySiteRepository
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.WalkEvent
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkMomentOutcome
import com.daengs.app.walk.WalkTrackingController
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class WalkPresentationState(
    val selection: WalkSelectionState = WalkSelectionState(),
    val knownPetIds: Set<String> = emptySet(),
    val map: WalkMapUiState = WalkMapUiState(),
    val completion: WalkCompletionUiState = WalkCompletionUiState(),
    val momentNotice: String? = null,
)

/**
 * 산책 화면의 정책과 비동기 상태를 소유한다.
 * 실제 경로 기록의 진실은 계속 [WalkTrackingController] 뒤 Foreground Service에 남는다.
 */
class WalkViewModel(
    private val walkController: WalkTrackingController,
    private val history: WalkHistory,
    locationSource: LocationSource,
    territoryRepository: TerritorySiteRepository,
    externalScope: CoroutineScope? = null,
    private val momentNoticeMillis: Long = MOMENT_NOTICE_MILLIS,
) : ViewModel() {
    private val runtimeScope = externalScope ?: viewModelScope
    private val location = WalkLocationCoordinator(locationSource, runtimeScope)
    private val territory = TerritoryBoardController(territoryRepository, runtimeScope)
    private val presentation = MutableStateFlow(WalkPresentationState())
    private val effectChannel = Channel<WalkEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var active = false
    private var completionJob: Job? = null
    private var noticeJob: Job? = null
    private var observedCompletedSessionId: String? = null

    val state: StateFlow<WalkUiState> = combine(
        walkController.state,
        location.state,
        territory.state,
        presentation,
    ) { tracking, locationState, territoryState, presentationState ->
        WalkUiState(
            tracking = tracking,
            location = locationState,
            selection = presentationState.selection,
            map = presentationState.map,
            territory = territoryState,
            completion = presentationState.completion,
            momentNotice = presentationState.momentNotice,
        )
    }.stateIn(
        scope = runtimeScope,
        started = SharingStarted.Eagerly,
        initialValue = WalkUiState(tracking = walkController.state.value),
    )

    init {
        walkController.state.value.let { tracking ->
            location.acceptTrackingState(
                active = tracking.trail.state != TrackingState.OFF,
                sample = tracking.lastSample,
            )
        }
        runtimeScope.launch {
            walkController.state.collect { tracking ->
                val trackingActive = tracking.trail.state != TrackingState.OFF
                location.acceptTrackingState(trackingActive, tracking.lastSample)
                observeCompletedSession(tracking.completedSessionId)
            }
        }
        runtimeScope.launch {
            walkController.events.collect(::onWalkEvent)
        }
        runtimeScope.launch {
            location.state.collect { locationState ->
                if (shouldRefreshTerritoryFromDevice(
                        presentation.value.map.purpose,
                        locationState.followDevice,
                    )
                ) {
                    locationState.currentPosition?.let(territory::onDevicePosition)
                }
            }
        }
    }

    fun activate(permissionGranted: Boolean, precisePermission: Boolean) {
        active = true
        location.activate(permissionGranted, precisePermission)
        if (presentation.value.map.purpose == MapPurpose.TERRITORY) {
            territory.activate(location.state.value.currentPosition)
        }
    }

    fun deactivate() {
        active = false
        location.deactivate()
        territory.deactivate()
    }

    fun updatePermission(granted: Boolean, precise: Boolean) {
        location.updatePermission(granted, precise)
    }

    fun updatePets(pets: List<Pet>) {
        val petIds = pets.mapTo(linkedSetOf(), Pet::id)
        presentation.update { current ->
            val tracking = walkController.state.value
            val mayReset = tracking.trail.state == TrackingState.OFF &&
                tracking.completedSessionId == null &&
                petIds != current.knownPetIds
            current.copy(
                selection = current.selection.copy(
                    pets = pets,
                    selectedDogIds = if (mayReset) petIds else current.selection.selectedDogIds,
                ),
                knownPetIds = if (mayReset) petIds else current.knownPetIds,
            )
        }
    }

    fun onAction(action: WalkAction) {
        when (action) {
            WalkAction.Back,
            WalkAction.Home,
            WalkAction.CloseResult,
            -> closeAndNavigateHome()
            WalkAction.StartRequested -> emit(WalkEffect.RequestNotificationPermission)
            WalkAction.StartConfirmed -> startWalk()
            WalkAction.Pause -> walkController.pause()
            WalkAction.Resume -> {
                location.setFollowDevice(true)
                walkController.resume()
            }
            WalkAction.Stop -> walkController.stop()
            WalkAction.Locate -> location.locate(recenter = true)
            WalkAction.OpenAppSettings -> emit(WalkEffect.OpenAppSettings)
            WalkAction.RetryTerritory -> territory.retry()
            WalkAction.ClearRoutePoint -> selectRoutePoint(null)
            WalkAction.ReviewMap -> presentation.update {
                it.copy(completion = it.completion.copy(resultExpanded = false))
            }
            WalkAction.ShowResult -> presentation.update {
                it.copy(completion = it.completion.copy(resultExpanded = true))
            }
            is WalkAction.ToggleDog -> toggleDog(action.id)
            is WalkAction.ChangeMapPurpose -> changeMapPurpose(action.purpose)
            is WalkAction.RequestOrientation -> emit(WalkEffect.ChangeOrientation(action.orientation))
            is WalkAction.AddMoment -> walkController.recordMoment(action.type)
            is WalkAction.SelectMoment -> selectMoment(action.id)
            is WalkAction.SelectTerritorySite -> territory.select(action.id)
            is WalkAction.SelectRouteEndpoint -> selectRouteEndpoint(action.id)
            is WalkAction.CameraSettled -> if (presentation.value.map.purpose == MapPurpose.TERRITORY) {
                territory.onCameraSettled(action.point)
            }
            WalkAction.CameraMoved -> location.setFollowDevice(false)
            is WalkAction.MapTapped -> onMapTapped(action)
        }
    }

    private fun startWalk() {
        location.setFollowDevice(true)
        presentation.update {
            it.copy(
                map = it.map.copy(
                    selectedMomentId = null,
                    selectedRoutePointKey = null,
                    selectedRouteSessionId = null,
                ),
                completion = WalkCompletionUiState(),
                momentNotice = null,
            )
        }
        walkController.start(presentation.value.selection.selectedDogIds.toList())
    }

    private fun toggleDog(id: String) {
        presentation.update { current ->
            if (current.selection.pets.none { it.id == id }) return@update current
            val selected = current.selection.selectedDogIds
            current.copy(
                selection = current.selection.copy(
                    selectedDogIds = if (id in selected) selected - id else selected + id,
                ),
            )
        }
    }

    private fun changeMapPurpose(purpose: MapPurpose) {
        presentation.update {
            it.copy(map = it.map.copy(purpose = purpose))
        }
        territory.clearSelection()
        if (purpose == MapPurpose.TERRITORY && active) {
            territory.activate(location.state.value.currentPosition)
        } else {
            territory.deactivate()
        }
    }

    private fun observeCompletedSession(sessionId: String?) {
        if (sessionId == observedCompletedSessionId) return
        observedCompletedSessionId = sessionId
        completionJob?.cancel()
        presentation.update { current ->
            current.copy(
                map = current.map.copy(
                    selectedRoutePointKey = null,
                    selectedRouteSessionId = null,
                ),
                completion = current.completion.copy(detail = null),
            )
        }
        if (sessionId == null) return
        completionJob = runtimeScope.launch {
            val detail = try {
                history.sessionDetail(sessionId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
            if (observedCompletedSessionId != sessionId || detail == null) return@launch
            territory.deactivate()
            location.setFollowDevice(false)
            presentation.update { current ->
                current.copy(
                    map = current.map.copy(purpose = MapPurpose.WALK),
                    completion = WalkCompletionUiState(detail = detail, resultExpanded = true),
                )
            }
        }
    }

    private fun onWalkEvent(event: WalkEvent) {
        when (event) {
            WalkEvent.MomentLocationUnavailable -> showNotice(
                "정확한 GPS가 잡히면 이 자리에 순간을 남길 수 있어요.",
            )
            is WalkEvent.MomentRecorded -> {
                presentation.update {
                    it.copy(
                        map = it.map.copy(
                            selectedMomentId = event.momentId,
                            selectedRoutePointKey = null,
                            selectedRouteSessionId = null,
                        ),
                    )
                }
                showNotice(
                    when (event.outcome) {
                        WalkMomentOutcome.CREATED -> "${event.type.label}을 이 장소에 남겼어요."
                        WalkMomentOutcome.MERGED -> "${event.type.label}을 이 장소에 추가했어요."
                        WalkMomentOutcome.ALREADY_EXISTS ->
                            "이미 이 장소에 ${event.type.label}이 남아 있어요."
                    },
                )
            }
        }
    }

    private fun showNotice(message: String) {
        noticeJob?.cancel()
        presentation.update { it.copy(momentNotice = message) }
        noticeJob = runtimeScope.launch {
            delay(momentNoticeMillis)
            presentation.update { current ->
                if (current.momentNotice == message) current.copy(momentNotice = null) else current
            }
        }
    }

    private fun selectMoment(id: String) {
        val moment = state.value.displayedMoments.firstOrNull { it.id == id } ?: return
        presentation.update {
            it.copy(
                map = it.map.copy(
                    selectedMomentId = id,
                    selectedRoutePointKey = null,
                    selectedRouteSessionId = null,
                ),
            )
        }
        showNotice("${moment.actionLabels} · ${formatClock(moment.latestRecordedAtMillis, seconds = true)}")
    }

    private fun selectRouteEndpoint(id: String) {
        val route = presentation.value.completion.detail?.route
        selectRoutePoint(
            when (id) {
                ROUTE_START_ID -> route?.start
                ROUTE_END_ID -> route?.end
                ROUTE_START_END_ID -> route?.end
                else -> null
            },
        )
    }

    private fun onMapTapped(action: WalkAction.MapTapped) {
        if (presentation.value.map.purpose == MapPurpose.TERRITORY) {
            territory.clearSelection()
        } else {
            val point = presentation.value.completion.detail?.route
                ?.nearestPointTo(action.point, ROUTE_POINT_TAP_RADIUS_METERS)
            selectRoutePoint(point)
        }
    }

    private fun selectRoutePoint(point: com.daengs.app.walk.WalkRoutePoint?) {
        presentation.update { current ->
            current.copy(
                map = current.map.copy(
                    selectedMomentId = current.map.selectedMomentId.takeIf { point == null },
                    selectedRoutePointKey = point?.routePointKey,
                    selectedRouteSessionId = walkController.state.value.completedSessionId
                        .takeIf { point != null },
                ),
                momentNotice = current.momentNotice.takeIf { point == null },
            )
        }
    }

    private fun closeAndNavigateHome() {
        if (walkController.state.value.completedSessionId != null) {
            walkController.dismissCompletion()
        }
        observedCompletedSessionId = null
        completionJob?.cancel()
        presentation.update {
            it.copy(
                map = it.map.copy(
                    selectedRoutePointKey = null,
                    selectedRouteSessionId = null,
                ),
                completion = WalkCompletionUiState(),
            )
        }
        emit(WalkEffect.NavigateHome)
    }

    private fun emit(effect: WalkEffect) {
        effectChannel.trySend(effect)
    }

    override fun onCleared() {
        deactivate()
        super.onCleared()
    }

    companion object {
        fun factory(
            context: Context,
            walkController: WalkTrackingController,
            history: WalkHistory,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(WalkViewModel::class.java))
                return WalkViewModel(
                    walkController = walkController,
                    history = history,
                    locationSource = FusedLocationSource(context.applicationContext),
                    territoryRepository = HttpTerritorySiteRepository(
                        TerritorySiteApi(baseUrl = { BuildConfig.API_BASE_URL }),
                    ),
                ) as T
            }
        }
    }
}

private const val MOMENT_NOTICE_MILLIS = 2_200L
