package com.daengs.app.ui.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.map.features.places.*
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.map.shell.MapScene
import com.daengs.app.place.*
import com.daengs.app.ui.places.lab.*
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.serialization.json.JsonObject

/** AI 확정 결과는 서버가 실행한 응답 그대로 그린다. 일반 검색을 다시 호출하면 조건을 잃는다. */
internal fun PlacesUiState.visibleDiscovery(): PlaceDiscoveryState {
    if (conversationAvailable) return discovery
    if (!facility.enabled) return discovery
    val lens = facility.confirmedLens ?: return discovery
    val response = lens.search
    return discovery.copy(
        requestedKinds = lens.kinds,
        origin = facility.confirmedResponse?.request?.origin ?: discovery.origin,
        radiusMeters = facility.confirmedResponse?.request?.radiusMeters ?: discovery.radiusMeters,
        nameQuery = "",
        preferParking = lens.parking,
        selectedPlaceKey = facility.selectedPlaceKey,
        search = when {
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
    /** 고정 검색 도우미에 쓸 견종 그림. 지도상의 내 위치는 기본 위치 점을 쓴다. */
    avatarBreed: DogBreed? = null,
    /** 올린 프로필 사진. 있으면 [avatarBreed] 보다 이쪽이 앞선다. */
    avatarPhoto: android.graphics.Bitmap? = null,
    bookmarkController: PlaceBookmarkController? = null,
) {
    val bookmarks = bookmarkController ?: rememberPlaceBookmarks()
    val saved = bookmarks?.let { key(it) { it.state.collectAsState().value } }
    val searchList = rememberLazyListState()
    val savedList = rememberLazyListState()
    var searchCamera by remember { mutableStateOf<MapCameraSnapshot?>(null) }
    var draft by rememberSaveable { mutableStateOf(state.discovery.nameQuery) }
    val ai = state.facility.enabled
    val display = state.visibleDiscovery()
    var expanded by remember { mutableStateOf<PlaceKey?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf(PlaceMapCamera()) }
    var follow by remember { mutableStateOf(true) }
    var filtersOpen by remember { mutableStateOf(false) }
    var dogOpen by rememberSaveable { mutableStateOf(false) }
    var dogAsked by rememberSaveable { mutableStateOf(false) }
    var dogQuery by rememberSaveable { mutableStateOf("") }
    val appliedFilters = state.conversation.result?.appliedPlaceFilters()
    LaunchedEffect(saved?.searchTransfer) {
        if ((saved?.searchTransfer ?: 0) > 0) {
            draft = saved!!.session.search.draft
            searchCamera = null
            searchList.scrollToItem(0)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val ui = state.toConnectedSearchState(draft, false, expanded, notice)
    LaunchedEffect(state.discovery.response) {
        // A loading frame has no response; only completed results can remove an expanded card.
        if (state.discovery.response != null) expanded = ui.expanded
    }
    val category = PlaceCategorySelection.fromKinds(display.requestedKinds)
    fun searchSnapshot() = PlaceBrowseSnapshot(PlaceBrowseFilters(
        kinds = display.requestedKinds.toSet(), name = display.nameQuery,
        origin = display.origin, radiusMeters = display.radiusMeters.takeIf { display.origin != null },
        dogIds = state.profiles.selectedIds, parkingFirst = display.preferParking,
        requiredConditions = state.conversation.result?.filters?.get("hard") as? JsonObject,
    ), draft = draft, selected = display.selectedPlaceKey, detail = expanded, camera = searchCamera)
    if (bookmarks != null && saved != null) {
        PlaceBookmarkFeedback(bookmarks, saved)
        if (saved.session.tab == PlaceBrowseTab.BOOKMARKS) {
            PlaceBookmarksScreen(bookmarks, saved, state.profiles, savedList, showMap, onCall,
                actions = { hit -> PlaceJourneyAction(
                    state.journey.takeIf { it.destinationKey == hit.place.key }.toActionPresentation(),
                    onJourney = { onAction(PlacesAction.LoadJourney(hit.place)) },
                    onRetry = { onAction(PlacesAction.LoadJourney(hit.place)) }, onOpenHandoff = onOpenHandoff,
                ) }, onRefreshProfiles = onRefreshProfiles, avatarBreed = avatarBreed, avatarPhoto = avatarPhoto,
                onSearch = { onAction(PlacesAction.ApplySearchPlan(it)) })
            return
        }
    }
    val permission = state.location is PlaceLocationState.PermissionRequired || state.location is PlaceLocationState.PermissionPermanentlyDenied
    fun requestPermission() { if (state.location is PlaceLocationState.PermissionPermanentlyDenied) onOpenSettings() else onRequestPermission() }
    fun search(selected: PlaceCategorySelection = category, parking: Boolean = display.preferParking, query: String? = null) {
        if (permission) { requestPermission(); return }
        notice = null
        dogOpen = false
        dogAsked = false
        if (ai) onAction(PlacesAction.SetAiMode(false))
        onAction(PlacesAction.Search(selected, parking, query))
    }
    BackHandler(onBack = onBack)
    fun retryConversationSearch() {
        val filterRetry = state.conversation.filterRetry
        onAction(if (filterRetry != null) PlacesAction.ApplyFilters(filterRetry)
            else if (ai) PlacesAction.Discover(dogQuery) else PlacesAction.RetrySearch)
    }
    if (filtersOpen && state.conversationAvailable) ConversationFiltersDialog(state.conversation,
        onApply = { onAction(PlacesAction.ApplyFilters(it)) }, onDismiss = { filtersOpen = false })
    PlaceSearchLabScreen(
        state = ui, live = true, onBack = onBack,
        bookmarks = saved?.panel(), resultsListState = searchList,
        onBrowseTab = { tab -> if (tab == PlaceBrowseTab.BOOKMARKS) {
            bookmarks?.enter(searchSnapshot(), state.profiles.snapshots())
        } },
        onToggleBookmark = { bookmarks?.toggle(it) }, onRetryBookmarks = { bookmarks?.refresh() },
        onEdit = { draft = it }, showAiToggle = false,
        onSubmit = {
            when {
                !isValidPlaceNameQuery(draft) -> notice = "장소명은 120자까지 입력할 수 있어요."
                else -> { keyboard?.hide(); search(query = draft.trim()) }
            }
        },
        categoryContent = {
            PlacePurposeMenu(category, onLimit = { notice = "카테고리는 6개까지 함께 검색할 수 있어요." }) { search(selected = it) }
            PlaceSearchQueue(category,
                filterSummary = if (state.conversationAvailable) appliedFilters?.summary.orEmpty()
                    else state.facility.confirmedLens?.let { "검색 방향 · ${it.label}" }.orEmpty(),
                nameQuery = display.nameQuery,
                onOpenFilters = { if (state.conversationAvailable) filtersOpen = true else dogOpen = true }) { search(selected = it) }
        },
        resultLabel = if (ai && !state.conversationAvailable) state.facility.confirmedLens?.label ?: "AI 조건 검색" else category.label,
        aiConnected = true,
        onSearchFilters = if (state.conversationAvailable) ({ filtersOpen = true }) else null,
        searchFilterCount = appliedFilters?.count ?: 0,
        answerContent = if (state.conversationAvailable && !ai &&
            (state.conversation.error != null || state.conversation.notice != null || state.conversation.result?.matches == false)) ({
            ConversationPanel(state.conversation.copy(busy = false, answerBusy = false), showAnswer = false,
                onRetryAnswer = { onAction(PlacesAction.RetryAi) },
                onRetrySearch = ::retryConversationSearch,
                onApplyCurrentFilters = { state.conversation.result?.let {
                    onAction(PlacesAction.ApplyFilters(ConversationFilterEdit(it.sessionId, it.revision)))
                } },
                onOpenFilters = { filtersOpen = true })
        }) else null,
        emptyMessage = if (category == PlaceCategorySelection.None) "카테고리를 담으면 주변 장소를 찾아드려요." else "검색 결과가 없어요.",
        onParking = { value ->
            if (category.kinds.any(PlaceKind::supportsParkingPreference)) search(parking = value)
            else notice = "이 업종은 주차 정보를 제공하지 않아요."
        },
        onRadius = { meters -> dogOpen = false; dogAsked = false; onAction(PlacesAction.SetRadius(meters)) },
        onDog = { id -> dogOpen = false; dogAsked = false; onAction(PlacesAction.ToggleDog(id)) },
        onRefreshProfiles = onRefreshProfiles,
        onToggle = { key ->
            expanded = key.takeUnless { it == expanded }
            onAction(PlacesAction.Select(key))
        },
        onRetry = {
            if (state.conversationAvailable) retryConversationSearch()
            else if (ai) { if (state.facility.canRetry) onAction(PlacesAction.RetryAi) }
            else if (permission) requestPermission() else onAction(PlacesAction.RetrySearch)
        },
        showRetry = !ai || (state.conversationAvailable && state.conversation.error != null) || state.facility.canRetry,
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
                    initialCamera = searchCamera, onCameraSnapshot = { searchCamera = it },
                    // 내 위치는 점, 검색 도우미는 하단 고정 버튼으로 역할을 분리한다.
                    onCameraIdle = { camera = camera.idle(it) }, onCameraGesture = { follow = false; camera = camera.gesture() },
                    onSelectPlace = { id -> keys[id]?.let { expanded = it; onAction(PlacesAction.Select(it)) } },
                    modifier = Modifier.fillMaxSize(),
                )
                val candidate = camera.searchPoint(state.discovery.origin)
                PlaceMapControls(candidate != null,
                    onMapSearch = { candidate?.let { point ->
                        follow = false; camera = camera.submitted(); dogOpen = false; dogAsked = false
                        if (ai) onAction(PlacesAction.SetAiMode(false))
                        onAction(PlacesAction.SearchAt(point, category, state.discovery.preferParking))
                    } },
                    onDeviceSearch = {
                        if (permission) requestPermission() else {
                            follow = true; camera = camera.submitted(); dogOpen = false; dogAsked = false
                            if (ai) onAction(PlacesAction.SetAiMode(false))
                            onAction(PlacesAction.Locate(category, state.discovery.preferParking))
                        }
                    }) {
                    PlaceDogAssistant(
                        busy = if (state.conversationAvailable) state.conversation.busy || state.conversation.answerBusy else state.facility.loading,
                        replyAvailable = dogAsked || (ai && (state.conversation.result?.answer != null || state.facility.response != null)),
                        open = dogOpen, onOpen = { dogOpen = it },
                        onSubmit = { query ->
                            dogAsked = true; dogQuery = query
                            if (!ai) onAction(PlacesAction.SetAiMode(true))
                            onAction(PlacesAction.Discover(query, bookmarks?.captureTurn(searchSnapshot(), state.profiles.snapshots())))
                        },
                        onCancel = { onAction(PlacesAction.CancelAi) },
                        onUndo = if (state.conversationAvailable && state.conversation.canUndo) ({ onAction(PlacesAction.UndoAi) }) else null,
                        avatarBreed = avatarBreed, avatarPhoto = avatarPhoto,
                        searchContext = if (display.origin == null) "검색 지역을 먼저 정해 줘"
                            else "현재 검색 지역 · 반경 " + if (display.radiusMeters % 1000 == 0) "${display.radiusMeters / 1000}km" else "${display.radiusMeters}m",
                    ) {
                        if (state.conversationAvailable) {
                            ConversationPanel(state.conversation, state.facility.error,
                                onRetryAnswer = { onAction(PlacesAction.RetryAi) }, onRetrySearch = ::retryConversationSearch,
                                onApplyCurrentFilters = { state.conversation.result?.let {
                                    onAction(PlacesAction.ApplyFilters(ConversationFilterEdit(it.sessionId, it.revision)))
                                } })
                        } else FacilitySearchPanel(state.facility,
                            { onAction(PlacesAction.ChooseAi(it)) }, { onAction(PlacesAction.RetryAi) })
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
