package com.daengs.app.ui.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.places.placeMarkerId
import com.daengs.app.map.layers.places.PlaceMarkerState
import com.daengs.app.map.shell.*
import com.daengs.app.place.*
import com.daengs.app.ui.places.lab.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

internal fun PlaceBookmarkState.panel() = PlaceBookmarkPanelState(
    tab = session.tab, savedKeys = page?.items?.map { it.key }?.toSet().orEmpty(),
    totalSaved = page?.items?.size, phase = phase, hasFilters = session.current.filters.narrowsBookmarks,
    allRegions = session.current.filters.radiusMeters == null, busy = busy,
    errorText = errorText, distanceAvailable = distanceAvailable,
)

@Composable
internal fun PlaceBookmarksScreen(controller: PlaceBookmarkController, state: PlaceBookmarkState,
    profiles: PlaceProfiles, list: LazyListState, showMap: Boolean, onCall: (String) -> Unit,
    actions: @Composable (PlaceSearchHit) -> Unit, onRefreshProfiles: () -> Unit) {
    val snapshot = state.session.current
    val filters = snapshot.filters
    val scope = rememberCoroutineScope()
    var filterDialog by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf<GeoPoint?>(null) }
    var cameraRequest by remember { mutableIntStateOf(0) }
    var collapse by remember { mutableIntStateOf(0) }
    val selectedProfiles = profiles.copy(selectedIds = filters.dogIds)
    val dogs = selectedProfiles.snapshots()
    LaunchedEffect(dogs, profiles.ready) {
        if (profiles.ready) controller.updateDogs(dogs)
    }
    val category = if (filters.kinds.isEmpty()) PlaceCategorySelection.All else PlaceCategorySelection.fromKinds(filters.kinds.toList())
    val hard = filters.requiredConditions?.appliedHardFilters() ?: AppliedPlaceFilters()
    fun change(update: (PlaceBrowseFilters) -> PlaceBrowseFilters) {
        center = null
        controller.filters(update)
        scope.launch { list.scrollToItem(0) }
    }
    fun open(key: PlaceKey) { controller.updateSnapshot { it.copy(selected = key, detail = key.takeUnless { key == snapshot.detail }) } }
    BackHandler { controller.returnToSearch() }
    if (filterDialog) AlertDialog(onDismissRequest = { filterDialog = false }, title = { Text("찜 검색 필터") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (hard.count == 0) Text("적용된 필수 조건이 없어요.")
            hard.all.forEach { condition -> Row(Modifier.fillMaxWidth()) {
                Text(condition.label, Modifier.weight(1f))
                TextButton(onClick = { change { f -> f.copy(requiredConditions = JsonObject(f.requiredConditions!!.toMutableMap().apply {
                    put("all", JsonArray(getValue("all").jsonArray.filterNot { it.jsonObject["id"]?.jsonPrimitive?.content == condition.id }))
                })) } }) { Text("해제") }
            } }
            if (hard.any.isNotEmpty()) {
                Text(hard.any.joinToString("\n또는\n") { branch -> branch.conditions.joinToString(" 그리고 ") { it.label } })
                TextButton(onClick = { change { f -> f.copy(requiredConditions = JsonObject(f.requiredConditions!!.toMutableMap().apply {
                    put("any", JsonArray(emptyList()))
                })) } }) { Text("조합 조건 해제") }
            }
            TextButton(onClick = { controller.all(); filterDialog = false }) { Text("전체 찜 보기") }
        }
    }, confirmButton = { TextButton(onClick = { filterDialog = false }) { Text("닫기") } })
    val profilesPending = filters.dogIds.isNotEmpty() && !profiles.ready
    val panel = state.panel().let { if (profilesPending) it.copy(phase = PlaceBookmarkPhase.LOADING) else it }
    val hits = if (profilesPending) emptyList() else state.hits
    PlaceSearchLabScreen(
        state = PlaceSearchLabState(draft = snapshot.draft,
            applied = LabCriteria(query = filters.name, kind = filters.kinds.singleOrNull(),
                radiusMeters = filters.radiusMeters ?: 0, parkingFirst = filters.parkingFirst),
            hits = hits, phase = if (hits.isEmpty()) LabPhase.EMPTY else LabPhase.RESULTS,
            selected = snapshot.selected, expanded = snapshot.detail,
            selectedDogIds = filters.dogIds, profilesReady = profiles.ready, profileMessage = profiles.message,
            profileNames = profiles.pets.associate { it.id to it.name }),
        live = true, showAiToggle = false, onBack = controller::returnToSearch,
        onEdit = { text -> controller.updateSnapshot { it.copy(draft = text) } },
        onSubmit = { if (isValidPlaceNameQuery(snapshot.draft)) change { it.copy(name = snapshot.draft.trim()) }
            else controller.notice("장소명은 120자 이내로 입력해 주세요.") },
        onParking = { value -> change { it.copy(parkingFirst = value) } },
        onRadius = { radius ->
            val origin = filters.origin ?: state.session.search.filters.origin
            if (radius > 0 && origin == null) controller.notice("검색 탭에서 기준 위치를 선택한 뒤 반경을 설정해 주세요.")
            else change { it.copy(radiusMeters = radius.takeIf { it > 0 }, origin = origin) }
        },
        onDog = { id -> if (profiles.ready) change { it.copy(dogIds = if (id in it.dogIds) it.dogIds - id else it.dogIds + id) } },
        onRefreshProfiles = onRefreshProfiles, onToggle = ::open,
        onSearchFilters = { filterDialog = true }, searchFilterCount = hard.count,
        categoryContent = {
            PlacePurposeMenu(category) { selected -> change { it.copy(kinds = if (selected == PlaceCategorySelection.All) emptySet() else selected.kinds.toSet()) } }
            PlaceSearchQueue(category, "찜에서 찾는 중" + hard.summary.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty(),
                filters.name, { filterDialog = true }) { selected -> change { it.copy(kinds = if (selected == PlaceCategorySelection.All) emptySet() else selected.kinds.toSet()) } }
        },
        resultLabel = category.label, bookmarks = panel,
        onBrowseTab = { if (it == PlaceBrowseTab.SEARCH) controller.returnToSearch() },
        onShowAllBookmarks = { center = null; controller.all(); scope.launch { list.scrollToItem(0) } },
        onToggleBookmark = controller::toggle, onRetryBookmarks = controller::refresh,
        resultsListState = list, collapseRequest = collapse,
        bookmarkNotices = {
            if (state.phase == PlaceBookmarkPhase.READY && state.missing.isNotEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Text("현재 정보를 확인할 수 없는 찜 ${state.missing.size}곳", fontSize = 12.sp)
                    state.page?.items?.filter { it.key in state.missing }?.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(item.name, Modifier.weight(1f), fontSize = 13.sp)
                            TextButton(onClick = { controller.toggle(item.key) }, enabled = !state.busy) { Text("찜 해제") }
                        }
                    }
                }
            }
            if (profilesPending) Text(profiles.message ?: "반려견 정보를 불러오는 중…", Modifier.padding(16.dp))
        },
        cardActions = { hit ->
            TextButton(onClick = {
                controller.updateSnapshot { it.copy(selected = hit.place.key, detail = null) }
                center = hit.place.point; cameraRequest++; collapse++
            }) { Text("지도에서 보기") }
            hit.place.facts.phone?.let { phone -> TextButton(onClick = { onCall(phone) }) { Text("전화로 확인") } }
            actions(hit)
        },
        map = {
            if (showMap) MapHost(
                scene = MapScene(places = hits.map { hit -> PlaceMarkerState(placeMarkerId(hit.place.key), hit.place.point,
                    hit.place.name, selected = hit.place.key == snapshot.selected, iconGroup = hit.place.iconGroup) }),
                searchOrigin = filters.origin, followDevice = false,
                initialCamera = snapshot.camera, centerOn = center, cameraRequestKey = cameraRequest, centerYFraction = .3f,
                fitBounds = hits.map { it.place.point }.takeIf { snapshot.camera == null && it.isNotEmpty() },
                onCameraSnapshot = { camera -> if (controller.state.value.session.tab == PlaceBrowseTab.BOOKMARKS) controller.updateSnapshot { it.copy(camera = camera) } },
                onCameraGesture = { center = null }, onCameraIdle = {},
                onSelectPlace = { id -> hits.find { placeMarkerId(it.place.key) == id }?.let { open(it.place.key) } },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SavedFacilitiesPreview() {
    val scope = rememberCoroutineScope()
    val controller = remember { PlaceBookmarkController(scope,
        com.daengs.app.place.bookmarks.PlaceBookmarkRepository(
            com.daengs.app.place.bookmarks.PlaceBookmarkApi(), { null }, { com.daengs.app.auth.AccountScope(null, 0) }),
        com.daengs.app.auth.AccountScope(null, 0)) }
    com.daengs.app.ui.theme.DaengsTheme {
        PlaceBookmarksScreen(controller,
            PlaceBookmarkState(session = PlaceBrowseSession().showAllBookmarks(), phase = PlaceBookmarkPhase.READY,
                page = com.daengs.app.place.bookmarks.SavedPlacePage(emptyList(), 200)),
            PlaceProfiles(ready = true, message = null), androidx.compose.foundation.lazy.rememberLazyListState(),
            showMap = false, onCall = {}, actions = {}, onRefreshProfiles = {})
    }
}
