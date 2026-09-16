package com.daengs.app.ui.walk.records

import com.daengs.app.walk.diary.DiaryActionTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapLocationTarget
import com.daengs.app.map.shell.MapVisibilityQuery
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.ui.walk.toCompletedRouteLayerState
import com.daengs.app.ui.walk.walkDiaryTitle
import com.daengs.app.walk.records.*
import com.daengs.app.walk.toSessionRoute
import com.daengs.app.map.features.records.*
import com.daengs.app.walk.WalkMomentType
import kotlinx.coroutines.launch

@Composable
internal fun WalkRecordsOverview(
    selection: WalkRecordsSelection,
    pets: List<Pet>,
    prepared: PreparedWalkRecordsTraces?,
    tiles: List<TraceRasterTile>?,
    error: String?,
    onRetry: () -> Unit,
    selectedId: String?,
    hiddenIds: Set<String>,
    onSelect: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onRestoreAll: () -> Unit,
    onClearSelection: () -> Unit,
    onOpen: (String) -> Unit,
    listState: LazyListState,
    camera: MapCameraSnapshot?,
    onCamera: (MapCameraSnapshot) -> Unit,
    fitBounds: List<GeoPoint>,
    cameraRequest: Int,
    overlapOnly: Boolean = false,
    minimumWalks: Int = 2,
    onOverlapOnly: (Boolean) -> Unit = {},
    onMinimumWalks: (Int) -> Unit = {},
    overlapHit: WalkTraceOverlapHit? = null,
    overlapMiss: Boolean = false,
    onMapTap: (GeoPoint) -> Unit = {},
    onClearOverlap: () -> Unit = {},
    traceLoading: Boolean = false,
    traceError: String? = null,
    onReloadTraces: () -> Unit = {},
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onExpanded: (Boolean) -> Unit = {},
    controls: (@Composable () -> Unit)? = null,
    behaviorCount: String? = null,
    routeSource: WalkRecordsSource? = null,
    actionPinState: WalkRecordsActionPinState = rememberWalkRecordsActionPinState(),
    pinBehavior: WalkMomentType? = null,
    onOpenAction: (DiaryActionTarget) -> Unit = { onOpen(it.sessionId) },
    onInspect: (String?) -> Unit = { id -> if (id == null) onClearSelection() else onSelect(id) },
) {
    val selected = selection.records.firstOrNull { it.summary.sessionId == selectedId }
    val highlighted = selected?.takeUnless { it.summary.sessionId in hiddenIds }
    val pinTypes = (pinBehavior ?: actionPinState.type.value)?.let(::setOf) ?: RECORD_ACTION_TYPES
    val pins = remember(selection, pinTypes, hiddenIds, actionPinState.enabled.value) {
        walkRecordsActionPins(selection, pinTypes, hiddenIds, actionPinState.enabled.value)
    }
    val selectedPin = pins.records.firstOrNull { it.key == actionPinState.selectedKey.value }
    // Membership is saved independently of native clusters, which change with zoom and bearing.
    val inspectedRecords = pins.records.filter { it.key in actionPinState.groupKeys.value && it.point != null && it.walk.summary.sessionId !in hiddenIds }
    val pinGroup = inspectedRecords.takeIf { it.isNotEmpty() }?.let { WalkActionPinGroup(requireNotNull(it.first().point), it) }
    val pinRecords = pinGroup?.records ?: pins.records
    val visits = remember(pinRecords) { placeWalks(pinRecords) }
    val peek = visits.currentPlaceAction(actionPinState.selectedKey.value)
    val markers = remember(pins, actionPinState.selectedKey.value, actionPinState.groupKeys.value) {
        pins.detachedMarkers(actionPinState.selectedKey.value, actionPinState.groupKeys.value)
    }
    var unplacedPinIds by remember(pins) { mutableStateOf<Set<String>>(emptySet()) }
    val unplacedCount = markers.filter { it.id in unplacedPinIds }.sumOf { it.recordPin?.count ?: 1 }
    LaunchedEffect(pins) {
        if (selectedPin == null || selectedPin.walk.summary.sessionId in hiddenIds) actionPinState.selectedKey.value = null
        if (pinGroup == null) { actionPinState.groupPoint.value = null; actionPinState.groupKeys.value = emptyList() }
    }
    var pinCenter by remember(selection) { mutableStateOf<GeoPoint?>(null) }
    var pinCameraRequest by remember(selection) { mutableIntStateOf(0) }
    var pinZoom by remember(selection) { mutableStateOf<Double?>(null) }
    val pinScope = rememberCoroutineScope()
    val displayPolicy = rememberWalkRecordsDisplayPolicy()
    val routePresentation = rememberWalkRecordsRoute(highlighted, routeSource)
    val routeSummary = routePresentation.summary
    val routeError = routePresentation.error
    val selectedRoute = remember(routeSummary) {
        routeSummary?.toSessionRoute()?.toCompletedRouteLayerState()?.let { layer ->
            layer.copy(start = layer.start?.copy(compact = true), end = layer.end?.copy(compact = true))
        } ?: CompletedRouteLayerState()
    }
    val route = selectedRoute.copy(selectedPoint = overlapHit?.takeIf { hit ->
        tiles != null && error == null && hit.walkIds.any { it !in hiddenIds }
    }?.point)
    val avoidancePaths = remember(selection, hiddenIds) { recordPinAvoidancePaths(selection.records, hiddenIds) }
    val renderPlan = remember(displayPolicy, tiles, route, markers, avoidancePaths) {
        composeWalkRecordsMapScene(displayPolicy, tiles.orEmpty(), route, markers, recordPinPaths = avoidancePaths)
    }
    val routeBounds = remember(selection) { selection.records.flatMap(::walkRecordFocusBounds) }
    val hasGeometry = prepared?.bounds?.isNotEmpty() == true || routeBounds.isNotEmpty() || route.paths.any { it.isNotEmpty() } || markers.isNotEmpty()
    val displayableWalkIds = remember(selection, prepared, pins.records) {
        prepared?.availableWalkIds.orEmpty() +
            selection.records.filter { it.summary.segments.any { path -> path.isNotEmpty() } }.map { it.summary.sessionId } +
            pins.records.filter { it.point != null }.map { it.walk.summary.sessionId }
    }
    val hiddenCount = displayableWalkIds.count { it in hiddenIds }
    val displayIds = if (overlapOnly) prepared?.overlapWalkIds(minimumWalks) else prepared?.availableWalkIds
    val visibleCount = displayIds?.count { it !in hiddenIds }
    val relatedRecords = overlapHit?.let { hit -> selection.records.filter { it.summary.sessionId in hit.walkIds } }
        ?: selection.records
    val remoteTraces = selection.records.any { it.traceState != null }
    val partialTraces = selection.records.any {
        it.effectiveTraceState != WalkTraceState.READY && it.effectiveTraceState != WalkTraceState.EMPTY
    }
    var localExpanded by rememberSaveable { mutableStateOf(false) }
    val sheetExpanded = expanded ?: localExpanded
    val setExpanded: (Boolean) -> Unit = { localExpanded = it; onExpanded(it) }
    val emptyActions: @Composable () -> Unit = {
        val canClearType = pinBehavior == null && actionPinState.type.value != null
        WalkRecordsEmptyActions(
            "${(pinBehavior ?: actionPinState.type.value)?.label ?: "행동"} 기록이 없어요.",
            if (canClearType) "모든 행동 보기" else "산책 목록 보기",
            onAction = {
                actionPinState.clearInspection(); onInspect(null)
                if (canClearType) { actionPinState.type.value = null; actionPinState.allRecords() }
                else { actionPinState.browsing.value = false; setExpanded(true) }
            })
    }
    val choosePin: (WalkBehaviorRecord) -> Unit = { record ->
        actionPinState.selectedKey.value = record.key
        if (record.walk.summary.sessionId !in hiddenIds) {
            pinCenter = record.point
            pinZoom = null
            pinCameraRequest++
            if (selectedId != record.walk.summary.sessionId) onSelect(record.walk.summary.sessionId)
        }
    }
    val selectPlaceAction: (WalkBehaviorRecord) -> Unit = { record ->
        pinCenter = null; pinZoom = null
        actionPinState.selectedKey.value = record.key
        actionPinState.browsing.value = true
        onInspect(record.walk.summary.sessionId)
    }
    val onPinGroup: (List<String>) -> Unit = { ids ->
        val group = pins.resolveGroup(ids)
        if (group != null) {
            pinCenter = null; pinZoom = null
            actionPinState.inspect(group)
            pinScope.launch { actionPinState.listState.scrollToItem(0) }
            // Map inspection never calls the list's fit/zoom action or changes drawer height.
            onInspect(placeWalks(group.records).currentPlaceAction(actionPinState.selectedKey.value)?.walk?.summary?.sessionId)
        }
    }
    LaunchedEffect(selectedId) {
        if (!actionPinState.browsing.value && selectedPin?.walk?.summary?.sessionId != selectedId) pinCenter = null
    }
    LaunchedEffect(overlapHit?.point) { if (overlapHit != null) setExpanded(true) }
    val map: @Composable (Modifier) -> Unit = { mapModifier ->
        val mapInsets = LocalRecordsMapInsets.current
        val visibilityQuery = remember(markers, mapInsets) {
            MapVisibilityQuery("records-pins", markers.map { MapLocationTarget(it.id, listOf(it.point)) }, mapInsets.bottom)
        }
        val latestVisibilityQuery by rememberUpdatedState(visibilityQuery)
        Box(mapModifier.background(CreamBg).testTag("records-overview-map").semantics {
            stateDescription = highlighted?.takeIf { route.paths.any { it.isNotEmpty() } }
                ?.let { "강조한 산책: ${walkDiaryTitle(it.summary)}" }
                ?: "강조한 산책 없음"
        }) {
            when {
                !hasGeometry && prepared == null && error != null -> RecordsMessage(error, "다시 시도", onRetry, Modifier.fillMaxSize())
                !hasGeometry && prepared == null -> RecordsMessage("산책 흔적을 준비하고 있어요.", modifier = Modifier.fillMaxSize())
                !hasGeometry -> RecordsMessage("지도에 표시할 위치가 없어요.", modifier = Modifier.fillMaxSize())
                else -> {
                    // Keep the map mounted even when every trace is hidden or composition is pending.
                    MapHost(scene = renderPlan.scene, searchOrigin = null, followDevice = false,
                        fitBounds = fitBounds.ifEmpty { routeBounds.ifEmpty { markers.map { it.point } } },
                        centerOn = pinCenter, centerMinZoom = 16.0, centerZoom = pinZoom,
                        cameraRequestKey = cameraRequest + pinCameraRequest,
                        topPaddingPx = mapInsets.top, bottomPaddingPx = mapInsets.bottom,
                        initialCamera = camera, onCameraSnapshot = onCamera,
                        onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                        onSelectMomentGroup = onPinGroup,
                        visibilityQuery = visibilityQuery,
                        onVisibility = { result ->
                            if (result.query == latestVisibilityQuery && result.visibleIds != null)
                                unplacedPinIds = result.unplacedIds
                        },
                        onMapTap = { point ->
                            actionPinState.browsing.value = false; actionPinState.clearInspection()
                            pinCenter = null; pinZoom = null
                            onInspect(null); onMapTap(point)
                        },
                        modifier = Modifier.fillMaxSize())
                    val message = when {
                        routeError != null -> routeError
                        highlighted != null && routeSummary == null -> "산책 동선을 불러오고 있어요."
                        error != null -> error
                        tiles == null -> "흔적 표시를 바꾸고 있어요."
                        traceLoading && visibleCount == 0 -> "흔적을 불러오는 동안 산책 카드를 살펴보세요."
                        overlapOnly && prepared?.hasOverlap(minimumWalks) == false ->
                            (if (partialTraces) "불러온 흔적에는 " else "") + "${minimumWalks}회 이상 겹친 구간이 없어요."
                        overlapOnly && visibleCount == 0 -> "겹친 구간의 산책 흔적을 모두 숨겼어요."
                        visibleCount == 0 && hiddenCount > 0 -> "산책 흔적을 모두 숨겼어요."
                        visibleCount == 0 -> "표시할 산책 흔적이 없어요."
                        else -> null
                    }
                    if (message != null) Surface(Modifier.align(Alignment.Center).padding(18.dp),
                        color = CreamBg.copy(alpha = .95f)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelSmall)
                            if (routeError != null) TextButton(onClick = routePresentation.retry) { Text("다시 시도") }
                            else if (error != null) TextButton(onClick = onRetry) { Text("다시 시도") }
                        }
                    }
                }
            }
        }
    }

    WalkRecordsMapFrame(sheetExpanded, setExpanded,
        if (pinGroup != null) "이곳의 산책 ${visits.size}회 · 행동 ${pinRecords.size}건"
        else if (actionPinState.browsing.value) "액션 기록 ${pinRecords.size}건" else "관련 산책 ${relatedRecords.size}회", modifier,
        controls = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.widthIn(max = 112.dp)) {
                    if (controls != null) controls() else WalkRecordsTraceControls(overlapOnly, minimumWalks, onOverlapOnly, onMinimumWalks,
                        menuExtras = {
                            WalkRecordsActionPinControls(actionPinState, pinBehavior)
                            WalkRecordsTraceStatus(selection.records, traceLoading, traceError, onReloadTraces, compact = true)
                        })
                }
                WalkRecordsActionSearch(actionPinState, pinBehavior, Modifier.weight(1f)) {
                    pinCenter = null; pinZoom = null
                    onInspect(null)
                    actionPinState.allRecords(); setExpanded(true)
                    pinScope.launch { actionPinState.listState.scrollToItem(0) }
                }
            }
        },
        map = map,
        summary = {
            if (pinGroup == null) {
                if (behaviorCount != null) Text(behaviorCount, Modifier.padding(horizontal = 18.dp)
                    .testTag("records-behavior-count"), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (actionPinState.browsing.value) Text(
                        "핀 표시 ${pins.visibleCount}건 · 위치 없음 ${pins.unlocatedCount}건",
                        Modifier.weight(1f).testTag("records-pins-summary"), style = MaterialTheme.typography.labelSmall)
                    else if (unplacedCount > 0) TextButton(onClick = {
                        actionPinState.allRecords(); setExpanded(true)
                        pinScope.launch { actionPinState.listState.scrollToItem(0) }
                    }, Modifier.weight(1f).testTag("records-pins-unplaced")) {
                        Text("밀집 기록 ${unplacedCount}건 · 목록에서 보기", style = MaterialTheme.typography.labelSmall)
                    }
                    else Text(if (visibleCount == null) "지도 흔적을 준비하고 있어요."
                        else if (overlapOnly) "선택 산책 ${selection.records.size}회 · 겹침 표시 ${visibleCount}회"
                        else "선택 산책 ${selection.records.size}회 · 표시 흔적 ${visibleCount}개",
                        Modifier.weight(1f).testTag("records-map-count"), style = MaterialTheme.typography.labelMedium)
                    if (actionPinState.browsing.value) TextButton(onClick = {
                        actionPinState.browsing.value = false; actionPinState.clearInspection(); pinCenter = null
                        onInspect(null)
                    }, Modifier.testTag("records-pins-back-walks")) { Text("산책 목록") }
                    else TextButton(onClick = {
                        actionPinState.allRecords(); setExpanded(true)
                        pinScope.launch { actionPinState.listState.scrollToItem(0) }
                    }, Modifier.testTag("records-pins-browse")) { Text("액션 ${pins.records.size}건") }
                    if (hiddenCount > 0) {
                        TextButton(onClick = onRestoreAll, modifier = Modifier.testTag("records-map-restore-all")) {
                            Text("모두 표시")
                        }
                    }
                }
            }
        },
        details = {
            if (routeError != null) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                .testTag("records-route-error"), verticalAlignment = Alignment.CenterVertically) {
                Text(routeError, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = routePresentation.retry, Modifier.testTag("records-route-retry")) { Text("다시 시도") }
            }
            if (actionPinState.browsing.value && pinGroup != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pinRecords.mapNotNull { it.point }.distinct().size > 1 && (camera?.zoom ?: 16.0) < 21.0) {
                        TextButton(onClick = {
                            val points = pinRecords.mapNotNull { it.point }
                            pinCenter = GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average())
                            pinZoom = ((camera?.zoom ?: 16.0) + 1).coerceAtMost(21.0)
                            pinCameraRequest++
                            setExpanded(false)
                        }, Modifier.testTag("records-pins-expand")) { Text("구간 확대") }
                    }
                    TextButton(onClick = actionPinState::allRecords, Modifier.testTag("records-pins-all")) { Text("모든 액션") }
                }
            }
            // The expanded list covers the map's center; keep result/error feedback reachable here too.
            if (error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(error, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onRetry) { Text("다시 시도") }
            } else if (overlapOnly && prepared != null && prepared?.hasOverlap(minimumWalks) == false) {
                Text((if (partialTraces) "불러온 흔적에는 " else "") + "${minimumWalks}회 이상 겹친 구간이 없어요.",
                    Modifier.padding(horizontal = 18.dp, vertical = 6.dp).testTag("records-overlap-empty"),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            if (overlapOnly && partialTraces) Text("불러온 흔적 기준 · 아직 준비되지 않은 산책은 겹침에 포함되지 않아요.",
                Modifier.padding(horizontal = 18.dp).testTag("records-overlap-partial"),
                style = MaterialTheme.typography.labelSmall, color = TextMuted)
            // The map frame stays mounted at a fixed size; only meaningful inspection results appear.
            if (!actionPinState.browsing.value && (overlapHit != null || overlapMiss)) Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(when {
                    overlapHit != null -> "이 구간: ${selection.records.size}회 중 ${overlapHit.walkIds.size}회 겹침\n아래에서 관련 산책을 살펴보세요."
                    overlapMiss -> "이곳에는 ${minimumWalks}회 이상 겹친 흔적이 없어요."
                    else -> ""
                }, Modifier.weight(1f).testTag("records-inspection-summary"), style = MaterialTheme.typography.labelSmall)
                if (overlapHit != null) TextButton(onClick = onClearOverlap, modifier = Modifier.testTag("records-overlap-clear")) {
                    Text("전체 목록")
                }
            }

        },
        records = { listModifier ->
            if (actionPinState.browsing.value) {
                if (pinRecords.isEmpty()) Box(listModifier, contentAlignment = Alignment.Center) { emptyActions() }
                else if (pinGroup != null) WalkRecordsPlaceList(visits, pets, actionPinState.selectedKey.value,
                    selectPlaceAction, { record -> choosePin(record); setExpanded(false) },
                    onOpenAction, routeSource, listModifier, actionPinState.listState)
                else BehaviorRecordList(pinRecords, pets, actionPinState.selectedKey.value, hiddenIds,
                    displayableWalkIds,
                    { key -> pinRecords.firstOrNull { it.key == key }?.let(choosePin) }, onToggleHidden,
                    onOpen, listModifier, actionPinState.listState, onOpenAction = onOpenAction, readingSource = routeSource)
            } else WalkRecordsMapList(relatedRecords, pets, selectedId, hiddenIds, displayableWalkIds,
                { setExpanded(true); onSelect(it) }, onToggleHidden, onOpen, listModifier, listState)
        },
        // Reserve this height before a pin is picked so inspection never shifts the map or drawer.
        collapsedHeight = 180.dp,
        // Zooming can move native pins outside the viewport; the inspected place stays readable.
        collapsedContent = if (peek != null && actionPinState.enabled.value && !overlapOnly &&
            (unplacedCount == 0 || pinGroup != null)) ({
            WalkRecordsPlacePeek(visits, peek, pets, selectPlaceAction, {
                selectPlaceAction(peek); setExpanded(true)
                pinScope.launch {
                    val index = if (pinGroup != null) visits.indexOfFirst { it.id == peek.walk.summary.sessionId }
                        else pinRecords.indexOfFirst { it.key == peek.key }
                    actionPinState.listState.scrollToItem(index.coerceAtLeast(0))
                }
            }, onOpenAction)
        }) else if (actionPinState.browsing.value && pinRecords.isEmpty() && actionPinState.enabled.value && !overlapOnly) emptyActions else null)
}

/** Network availability is separate from brush rendering and never replaces the local record list. */
@Composable
internal fun WalkRecordsTraceStatus(
    records: List<WalkRecord>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val missing = records.filter { it.effectiveTraceState != WalkTraceState.READY }
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier.padding(horizontal = 6.dp).testTag("records-traces-status")) {
        Text(if (compact) "표시 정보" else if (loading) "흔적 불러오는 중 · 자세히" else "${records.size}회 중 ${records.size - missing.size}회 흔적 준비 · 자세히",
            style = MaterialTheme.typography.labelSmall)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("지도 흔적") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(if (loading) "흔적을 불러오고 있어요." else "${records.size}회 중 ${records.size - missing.size}회 흔적이 준비됐어요.")
                if (missing.isNotEmpty()) Text("흔적이 없는 산책도 목록에서 볼 수 있어요.")
                if (error != null) Text(error, Modifier.padding(top = 12.dp))
                missing.forEach { record ->
                    Text(walkDiaryTitle(record.summary), Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.titleSmall)
                    Text(traceStateExplanation(record.effectiveTraceState), style = MaterialTheme.typography.bodySmall)
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onRefresh, enabled = !loading,
            modifier = Modifier.testTag("records-traces-refresh")) { Text("흔적 다시 불러오기") } })
}

private fun traceStateExplanation(state: WalkTraceState): String = when (state) {
    WalkTraceState.NOT_UPLOADED -> "서버에서 이 산책의 흔적을 아직 찾을 수 없어요. 다시 불러오기는 흔적을 조회하며 산책을 전송하지 않아요."
    WalkTraceState.EMPTY -> "이 산책에는 지도에 표시할 흔적이 없어요."
    else -> traceStateLabel(state)
}

internal fun traceStateLabel(state: WalkTraceState): String = when (state) {
    WalkTraceState.NOT_REQUESTED -> "흔적 확인 전"
    WalkTraceState.LOADING -> "흔적 불러오는 중"
    WalkTraceState.READY -> "흔적 준비됨"
    WalkTraceState.EMPTY -> "지도 흔적 없음"
    WalkTraceState.NOT_UPLOADED -> "지도 흔적 아직 없음"
    WalkTraceState.ANALYSIS_PENDING -> "서버에서 흔적 계산 중"
    WalkTraceState.UNSUPPORTED -> "이 흔적은 아직 지원하지 않아요"
    WalkTraceState.FAILED -> "흔적을 불러오지 못했어요"
}

@Preview(name = "일부 흔적 대기", showBackground = true, widthDp = 390)
@Composable
private fun WalkRecordsTraceStatusPreview() {
    val sample = listOf(WalkTraceState.ANALYSIS_PENDING, WalkTraceState.NOT_UPLOADED, WalkTraceState.FAILED)
        .mapIndexed { index, state -> WalkRecord(previewDiarySummary().copy(sessionId = "walk-$index"), traceState = state) }
    DaengsTheme { WalkRecordsTraceStatus(sample, false, null, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 580)
@Composable
private fun WalkRecordsOverviewPreview() {
    val record = WalkRecord(previewDiarySummary(), "숲길을 걸었어요")
    DaengsTheme { WalkRecordsOverview(WalkRecordsSelection(WalkRecordsQuery(), listOf(record)),
        emptyList(), null, null, null, {}, record.summary.sessionId, emptySet(), {}, {}, {}, {}, {},
        rememberLazyListState(), null, {}, emptyList(), 0, modifier = Modifier.fillMaxSize()) }
}
