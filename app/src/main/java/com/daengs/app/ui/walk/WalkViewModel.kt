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
    /**
     * 이미 보여 주고 거둔 산책 안내.
     *
     * **서비스가 든 값은 안 건드린다.** 기록이 실패한 것은 사실이고 그 사실은 서비스가
     * 들고 있어야 한다 — 화면만 잠깐 뒤 안 보여 준다.
     */
    val dismissedTrackingError: String? = null,
)

/**
 * 지금 화면에 띄울 산책 안내.
 *
 * 거둔 것과 같은 말이면 안 띄운다. **말이 바뀌면 다시 띄운다** — 다음 산책이 다른
 * 이유로 실패하면 그건 새 소식이다.
 */
internal fun visibleTrackingError(message: String?, dismissed: String?): String? =
    message?.takeIf { it != dismissed }

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
    private val trackingErrorMillis: Long = TRACKING_ERROR_MILLIS,
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
    private var trackingErrorJob: Job? = null
    private var observedCompletedSessionId: String? = null

    val state: StateFlow<WalkUiState> = combine(
        walkController.state,
        location.state,
        territory.state,
        presentation,
    ) { tracking, locationState, territoryState, presentationState ->
        WalkUiState(
            // 안내는 **잠깐 떴다 사라진다.** 예전에는 지우는 곳이 아예 없어서, 다시
            // 걸으려고 들어와도 지난 실패가 먼저 붙어 있었다.
            tracking = tracking.copy(
                errorMessage = visibleTrackingError(
                    tracking.errorMessage,
                    presentationState.dismissedTrackingError,
                ),
            ),
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
                scheduleTrackingErrorDismiss(tracking.errorMessage)
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
        walkController.state.value.let { tracking ->
            location.acceptTrackingState(
                active = tracking.trail.state != TrackingState.OFF,
                sample = tracking.lastSample,
            )
        }
        if (permissionGranted && presentation.value.map.purpose == MapPurpose.TERRITORY) {
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
        if (!granted) {
            territory.deactivate()
        } else {
            val tracking = walkController.state.value
            val trackingActive = tracking.trail.state != TrackingState.OFF
            location.acceptTrackingState(
                active = trackingActive,
                sample = tracking.lastSample,
            )
            if (active && !trackingActive && location.state.value.currentPosition == null) {
                location.locate(recenter = true)
            }
            if (active && presentation.value.map.purpose == MapPurpose.TERRITORY) {
                territory.activate(location.state.value.currentPosition)
            }
        }
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
                    // **전부 고르지 않는다.** 예전에는 여기서 `petIds` 를 통째로 넣었는데,
                    // 화면은 "누구와 나갈까요?" 라고 묻고 골라진 표시는 연분홍/흰색
                    // 차이뿐이라, 데려갈 아이를 "고르려고" 누른 것이 빼는 동작이 되어
                    // 나머지 아이들과 다녀온 것으로 기록됐다 (`WalkDogPick.kt`).
                    selectedDogIds = if (mayReset) {
                        defaultWalkDogs(pets)
                    } else {
                        current.selection.selectedDogIds
                    },
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
        if (purpose == MapPurpose.TERRITORY && active && location.state.value.permissionGranted) {
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

    /**
     * 산책 안내를 [trackingErrorMillis] 뒤에 거둔다.
     *
     * **읽을 시간은 준다.** "너무 짧아서 기록하지 않았어요" 는 걷고 온 사람에게
     * 산책이 어디 갔는지 설명하는 말이라, 토스트처럼 스쳐 지나가면 안 된다.
     */
    private fun scheduleTrackingErrorDismiss(message: String?) {
        if (message == null || message == presentation.value.dismissedTrackingError) return
        trackingErrorJob?.cancel()
        trackingErrorJob = runtimeScope.launch {
            delay(trackingErrorMillis)
            presentation.update { it.copy(dismissedTrackingError = message) }
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

/**
 * 산책 안내가 붙어 있는 시간.
 *
 * 순간 기록 알림([MOMENT_NOTICE_MILLIS])보다 길다. 저건 "찍혔어요" 라는 확인이고
 * 이건 **걷고 온 산책이 왜 없는지**를 설명하는 말이다.
 */
private const val TRACKING_ERROR_MILLIS = 5_000L
