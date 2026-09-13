package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.DiarySceneKind
import com.daengs.app.walk.routeexplorer.CompletedRouteSection
import com.daengs.app.walk.trajectory.ObservedRouteSection
import com.daengs.app.walk.trajectory.RecordContext

/** Time controls stay in the visible drawer; only its reading region scrolls. */
@Composable
internal fun WalkRouteExplorerPanel(state: WalkRouteExplorerState, onOverview: () -> Unit,
    onSection: (CompletedRouteSection) -> Unit = {}, onAuxiliary: (ObservedRouteSection) -> Unit = {},
    onContext: (RecordContext) -> Unit = {}, sliceScenes: List<DiaryScene> = emptyList(),
    onScene: (DiaryScene) -> Unit = {}, sceneKinds: Map<String, DiarySceneKind> = emptyMap(),
    reading: DiaryReadingMemory? = null, allScenes: List<DiaryScene> = sliceScenes,
    scenesLoading: Boolean = false, unknownTimeScenes: Int = 0,
) {
    val scroll = reading?.explorer ?: rememberScrollState()
    var localDetails by remember { mutableStateOf(false) }
    val details = reading?.explorerDetails ?: localDetails
    val measured = state.review?.timeline?.durationMillis != null && state.duration > 0
    val replay = state.mode == RouteExplorerMode.REPLAY
    val slice = state.selectedSlice
    val scenes = if (slice != null) sliceScenes else allScenes
    val ordinals = remember(allScenes) { allScenes.mapIndexed { i, scene -> scene.id to i + 1 }.toMap() }
    val auxiliary = state.mode in setOf(RouteExplorerMode.SECTION, RouteExplorerMode.AUXILIARY, RouteExplorerMode.CONTEXT, RouteExplorerMode.PASSAGE)
    LaunchedEffect(reading, reading?.pendingExplorerOffset) {
        reading?.pendingExplorerOffset?.let { offset ->
            scroll.scrollTo(offset); reading.pendingExplorerOffset = null
        }
    }
    Column(Modifier.fillMaxSize()) {
        WalkExplorerTimeHeader(state, onOverview)
        HorizontalDivider(color = PinkFaint)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
            .testTag("explorer-reading").padding(horizontal = 12.dp).padding(bottom = 20.dp)) {
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.preparationError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.index == null && state.preparationError == null) Text("동선 탐색을 준비하고 있어요.")
            if (state.analyzing) Text("선택한 길을 지난 시각을 확인하고 있어요.")
            if (slice != null && !state.canPlayback) Text("선택 범위에 재생할 이동 근거가 없어요.", style = MaterialTheme.typography.bodySmall)
            if (replay) {
                val frame = state.replayFrame
                Text(if (frame?.inGap != false) "이 시각에는 재생할 위치 근거가 충분하지 않아요."
                    else frame.recordedAtMillis?.let { "기록 시각 " + formatRouteExplorerClock(it) }
                        ?: "기기 시간으로 확인한 위치예요. 표시 시각은 확정하지 않아요.",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            if (auxiliary) WalkExplorerRouteDetails(state, onSection, onAuxiliary, onContext)
            Text(if (slice == null) "전체 장면 ${scenes.size}개" else "이 범위의 장면 ${scenes.size}개",
                Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (scenesLoading) Text("장면을 불러오고 있어요.", style = MaterialTheme.typography.bodySmall)
            else if (scenes.isEmpty()) Text(if (slice == null) "아직 남긴 장면이 없어요." else "이 시간 범위에 확인된 장면이 없어요.",
                style = MaterialTheme.typography.bodySmall, color = TextMuted)
            scenes.forEach { scene ->
                DiarySceneListButton(scene, sceneKinds[scene.id] ?: DiarySceneKind.GENERAL,
                    onClick = { onScene(scene) }, modifier = Modifier.fillMaxWidth(),
                    ordinal = ordinals[scene.id])
            }
            if (slice != null && unknownTimeScenes > 0) Text("시각을 확인하지 못한 장면 ${unknownTimeScenes}개는 전체 장면에서 볼 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (measured) {
                if (slice != null && !replay) TextButton(onClick = { state.seek(slice.from) }) { Text("범위 시작으로 이동") }
                Text("기록 중 경과 시간이에요. 일시정지 시간은 제외하고, 시계 경계와 경로 공백은 이어 그리지 않아요.",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                if (!auxiliary) {
                    TextButton(onClick = { if (reading != null) reading.explorerDetails = !details else localDetails = !details }) {
                        Text(if (details) "경로 정보 접기" else "경로 정보 · 구간과 전후 관계")
                    }
                    if (details) WalkExplorerRouteDetails(state, onSection, onAuxiliary, onContext)
                }
            } else {
                Text("이 산책은 시간 구간을 고를 수 있는 측정 정보가 없어요.",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                if (state.review?.context?.available == true && state.review?.context?.durationMillis == null)
                    Text("기록 시간의 순서를 확정하지 못해 자동 재생을 제공하지 않아요. 전후 관계에서 해당 범위를 열 수 있어요.",
                        style = MaterialTheme.typography.bodySmall)
                if (!auxiliary) WalkExplorerRouteDetails(state, onSection, onAuxiliary, onContext)
            }
            if (!replay) WalkSpeedLegend()
        }
    }
}

/** Existing route/observation/context/passage entry points remain under the reading region. */
@Composable
private fun WalkExplorerRouteDetails(state: WalkRouteExplorerState, onSection: (CompletedRouteSection) -> Unit,
    onAuxiliary: (ObservedRouteSection) -> Unit, onContext: (RecordContext) -> Unit) {
    val review = state.review ?: return
    Text("기록 " + formatRouteExplorerClock(review.summary.startedAtMillis) + "–" +
        (review.summary.endedAtMillis?.let(::formatRouteExplorerClock) ?: "진행 중"), style = MaterialTheme.typography.bodySmall)
    state.selectedContext?.let { RecordContextDetail(it) }
    state.selectedAuxiliary?.let { Text(observedRouteDescription(it), style = MaterialTheme.typography.bodySmall) }
    if (state.mode == RouteExplorerMode.PASSAGE) {
        val result = state.passages
        if (result?.uncertain == true) Text("위치 오차나 기록 간격 때문에 통과를 확실하게 구분하기 어려워요.")
        else if (result?.passes.isNullOrEmpty()) Text("이 지점에서 길게 이어진 통과 구간을 찾지 못했어요.")
        else {
            Text("선택한 길 · ${result!!.passes.size}회 통과", style = MaterialTheme.typography.titleSmall)
            result.passes.forEachIndexed { index, pass ->
                TextButton(onClick = { state.selectPass(pass.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (pass.id == state.selectedPassId) "● " else "○ ") + "${index + 1}번째 통과 · " +
                        formatRouteExplorerClock(pass.startedAtMillis) + "–" + formatRouteExplorerClock(pass.endedAtMillis))
                }
            }
        }
    }
    review.sections.forEachIndexed { ordinal, section ->
        OutlinedButton(onClick = { state.selectSection(section.index); onSection(section) }, modifier = Modifier.fillMaxWidth()) {
            Text((if (state.selectedSection?.index == section.index) "● " else "○ ") + "동선 ${ordinal + 1} · " +
                formatRouteExplorerClock(section.startedAtMillis) + "–" + formatRouteExplorerClock(section.endedAtMillis))
        }
    }
    review.observed.sections.forEachIndexed { ordinal, section ->
        OutlinedButton(onClick = { state.selectAuxiliary(section.id); onAuxiliary(section) }, modifier = Modifier.fillMaxWidth()) {
            Text((if (state.selectedAuxiliary?.id == section.id) "● " else "○ ") + "관측 경로 ${ordinal + 1} · " + observedRouteLabel(section) + "\n" +
                formatRouteExplorerClock(section.startedAtMillis) + "–" + formatRouteExplorerClock(section.endedAtMillis))
        }
    }
    if (review.context.contexts.isNotEmpty()) {
        Text("기록의 전후 관계", style = MaterialTheme.typography.titleSmall)
        review.context.contexts.forEach { value ->
            OutlinedButton(onClick = { state.selectContext(value.id); onContext(value) }, modifier = Modifier.fillMaxWidth()) {
                Text(recordContextTitle(value) + "\n" + recordContextTime(value))
            }
        }
    }
    if (review.sections.isEmpty()) Text("이어지는 보행선이 없어요. 확인된 위치와 장면은 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
    Text("지도에서 겹친 길을 누르면 통과 시각을 골라 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
}
