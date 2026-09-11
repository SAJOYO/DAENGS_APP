package com.daengs.app.ui.places.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.places.placeMarkerId
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.map.layers.places.PlaceMarkerState
import com.daengs.app.place.*
import com.daengs.app.ui.places.*
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.launch

/** Explicit debug entry. No bookmark API, user account, or persistence is simulated in production. */
class PlaceBookmarkLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DaengsTheme {
            val model: PlaceBookmarkLabModel = viewModel()
            var nativeMap by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().background(DaengsColors.Surface).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("시설 찜 UI 검토 · 예시 데이터 · 저장 안 됨", Modifier.weight(1f), fontSize = 11.sp)
                    TextButton(onClick = { nativeMap = !nativeMap }) { Text(if (nativeMap) "지도 끄기" else "지도 켜기", fontSize = 11.sp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { model.reset() }) { Text("기본") }
                    TextButton(onClick = { model.saved = emptySet(); model.tab(PlaceBrowseTab.BOOKMARKS) }) { Text("빈 찜") }
                    TextButton(onClick = { model.tab(PlaceBrowseTab.BOOKMARKS); model.phase = PlaceBookmarkPhase.LOADING }) { Text("로딩") }
                    TextButton(onClick = { model.tab(PlaceBrowseTab.BOOKMARKS); model.phase = PlaceBookmarkPhase.FAILED }) { Text("실패") }
                }
                Box(Modifier.weight(1f)) { PlaceBookmarkLabScreen(model, nativeMap, ::finish) }
            }
        } }
    }
}

/** The only local filtering in this feature is this debug fixture; it is not an API adapter. */
class PlaceBookmarkLabModel(val catalog: List<PlaceSearchHit> = bookmarkLabHits()) : ViewModel() {
    var session by mutableStateOf(initialSession()); private set
    var saved by mutableStateOf(catalog.drop(1).map { it.place.key }.toSet())
    var phase by mutableStateOf(PlaceBookmarkPhase.READY)
    private fun initialSession() = PlaceBrowseSession(search = PlaceBrowseSnapshot(filters = PlaceBrowseFilters(
        origin = GeoPoint(37.54, 127.05), dogIds = setOf("demo-bori"))))
    fun reset() { session = initialSession(); saved = catalog.drop(1).map { it.place.key }.toSet(); phase = PlaceBookmarkPhase.READY }
    fun tab(tab: PlaceBrowseTab) { session = session.select(tab) }
    fun all() { session = session.showAllBookmarks() }
    fun edit(text: String) { session = session.updateCurrent { it.copy(draft = text) } }
    fun update(update: (PlaceBrowseSnapshot) -> PlaceBrowseSnapshot) { session = session.updateCurrent(update) }
    fun filter(update: (PlaceBrowseFilters) -> PlaceBrowseFilters) {
        session = session.updateCurrent { it.copy(filters = update(it.filters), selected = null, detail = null) }
    }
    fun open(key: PlaceKey) { update { snapshot -> snapshot.copy(selected = key, detail = key.takeUnless { key == snapshot.detail }) } }
    fun toggle(key: PlaceKey) { saved = if (key in saved) saved - key else saved + key }
    fun visible(): List<PlaceSearchHit> {
        val f = session.current.filters
        if (session.tab == PlaceBrowseTab.BOOKMARKS && phase != PlaceBookmarkPhase.READY) return emptyList()
        return catalog.filter { hit ->
            (session.tab != PlaceBrowseTab.BOOKMARKS || hit.place.key in saved) &&
                (f.kinds.isEmpty() || hit.place.match.kind in f.kinds) &&
                (f.name.isBlank() || hit.place.name.contains(f.name, ignoreCase = true)) &&
                (f.radiusMeters == null || hit.place.distanceMeters <= f.radiusMeters)
        }.let { if (f.parkingFirst) it.sortedByDescending { hit -> hit.place.facts.parking == true } else it }
    }
}

@Composable
fun PlaceBookmarkLabScreen(model: PlaceBookmarkLabModel, nativeMap: Boolean = false, onBack: () -> Unit = {}) {
    val session = model.session
    val snapshot = session.current
    val filters = snapshot.filters
    val hits = model.visible()
    val searching = session.tab == PlaceBrowseTab.SEARCH
    val searchList = rememberLazyListState()
    val savedList = rememberLazyListState()
    val list = if (searching) searchList else savedList
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var centerOn by remember(session.tab) { mutableStateOf<GeoPoint?>(null) }
    var cameraRequest by remember { mutableIntStateOf(0) }
    var collapseRequest by remember { mutableIntStateOf(0) }
    val category = if (filters.kinds.isEmpty()) PlaceCategorySelection.All else PlaceCategorySelection.fromKinds(filters.kinds.toList())
    fun filter(change: (PlaceBrowseFilters) -> PlaceBrowseFilters) {
        centerOn = null
        model.filter(change)
        scope.launch { list.scrollToItem(0) }
    }
    fun toggle(key: PlaceKey) {
        val removed = key in model.saved
        model.toggle(key)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(if (removed) "찜을 해제했어요." else "찜한 시설에 담았어요.",
                actionLabel = if (removed) "실행 취소" else null, duration = SnackbarDuration.Short)
            if (removed && result == SnackbarResult.ActionPerformed) model.saved = model.saved + key
        }
    }
    BackHandler(enabled = !searching && snapshot.detail == null) { model.tab(PlaceBrowseTab.SEARCH) }
    Box(Modifier.fillMaxSize()) {
        PlaceSearchLabScreen(
            state = PlaceSearchLabState(draft = snapshot.draft,
                applied = LabCriteria(query = filters.name, kind = filters.kinds.singleOrNull(),
                    radiusMeters = filters.radiusMeters ?: 0, parkingFirst = filters.parkingFirst),
                hits = hits, phase = if (hits.isEmpty()) LabPhase.EMPTY else LabPhase.RESULTS,
                selected = snapshot.selected, expanded = snapshot.detail,
                selectedDogIds = filters.dogIds, profilesReady = true,
                profileNames = mapOf("demo-bori" to "보리", "demo-choco" to "초코")),
            live = true, showAiToggle = false, onBack = { if (!searching) model.tab(PlaceBrowseTab.SEARCH) else onBack() },
            onEdit = model::edit, onSubmit = { filter { it.copy(name = model.session.current.draft.trim()) } },
            onParking = { value -> filter { it.copy(parkingFirst = value) } },
            onRadius = { radius -> filter { it.copy(radiusMeters = radius.takeIf { it > 0 }) } },
            onDog = { id -> filter { it.copy(dogIds = if (id in it.dogIds) it.dogIds - id else it.dogIds + id) } },
            onToggle = model::open, resultLabel = category.label,
            categoryContent = {
                PlacePurposeMenu(category) { selection -> filter { it.copy(kinds = if (selection == PlaceCategorySelection.All) emptySet() else selection.kinds.toSet()) } }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (searching) "검색 중" else "찜에서 찾는 중", fontSize = 11.sp, color = DaengsColors.TextSecondary)
                    Text("  ${category.label}", Modifier.weight(1f), fontSize = 12.sp)
                    if (filters.name.isNotBlank()) Text(filters.name, fontSize = 12.sp)
                }
            },
            bookmarks = PlaceBookmarkPanelState(session.tab, model.saved, model.saved.size, model.phase,
                filters.narrowsBookmarks, filters.radiusMeters == null),
            onBrowseTab = model::tab, onShowAllBookmarks = { centerOn = null; model.all(); scope.launch { savedList.scrollToItem(0) } },
            onToggleBookmark = ::toggle, onRetryBookmarks = { model.phase = PlaceBookmarkPhase.READY },
            resultsListState = list,
            collapseRequest = collapseRequest,
            cardActions = { hit -> TextButton(onClick = {
                model.update { it.copy(selected = hit.place.key, detail = null) }
                centerOn = hit.place.point
                cameraRequest++
                collapseRequest++
            }) { Text("지도에서 보기") } },
            map = {
                // Remount only on tab switches so the saved camera never overwrites search's camera.
                key(session.tab) {
                    if (nativeMap) MapHost(
                        scene = MapScene(places = hits.map { hit -> PlaceMarkerState(
                            id = placeMarkerId(hit.place.key), point = hit.place.point, label = hit.place.name,
                            selected = hit.place.key == snapshot.selected, iconGroup = hit.place.iconGroup) }),
                        searchOrigin = filters.origin, followDevice = false, initialCamera = snapshot.camera,
                        centerOn = centerOn, cameraRequestKey = cameraRequest, centerYFraction = .3f,
                        fitBounds = hits.map { it.place.point }.takeIf { snapshot.camera == null },
                        onCameraSnapshot = { camera -> if (model.session.tab == session.tab) model.update { it.copy(camera = camera) } },
                        onCameraIdle = {}, onCameraGesture = { centerOn = null },
                        onSelectPlace = { id -> hits.find { placeMarkerId(it.place.key) == id }?.let { model.open(it.place.key) } },
                        modifier = Modifier.fillMaxSize(),
                    ) else Column(Modifier.fillMaxSize().background(DaengsColors.SurfaceMuted).padding(12.dp)
                        .testTag("bookmark-map-preview")) {
                        Text("${if (searching) "검색 결과" else "찜한 시설"} · 지도에 ${hits.size}곳", fontSize = 12.sp)
                        Text("예시 위치 · 실제 지도는 상단에서 켤 수 있어요.", fontSize = 11.sp, color = DaengsColors.TextSecondary)
                        hits.take(3).forEach { hit -> TextButton(onClick = { model.open(hit.place.key) }) { Text(hit.place.name, fontSize = 12.sp) } }
                    }
                }
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

fun bookmarkLabHits(): List<PlaceSearchHit> {
    val sample = PreviewPlaceHit()
    return listOf(Triple("느티나무 카페", PlaceKind.CAFE, 320), Triple("오후의 정원", PlaceKind.CAFE, 740),
        Triple("보리 동물병원", PlaceKind.HOSPITAL, 2100), Triple("바람숲 스테이", PlaceKind.PENSION, 180000)).mapIndexed { index, (name, kind, distance) ->
        val key = PlaceKey("bookmark-preview", "place-$index")
        sample.copy(place = sample.place.copy(key = key, name = name, distanceMeters = distance,
            point = if (index == 3) GeoPoint(38.07, 128.62) else GeoPoint(37.54 + index * .004, 127.05),
            match = PlaceMatch(key, kind), facts = sample.place.facts.copy(address = if (index == 3) "강원 양양군 · 검토용 시설" else "서울 성동구 · 검토용 시설")))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun BookmarkSearchPreview() { DaengsTheme { PlaceBookmarkLabScreen(remember { PlaceBookmarkLabModel() }) } }

@Preview(showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun BookmarkFilteredPreview() { DaengsTheme { PlaceBookmarkLabScreen(remember {
    PlaceBookmarkLabModel().apply { tab(PlaceBrowseTab.BOOKMARKS) }
}) } }
