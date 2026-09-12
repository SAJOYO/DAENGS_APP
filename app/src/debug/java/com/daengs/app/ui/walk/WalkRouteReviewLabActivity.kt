package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.BuildConfig
import com.daengs.app.DaengsApp
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.map.shell.*
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.flow.flowOf

/** Read-only in-memory fixtures. Never seeds, exports or modifies a user's walk database. */
class WalkRouteReviewLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val allowed = BuildConfig.APPLICATION_ID.endsWith(".routepreview") &&
            BuildConfig.API_BASE_URL.isBlank() && (application as DaengsApp).tokenStore.load() == null
        setContent { DaengsTheme {
            if (allowed) WalkRouteReviewLab() else Text("별도 동선 검토 앱에서만 여는 가상 기록이에요.", Modifier.padding(24.dp))
        } }
    }
}

@Composable
private fun WalkRouteReviewLab() {
    val fixture = remember { routeReviewFixture() }
    WalkRouteReviewContent(fixture.first, fixture.second, "가상 기록 · 왕복 / 공백 / 먼 재개 / 경로 밖 사진")
}

@Composable
internal fun WalkRouteReviewContent(detail: WalkSessionDetail, scenes: List<DiaryScene>, label: String,
    onBack: () -> Unit = {},
    renderMap: (@Composable (DiaryReviewMap) -> Unit)? = null,
) {
    val explorer = rememberWalkRouteExplorer(detail.summary.sessionId, null)
    val inspecting = LocalInspectionMode.current
    var readView by remember(detail.summary.sessionId) { mutableStateOf<WalkDiaryReadView?>(
        if (inspecting) WalkDiaryReadView(PreparedDiaryRoute(detail), DiaryWalk(detail.summary, scenes, "")) else null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(detail, scenes) {
        walkDiaryReadUpdates(flowOf(Unit), load = { detail }, observe = { flowOf(DiaryWalk(it.summary, scenes, "")) })
            .collect { update -> Snapshot.withMutableSnapshot {
                when (update) {
                    is DiaryReadUpdate.Ready -> { explorer.adopt(update.view); readView = update.view; error = null }
                    DiaryReadUpdate.Missing -> readView = null
                    is DiaryReadUpdate.Failed -> error = update.message
                }
            } }
    }
    val navigation = rememberSaveable(detail.summary.sessionId, saver = DiaryMapNavigation.Saver) { DiaryMapNavigation() }
    var visibility by remember { mutableStateOf<MapVisibilityResult?>(null) }
    var directionCount by remember { mutableIntStateOf(0) }
    val ready = readView
    if (ready == null) { Text(error ?: "기록을 불러오는 중이에요.", Modifier.padding(24.dp)); return }
    val currentDetail = ready.route.detail
    val currentScenes = ready.diary?.scenes.orEmpty()
    val selected = currentScenes.firstOrNull { it.id == explorer.selectedSceneId }
    val focus = ready.sceneFocus[selected?.id]
    val presentation = recordPresentationLayer(explorer, currentDetail, focus)
    val walkingBounds = remember(ready.route, currentScenes) { diaryWalkingBounds(currentDetail, ready.route.review, currentScenes) }
    val wholeBounds = remember(ready.route, currentScenes) { diaryWholeRecordBounds(currentDetail, ready.route.review, currentScenes) }
    val targets = remember(currentScenes) { diaryVisibilityTargets(currentScenes) }
    val offscreen = diaryOffscreenScenes(currentScenes, visibility?.takeIf {
        it.query.revisionKey == ready.revisionKey && it.query.targets == targets })
    LaunchedEffect(walkingBounds) { navigation.initialize(walkingBounds) }
    val camera = navigation.camera
    val scene = diaryDisplayScene(composeMapScene(MapPurpose.WALK, MapSceneSources(
        completedRoute = currentDetail.route.toCompletedRouteLayerState(), moments = diarySceneMarkers(currentScenes, selected?.id),
    ))).copy(sessionExplorer = presentation)
    fun overview() { explorer.overview(); navigation.fit(wholeBounds, DiaryMapView.WHOLE) }
    fun selectScene(value: DiaryScene, fromMap: Boolean = false) {
        explorer.selectScene(value.id, fromMap)
        navigation.locate(value.point, fromMap, minZoom = SCENE_ROUTE_MIN_ZOOM.takeIf {
            ready.sceneFocus[value.id]?.let { it.paths.isNotEmpty() || it.observedParts.isNotEmpty() } == true })
    }
    fun selectContext(value: com.daengs.app.walk.trajectory.RecordContext, fromMap: Boolean = false) {
        explorer.selectContext(value.id, openExplorer = value.kind != com.daengs.app.walk.trajectory.RecordContextKind.GAP,
            fromMap = fromMap)
        if (!fromMap) navigation.fit(value.locations,
            expandedContext = value.kind != com.daengs.app.walk.trajectory.RecordContextKind.GAP)
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text("$label · 방향 $directionCount", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        WalkDiaryMapContent(currentScenes, selected, ready.scenesLoading, error,
            onSelect = { selectScene(it) }, onClose = explorer::closeScene,
            selectionFromMap = explorer.selectionFromMap,
            selectionPending = explorer.selectedSceneId != null && ready.scenesLoading,
            mapView = navigation.view, offscreenScenes = offscreen,
            onWalkingOverview = { explorer.overview(); navigation.fit(walkingBounds, DiaryMapView.WALKING) },
            onEdit = {}, onPhoto = {}, onRetry = {}, onAdd = {}, title = "동선과 장면 함께 보기",
            onBack = onBack,
            subtitle = formatWalkDay(currentDetail.summary.startedAtMillis), summaryContent = {
                WalkSessionSummary(currentDetail.summary)
                ObservedRouteLegend(presentation.observedParts.map { it.role })
            },
            explorerSelected = explorer.panelOpen,
            onChooseExplorer = { explorer.overview(); explorer.choosePanel(it) },
            explorerPanel = { WalkRouteExplorerPanel(explorer, ::overview, onSection = {
                navigation.fit(it.path)
            }, onAuxiliary = { navigation.fit(it.path) }, onContext = { selectContext(it) }) },
            onOverview = ::overview, selectedRouteNotice = focus?.let(::sceneRouteNotice),
            explorerFocusId = explorer.selectedContext?.id,
            onContextDismiss = { if (explorer.selectedContext != null) explorer.overview() },
            gapContexts = explorer.review?.context?.contexts.orEmpty(),
            selectedGap = explorer.selectedContext?.takeIf { it.kind == com.daengs.app.walk.trajectory.RecordContextKind.GAP },
            onSelectGap = { selectContext(it) },
            map = { viewport ->
                val query = MapVisibilityQuery(ready.revisionKey, targets, viewport.bottomOcclusionPx,
                    viewport.controlsWidthPx, viewport.controlsHeightPx, viewport.settingsCoverPx)
                val latestQuery by rememberUpdatedState(query)
                val input = DiaryReviewMap(scene, camera, viewport, query,
                    onScene = { id -> currentScenes.firstOrNull { it.id == id }?.let { selectScene(it, fromMap = true) } },
                    onContext = { id -> ready.route.review.context.context(id)?.let { selectContext(it, fromMap = true) } },
                    onGesture = navigation::gesture, onVisibility = { if (it.query == latestQuery) visibility = it })
                if (renderMap != null) renderMap(input)
                else if (LocalInspectionMode.current) Box(Modifier.fillMaxSize().background(PinkFaint))
                else MapHost(scene, null, false, fitBounds = camera.bounds, centerOn = camera.center,
                    onRouteDirectionCount = { directionCount = it },
                    centerZoom = camera.zoom, centerMinZoom = camera.minZoom,
                    cameraRequestKey = camera.revision, keepSelectionVisible = true,
                    bottomPaddingPx = if (camera.expandedContext)
                        viewport.contextBottomPaddingPx else viewport.bottomPaddingPx,
                    centerYFraction = viewport.selectionYFraction,
                    onCameraIdle = {}, onCameraGesture = input.onGesture, onSelectPlace = {},
                    visibilityQuery = query, onVisibility = input.onVisibility,
                    onSelectMoment = input.onScene, onSelectRecordContext = input.onContext, onSelectRouteEndpoint = input.onContext,
                    onMapTap = { if (explorer.panelOpen) explorer.inspect(it) }, modifier = Modifier.fillMaxSize())
            })
    }
}

/** The review screen exposes its real map commands for UI tests without loading the native SDK. */
internal data class DiaryReviewMap(val scene: MapScene, val camera: DiaryCameraRequest,
    val viewport: DiaryMapViewport, val query: MapVisibilityQuery,
    val onScene: (String) -> Unit, val onContext: (String) -> Unit,
    val onGesture: () -> Unit, val onVisibility: (MapVisibilityResult) -> Unit)

private fun routeReviewFixture(): Pair<WalkSessionDetail, List<DiaryScene>> {
    val start = java.time.Instant.parse("2026-09-11T08:00:00Z").toEpochMilli()
    fun point(x: Double) = GeoPoint(37.5445, 127.0377 + x / 88_170)
    val out = (0..40).map { -80.0 + it * 4 }
    val xs = listOf(out + out.asReversed().drop(1), (0..20).map { 1_200.0 + it * 4 })
    val segments = xs.mapIndexed { chain, coordinates -> coordinates.mapIndexed { i, x ->
        LocationSample(point(x), start + (if (chain == 0) 20_000 else 400_000) + i * 2_000, accuracyMeters = 3f)
    } }
    var seq = 0
    val fixes = segments.flatMapIndexed { chain, samples -> samples.map {
        RecordedFix(seq++, chain, it.capturedAtMillis, it.point.latitude, it.point.longitude, 3f, false)
    } }
    val summary = WalkSummary("route-review-fixture", emptyList(), start, start + 500_000,
        null, 400.0, 500_000, segments, point(-80.0),
        activeElapsedAtMillis = fixes.associate { it.atMillis to it.atMillis - start })
    val detail = WalkSessionDetail(summary, summary.toSessionRoute(), emptyList(), observations = fixes)
    fun scene(id: String, seconds: Long, title: String, x: Double?) = DiaryScene(id, summary.sessionId,
        start + seconds * 1_000, title, "화면 동작을 확인하기 위한 가상 장면이에요.", x?.let(::point), "")
    return detail to listOf(scene("out", 60, "갈 때 벤치", 0.0), scene("return", 140, "돌아올 때 벤치", 0.0),
        scene("gap", 300, "위치 없는 공백 메모", null), scene("restart", 420, "멀리서 다시 걸은 구간", 1_240.0),
        scene("tail", 490, "동선이 끝난 뒤 사진", 1_500.0))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun WalkRouteReviewLabPreview() { DaengsTheme { WalkRouteReviewLab() } }
