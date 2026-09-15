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
import com.daengs.app.map.shell.MapScene
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
import com.daengs.app.map.layers.moments.RecordPinAppearance
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
    val searching = pinBehavior != null || actionPinState.type.value != null
    val markers = remember(pins, actionPinState.selectedKey.value, actionPinState.groupKeys.value, searching) {
        pins.groups.map { group ->
            val chosen = group.records.firstOrNull { it.key == actionPinState.selectedKey.value }
            val marker = group.marker(actionPinState.selectedKey.value)
            marker.copy(selected = marker.selected || (actionPinState.selectedKey.value == null && group.records.any { it.key in actionPinState.groupKeys.value }),
                behaviors = if (!searching && chosen != null) setOf(chosen.entry.type) else marker.behaviors,
                recordPin = RecordPinAppearance(if (!searching && chosen != null) 1 else group.records.size,
                    background = !searching && chosen == null, alpha = .25f))
        } + pins.backgroundGroups.map { group -> group.marker(null).copy(id = "background:${group.id}",
            recordPin = RecordPinAppearance(group.records.size, background = true)) }
    }
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
    val renderPlan = remember(displayPolicy, tiles, route, markers) {
        composeWalkRecordsMapScene(displayPolicy, tiles.orEmpty(), route, markers)
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
    val choosePin: (WalkBehaviorRecord) -> Unit = { record ->
        actionPinState.selectedKey.value = record.key
        if (record.walk.summary.sessionId !in hiddenIds) {
            pinCenter = record.point
            pinZoom = null
            pinCameraRequest++
            if (selectedId != record.walk.summary.sessionId) onSelect(record.walk.summary.sessionId)
        }
    }
    val onPinGroup: (List<String>) -> Unit = { ids ->
        val records = pins.groups.filter { it.id in ids }.flatMap { it.records }.distinctBy { it.key }
        if (records.isNotEmpty()) {
            actionPinState.inspect(WalkActionPinGroup(requireNotNull(records.first().point), records))
            setExpanded(true)
            pinScope.launch { actionPinState.listState.scrollToItem(0) }
            if (records.size == 1) onSelect(records.single().walk.summary.sessionId) else onClearSelection()
        }
    }
    LaunchedEffect(selectedId) {
        if (!actionPinState.browsing.value && selectedPin?.walk?.summary?.sessionId != selectedId) pinCenter = null
    }
    LaunchedEffect(overlapHit?.point) { if (overlapHit != null) setExpanded(true) }
    val map: @Composable (Modifier) -> Unit = { mapModifier ->
        val mapInsets = LocalRecordsMapInsets.current
        Box(mapModifier.background(CreamBg).testTag("records-overview-map").semantics {
            stateDescription = highlighted?.takeIf { route.paths.any { it.isNotEmpty() } }
                ?.let { "강조한 산책: ${walkDiaryTitle(it.summary, it.title)}" }
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
                        onMapTap = { point -> actionPinState.browsing.value = false; actionPinState.clearInspection(); onMapTap(point) },
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
        if (actionPinState.browsing.value) "액션 기록 ${pinRecords.size}건" else "관련 산책 ${relatedRecords.size}회", modifier,
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
                    actionPinState.allRecords(); setExpanded(true)
                    pinScope.launch { actionPinState.listState.scrollToItem(0) }
                }
            }
        },
        map = map,
        summary = {
            if (behaviorCount != null) Text(behaviorCount, Modifier.padding(horizontal = 18.dp)
                .testTag("records-behavior-count"), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (actionPinState.browsing.value) Text(
                    if (pinGroup != null) (if (pinRecords.mapNotNull { it.point }.distinct().size > 1) "이 구간의 액션 ${pinRecords.size}건" else "이 위치의 액션 ${pinRecords.size}건")
                    else "핀 표시 ${pins.visibleCount}건 · 위치 없음 ${pins.unlocatedCount}건",
                    Modifier.weight(1f).testTag("records-pins-summary"), style = MaterialTheme.typography.labelSmall)
                else Text(if (visibleCount == null) "지도 흔적을 준비하고 있어요."
                    else if (overlapOnly) "선택 산책 ${selection.records.size}회 · 겹침 표시 ${visibleCount}회"
                    else "선택 산책 ${selection.records.size}회 · 표시 흔적 ${visibleCount}개",
                    Modifier.weight(1f).testTag("records-map-count"), style = MaterialTheme.typography.labelMedium)
                if (actionPinState.browsing.value) TextButton(onClick = {
                    actionPinState.browsing.value = false; actionPinState.clearInspection(); pinCenter = null
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

        },
        details = {
            if (actionPinState.browsing.value && pinGroup != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pinGroup != null && pinRecords.mapNotNull { it.point }.distinct().size > 1 && (camera?.zoom ?: 16.0) < 21.0) {
                        TextButton(onClick = {
                            val points = pinRecords.mapNotNull { it.point }
                            pinCenter = GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average())
                            pinZoom = ((camera?.zoom ?: 16.0) + 1).coerceAtMost(21.0)
                            pinCameraRequest++
                            setExpanded(false)
                        }, Modifier.testTag("records-pins-expand")) { Text("구간 확대") }
                    }
                    if (pinGroup != null) TextButton(onClick = actionPinState::allRecords, Modifier.testTag("records-pins-all")) { Text("모든 액션") }
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
                if (pinRecords.isEmpty()) RecordsMessage("표시할 액션 기록이 없어요.", modifier = listModifier)
                else BehaviorRecordList(pinRecords, pets, actionPinState.selectedKey.value, hiddenIds,
                    displayableWalkIds,
                    { key -> pinRecords.firstOrNull { it.key == key }?.let(choosePin) }, onToggleHidden,
                    onOpen, listModifier, actionPinState.listState, onOpenAction = onOpenAction, readingSource = routeSource)
            } else WalkRecordsMapList(relatedRecords, pets, selectedId, hiddenIds, displayableWalkIds,
                { setExpanded(true); onSelect(it) }, onToggleHidden, onOpen, listModifier, listState)
        })
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
                    Text(walkDiaryTitle(record.summary, record.title), Modifier.padding(top = 16.dp),
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
