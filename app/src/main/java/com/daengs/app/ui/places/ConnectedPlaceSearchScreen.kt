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

/** 운영 coordinator의 결과를 새 화면에 투영한다. 네트워크·위치의 별도 상태 소유자는 없다. */
fun PlacesUiState.toConnectedSearchState(draft: String, ai: Boolean, expanded: PlaceKey?, notice: String?): PlaceSearchLabState {
    val response = discovery.response
    val hits = response?.groups?.flatMap { it.results }.orEmpty().distinctBy { it.place.key }
    val phase = when {
        location is PlaceLocationState.PermissionRequired || location is PlaceLocationState.PermissionPermanentlyDenied -> LabPhase.PERMISSION
        discovery.loading || location.locating -> LabPhase.LOADING
        discovery.search is PlaceSearchState.Failed -> LabPhase.ERROR
        hits.isNotEmpty() -> LabPhase.RESULTS
        else -> LabPhase.EMPTY
    }
    return PlaceSearchLabState(
        draft = draft, aiMode = ai,
        applied = LabCriteria(query = discovery.nameQuery, kind = selectedPlaceKind(discovery), parkingFirst = discovery.preferParking),
        hits = if (phase == LabPhase.RESULTS) hits else emptyList(), phase = phase,
        selected = discovery.selectedPlaceKey,
        expanded = expanded?.takeIf { key -> hits.any { it.place.key == key } },
        notice = notice ?: location.userMessage(), errorText = discovery.error,
        truncated = response?.groups?.any { it.truncated } == true,
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
) {
    var draft by rememberSaveable { mutableStateOf(state.discovery.nameQuery) }
    var ai by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<PlaceKey?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<GeoPoint?>(null) }
    var follow by remember { mutableStateOf(true) }
    val keyboard = LocalSoftwareKeyboardController.current
    val ui = state.toConnectedSearchState(draft, ai, expanded, notice)
    val kind = selectedPlaceKind(state.discovery)
    val permission = state.location is PlaceLocationState.PermissionRequired || state.location is PlaceLocationState.PermissionPermanentlyDenied
    fun requestPermission() { if (state.location is PlaceLocationState.PermissionPermanentlyDenied) onOpenSettings() else onRequestPermission() }
    fun search(selected: PlaceKind = kind, parking: Boolean = state.discovery.preferParking, query: String? = null) {
        if (permission) { requestPermission(); return }
        notice = null
        onAction(PlacesAction.Search(selected, parking, query))
    }
    BackHandler(onBack = onBack)
    PlaceSearchLabScreen(
        state = ui, live = true,
        onEdit = { draft = it }, onAi = { ai = !ai; notice = null },
        onSubmit = {
            when {
                ai -> notice = "AI 조건 검색은 아직 연결되지 않았어요. 일반 검색을 사용해 주세요."
                !isValidPlaceNameQuery(draft) -> notice = "장소명은 120자까지 입력할 수 있어요."
                else -> { keyboard?.hide(); search(query = draft.trim()) }
            }
        },
        onCategory = { selected ->
            if (selected == null) notice = "전체 업종 검색은 다음 연결 단계에서 제공됩니다. 업종을 선택해 주세요."
            else search(selected = selected)
        },
        onParking = { value ->
            if (kind.supportsParkingPreference()) search(parking = value)
            else notice = "이 업종은 주차 정보를 제공하지 않아요."
        },
        onToggle = { key ->
            expanded = key.takeUnless { it == expanded }
            onAction(PlacesAction.Select(key))
        },
        onRetry = { if (permission) requestPermission() else onAction(PlacesAction.RetrySearch) },
        cardActions = { hit ->
            hit.place.facts.phone?.let { phone -> TextButton(onClick = { onCall(phone) }) { Text("전화로 확인") } }
            PlaceJourneyAction(
                state.journey.takeIf { it.destinationKey == hit.place.key }.toActionPresentation(),
                onJourney = { onAction(PlacesAction.LoadJourney(hit.place)) },
                onRetry = { onAction(PlacesAction.RetryJourney) }, onOpenHandoff = onOpenHandoff,
            )
        },
        map = {
            Box(Modifier.fillMaxSize()) {
            val keys = canonicalPlaceKeysByMarker(state.discovery)
            if (showMap) MapHost(
                scene = MapScene(currentPosition = state.location.currentPosition, places = canonicalPlaceMarkers(state.discovery)),
                searchOrigin = state.discovery.origin, followDevice = follow,
                onCameraIdle = { camera = it }, onCameraGesture = { follow = false },
                onSelectPlace = { id -> keys[id]?.let { onAction(PlacesAction.Select(it)) } },
                modifier = Modifier.fillMaxSize(),
            )
            Row(Modifier.align(Alignment.TopCenter)) {
                TextButton(onClick = onBack) { Text("← 홈") }
                TextButton(onClick = {
                    if (permission) requestPermission() else { follow = true; onAction(PlacesAction.Locate(kind, state.discovery.preferParking)) }
                }) { Text(if (permission) "위치 권한" else "내 위치") }
                camera?.takeIf { it != state.discovery.origin }?.let { point ->
                    TextButton(onClick = { follow = false; onAction(PlacesAction.SearchAt(point, kind, state.discovery.preferParking)) }) { Text("이 지역 검색") }
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
