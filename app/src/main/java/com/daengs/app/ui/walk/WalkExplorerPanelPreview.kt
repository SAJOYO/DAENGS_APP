package com.daengs.app.ui.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*

internal enum class ExplorerPanelExample { OVERVIEW, RANGE, REPLAY, GAP, LEGACY, LOADING, NOTICES }
internal class ExplorerPanelExamples : PreviewParameterProvider<ExplorerPanelExample> {
    override val values = ExplorerPanelExample.entries.asSequence()
}

/** Explicitly synthetic source data for the actual panel. No map SDK or provider is called. */
internal fun explorerPanelPreviewRead(legacy: Boolean = false): WalkDiaryReadView {
    val fixes = listOf(0L, 15_000, 30_000, 120_000, 135_000, 150_000).mapIndexed { i, at ->
        RecordedFix(i, i / 3, at, 37.5, 127.0 + i * .0002, 1f, false,
            ingressSeq = i.toLong(), sourceEpoch = "preview-epoch", clockEpochId = "preview-clock", elapsedRealtimeNanos = at * 1_000_000,
            recordingEligible = true)
    }
    val groups = fixes.chunked(3)
    val route = WalkSessionRoute(groups.mapIndexed { index, group -> WalkRouteSegment(index, group.mapIndexed { i, f ->
        WalkRoutePoint(GeoPoint(f.lat, f.lng), f.atMillis, 1f, f.atMillis, i * 20.0, null, index, i, f.elapsedRealtimeNanos)
    }) })
    val summary = WalkSummary("panel-preview", emptyList(), 0, 180_000, null, 80.0, 180_000,
        groups.map { group -> group.map { LocationSample(GeoPoint(it.lat, it.lng), it.atMillis, it.elapsedRealtimeNanos, 1f) } }, null)
    val measurement = WalkMeasurementDetail("preview-measurement", "preview-digest", mapOf(
        "record_start" to WalkMeasurementBoundary("preview-epoch", "preview-clock", null, "start", 0, null),
        "record_end" to WalkMeasurementBoundary("preview-epoch", "preview-clock", null, "stop", 180_000, null)), emptyList(), "preview-owner",
        groups.mapIndexed { i, group -> MeasurementWalkingSection("preview-section-$i", group.map { it.measurementRef(summary.sessionId) }) },
        fixes.map { it.measurementRef(summary.sessionId) }.toSet(), recordingEpochs = listOf(
            RecordingEpoch("preview-epoch", summary.sessionId, "preview-clock", 0, 0, 0, 0,
                endedAtMillis = 180_000, endedElapsedNanos = 180_000_000_000, endKind = "stop", drained = true)))
    val prepared = PreparedDiaryRoute(WalkSessionDetail(summary, route, emptyList(), observations = fixes, measurement = measurement.takeUnless { legacy }))
    val scenes = fixes.mapIndexed { i, fix ->
        val point = GeoPoint(fix.lat, fix.lng)
        val title = listOf("산책을 시작했어요", "풀 냄새에 잠깐 멈춤", "함께 남긴 메모", "다시 걸어간 길", "잠깐 쉬었던 기억", "집 앞에서 마무리")[i]
        DiaryScene("panel-preview/$i", summary.sessionId, fix.atMillis, title, "함께 걷다가 남긴 기록이에요.", point, "",
            source = StoryboardScene("source-$i", fix.atMillis, title, "함께 걷다가 남긴 기록이에요.", "", "preview-$i",
                observation = StoryboardObservation(fix.clientSeq, fix.chainIndex, fix.atMillis, point)))
    }
    return WalkDiaryReadView(prepared, DiaryWalk(summary, scenes, ""), scenes.associate { it.id to prepared.review.recordSceneFocus(it) })
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
internal fun WalkExplorerPanelPreview(@PreviewParameter(ExplorerPanelExamples::class) example: ExplorerPanelExample) {
    val read = remember(example) { explorerPanelPreviewRead(example == ExplorerPanelExample.LEGACY) }
    val scope = rememberCoroutineScope()
    val state = remember(example) { WalkRouteExplorerState(scope, 0).apply {
        adopt(read); choosePanel(true)
        when (example) {
            ExplorerPanelExample.RANGE, ExplorerPanelExample.NOTICES -> selectTimeRange(0, 30_000)
            ExplorerPanelExample.REPLAY -> { selectTimeRange(0, 30_000); seek(15_000) }
            ExplorerPanelExample.GAP -> selectTimeRange(60_000, 90_000)
            else -> Unit
        }
    } }
    val memory = rememberDiaryReadingMemory()
    val scenes = read.diary!!.scenes
    val scene = scenes.firstOrNull { it.id == state.selectedSceneId }
    val notices = example == ExplorerPanelExample.NOTICES
    DaengsTheme { WalkDiaryMapContent(scenes, scene, false, if (notices) "장면 갱신을 마치지 못했어요." else null,
        onSelect = { state.selectScene(it.id) }, onClose = state::closeScene, onEdit = {}, onPhoto = {}, onRetry = {}, onAdd = {},
        title = "함께 걸었던 길", subtitle = "미리보기 산책", readingMemory = memory,
        summaryContent = { WalkSessionSummary(read.route.detail.summary) },
        mapView = DiaryMapView.WALKING.takeIf { notices },
        offscreenScenes = if (notices) scenes.take(1) else emptyList(), directionNotice = notices,
        generationNotice = "저장한 장면을 보여드려요.".takeIf { notices },
        onReturnToRange = if (state.returnRange != null) ({ state.returnToRange() }) else null,
        onSceneNeighborhood = if (scene != null && state.sceneNeighborhood(read, scene) != null) ({ state.selectSceneNeighborhood(read, scene) }) else null,
        explorerSelected = state.panelOpen, onChooseExplorer = state::choosePanel,
        explorerPanel = { notices -> WalkRouteExplorerPanel(state, {}, reading = memory, allScenes = scenes, readingNotices = notices,
            scenesLoading = example == ExplorerPanelExample.LOADING,
            sliceScenes = scenes.filter { s -> val at = read.focusFor(s)?.let { read.route.review.timeline?.scenePosition(it) }
                state.selectedSlice?.let { at != null && at in it.from..it.until } == true }, onScene = { state.selectScene(it.id) }) },
        map = { Box(Modifier.fillMaxSize().background(PinkFaint)) { Text("지도 영역 · 미리보기", color = TextMuted) } }) }
}
