package com.daengs.app.ui.walk.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
) {
    val selected = selection.records.firstOrNull { it.summary.sessionId == selectedId }
    val highlighted = selected?.takeUnless { it.summary.sessionId in hiddenIds }
    val selectedRoute = remember(highlighted) {
        highlighted?.summary?.toSessionRoute()?.toCompletedRouteLayerState()?.let { layer ->
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
                            if (error != null) TextButton(onClick = onRetry) { Text("다시 시도") }
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
            WalkRecordsTraceStatus(selection.records, traceLoading, traceError, onReloadTraces)
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
            // Reserve the same space before selection: camera fitting must not race a map resize.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(when {
                    overlapHit != null -> "이 구간: ${selection.records.size}회 중 ${overlapHit.walkIds.size}회 겹침\n아래에서 관련 산책을 살펴보세요."
                    overlapMiss -> "이곳에는 ${minimumWalks}회 이상 겹친 흔적이 없어요."
                    selected == null && overlapOnly -> "겹친 구간을 누르면 관련 산책을 볼 수 있어요."
                    selected == null -> "카드를 눌러 산책 경로를 살펴보세요."
                    selected.summary.sessionId in hiddenIds -> "고른 산책은 지도에서 숨김"
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
) {
    if (records.none { it.traceState != null } && error == null) return
    val counts = records.groupingBy { it.effectiveTraceState }.eachCount()
    val details = listOf(
        WalkTraceState.NOT_REQUESTED to "확인 전",
        WalkTraceState.LOADING to "불러오는 중",
        WalkTraceState.EMPTY to "흔적 없음",
        WalkTraceState.NOT_UPLOADED to "전송 확인 필요",
        WalkTraceState.ANALYSIS_PENDING to "계산 대기",
        WalkTraceState.UNSUPPORTED to "지원 안 됨",
        WalkTraceState.FAILED to "불러오기 실패",
    ).mapNotNull { (state, label) -> counts[state]?.takeIf { it > 0 }?.let { "$label ${it}회" } }
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(when {
            error != null -> error
            loading -> "흔적을 불러오고 있어요."
            details.isNotEmpty() -> details.joinToString(" · ")
            else -> "산책 흔적을 불러왔어요."
        }, Modifier.weight(1f).testTag("records-traces-status"),
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
        TextButton(onClick = onRefresh, enabled = !loading,
            modifier = Modifier.testTag("records-traces-refresh")) {
            Text(if (error != null || WalkTraceState.FAILED in counts) "다시 불러오기" else "새로고침")
        }
    }
}

internal fun traceStateLabel(state: WalkTraceState): String = when (state) {
    WalkTraceState.NOT_REQUESTED -> "흔적 확인 전"
    WalkTraceState.LOADING -> "흔적 불러오는 중"
    WalkTraceState.READY -> "흔적 준비됨"
    WalkTraceState.EMPTY -> "지도 흔적 없음"
    WalkTraceState.NOT_UPLOADED -> "산책 전송 확인이 필요해요"
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
