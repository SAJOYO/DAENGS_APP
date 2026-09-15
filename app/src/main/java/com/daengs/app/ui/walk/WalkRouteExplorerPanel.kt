package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.reading.DiaryReadingChrome
import com.daengs.app.ui.walk.reading.DiaryReviewTheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.routeexplorer.CompletedRouteSection
import com.daengs.app.walk.trajectory.ObservedRouteSection
import com.daengs.app.walk.trajectory.RecordContext

/** Time controls stay in the visible drawer; only its reading region scrolls. */
@Composable
internal fun WalkRouteExplorerPanel(state: WalkRouteExplorerState, onOverview: () -> Unit,
    onSection: (CompletedRouteSection) -> Unit = {}, onAuxiliary: (ObservedRouteSection) -> Unit = {},
    onContext: (RecordContext) -> Unit = {}, reading: DiaryReadingMemory? = null,
    readingNotices: @Composable () -> Unit = {},
) {
    val scroll = reading?.explorer ?: rememberScrollState()
    var localDetails by remember { mutableStateOf(false) }
    val details = reading?.explorerDetails ?: localDetails
    val measured = state.review?.timeline?.durationMillis != null && state.duration > 0
    val replay = state.mode == RouteExplorerMode.REPLAY
    val slice = state.selectedSlice
    LaunchedEffect(reading, reading?.pendingExplorerOffset) {
        reading?.pendingExplorerOffset?.let { offset ->
            scroll.scrollTo(offset); reading.pendingExplorerOffset = null
        }
    }
    Column(Modifier.fillMaxSize()) {
        WalkExplorerTimeHeader(state)
        HorizontalDivider(color = PinkFaint)
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodySmall.copy(color = TextMuted, lineHeight = 20.sp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                .testTag("explorer-reading").padding(horizontal = DiaryReadingChrome.Gutter).padding(bottom = 20.dp)) {
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
                WalkExplorerRangeActions(state, onOverview)
                if (measured && !replay && slice != null) ExplorerRangePresets(state)
                WalkExplorerSelectionDetails(state)
                if (!measured && !state.canPlayback) {
                    Text("이 산책은 시간 구간을 고를 수 있는 측정 정보가 없어요.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    if (state.review?.context?.available == true && state.review?.context?.durationMillis == null)
                        Text("기록 시간의 순서를 확정하지 못해 자동 재생을 제공하지 않아요. 전후 관계에서 해당 범위를 열 수 있어요.",
                            style = MaterialTheme.typography.bodySmall)
                }
                readingNotices()
                HorizontalDivider(color = PinkFaint)
                TextButton(onClick = { if (reading != null) reading.explorerDetails = !details else localDetails = !details },
                    modifier = Modifier.fillMaxWidth().testTag("explorer-details-toggle")
                        .semantics { stateDescription = if (details) "펼침" else "접힘" },
                    contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("기록 상세", Modifier.weight(1f), fontSize = 12.sp, color = TextMuted)
                        Text(if (details) "−" else "+", fontSize = 16.sp, color = TextMuted)
                    }
                }
                if (details) {
                    if (measured) {
                        if (!replay && slice == null) ExplorerRangePresets(state)
                        Text("기록 중 경과 시간이에요. 일시정지 시간은 제외하고, 시계 경계와 경로 공백은 이어 그리지 않아요.",
                            Modifier.padding(bottom = 12.dp), fontSize = 12.sp, lineHeight = 20.sp, color = TextMuted)
                    } else if (state.canPlayback) {
                        Text("이 산책은 시간 구간을 고를 수 있는 측정 정보가 없어요.", style = MaterialTheme.typography.bodySmall)
                    }
                    WalkExplorerRouteDetails(state, onSection, onAuxiliary, onContext)
                }
            }
        }
    }
}

/** Existing route/observation/context/passage entry points remain under the reading region. */
@Composable
private fun WalkExplorerSelectionDetails(state: WalkRouteExplorerState) {
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
}

@Composable
private fun WalkExplorerRouteDetails(state: WalkRouteExplorerState, onSection: (CompletedRouteSection) -> Unit,
    onAuxiliary: (ObservedRouteSection) -> Unit, onContext: (RecordContext) -> Unit) {
    val review = state.review ?: return
    Text("기록 " + formatRouteExplorerClock(review.summary.startedAtMillis) + "–" +
        (review.summary.endedAtMillis?.let(::formatRouteExplorerClock) ?: "진행 중"), style = MaterialTheme.typography.bodySmall)
    review.sections.forEachIndexed { ordinal, section ->
        ExplorerRouteOption("동선 ${ordinal + 1} · " + formatRouteExplorerClock(section.startedAtMillis) + "–" +
            formatRouteExplorerClock(section.endedAtMillis), state.selectedSection?.index == section.index) {
            state.selectSection(section.index); onSection(section)
        }
    }
    review.observed.sections.forEachIndexed { ordinal, section ->
        ExplorerRouteOption("관측 경로 ${ordinal + 1} · " + observedRouteLabel(section) + "\n" +
            formatRouteExplorerClock(section.startedAtMillis) + "–" + formatRouteExplorerClock(section.endedAtMillis),
            state.selectedAuxiliary?.id == section.id) { state.selectAuxiliary(section.id); onAuxiliary(section) }
    }
    if (review.context.contexts.isNotEmpty()) {
        Text("기록의 전후 관계", style = MaterialTheme.typography.titleSmall)
        review.context.contexts.forEach { value ->
            ExplorerRouteOption(recordContextTitle(value) + "\n" + recordContextTime(value), state.selectedContext?.id == value.id) {
                state.selectContext(value.id); onContext(value)
            }
        }
    }
    if (review.sections.isEmpty()) Text("이어지는 보행선이 없어요. 확인된 위치는 지도에서 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
    Text("지도에서 겹친 길을 누르면 통과 시각을 골라 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ExplorerRouteOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().background(if (selected) PinkFaint else CardWhite, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected }
            .heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 20.sp, color = TextDark,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text("›", Modifier.padding(start = 8.dp), fontSize = 18.sp, color = TextMuted)
        }
        HorizontalDivider(color = PinkFaint)
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun ExplorerRouteDetailsPreview() {
    val scope = rememberCoroutineScope()
    val read = remember { explorerPanelPreviewRead() }
    val state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read); selectSection(0) } }
    DiaryReviewTheme { Column(Modifier.padding(DiaryReadingChrome.Gutter)) {
        WalkExplorerSelectionDetails(state)
        WalkExplorerRouteDetails(state, {}, {}, {})
    } }
}
