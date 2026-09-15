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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
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
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.walk.WalkHistoryFilter
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.records.WalkRecord
import com.daengs.app.walk.records.WalkRecordsQuery
import com.daengs.app.walk.records.WalkRecordsSelection
import com.daengs.app.walk.records.WalkRecordsSource
import com.daengs.app.walk.records.WalkTraceState
import com.daengs.app.walk.records.WalkTraceOverlapHit
import com.daengs.app.walk.records.carerSummary
import com.daengs.app.walk.records.selectWalkRecords
import com.daengs.app.walk.records.sharedWalksNeededFor
import com.daengs.app.walk.records.unifiedWalkPage
import com.daengs.app.walk.records.PreparedWalkRecordsTraces
import com.daengs.app.map.features.records.TraceView
import com.daengs.app.walk.records.walkRecordFocusBounds
import com.daengs.app.walk.records.selectWalkRecordBehaviors
import com.daengs.app.walk.shared.SharedWalk
import com.daengs.app.walk.shared.SharedWalksHolder
import com.daengs.app.walk.shared.SharedWalksStatus
import com.daengs.app.walk.shared.sharedFeedQueryOf
import com.daengs.app.walk.shared.sharedWalksExcludedByText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * One query and complete selection, viewed either as paginated records or combined traces.
 *
 * [sharedWalks] 가 있으면 「산책별」은 기기의 내 산책과 서버의 공동 보호자 산책을 한 목록으로 섞는다
 * (보호자 조건도 여기에만 적용). 「모아보기」 지도는 계속 내 산책 경로만 그린다.
 */
@Composable
fun WalkRecordsScreen(
    source: WalkRecordsSource,
    pets: List<Pet>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    sampleLabel: String? = null,
    today: LocalDate = LocalDate.now(),
    petsLoaded: Boolean = true,
    photoOf: (String) -> androidx.compose.ui.graphics.ImageBitmap? = { null },
    sharedWalks: SharedWalksHolder? = null,
    /** 로그인한 사람의 app user id. 보호자 조건의 "나" 다. 없으면 공동 보호자 산책을 섞지 않는다. */
    myId: String? = null,
    onOpenShared: (SharedWalk) -> Unit = {},
) {
    var dogIds by rememberSaveable(stateSaver = RecordsDogIdsSaver) { mutableStateOf<Set<String>?>(null) }
    // 보호자 조건. null = 모든 보호자. 「산책별」에만 적용된다.
    var carerIds by rememberSaveable(stateSaver = RecordsDogIdsSaver) { mutableStateOf<Set<String>?>(null) }
    var filter by rememberSaveable(stateSaver = HistoryFilterSaver) { mutableStateOf(WalkHistoryFilter()) }
    val query = remember(dogIds, filter) { WalkRecordsQuery(dogIds, filter) }
    var view by rememberSaveable { mutableStateOf(RecordsView.WALKS) }
    var behavior by rememberSaveable { mutableStateOf<WalkMomentType?>(null) }
    var behaviorView by rememberSaveable(query) { mutableStateOf(BehaviorRecordsView.RECORD_LOCATIONS) }
    // Keep inspection state above the loading/tab branches, with a stable restoration location.
    // The native map must not be wrapped in SaveableStateHolder's ReusableContent subtree.
    val behaviorState = rememberWalkRecordsBehaviorState(query, behavior)
    val actionPinState = rememberWalkRecordsActionPinState(query, behavior)
    var activeFilter by rememberSaveable { mutableStateOf<RecordsFilter?>(null) }
    var pageIndex by rememberSaveable(query, behavior, carerIds) { mutableIntStateOf(0) }
    var camera by rememberSaveable(query, stateSaver = CameraSnapshotSaver) { mutableStateOf<MapCameraSnapshot?>(null) }
    var selectedId by rememberSaveable(query) { mutableStateOf<String?>(null) }
    var hiddenIds by rememberSaveable(query, stateSaver = HiddenWalkIdsSaver) { mutableStateOf(emptySet<String>()) }
    var overviewExpanded by rememberSaveable(query) { mutableStateOf(false) }
    var overlapOnly by rememberSaveable(query) { mutableStateOf(false) }
    var minimumWalks by rememberSaveable(query) { mutableIntStateOf(2) }
    var overlapPoint by rememberSaveable(query, stateSaver = OverlapPointSaver) { mutableStateOf<GeoPoint?>(null) }
    var overlapSelectionRequest by rememberSaveable(query) { mutableIntStateOf(0) }
    var overlapMiss by remember(query, overlapOnly, minimumWalks) { mutableStateOf(false) }
    val overviewScroll = key(query, overlapPoint, overlapSelectionRequest) { rememberLazyListState() }
    var focusBounds by remember(query) { mutableStateOf<List<GeoPoint>?>(null) }
    var cameraRequest by remember(query) { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var retry by remember { mutableIntStateOf(0) }
    // Reset synchronously with the query so an earlier query's records never flash underneath it.
    var selection by remember(source, query, retry) { mutableStateOf<WalkRecordsSelection?>(null) }
    var error by remember(source, query, retry) { mutableStateOf<String?>(null) }
    val savedLists = key(query, behavior, carerIds) { rememberSaveableStateHolder() }
    LaunchedEffect(source, query, retry) {
        try {
            if (query.filter.keyword.isNotBlank()) delay(250)
            source.changes.collectLatest {
                // Keep the last successful result during same-source, same-query refreshes.
                // Source/query/retry changes still clear it synchronously through remember above.
                error = null
                try {
                    val previous = selection
                    val loaded = withContext(Dispatchers.Default) {
                        val next = source.select(query)
                        require(next.query == query) { "조회 조건과 결과 조건이 달라요." }
                        // Selection is not a data class. Retain its identity for equal contents so
                        // table invalidations do not restart trace loading or recreate map inputs.
                        if (previous != null && previous.query == next.query && previous.records == next.records)
                            previous else next
                    }
                    currentCoroutineContext().ensureActive()
                    selection = loaded
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    // A failed read may mean the captured account is no longer valid.
                    // Never reveal this stale snapshot again when a later refresh starts.
                    selection = null
                    error = "산책 기록을 불러오지 못했어요."
                }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            selection = null
            error = "산책 기록을 불러오지 못했어요."
        }
    }
    LaunchedEffect(pets, petsLoaded) {
        if (petsLoaded) dogIds = dogIds?.intersect(pets.map { it.id }.toSet())?.takeIf { it.isNotEmpty() }
    }
    LaunchedEffect(selection) {
        selection?.let { current ->
            if (selectedId !in current.sessionIds) selectedId = null
            hiddenIds = hiddenIds.intersect(current.sessionIds.toSet())
        }
    }
    // 공동 보호자 산책은 로그인한 사람을 알 때만 섞는다.
    val holder = sharedWalks?.takeIf { !myId.isNullOrBlank() }
    LaunchedEffect(holder) { holder?.loadCarers() }

    // Opening the other view never selects records again or changes the current list page.
    var mapRequested by remember(source, query, selection) { mutableStateOf(false) }
    LaunchedEffect(view, selection) { if (view == RecordsView.OVERVIEW) mapRequested = true }
    val tracesRequested = mapRequested || view == RecordsView.OVERVIEW
    // Local rows stay authoritative while this separate request enriches only their map inputs.
    var traceSelection by remember(source, selection) { mutableStateOf<WalkRecordsSelection?>(null) }
    var traceLoading by remember(source, selection) { mutableStateOf(false) }
    var traceError by remember(source, selection) { mutableStateOf<String?>(null) }
    var traceRequest by remember(source, selection) { mutableIntStateOf(0) }
    LaunchedEffect(source, selection, tracesRequested, traceRequest) {
        val local = selection ?: return@LaunchedEffect
        if (!tracesRequested || local.records.isEmpty()) return@LaunchedEffect
        traceLoading = true
        traceError = null
        traceSelection = local.withTraceRequestState(WalkTraceState.LOADING)
        try {
            val loaded = withContext(Dispatchers.Default) {
                source.loadTraces(local).also { enriched ->
                    require(enriched.query == local.query && enriched.sessionIds == local.sessionIds &&
                        enriched.records.zip(local.records).all { (after, before) ->
                            currentCoroutineContext().ensureActive()
                            after.copy(trace = before.trace, traceState = before.traceState) == before
                        }) { "흔적 조회가 원본 산책 기록을 변경했어요." }
                }
            }
            currentCoroutineContext().ensureActive()
            traceSelection = loaded
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            traceSelection = local.withTraceRequestState(WalkTraceState.FAILED)
            traceError = "흔적을 불러오지 못했어요. 산책 기록과 행동 위치는 그대로 볼 수 있어요."
        } finally {
            if (currentCoroutineContext().isActive) traceLoading = false
        }
    }
    val mappedSelection = traceSelection ?: selection
    val shouldPrepareMap = behavior == null && tracesRequested
    val displayPolicy = rememberWalkRecordsDisplayPolicy()
    val tracePresentation = rememberWalkRecordsTraces(mappedSelection, shouldPrepareMap,
        if (overlapOnly) TraceView.Overlap(minimumWalks) else TraceView.All, hiddenIds, displayPolicy.trace)
    val prepared = tracePresentation.prepared
    val tiles = tracePresentation.tiles
    val mapError = tracePresentation.preparationError
    val compositionError = tracePresentation.compositionError
    // The area is an inspection of the full query, independent of hidden display layers.
    val overlapHit = remember(prepared, overlapPoint, overlapOnly, minimumWalks) {
        overlapPoint?.takeIf { overlapOnly }?.let { prepared?.hitTestOverlap(it, minimumWalks, snapRadiusU = 0.0) }
    }
    ReconcileWalkRecordsOverlapPoint(mappedSelection, traceLoading, traceError, prepared,
        overlapPoint, overlapHit, onClear = { overlapPoint = null })
    LaunchedEffect(overlapHit) {
        if (overlapHit != null && selectedId !in overlapHit.walkIds) selectedId = null
    }
    val selectedCarers = carerIds
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().background(CreamBg)
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        WalkRecordsHeader(query, pets, view == RecordsView.OVERVIEW, behavior,
            onBack = onBack, onOverview = { view = if (it) RecordsView.OVERVIEW else RecordsView.WALKS },
            onConditions = { focusManager.clearFocus(); activeFilter = RecordsFilter.ALL }, today = today,
            carerLabel = holder?.let { carerSummary(selectedCarers, it.carers, myId!!) })
        sampleLabel?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = TextMuted) }
        val current = remember(selection, behavior) {
            selection?.let { base -> behavior?.let { selectWalkRecordBehaviors(base, it).related } ?: base }
        }
        // 보호자 조건에 내가 빠졌으면 「산책별」에 내 산책을 두지 않는다.
        val includeMine = holder == null || selectedCarers == null || myId in selectedCarers
        val zone = remember { ZoneId.systemDefault() }
        val feedQuery = remember(holder, query, selectedCarers, behavior) {
            if (holder == null) null else sharedFeedQueryOf(query, selectedCarers, myId!!, behavior != null, zone)
        }
        val feedLoaded = feedQuery != null && holder?.feedQuery == feedQuery
        val sharedTotals = if (feedLoaded) holder?.totals else null
        val sharedStatus = if (feedQuery != null) holder?.status else null
        val sharedStopped = sharedStatus is SharedWalksStatus.Failed || sharedStatus == SharedWalksStatus.Unsupported
        val sharedList = if (feedLoaded) holder!!.walks else emptyList()
        val mineRecords = if (includeMine) current?.records.orEmpty() else emptyList()
        val walksPage = unifiedWalkPage(mineRecords, sharedList, sharedTotals?.count ?: sharedList.size,
            sharedExhausted = feedQuery == null || sharedStopped || (sharedTotals != null && holder?.nextCursor == null),
            pageIndex, PAGE_SIZE)
        LaunchedEffect(feedQuery, walksPage.pageIndex, view) {
            if (feedQuery != null && view == RecordsView.WALKS) holder?.ensure(feedQuery, sharedWalksNeededFor(walksPage.pageIndex, PAGE_SIZE))
        }
        // 합계는 페이지가 아니라 조건 전체 — 내 산책(기기) + 공동 보호자 산책(서버 합계).
        WalkRecordsTotals(current?.let { mineRecords.size + (sharedTotals?.count ?: 0) },
            mineRecords.sumOf { it.summary.distanceMeters } + (sharedTotals?.distanceM ?: 0L).toDouble(),
            mineRecords.sumOf { it.summary.activeDurationMillis } + (sharedTotals?.durationS ?: 0L) * 1_000L,
            failed = error != null)
        val hasCondition = query.dogIds != null || query.filter.active || behavior != null || selectedCarers != null
        val showAll = { dogIds = null; filter = WalkHistoryFilter(); behavior = null; carerIds = null }
        when {
            error != null -> RecordsMessage(error!!, "다시 시도", { retry++ }, Modifier.weight(1f))
            current == null -> RecordsMessage("산책 기록을 찾고 있어요.", modifier = Modifier.weight(1f))
            view == RecordsView.WALKS -> {
                val sharedLoading = feedQuery != null && sharedTotals == null && !sharedStopped
                SharedWalksNotice(
                    excluded = holder != null && sharedWalksExcludedByText(query, selectedCarers, myId!!, behavior != null),
                    status = sharedStatus, loading = sharedLoading,
                    onRetry = { feedQuery?.let { q -> scope.launch { holder?.retry(q, sharedWalksNeededFor(walksPage.pageIndex, PAGE_SIZE)) } } })
                if (walksPage.total == 0 && !sharedLoading) RecordsMessage(
                    if (hasCondition) "조건에 맞는 산책이 없어요." else "아직 산책 기록이 없어요.",
                    if (hasCondition) "전체 기록 보기" else null, showAll, Modifier.weight(1f),
                ) else {
                    val currentPage = walksPage.pageIndex
                    savedLists.SaveableStateProvider(currentPage) {
                        WalkRecordRowsList(walksPage.rows, currentPage + 1, walksPage.pageCount,
                            { pageIndex = currentPage - 1 }, { pageIndex = currentPage + 1 },
                            onOpen, onOpenShared, pets, Modifier.weight(1f), showActor = holder != null)
                    }
                }
            }
            // 모아보기는 내 산책 경로만 그린다 — 보호자 조건에서 내가 빠졌는데 내 경로를 그리면 모순이다.
            !includeMine -> RecordsMessage("모아보기는 내 산책 경로만 표시돼요. 보호자에 '나'를 넣으면 볼 수 있어요.",
                "모든 보호자 보기", { carerIds = null }, Modifier.weight(1f).testTag("records-overview-mine-only"))
            current.records.isEmpty() -> RecordsMessage(
                if (hasCondition) "조건에 맞는 산책이 없어요." else "아직 산책 기록이 없어요.",
                if (hasCondition) "전체 기록 보기" else null, showAll, Modifier.weight(1f),
            )
            else -> {
                if (holder?.carers?.any { !it.isMe } == true) Text("모아보기는 내 산책 경로만 표시돼요.",
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp).testTag("records-overview-mine-only-notice"),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted)
                if (behavior != null) {
                    val mapRecords = mappedSelection ?: current
                    val behaviorResult = remember(mapRecords, behavior) { selectWalkRecordBehaviors(mapRecords, requireNotNull(behavior)) }
                    WalkRecordsBehaviorExplorer(behaviorResult, pets, onOpen, routeSource = source,
                        view = behaviorView, onView = { behaviorView = it }, state = behaviorState, actionPinState = actionPinState,
                        traceLoading = traceLoading, traceError = traceError, onReloadTraces = { traceRequest++ },
                        modifier = Modifier.weight(1f))
                } else {
                    WalkRecordsOverview(mappedSelection ?: current, pets, prepared, tiles, mapError ?: compositionError,
                        routeSource = source, actionPinState = actionPinState,
                        expanded = overviewExpanded, onExpanded = { overviewExpanded = it },
                        onRetry = tracePresentation.retry,
                        selectedId = selectedId, hiddenIds = hiddenIds,
                        onSelect = { id ->
                            selectedId = id.takeIf { it != selectedId }
                            if (selectedId != null && id !in hiddenIds) {
                                val points = walkRecordFocusBounds(current.records.first { it.summary.sessionId == id })
                                if (points.isNotEmpty()) {
                                    // A choice made before MapHost mounts overrides its saved camera.
                                    if (prepared == null) camera = null
                                    focusBounds = points
                                    cameraRequest++
                                }
                            }
                        },
                        onToggleHidden = { id -> if (id in current.sessionIds) {
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
                        traceLoading = traceLoading, traceError = traceError, onReloadTraces = { traceRequest++ },
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
    activeFilter?.let { kind ->
        WalkRecordsConditionsSheet(kind, query, pets, today,
            onApply = { next -> dogIds = next.dogIds; filter = next.filter },
            onDismiss = { activeFilter = null }, behavior = behavior, petsLoaded = petsLoaded, photoOf = photoOf,
            onBehaviorApply = { next ->
                if (next != behavior) {
                    if (behavior == null) behaviorView = BehaviorRecordsView.RECORD_LOCATIONS
                    behavior = next
                }
            },
            carers = holder?.carers, myId = myId, carerIds = carerIds,
            onCarersApply = { next -> carerIds = next })
    }
}

/**
 * 「산책별」 위의 공동 보호자 산책 한 줄 안내. 실패해도 **내 산책은 그대로** 두고 다시 시도만 보탠다.
 */
@Composable
private fun SharedWalksNotice(excluded: Boolean, status: SharedWalksStatus?, loading: Boolean, onRetry: () -> Unit) {
    val (text, tag) = when {
        excluded -> "검색어·행동 조건에서는 내 산책만 찾아요." to "records-shared-excluded"
        status is SharedWalksStatus.Failed ->
            "함께 돌보는 보호자의 산책을 불러오지 못했어요. 내 산책은 그대로 볼 수 있어요." to "records-shared-failed"
        status == SharedWalksStatus.Unsupported ->
            "지금 서버에서는 함께 돌보는 보호자의 산책을 볼 수 없어요." to "records-shared-unsupported"
        loading -> "함께 돌보는 보호자의 산책을 불러오고 있어요." to "records-shared-loading"
        else -> return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).testTag(tag), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        if (!excluded && status is SharedWalksStatus.Failed) {
            TextButton(onClick = onRetry, modifier = Modifier.testTag("records-shared-retry")) { Text("다시 시도") }
        }
    }
}

/** A missing hit proves removal only after every sheet was successfully checked. */
@Composable
internal fun ReconcileWalkRecordsOverlapPoint(
    selection: WalkRecordsSelection?,
    traceLoading: Boolean,
    traceError: String?,
    prepared: PreparedWalkRecordsTraces?,
    point: GeoPoint?,
    hit: WalkTraceOverlapHit?,
    onClear: () -> Unit,
) {
    val complete = !traceLoading && traceError == null && selection?.records?.all {
        it.effectiveTraceState == WalkTraceState.READY || it.effectiveTraceState == WalkTraceState.EMPTY
    } == true
    LaunchedEffect(complete, prepared, point, hit) {
        if (complete && prepared != null && point != null && hit == null) onClear()
    }
}

/** Embedded preview traces need no server request; unsent records keep their distinct explanation. */
private fun WalkRecordsSelection.withTraceRequestState(state: WalkTraceState): WalkRecordsSelection {
    if (records.none { it.traceState != null }) return this
    return WalkRecordsSelection(query, records.map { record ->
        if (record.traceState == null || record.traceState == WalkTraceState.NOT_UPLOADED) record
        else record.copy(trace = null, traceState = state)
    })
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

internal val OverlapPointSaver = Saver<GeoPoint?, List<Double>>(
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
