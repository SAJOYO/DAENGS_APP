package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
) {
    val explorer = rememberWalkRouteExplorer(detail.summary.sessionId, detail)
    val selected = scenes.firstOrNull { it.id == explorer.selectedSceneId }
    val focus = remember(selected, explorer.review) { selected?.let { explorer.review?.recordSceneFocus(it) } }
    val presentation = recordPresentationLayer(explorer, detail, focus)
    val paths = presentation.emphasisPaths
    var bounds by remember { mutableStateOf(detail.route.bounds) }
    var center by remember { mutableStateOf<GeoPoint?>(null) }
    var request by remember { mutableIntStateOf(0) }
    var cameraForContext by remember { mutableStateOf(false) }
    var directionCount by remember { mutableIntStateOf(0) }
    val scene = diaryDisplayScene(composeMapScene(MapPurpose.WALK, MapSceneSources(
        completedRoute = detail.route.toCompletedRouteLayerState(), moments = diarySceneMarkers(scenes, selected?.id),
    ))).copy(sessionExplorer = presentation)
    fun overview() { explorer.overview(); center = null; bounds = detail.route.bounds; cameraForContext = false; request++ }
    fun selectScene(value: DiaryScene, fromMap: Boolean = false) {
        explorer.selectScene(value.id, fromMap)
        if (!fromMap) value.point?.let { center = it; cameraForContext = false; request++ }
    }
    fun selectContext(value: com.daengs.app.walk.trajectory.RecordContext, fromMap: Boolean = false) {
        explorer.selectContext(value.id, openExplorer = value.kind != com.daengs.app.walk.trajectory.RecordContextKind.GAP,
            fromMap = fromMap)
        if (!fromMap) {
            center = null
            cameraForContext = value.kind != com.daengs.app.walk.trajectory.RecordContextKind.GAP
            value.locations.takeIf { it.isNotEmpty() }?.let { bounds = it }; request++
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text("$label · 방향 $directionCount", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        WalkDiaryMapContent(scenes, selected, false, null,
            onSelect = { selectScene(it) }, onClose = explorer::closeScene,
            selectionFromMap = explorer.selectionFromMap,
            onEdit = {}, onPhoto = {}, onRetry = {}, onAdd = {}, title = "동선과 장면 함께 보기",
            onBack = onBack,
            subtitle = formatWalkDay(detail.summary.startedAtMillis), summaryContent = {
                WalkSessionSummary(detail.summary)
                ObservedRouteLegend(presentation.observedParts.map { it.role })
            },
            explorerSelected = explorer.panelOpen,
            onChooseExplorer = { explorer.overview(); explorer.choosePanel(it) },
            explorerPanel = { WalkRouteExplorerPanel(explorer, ::overview, onSection = {
                center = null; bounds = it.path; cameraForContext = false; request++
            }, onAuxiliary = { center = null; bounds = it.path; cameraForContext = false; request++ }, onContext = { selectContext(it) }) },
            onOverview = ::overview, selectedRouteNotice = focus?.let(::sceneRouteNotice),
            explorerFocusId = explorer.selectedContext?.id,
            onContextDismiss = { if (explorer.selectedContext != null) explorer.overview() },
            gapContexts = explorer.review?.context?.contexts.orEmpty(),
            selectedGap = explorer.selectedContext?.takeIf { it.kind == com.daengs.app.walk.trajectory.RecordContextKind.GAP },
            onSelectGap = { selectContext(it) },
            map = { viewport ->
                if (LocalInspectionMode.current) Box(Modifier.fillMaxSize().background(PinkFaint))
                else MapHost(scene, null, false, fitBounds = bounds, centerOn = center,
                    onRouteDirectionCount = { directionCount = it },
                    centerMinZoom = if (selected != null && paths.isNotEmpty()) SCENE_ROUTE_MIN_ZOOM else null,
                    cameraRequestKey = request, keepSelectionVisible = true,
                    bottomPaddingPx = if (cameraForContext)
                        viewport.contextBottomPaddingPx else viewport.bottomPaddingPx,
                    centerYFraction = viewport.selectionYFraction,
                    onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                    onSelectMoment = { id -> scenes.firstOrNull { it.id == id }?.let { selectScene(it, fromMap = true) } },
                    onSelectRecordContext = { id -> explorer.review?.context?.context(id)?.let { selectContext(it, fromMap = true) } },
                    onSelectRouteEndpoint = { id -> explorer.review?.context?.context(id)?.let { selectContext(it, fromMap = true) } },
                    onMapTap = { if (explorer.panelOpen) explorer.inspect(it) }, modifier = Modifier.fillMaxSize())
            })
    }
}

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
