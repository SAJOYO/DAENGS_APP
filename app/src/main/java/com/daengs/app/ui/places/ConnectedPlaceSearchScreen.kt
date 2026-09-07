package com.daengs.app.ui.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.places.*
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.place.*
import com.daengs.app.ui.places.lab.*
import com.daengs.app.ui.theme.DaengsTheme

/** AI 확정 결과는 서버가 실행한 응답 그대로 그린다. 일반 검색을 다시 호출하면 조건을 잃는다. */
internal fun PlacesUiState.visibleDiscovery(): PlaceDiscoveryState {
    if (!facility.enabled) return discovery
    val lens = facility.confirmedLens
    val response = lens?.search
    return discovery.copy(
        origin = facility.response?.request?.origin ?: discovery.origin,
        preferParking = lens?.parking ?: discovery.preferParking,
        selectedPlaceKey = facility.selectedPlaceKey,
        search = when {
            facility.loading -> PlaceSearchState.Loading
            facility.error != null || response == null -> PlaceSearchState.Idle
            response.groups.all { it.results.isEmpty() } -> PlaceSearchState.Empty(response)
            else -> PlaceSearchState.Content(response)
        },
    )
}

/** 운영 coordinator의 결과를 새 화면에 투영한다. 네트워크·위치의 별도 상태 소유자는 없다. */
fun PlacesUiState.toConnectedSearchState(draft: String, ai: Boolean, expanded: PlaceKey?, notice: String?): PlaceSearchLabState {
    val discovery = visibleDiscovery()
    val response = discovery.response
    val profileMismatch = response != null && response.dogs != profiles.snapshots()
    val locationFailed = waitingForSearchLocation && location is PlaceLocationState.Failed
    val category = PlaceCategorySelection.fromKinds(discovery.requestedKinds)
    val all = category == PlaceCategorySelection.All
    val hits = if (facility.enabled || discovery.requestedKinds.size > 1) response?.overviewHits(discovery.preferParking).orEmpty()
        else response?.groups?.flatMap { it.results }.orEmpty().distinctBy { it.place.key }
    val phase = when {
        facility.enabled && facility.loading -> LabPhase.LOADING
        facility.enabled && facility.error != null -> LabPhase.ERROR
        facility.enabled && facility.confirmedLens == null -> LabPhase.EMPTY
        location is PlaceLocationState.PermissionRequired || location is PlaceLocationState.PermissionPermanentlyDenied -> LabPhase.PERMISSION
        locationFailed -> LabPhase.ERROR
        discovery.loading || waitingForSearchLocation || profileMismatch -> LabPhase.LOADING
        discovery.search is PlaceSearchState.Failed -> LabPhase.ERROR
        hits.isNotEmpty() -> LabPhase.RESULTS
        else -> LabPhase.EMPTY
    }
    return PlaceSearchLabState(
        draft = draft, aiMode = ai,
        applied = LabCriteria(query = discovery.nameQuery, kind = (category as? PlaceCategorySelection.Kind)?.kind, parkingFirst = discovery.preferParking, radiusMeters = discovery.radiusMeters),
        hits = if (phase == LabPhase.RESULTS) hits else emptyList(), phase = phase,
        selected = discovery.selectedPlaceKey,
        expanded = expanded?.takeIf { key -> hits.any { it.place.key == key } },
        notice = notice ?: location.userMessage(), errorText = if (facility.enabled && facility.error != null) facility.error else if (locationFailed) location.userMessage()
            else discovery.error?.let { if (all) "전체 업종을 불러오지 못했어요. $it" else it },
        truncated = response?.groups?.any { it.truncated } == true,
        selectedDogIds = profiles.selectedIds,
        profileNames = profiles.pets.associate { it.id to it.name },
        profilesReady = profiles.ready,
        profileMessage = profiles.message,
    )
}

@Composable
fun ConnectedPlaceSearchScreen(
    state: PlacesUiState,
    onAction: (PlacesAction) -> Unit,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCall: (String) -> Unit,
    onOpenHandoff: (String) -> Unit,
    showMap: Boolean = true,
    onRefreshProfiles: () -> Unit = {},
) {
    var draft by rememberSaveable { mutableStateOf(state.discovery.nameQuery) }
    val ai = state.facility.enabled
    val display = state.visibleDiscovery()
    var expanded by remember { mutableStateOf<PlaceKey?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<GeoPoint?>(null) }
    var follow by remember { mutableStateOf(true) }
    val keyboard = LocalSoftwareKeyboardController.current
    val ui = state.toConnectedSearchState(draft, ai, expanded, notice)
    LaunchedEffect(state.discovery.response) {
        // A loading frame has no response; only completed results can remove an expanded card.
        if (state.discovery.response != null) expanded = ui.expanded
    }
    val category = PlaceCategorySelection.fromKinds(state.discovery.requestedKinds)
    val permission = state.location is PlaceLocationState.PermissionRequired || state.location is PlaceLocationState.PermissionPermanentlyDenied
    fun requestPermission() { if (state.location is PlaceLocationState.PermissionPermanentlyDenied) onOpenSettings() else onRequestPermission() }
    fun search(selected: PlaceCategorySelection = category, parking: Boolean = state.discovery.preferParking, query: String? = null) {
        if (permission) { requestPermission(); return }
        notice = null
        onAction(PlacesAction.Search(selected, parking, query))
    }
    BackHandler(onBack = onBack)
    PlaceSearchLabScreen(
        state = ui, live = true,
        onEdit = { draft = it }, onAi = { onAction(PlacesAction.SetAiMode(!ai)); notice = null },
        onSubmit = {
            when {
                ai -> { keyboard?.hide(); onAction(PlacesAction.Discover(draft)) }
                !isValidPlaceNameQuery(draft) -> notice = "장소명은 120자까지 입력할 수 있어요."
                else -> { keyboard?.hide(); search(query = draft.trim()) }
            }
        },
        categoryContent = { PlacePurposeMenu(category) { search(selected = it) } },
        resultLabel = if (ai) state.facility.confirmedLens?.label ?: "AI 조건 검색" else category.label,
        aiConnected = true,
        conditionContent = { FacilitySearchPanel(state.facility, { onAction(PlacesAction.ChooseAi(it)) }, { onAction(PlacesAction.RetryAi) }) },
        emptyMessage = if (ai && state.facility.confirmedLens == null) "검색 방향을 확정하면 장소가 여기에 표시돼요." else "검색 결과가 없어요.",
        onParking = { value ->
            if (category.kinds.any(PlaceKind::supportsParkingPreference)) search(parking = value)
            else notice = "이 업종은 주차 정보를 제공하지 않아요."
        },
        onRadius = { meters -> onAction(PlacesAction.SetRadius(meters)) },
        onDog = { id -> onAction(PlacesAction.ToggleDog(id)) },
        onRefreshProfiles = onRefreshProfiles,
        onToggle = { key ->
            expanded = key.takeUnless { it == expanded }
            onAction(PlacesAction.Select(key))
        },
        onRetry = {
            if (ai) { if (state.facility.canRetry) onAction(PlacesAction.RetryAi) }
            else if (permission) requestPermission() else onAction(PlacesAction.RetrySearch)
        },
        showRetry = !ai || state.facility.canRetry,
        cardActions = { hit ->
            if (ai) state.facility.confirmedLens?.presentations?.firstOrNull { it.key == hit.place.key }?.let { FacilityPresentationDetails(it) }
            hit.place.facts.phone?.let { phone -> TextButton(onClick = { onCall(phone) }) { Text("전화로 확인") } }
            PlaceJourneyAction(
                state.journey.takeIf { it.destinationKey == hit.place.key }.toActionPresentation(),
                onJourney = { onAction(PlacesAction.LoadJourney(hit.place)) },
                onRetry = { onAction(PlacesAction.LoadJourney(hit.place)) }, onOpenHandoff = onOpenHandoff,
            )
        },
        map = {
            Box(Modifier.fillMaxSize()) {
            val keys = canonicalPlaceKeysByMarker(display)
            if (showMap) MapHost(
                scene = MapScene(currentPosition = state.location.currentPosition, places = canonicalPlaceMarkers(display)),
                searchOrigin = display.origin, followDevice = follow,
                onCameraIdle = { camera = it }, onCameraGesture = { follow = false },
                onSelectPlace = { id -> keys[id]?.let { onAction(PlacesAction.Select(it)) } },
                modifier = Modifier.fillMaxSize(),
            )
            Row(Modifier.align(Alignment.TopCenter)) {
                TextButton(onClick = onBack) { Text("← 홈") }
                TextButton(onClick = {
                    if (permission) requestPermission() else { follow = true; onAction(PlacesAction.Locate(category, state.discovery.preferParking)) }
                }) { Text(if (permission) "위치 권한" else "내 위치") }
                camera?.takeIf { it != state.discovery.origin }?.let { point ->
                    TextButton(onClick = { follow = false; onAction(PlacesAction.SearchAt(point, category, state.discovery.preferParking)) }) { Text("이 지역 검색") }
                }
            }
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConnectedSearchPreview() {
    DaengsTheme { ConnectedPlaceSearchScreen(PlacesUiState(), {}, {}, {}, {}, {}, {}, showMap = false) }
}
