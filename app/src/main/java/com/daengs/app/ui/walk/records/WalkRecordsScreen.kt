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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.traces.TraceBrush
import com.daengs.app.map.layers.traces.TraceBrushPolicy
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.daengs.app.map.layers.traces.WalkTraceMask
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.HistoryFilterSaver
import com.daengs.app.ui.walk.WalkHistoryPageContent
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.walk.WalkHistoryFilter
import com.daengs.app.walk.diary.SpatialDiaryHexGrid
import com.daengs.app.walk.records.WalkRecord
import com.daengs.app.walk.records.WalkRecordsQuery
import com.daengs.app.walk.records.WalkRecordsSelection
import com.daengs.app.walk.records.WalkRecordsSource
import com.daengs.app.walk.records.selectWalkRecords
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
    var conditionsOpen by rememberSaveable { mutableStateOf(false) }
    var pageIndex by rememberSaveable(query) { mutableIntStateOf(0) }
    var camera by rememberSaveable(query, stateSaver = CameraSnapshotSaver) { mutableStateOf<MapCameraSnapshot?>(null) }
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

    // Opening the other view never selects records again or changes the current list page.
    var mapRequested by remember(query) { mutableStateOf(false) }
    LaunchedEffect(view) { if (view == RecordsView.OVERVIEW) mapRequested = true }
    val shouldPrepareMap = mapRequested || view == RecordsView.OVERVIEW
    var mapResult by remember(selection) { mutableStateOf<RecordsMapResult?>(null) }
    var mapError by remember(selection) { mutableStateOf<String?>(null) }
    var mapRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(selection, shouldPrepareMap, mapRetry) {
        val selected = selection ?: return@LaunchedEffect
        if (!shouldPrepareMap || selected.records.isEmpty()) return@LaunchedEffect
        mapResult = null
        mapError = null
        try {
            mapResult = withContext(Dispatchers.Default) { prepareTraces(selected) }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mapError = "선택한 산책의 흔적을 표시하지 못했어요. 기간이나 조건을 좁혀 다시 확인해 주세요."
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
            onOpenConditions = { conditionsOpen = true },
            onReset = { dogId = null; filter = WalkHistoryFilter() })
        TabRow(selectedTabIndex = view.ordinal) {
            Tab(selected = view == RecordsView.WALKS, onClick = { view = RecordsView.WALKS },
                text = { Text("산책별") }, modifier = Modifier.testTag("records-view-walks"))
            Tab(selected = view == RecordsView.OVERVIEW, onClick = { view = RecordsView.OVERVIEW },
                text = { Text("모아보기") }, modifier = Modifier.testTag("records-view-overview"))
        }
        val current = selection
        if (current != null) {
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
                } else {
                    WalkRecordsOverview(current.records.size, mapResult, mapError,
                        onRetry = { mapRetry++ }, camera = camera, onCamera = { camera = it },
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (conditionsOpen) {
        WalkRecordsConditionsSheet(query, pets, today,
            onApply = { next -> dogId = next.dogId; filter = next.filter; conditionsOpen = false },
            onDismiss = { conditionsOpen = false })
    }
}

@Composable
private fun WalkRecordsOverview(
    selectedCount: Int,
    result: RecordsMapResult?,
    error: String?,
    onRetry: () -> Unit,
    camera: MapCameraSnapshot?,
    onCamera: (MapCameraSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        when {
            error != null -> RecordsMessage(error, "다시 시도", onRetry, Modifier.weight(1f))
            result == null -> RecordsMessage("산책 흔적을 준비하고 있어요.", modifier = Modifier.weight(1f))
            else -> {
                Text("선택 산책 ${selectedCount}회 · 표시 흔적 ${result.visibleWalks}개",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("records-map-count"),
                    style = MaterialTheme.typography.bodySmall)
                if (result.visibleWalks < selectedCount) {
                    Text("흔적이 없는 산책 ${selectedCount - result.visibleWalks}회도 산책별 목록에서 볼 수 있어요.",
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                HorizontalDivider()
                if (result.tiles.isEmpty()) {
                    RecordsMessage("표시할 산책 흔적이 없어요.", modifier = Modifier.weight(1f))
                } else {
                    MapHost(scene = MapScene(traceTiles = result.tiles), searchOrigin = null, followDevice = false,
                        fitBounds = result.bounds, initialCamera = camera, onCameraSnapshot = onCamera,
                        onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {}, modifier = Modifier.weight(1f).fillMaxWidth())
                    Text("선택한 산책들이 겹친 곳은 더 진하게 보여요.",
                        Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun RecordsMessage(
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

private data class RecordsMapResult(val tiles: List<TraceRasterTile>, val visibleWalks: Int, val bounds: List<GeoPoint>)

private suspend fun prepareTraces(selection: WalkRecordsSelection): RecordsMapResult {
    val context = currentCoroutineContext()
    val sheets = selection.records.mapNotNull { it.trace }.filter { it.cells.isNotEmpty() }
    require(sheets.size <= 400)
    val masks = mutableListOf<WalkTraceMask>()
    var south = Double.POSITIVE_INFINITY
    var west = Double.POSITIVE_INFINITY
    var north = Double.NEGATIVE_INFINITY
    var east = Double.NEGATIVE_INFINITY
    var totalTiles = 0
    val policy = TraceBrushPolicy()
    sheets.forEach { sheet ->
        context.ensureActive()
        val mask = TraceBrush.mask(sheet, policy) { context.ensureActive() }
        totalTiles += mask.tiles.size
        require(totalTiles <= 1_024)
        if (mask.tiles.isNotEmpty()) {
            masks += mask
            sheet.cells.forEach { cell ->
                context.ensureActive()
                val point = SpatialDiaryHexGrid.center(cell, sheet.radiusU)
                south = minOf(south, point.latitude)
                west = minOf(west, point.longitude)
                north = maxOf(north, point.latitude)
                east = maxOf(east, point.longitude)
            }
        }
    }
    val bounds = if (masks.isEmpty()) emptyList() else listOf(GeoPoint(south, west), GeoPoint(north, east))
    return RecordsMapResult(TraceBrush.compose(masks, policy.baseAlpha) { context.ensureActive() }, masks.size, bounds)
}

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

@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
private fun WalkRecordsOverviewPreview() {
    DaengsTheme { WalkRecordsOverview(2, RecordsMapResult(emptyList(), 0, emptyList()), null, {}, null, {}, Modifier.fillMaxSize()) }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun RecordsMessagePreview() {
    DaengsTheme { RecordsMessage("조건에 맞는 산책이 없어요.", "전체 기록 보기") }
}
