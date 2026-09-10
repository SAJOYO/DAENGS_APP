package com.daengs.app.ui.walk.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.HistoryFilterSaver
import com.daengs.app.ui.walk.WalkHistoryPageContent
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.walk.WalkHistoryFilter
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.records.WalkRecord
import com.daengs.app.walk.records.WalkRecordsQuery
import com.daengs.app.walk.records.WalkRecordsSelection
import com.daengs.app.walk.records.WalkRecordsSource
import com.daengs.app.walk.records.selectWalkRecords
import com.daengs.app.walk.records.PreparedWalkRecordsTraces
import com.daengs.app.walk.records.prepareWalkRecordsTraces
import com.daengs.app.walk.records.walkRecordFocusBounds
import com.daengs.app.walk.records.selectWalkRecordBehaviors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** One query and complete selection, viewed either as paginated records or combined traces. */
@Composable
fun WalkRecordsScreen(
    source: WalkRecordsSource,
    pets: List<Pet>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    sampleLabel: String? = null,
    today: LocalDate = LocalDate.now(),
) {
    var dogId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable(stateSaver = HistoryFilterSaver) { mutableStateOf(WalkHistoryFilter()) }
    val query = remember(dogId, filter) { WalkRecordsQuery(dogId, filter) }
    var view by rememberSaveable { mutableStateOf(RecordsView.WALKS) }
    var behavior by rememberSaveable { mutableStateOf<WalkMomentType?>(null) }
    var behaviorView by rememberSaveable(query) { mutableStateOf(BehaviorRecordsView.RECORD_LOCATIONS) }
    // Keep inspection state above the loading/tab branches, with a stable restoration location.
    // The native map must not be wrapped in SaveableStateHolder's ReusableContent subtree.
    val behaviorState = rememberWalkRecordsBehaviorState(query, behavior)
    var conditionsOpen by rememberSaveable { mutableStateOf(false) }
    var pageIndex by rememberSaveable(query) { mutableIntStateOf(0) }
    var camera by rememberSaveable(query, stateSaver = CameraSnapshotSaver) { mutableStateOf<MapCameraSnapshot?>(null) }
    var selectedId by rememberSaveable(query) { mutableStateOf<String?>(null) }
    var hiddenIds by rememberSaveable(query, stateSaver = HiddenWalkIdsSaver) { mutableStateOf(emptySet<String>()) }
    var overlapOnly by rememberSaveable(query) { mutableStateOf(false) }
    var minimumWalks by rememberSaveable(query) { mutableIntStateOf(2) }
    var overlapPoint by rememberSaveable(query, stateSaver = OverlapPointSaver) { mutableStateOf<GeoPoint?>(null) }
    var overlapSelectionRequest by rememberSaveable(query) { mutableIntStateOf(0) }
    var overlapMiss by remember(query, overlapOnly, minimumWalks) { mutableStateOf(false) }
    val overviewScroll = key(query, overlapPoint, overlapSelectionRequest) { rememberLazyListState() }
    var focusBounds by remember(query) { mutableStateOf<List<GeoPoint>?>(null) }
    var cameraRequest by remember(query) { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    var retry by remember { mutableIntStateOf(0) }
    // Reset synchronously with the query so an earlier query's records never flash underneath it.
    var selection by remember(source, query, retry) { mutableStateOf<WalkRecordsSelection?>(null) }
    var error by remember(source, query, retry) { mutableStateOf<String?>(null) }
    val savedLists = key(query) { rememberSaveableStateHolder() }
    LaunchedEffect(source, query, retry) {
        try {
            if (query.filter.keyword.isNotBlank()) delay(250)
            val loaded = withContext(Dispatchers.Default) { source.select(query) }
            currentCoroutineContext().ensureActive()
            require(loaded.query == query) { "조회 조건과 결과 조건이 달라요." }
            selection = loaded
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = "산책 기록을 불러오지 못했어요."
        }
    }
    LaunchedEffect(pets) {
        if (dogId != null && pets.none { it.id == dogId }) dogId = null
    }
    LaunchedEffect(selection) {
        selection?.let { current ->
            if (selectedId !in current.sessionIds) selectedId = null
            hiddenIds = hiddenIds.intersect(current.sessionIds.toSet())
        }
    }

    // Opening the other view never selects records again or changes the current list page.
    var mapRequested by remember(query) { mutableStateOf(false) }
    LaunchedEffect(view) { if (view == RecordsView.OVERVIEW) mapRequested = true }
    val shouldPrepareMap = behavior == null && (mapRequested || view == RecordsView.OVERVIEW)
    var prepared by remember(selection) { mutableStateOf<PreparedWalkRecordsTraces?>(null) }
    var mapError by remember(selection) { mutableStateOf<String?>(null) }
    var mapRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(selection, shouldPrepareMap, mapRetry) {
        val selected = selection ?: return@LaunchedEffect
        if (!shouldPrepareMap || selected.records.isEmpty()) return@LaunchedEffect
        prepared = null
        mapError = null
        try {
            prepared = prepareWalkRecordsTraces(selected)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mapError = "선택한 산책의 흔적을 표시하지 못했어요. 기간이나 조건을 좁혀 다시 확인해 주세요."
        }
    }
    // The area is an inspection of the full query, independent of hidden display layers.
    val overlapHit = remember(prepared, overlapPoint, overlapOnly, minimumWalks) {
        overlapPoint?.takeIf { overlapOnly }?.let { prepared?.hitTestOverlap(it, minimumWalks, snapRadiusU = 0.0) }
    }
    LaunchedEffect(prepared, overlapHit) {
        if (prepared != null && overlapPoint != null && overlapHit == null) overlapPoint = null
        if (overlapHit != null && selectedId !in overlapHit.walkIds) selectedId = null
    }
    // Any display change clears old ink before the next asynchronous composition can publish.
    val overlapMinimum = minimumWalks.takeIf { overlapOnly }
    var tiles by remember(prepared, hiddenIds, overlapMinimum) { mutableStateOf<List<TraceRasterTile>?>(null) }
    var compositionError by remember(prepared, hiddenIds, overlapMinimum) { mutableStateOf<String?>(null) }
    var composeRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(prepared, hiddenIds, overlapMinimum, composeRetry) {
        val ready = prepared ?: return@LaunchedEffect
        tiles = null
        compositionError = null
        try {
            val composed = ready.compose(hiddenIds, minimumOverlapWalks = overlapMinimum)
            currentCoroutineContext().ensureActive()
            tiles = composed
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            compositionError = if (overlapOnly) ready.overlapUnavailableReason
                ?: "겹친 구간을 표시하지 못했어요. 조건을 좁히거나 전체 흔적으로 돌아가 주세요."
                else "산책 흔적을 표시하지 못했어요. 다시 시도해 주세요."
        }
    }

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().background(CreamBg)
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 뒤로") }
            Text("산책 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        sampleLabel?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = TextMuted) }
        WalkRecordsConditions(query, pets,
            onKeyword = { filter = filter.copy(keyword = it) },
            onOpenConditions = { focusManager.clearFocus(); conditionsOpen = true },
            onReset = { focusManager.clearFocus(); dogId = null; filter = WalkHistoryFilter(); behavior = null },
            showBehavior = view == RecordsView.OVERVIEW, behavior = behavior,
            onClearBehavior = { behavior = null })
        TabRow(selectedTabIndex = view.ordinal) {
            Tab(selected = view == RecordsView.WALKS, onClick = { focusManager.clearFocus(); view = RecordsView.WALKS },
                text = { Text("산책별") }, modifier = Modifier.testTag("records-view-walks"))
            Tab(selected = view == RecordsView.OVERVIEW, onClick = { focusManager.clearFocus(); view = RecordsView.OVERVIEW },
                text = { Text("모아보기") }, modifier = Modifier.testTag("records-view-overview"))
        }
        val current = selection
        if (current != null && view == RecordsView.WALKS) {
            Text("선택 산책 ${current.records.size}회", Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp).testTag("records-count"),
                style = MaterialTheme.typography.labelLarge)
        }
        when {
            error != null -> RecordsMessage(error!!, "다시 시도", { retry++ }, Modifier.weight(1f))
            current == null -> RecordsMessage("산책 기록을 찾고 있어요.", modifier = Modifier.weight(1f))
            current.records.isEmpty() -> RecordsMessage(
                if (query.dogId != null || query.filter.active) "조건에 맞는 산책이 없어요." else "아직 산책 기록이 없어요.",
                if (query.dogId != null || query.filter.active) "전체 기록 보기" else null,
                { dogId = null; filter = WalkHistoryFilter() }, Modifier.weight(1f),
            )
            else -> {
                if (view == RecordsView.WALKS) {
                    val currentPage = pageIndex.coerceAtMost((current.records.size - 1) / PAGE_SIZE)
                    val rows = current.page(currentPage, PAGE_SIZE)
                    savedLists.SaveableStateProvider(currentPage) {
                        WalkHistoryPageContent(rows.map { it.summary }, currentPage + 1,
                            currentPage > 0, (currentPage + 1) * PAGE_SIZE < current.records.size,
                            { pageIndex = currentPage - 1 }, { pageIndex = currentPage + 1 },
                            onOpen, pets, Modifier.weight(1f),
                            rows.mapNotNull { record -> record.title?.let { record.summary.sessionId to it } }.toMap())
                    }
                } else if (behavior != null) {
                    val behaviorResult = remember(current, behavior) { selectWalkRecordBehaviors(current, requireNotNull(behavior)) }
                    WalkRecordsBehaviorExplorer(behaviorResult, pets, onOpen,
                        view = behaviorView, onView = { behaviorView = it }, state = behaviorState,
                        modifier = Modifier.weight(1f))
                } else {
                    WalkRecordsOverview(current, pets, prepared, tiles, mapError ?: compositionError,
                        onRetry = { if (prepared == null) mapRetry++ else composeRetry++ },
                        selectedId = selectedId, hiddenIds = hiddenIds,
                        onSelect = { id ->
                            selectedId = id.takeIf { it != selectedId }
                            if (selectedId != null && id !in hiddenIds) {
                                val points = walkRecordFocusBounds(current.records.first { it.summary.sessionId == id })
                                if (points.isNotEmpty()) { focusBounds = points; cameraRequest++ }
                            }
                        },
                        onToggleHidden = { id -> if (id in prepared?.availableWalkIds.orEmpty()) {
                            hiddenIds = if (id in hiddenIds) hiddenIds - id else hiddenIds + id
                        } },
                        onRestoreAll = { hiddenIds = emptySet() },
                        onClearSelection = { selectedId = null },
                        overlapOnly = overlapOnly, minimumWalks = minimumWalks,
                        onOverlapOnly = { next -> if (next != overlapOnly) {
                            overlapOnly = next; overlapPoint = null; selectedId = null; overlapMiss = false
                        } },
                        onMinimumWalks = { next -> if (next != minimumWalks) {
                            minimumWalks = next; overlapPoint = null; selectedId = null; overlapMiss = false
                        } },
                        overlapHit = overlapHit, overlapMiss = overlapMiss,
                        onMapTap = { point ->
                            val ready = prepared
                            if (overlapOnly && ready != null && tiles != null && compositionError == null && mapError == null) {
                                val hit = ready.hitTestOverlap(point, minimumWalks, hiddenIds = hiddenIds)
                                overlapPoint = hit?.point; overlapMiss = hit == null; selectedId = null
                                overlapSelectionRequest++
                            }
                        },
                        onClearOverlap = { overlapPoint = null; overlapMiss = false; selectedId = null },
                        onOpen = onOpen, listState = overviewScroll,
                        camera = camera, onCamera = { camera = it },
                        fitBounds = focusBounds ?: prepared?.bounds.orEmpty(), cameraRequest = cameraRequest,
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (conditionsOpen) {
        WalkRecordsConditionsSheet(query, pets, today,
            onApply = { next -> dogId = next.dogId; filter = next.filter; conditionsOpen = false },
            onDismiss = { conditionsOpen = false }, behavior = behavior,
            onBehaviorApply = { next ->
                if (next != behavior) {
                    if (behavior == null) behaviorView = BehaviorRecordsView.RECORD_LOCATIONS
                    behavior = next
                    if (next != null) view = RecordsView.OVERVIEW
                }
            })
    }
}

@Composable
internal fun RecordsMessage(
    text: String, action: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(text, textAlign = TextAlign.Center, color = TextMuted)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

private enum class RecordsView { WALKS, OVERVIEW }
private const val PAGE_SIZE = 5

private val HiddenWalkIdsSaver = listSaver<Set<String>, String>(
    save = { it.sorted() }, restore = { it.toSet() },
)

private val OverlapPointSaver = Saver<GeoPoint?, List<Double>>(
    save = { point -> point?.let { listOf(it.latitude, it.longitude) } ?: emptyList() },
    restore = { values -> values.takeIf { it.size == 2 }?.let { GeoPoint(it[0], it[1]) } },
)

private val CameraSnapshotSaver = Saver<MapCameraSnapshot?, List<Double>>(
    save = { camera -> camera?.let { listOf(it.target.latitude, it.target.longitude, it.zoom, it.bearing, it.tilt) } ?: emptyList() },
    restore = { values -> values.takeIf { it.size == 5 }?.let {
        MapCameraSnapshot(GeoPoint(it[0], it[1]), it[2], it[3], it[4])
    } },
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WalkRecordsScreenPreview() {
    val source = remember { WalkRecordsSource { query ->
        selectWalkRecords(listOf(WalkRecord(previewDiarySummary(), "숲길을 걸었어요")), query)
    } }
    DaengsTheme { WalkRecordsScreen(source, emptyList(), {}, {}, sampleLabel = "화면 예시 · 가상의 산책") }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun RecordsMessagePreview() {
    DaengsTheme { RecordsMessage("조건에 맞는 산책이 없어요.", "전체 기록 보기") }
}
