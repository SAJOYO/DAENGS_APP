package com.daengs.app.ui.walk.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.records.*
import com.daengs.app.map.features.records.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class BehaviorRecordsView { RECORD_LOCATIONS, WALK_TRACES, WALK_OVERLAP }

/** Screen-owned state survives when this result's native map is unmounted. */
@Composable
internal fun WalkRecordsBehaviorExplorer(
    result: WalkRecordBehaviors,
    pets: List<Pet>,
    onOpen: (String) -> Unit,
    view: BehaviorRecordsView,
    onView: (BehaviorRecordsView) -> Unit,
    modifier: Modifier = Modifier,
    state: WalkRecordsBehaviorState = rememberWalkRecordsBehaviorState(),
    routeSource: WalkRecordsSource? = null,
    actionPinState: WalkRecordsActionPinState = rememberWalkRecordsActionPinState(),
    traceLoading: Boolean = false,
    traceError: String? = null,
    onReloadTraces: () -> Unit = {},
) {
    var selectedEntryKey by state.selectedEntryKey
    var hiddenWalkIds by state.hiddenWalkIds
    var camera by state.camera
    val listState = state.listState
    var expanded by state.expanded
    var selectedWalkId by state.selectedWalkId
    var minimumWalks by state.minimumWalks
    var overlapPoint by state.overlapPoint
    val overlapOnly = view == BehaviorRecordsView.WALK_OVERLAP
    var overlapMiss by remember(result, view, minimumWalks) { mutableStateOf(false) }
    val changeView: (BehaviorRecordsView) -> Unit = { next ->
        if (next != view) { overlapPoint = null; overlapMiss = false; onView(next) }
    }
    val scope = rememberCoroutineScope()
    var listScrollJob by remember { mutableStateOf<Job?>(null) }
    val relatedIds = remember(result) { result.related.sessionIds.toSet() }
    val hidden = hiddenWalkIds.intersect(relatedIds)
    val selected = result.records.firstOrNull { it.key == selectedEntryKey }
    var focusBounds by remember(result) { mutableStateOf<List<GeoPoint>?>(null) }
    var cameraRequest by remember(result) { mutableIntStateOf(0) }
    LaunchedEffect(result) {
        listScrollJob?.cancel()
        if (selected == null) selectedEntryKey = null
        if (selectedWalkId !in relatedIds) selectedWalkId = null
        hiddenWalkIds = hidden
    }

    val displayPolicy = rememberWalkRecordsDisplayPolicy()
    val traceView = when (view) {
        BehaviorRecordsView.RECORD_LOCATIONS -> TraceView.Locations
        BehaviorRecordsView.WALK_TRACES -> TraceView.All
        BehaviorRecordsView.WALK_OVERLAP -> TraceView.Overlap(minimumWalks)
    }
    val tracePresentation = rememberWalkRecordsTraces(result.related, true, traceView, hidden, displayPolicy.trace)
    val prepared = tracePresentation.prepared
    val tiles = tracePresentation.tiles
    val preparationError = tracePresentation.preparationError
    val compositionError = tracePresentation.compositionError
    var initialBounds by remember(result) { mutableStateOf<List<GeoPoint>?>(null) }
    LaunchedEffect(result, prepared) {
        val points = withContext(Dispatchers.Default) { behaviorBounds(buildList {
            result.records.forEach { currentCoroutineContext().ensureActive(); it.point?.let(::add) }
            result.related.records.forEach { currentCoroutineContext().ensureActive(); addAll(walkRecordFocusBounds(it)) }
            prepared?.bounds?.let(::addAll)
        }) }
        currentCoroutineContext().ensureActive()
        initialBounds = points
    }
    val visibleLocatedRecords = remember(result, hidden) {
        result.records.filter { it.point != null && it.walk.summary.sessionId !in hidden }
    }
    val markerGroups = remember(visibleLocatedRecords) { visibleLocatedRecords.groupBy { requireNotNull(it.point) } }
    val markers = remember(markerGroups, selectedEntryKey) {
        markerGroups.map { (point, records) ->
            val representative = records.firstOrNull { it.key == selectedEntryKey } ?: records.first()
            MomentMarkerState(representative.key, point,
                representative.entry.type.label + if (records.size > 1) " ${records.size}건" else "",
                selected = representative.key == selectedEntryKey, aboveRouteEndpoints = true,
                sequenceLabel = records.size.takeIf { it > 1 }?.toString(),
                behaviors = records.map { it.entry.type }.toSet())
        }
    }
    val hideableIds = remember(result, prepared) {
        result.records.filter { it.point != null }.mapTo(mutableSetOf()) { it.walk.summary.sessionId }
            .apply {
                addAll(result.related.records.filter { record ->
                    record.summary.segments.any { it.isNotEmpty() }
                }.map { it.summary.sessionId })
                addAll(prepared?.availableWalkIds.orEmpty())
            }.toSet()
    }
    // A pending or failed trace is not a deleted walk. Keep its visibility choice for a retry;
    // the result effect above removes IDs only when they leave the related walk selection.
    val onSelect: (String, Boolean) -> Unit = { key, scrollToRecord ->
        val record = result.records.firstOrNull { it.key == key }
        if (record != null) {
            selectedEntryKey = key
            expanded = true
            if (record.walk.summary.sessionId !in hidden) {
                val points = record.point?.let { listOf(it, it) }.orEmpty()
                if (points.isNotEmpty()) {
                    // A selection before MapHost mounts takes precedence over its saved camera.
                    if (initialBounds == null) camera = null
                    focusBounds = points
                    cameraRequest++
                }
            }
            if (scrollToRecord) {
                val index = result.records.indexOfFirst { it.key == key }
                listScrollJob?.cancel()
                listScrollJob = scope.launch { listState.scrollToItem(index) }
            }
        }
    }
    val hiddenCount = hidden.count { it in hideableIds }
    val missingLocations = result.records.count { it.point == null }
    val missingTraces = prepared?.let { result.related.records.size - it.availableWalkIds.size }
    val remoteTraces = result.related.records.any { it.traceState != null }

    val hit = remember(prepared, overlapPoint, overlapOnly, minimumWalks) {
        overlapPoint?.takeIf { overlapOnly }?.let { prepared?.hitTestOverlap(it, minimumWalks, snapRadiusU = 0.0) }
    }
    ReconcileWalkRecordsOverlapPoint(result.related, traceLoading, traceError, prepared,
        overlapPoint, hit, { overlapPoint = null })
    LaunchedEffect(hit) {
        if (hit != null) {
            expanded = true
            if (selectedWalkId !in hit.walkIds) selectedWalkId = null
            state.walkListState.scrollToItem(0)
        }
    }
    val controls: @Composable () -> Unit = {
        BehaviorViewControls(view, changeView, minimumWalks = minimumWalks, onMinimumWalks = {
            minimumWalks = it; overlapPoint = null; overlapMiss = false; selectedWalkId = null
        }, menuExtras = { if (view != BehaviorRecordsView.RECORD_LOCATIONS) WalkRecordsActionPinControls(actionPinState, result.behavior) })
    }
    if (view != BehaviorRecordsView.RECORD_LOCATIONS) {
        WalkRecordsOverview(result.related, pets, prepared, tiles, preparationError ?: compositionError,
            routeSource = routeSource, actionPinState = actionPinState, pinBehavior = result.behavior,
            onRetry = tracePresentation.retry,
            selectedId = selectedWalkId, hiddenIds = hidden,
            onSelect = { id ->
                selectedWalkId = id.takeIf { it != selectedWalkId }
                if (selectedWalkId != null && id !in hidden) {
                    val bounds = walkRecordFocusBounds(result.related.records.first { it.summary.sessionId == id })
                    if (bounds.isNotEmpty()) { focusBounds = bounds; cameraRequest++ }
                }
            },
            onToggleHidden = { id -> if (id in hideableIds) {
                hiddenWalkIds = if (id in hidden) hidden - id else hidden + id
            } }, onRestoreAll = { hiddenWalkIds = emptySet() }, onClearSelection = { selectedWalkId = null },
            onOpen = onOpen, listState = state.walkListState, camera = camera, onCamera = { camera = it },
            fitBounds = focusBounds ?: initialBounds.orEmpty(), cameraRequest = cameraRequest,
            overlapOnly = overlapOnly, minimumWalks = minimumWalks,
            overlapHit = hit, overlapMiss = overlapMiss,
            onMapTap = { point ->
                if (overlapOnly && tiles != null && preparationError == null && compositionError == null) {
                    val next = prepared?.hitTestOverlap(point, minimumWalks, hiddenIds = hidden)
                    overlapPoint = next?.point; overlapMiss = next == null; selectedWalkId = null
                }
            }, onClearOverlap = { overlapPoint = null; overlapMiss = false; selectedWalkId = null },
            traceLoading = traceLoading, traceError = traceError, onReloadTraces = onReloadTraces,
            modifier = modifier, expanded = expanded, onExpanded = { expanded = it }, controls = controls,
            behaviorCount = "행동 기록 ${result.records.size}건 · 관련 산책 ${result.related.records.size}회")
        return
    }
    val map: @Composable (Modifier) -> Unit = { mapModifier ->
        val mapInsets = LocalRecordsMapInsets.current
        Box(mapModifier.background(CreamBg).testTag("records-behavior-map").semantics {
            stateDescription = "위치 표시 ${visibleLocatedRecords.size}건 · 선택 기록 ${selected?.key ?: "없음"}"
        }) {
            when {
                initialBounds == null -> RecordsMessage("기록 지도를 준비하고 있어요.", modifier = Modifier.fillMaxSize())
                initialBounds.orEmpty().isEmpty() -> RecordsMessage(
                    preparationError ?: "지도에 표시할 위치가 없어요.",
                    if (preparationError != null) "다시 시도" else null,
                    tracePresentation.retry, Modifier.fillMaxSize())
                else -> {
                    MapHost(scene = composeWalkRecordsMapScene(displayPolicy, moments = markers).scene, searchOrigin = null, followDevice = false,
                        fitBounds = focusBounds ?: initialBounds, cameraRequestKey = cameraRequest,
                        topPaddingPx = mapInsets.top, bottomPaddingPx = mapInsets.bottom,
                        initialCamera = camera, onCameraSnapshot = { camera = it },
                        onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                        onSelectMoment = { markerId ->
                            val record = visibleLocatedRecords.firstOrNull { it.key == markerId }
                            val group = record?.point?.let(markerGroups::get).orEmpty()
                            if (group.isNotEmpty()) {
                                val selectedIndex = group.indexOfFirst { it.key == selectedEntryKey }
                                onSelect(group[(selectedIndex + 1) % group.size].key, true)
                            }
                        }, modifier = Modifier.fillMaxSize())
                    val message = when {
                        markers.isNotEmpty() -> null
                        hiddenCount > 0 -> "표시할 행동 위치가 없어요. 숨긴 산책은 다시 표시할 수 있어요."
                        else -> "위치 없는 기록은 아래 목록에서 볼 수 있어요."
                    }
                    if (message != null) Surface(Modifier.align(Alignment.Center).padding(18.dp),
                        color = CreamBg.copy(alpha = .95f)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    WalkRecordsMapFrame(expanded, { expanded = it }, "행동 기록 ${result.records.size}건", modifier,
        controls = controls, map = map,
        summary = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("행동 기록 ${result.records.size}건 · 관련 산책 ${result.related.records.size}회",
                        Modifier.testTag("records-behavior-count"), style = MaterialTheme.typography.labelMedium)
                    Text("위치 있는 기록 ${result.records.size - missingLocations}건 · 표시 ${visibleLocatedRecords.size}건",
                        Modifier.testTag("records-behavior-display-count"),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                if (hiddenCount > 0) TextButton(onClick = { hiddenWalkIds = emptySet() },
                    modifier = Modifier.testTag("records-behavior-restore-all")) { Text("모두 표시") }
            }

        },
        details = {
            WalkRecordsTraceStatus(result.related.records, traceLoading, traceError, onReloadTraces)
            val omissions = listOfNotNull(
                "위치 없음 ${missingLocations}건".takeIf { missingLocations > 0 },
                missingTraces?.takeIf { !remoteTraces && it > 0 }?.let { "흔적 없음 ${it}회" },
                "산책 숨김 ${hiddenCount}회".takeIf { hiddenCount > 0 },
            ).joinToString(" · ")
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 20.dp)) {
                if (omissions.isNotEmpty()) Text(omissions + " · 목록 유지",
                    Modifier.testTag("records-behavior-status"), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }

            if (selected != null) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(selected.locationLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { selectedEntryKey = null },
                    modifier = Modifier.testTag("records-behavior-clear-selection")) { Text("강조 해제") }
            }
        },
        records = { listModifier ->
            if (result.records.isEmpty()) RecordsMessage("이 조건의 산책에 ${result.behavior.label} 기록이 없어요.", modifier = listModifier)
            else BehaviorRecordList(result.records, pets, selectedEntryKey, hidden, hideableIds,
                { onSelect(it, false) }, { id ->
                    if (id in hideableIds) hiddenWalkIds = if (id in hidden) hidden - id else hidden + id
                }, onOpen, listModifier, listState, availabilityChecked = initialBounds != null)
        })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BehaviorViewControls(
    view: BehaviorRecordsView, onView: (BehaviorRecordsView) -> Unit, modifier: Modifier = Modifier,
    minimumWalks: Int = 2, onMinimumWalks: (Int) -> Unit = {},
    menuExtras: @Composable () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("records-map-display")) {
            Text(when (view) {
                BehaviorRecordsView.RECORD_LOCATIONS -> "행동 위치 ▾"
                BehaviorRecordsView.WALK_TRACES -> "전체 흔적 ▾"
                BehaviorRecordsView.WALK_OVERLAP -> "겹친 구간 · ${minimumWalks}회 이상 ▾"
            })
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("지도 표시", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
            listOf(Triple(BehaviorRecordsView.WALK_TRACES, "전체 흔적", "traces"),
                Triple(BehaviorRecordsView.WALK_OVERLAP, "겹친 구간", "overlap"),
                Triple(BehaviorRecordsView.RECORD_LOCATIONS, "행동 위치", "locations")).forEach { (mode, label, tag) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onView(mode); if (mode != BehaviorRecordsView.WALK_OVERLAP) open = false },
                    modifier = Modifier.testTag("records-behavior-view-$tag"))
            }
            if (view == BehaviorRecordsView.WALK_OVERLAP) Box(Modifier.padding(horizontal = 16.dp)) {
                WalkRecordsOverlapOptions(minimumWalks, { onMinimumWalks(it); open = false })
            }
            menuExtras()
        }
    }
}

private fun behaviorBounds(points: List<GeoPoint>): List<GeoPoint> {
    if (points.isEmpty()) return emptyList()
    var south = points.minOf { it.latitude }; var north = points.maxOf { it.latitude }
    var west = points.minOf { it.longitude }; var east = points.maxOf { it.longitude }
    if (south == north && west != east) { south -= .00001; north += .00001 }
    if (west == east && south != north) { west -= .00001; east += .00001 }
    return listOf(GeoPoint(south.coerceAtLeast(-90.0), west.coerceAtLeast(-180.0)),
        GeoPoint(north.coerceAtMost(90.0), east.coerceAtMost(180.0)))
}

internal fun previewRecordBehaviors(): WalkRecordBehaviors {
    val summary = previewDiarySummary()
    val entries = listOf(
        WalkEntry(id = "preview-located", sessionId = summary.sessionId, type = WalkMomentType.SNIFFING,
            recordedAtMillis = summary.startedAtMillis + 60_000, point = summary.segments.first().first().point,
            locationCapturedAtMillis = summary.startedAtMillis + 60_000),
        WalkEntry(id = "preview-unlocated", sessionId = summary.sessionId, type = WalkMomentType.SNIFFING,
            recordedAtMillis = summary.startedAtMillis + 120_000),
    )
    return selectWalkRecordBehaviors(WalkRecordsSelection(WalkRecordsQuery(),
        listOf(WalkRecord(summary, "숲길에서 남긴 기록", entries = entries))), WalkMomentType.SNIFFING)
}

@Preview(name = "행동 기록 위치", showBackground = true, widthDp = 390, heightDp = 580)
@Composable
private fun WalkRecordsBehaviorExplorerPreview() {
    DaengsTheme { WalkRecordsBehaviorExplorer(previewRecordBehaviors(), emptyList(), {},
        BehaviorRecordsView.RECORD_LOCATIONS, {}, Modifier.fillMaxSize()) }
}

@Preview(name = "관련 산책 흔적", showBackground = true, widthDp = 700, heightDp = 480)
@Composable
private fun WalkRecordBehaviorTracesPreview() {
    DaengsTheme { WalkRecordsBehaviorExplorer(previewRecordBehaviors(), emptyList(), {},
        BehaviorRecordsView.WALK_TRACES, {}, Modifier.fillMaxSize()) }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun BehaviorViewControlsPreview() {
    DaengsTheme { BehaviorViewControls(BehaviorRecordsView.RECORD_LOCATIONS, {}, Modifier.padding(horizontal = 18.dp)) }
}
