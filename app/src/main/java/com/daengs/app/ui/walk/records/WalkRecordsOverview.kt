package com.daengs.app.ui.walk.records

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
import kotlinx.coroutines.ensureActive

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
) {
    val selected = selection.records.firstOrNull { it.summary.sessionId == selectedId }
    val highlighted = selected?.takeUnless { it.summary.sessionId in hiddenIds }
    var routeRetry by remember(highlighted, routeSource) { mutableIntStateOf(0) }
    var routeSummary by remember(highlighted, routeSource, routeRetry) {
        mutableStateOf(highlighted?.summary.takeIf { routeSource == null })
    }
    var routeError by remember(highlighted, routeSource, routeRetry) { mutableStateOf<String?>(null) }
    LaunchedEffect(highlighted, routeSource, routeRetry) {
        val record = highlighted ?: return@LaunchedEffect
        val source = routeSource ?: return@LaunchedEffect
        try {
            val loaded = source.loadRoute(record)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            check(loaded.sessionId == record.summary.sessionId) { "선택한 산책과 동선이 달라요." }
            routeSummary = loaded
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            routeError = "산책 동선을 불러오지 못했어요."
        }
    }
    val selectedRoute = remember(routeSummary) {
        routeSummary?.toSessionRoute()?.toCompletedRouteLayerState()?.let { layer ->
            layer.copy(start = layer.start?.copy(compact = true), end = layer.end?.copy(compact = true))
        } ?: CompletedRouteLayerState()
    }
    val route = selectedRoute.copy(selectedPoint = overlapHit?.takeIf { hit ->
        tiles != null && error == null && hit.walkIds.any { it !in hiddenIds }
    }?.point)
    val hiddenCount = prepared?.availableWalkIds?.count { it in hiddenIds } ?: 0
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
    LaunchedEffect(overlapHit?.point) { if (overlapHit != null) setExpanded(true) }
    val map: @Composable (Modifier) -> Unit = { mapModifier ->
        val mapInsets = LocalRecordsMapInsets.current
        Box(mapModifier.background(CreamBg).testTag("records-overview-map").semantics {
            stateDescription = highlighted?.takeIf { route.paths.any { it.isNotEmpty() } }
                ?.let { "강조한 산책: ${walkDiaryTitle(it.summary, it.title)}" }
                ?: "강조한 산책 없음"
        }) {
            when {
                prepared == null && error != null -> RecordsMessage(error, "다시 시도", onRetry, Modifier.fillMaxSize())
                prepared == null -> RecordsMessage("산책 흔적을 준비하고 있어요.", modifier = Modifier.fillMaxSize())
                prepared.bounds.isEmpty() -> RecordsMessage("지도에 표시할 위치가 없어요.", modifier = Modifier.fillMaxSize())
                else -> {
                    // Keep the map mounted even when every trace is hidden or composition is pending.
                    MapHost(scene = MapScene(traceTiles = tiles.orEmpty(), completedRoute = route,
                        allowRegionalOverview = true), searchOrigin = null, followDevice = false,
                        fitBounds = fitBounds, cameraRequestKey = cameraRequest,
                        topPaddingPx = mapInsets.top, bottomPaddingPx = mapInsets.bottom,
                        initialCamera = camera, onCameraSnapshot = onCamera,
                        onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                        onMapTap = onMapTap,
                        modifier = Modifier.fillMaxSize())
                    val message = when {
                        routeError != null -> routeError
                        highlighted != null && routeSummary == null -> "산책 동선을 불러오고 있어요."
                        error != null -> error
                        tiles == null -> "흔적 표시를 바꾸고 있어요."
                        traceLoading && visibleCount == 0 -> "흔적을 불러오는 동안 산책 카드를 살펴보세요."
                        overlapOnly && !prepared.hasOverlap(minimumWalks) ->
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
                            if (routeError != null) TextButton(onClick = { routeRetry++ }) { Text("다시 시도") }
                            else if (error != null) TextButton(onClick = onRetry) { Text("다시 시도") }
                        }
                    }
                }
            }
        }
    }

    WalkRecordsMapFrame(sheetExpanded, setExpanded, "관련 산책 ${relatedRecords.size}회", modifier,
        controls = { if (controls != null) controls() else
            WalkRecordsTraceControls(overlapOnly, minimumWalks, onOverlapOnly, onMinimumWalks) },
        map = map,
        summary = {
            if (behaviorCount != null) Text(behaviorCount, Modifier.padding(horizontal = 18.dp)
                .testTag("records-behavior-count"), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (visibleCount == null) "지도 흔적을 준비하고 있어요."
                    else if (overlapOnly) "선택 산책 ${selection.records.size}회 · 겹침 표시 ${visibleCount}회"
                    else "선택 산책 ${selection.records.size}회 · 표시 흔적 ${visibleCount}개",
                    Modifier.weight(1f).testTag("records-map-count"), style = MaterialTheme.typography.labelMedium)
                WalkRecordsTraceStatus(selection.records, traceLoading, traceError, onReloadTraces, compact = true)
                if (hiddenCount > 0) {
                    TextButton(onClick = onRestoreAll, modifier = Modifier.testTag("records-map-restore-all")) {
                        Text("모두 표시")
                    }
                }
            }

        },
        details = {
            // The expanded list covers the map's center; keep result/error feedback reachable here too.
            if (error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(error, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onRetry) { Text("다시 시도") }
            } else if (overlapOnly && prepared != null && !prepared.hasOverlap(minimumWalks)) {
                Text((if (partialTraces) "불러온 흔적에는 " else "") + "${minimumWalks}회 이상 겹친 구간이 없어요.",
                    Modifier.padding(horizontal = 18.dp, vertical = 6.dp).testTag("records-overlap-empty"),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            if (overlapOnly && partialTraces) Text("불러온 흔적 기준 · 아직 준비되지 않은 산책은 겹침에 포함되지 않아요.",
                Modifier.padding(horizontal = 18.dp).testTag("records-overlap-partial"),
                style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (prepared != null) {
                val missing = selection.records.size - prepared.availableWalkIds.size
                if ((!remoteTraces && missing > 0) || hiddenCount > 0) Text(
                    listOfNotNull("숨김 ${hiddenCount}회".takeIf { hiddenCount > 0 },
                        "흔적 없음 ${missing}회".takeIf { !remoteTraces && missing > 0 }).joinToString(" · ") + " · 목록에는 모두 남아 있어요.",
                    Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp).testTag("records-map-status"),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            // The map frame stays mounted at a fixed size; only meaningful inspection results appear.
            if (selected != null || overlapHit != null || overlapMiss) Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(when {
                    overlapHit != null -> "이 구간: ${selection.records.size}회 중 ${overlapHit.walkIds.size}회 겹침\n아래에서 관련 산책을 살펴보세요."
                    overlapMiss -> "이곳에는 ${minimumWalks}회 이상 겹친 흔적이 없어요."
                    selected?.summary?.sessionId in hiddenIds -> "고른 산책은 지도에서 숨김"
                    route.paths.none { it.isNotEmpty() } -> "이 산책에는 강조할 경로가 없어요."
                    else -> "고른 산책 경로"
                }, Modifier.weight(1f).testTag("records-inspection-summary"), style = MaterialTheme.typography.labelSmall)
                if (overlapHit != null) TextButton(onClick = onClearOverlap, modifier = Modifier.testTag("records-overlap-clear")) {
                    Text("전체 목록")
                } else if (selected != null) TextButton(onClick = onClearSelection, modifier = Modifier.testTag("records-map-clear-selection")) {
                    Text("강조 해제")
                }
            }

        },
        records = { listModifier ->
            WalkRecordsMapList(relatedRecords, pets, selectedId, hiddenIds, prepared?.availableWalkIds,
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
