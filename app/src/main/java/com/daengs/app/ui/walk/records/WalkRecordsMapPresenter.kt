package com.daengs.app.ui.walk.records

import androidx.compose.runtime.*
import com.daengs.app.map.features.records.*
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.daengs.app.map.style.WalkRouteAppearance
import com.daengs.app.map.style.rememberWalkStyle
import com.daengs.app.ui.theme.WalkTraceShadow
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.records.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal val DefaultRecordsTracePolicy = TraceDisplayPolicy(WalkTraceShadow.RGB)
internal val LocalRecordsTracePolicy = staticCompositionLocalOf { DefaultRecordsTracePolicy }

@Composable
internal fun rememberWalkRecordsDisplayPolicy(): WalkRecordsDisplayPolicy {
    val selected by rememberWalkStyle()
    val trace = LocalRecordsTracePolicy.current
    val diagnostics = com.daengs.app.map.provider.naver.LocalWalkMapDiagnostics.current
    SideEffect { diagnostics?.details?.set("scale", "셀로판 단계 ${trace.density}") }
    return remember(trace, selected) { WalkRecordsDisplayPolicy(trace, WalkRouteAppearance(selected.policy, selected.themeId)) }
}

internal data class RecordsTracePresentation(
    val prepared: PreparedWalkRecordsTraces?, val tiles: List<TraceRasterTile>?,
    val preparationError: String?, val compositionError: String?, val retry: () -> Unit,
)

/** Saveable user choices stay in their route owner; only derived work and its lifetime live here. */
@Composable
internal fun rememberWalkRecordsTraces(
    selection: WalkRecordsSelection?, requested: Boolean, view: TraceView,
    hiddenIds: Set<String>, policy: TraceDisplayPolicy,
): RecordsTracePresentation {
    var prepareRetry by remember(selection, policy.brush) { mutableIntStateOf(0) }
    var composeRetry by remember(selection) { mutableIntStateOf(0) }
    var prepared by remember(selection, policy.brush, prepareRetry) { mutableStateOf<PreparedWalkRecordsTraces?>(null) }
    var preparationError by remember(selection, policy.brush, prepareRetry) { mutableStateOf<String?>(null) }
    LaunchedEffect(selection, requested, policy.brush, prepareRetry) {
        val selected = selection ?: return@LaunchedEffect
        if (!requested || selected.records.isEmpty() || prepared != null) return@LaunchedEffect
        preparationError = null
        try {
            val result = prepareWalkRecordsTraces(selected, policy.brush)
            currentCoroutineContext().ensureActive()
            prepared = result
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            preparationError = "선택한 산책의 흔적을 표시하지 못했어요. 기간이나 조건을 좁혀 다시 확인해 주세요."
        }
    }
    // A new visibility/scale key owns new state immediately; late cancelled work cannot publish.
    var tiles by remember(prepared, hiddenIds, view, policy, composeRetry) { mutableStateOf<List<TraceRasterTile>?>(null) }
    var compositionError by remember(prepared, hiddenIds, view, policy, composeRetry) { mutableStateOf<String?>(null) }
    LaunchedEffect(prepared, hiddenIds, view, policy, composeRetry) {
        val ready = prepared ?: return@LaunchedEffect
        if (view == TraceView.Locations) return@LaunchedEffect
        try {
            val result = ready.compose(hiddenIds, (view as? TraceView.Overlap)?.minimum, policy)
            currentCoroutineContext().ensureActive()
            tiles = result
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            compositionError = if (view is TraceView.Overlap) ready.overlapUnavailableReason
                ?: "겹친 구간을 표시하지 못했어요. 조건을 좁히거나 전체 흔적으로 돌아가 주세요."
                else "산책 흔적을 표시하지 못했어요. 다시 시도해 주세요."
        }
    }
    return RecordsTracePresentation(prepared, tiles, preparationError, compositionError,
        { if (prepared == null) prepareRetry++ else composeRetry++ })
}

internal data class RecordsRoutePresentation(val summary: WalkSummary?, val error: String?, val retry: () -> Unit)

@Composable
internal fun rememberWalkRecordsRoute(record: WalkRecord?, source: WalkRecordsSource?): RecordsRoutePresentation {
    // Remote trace state and colour choices are not route revisions.
    val input = record?.copy(trace = null, traceState = null)
    var retry by remember(input, source) { mutableIntStateOf(0) }
    var summary by remember(input, source, retry) { mutableStateOf(input?.summary.takeIf { source == null }) }
    var error by remember(input, source, retry) { mutableStateOf<String?>(null) }
    LaunchedEffect(input, source, retry) {
        if (input == null || source == null) return@LaunchedEffect
        try {
            val result = source.loadRoute(input)
            currentCoroutineContext().ensureActive()
            check(result.sessionId == input.summary.sessionId) { "선택한 산책과 동선이 달라요." }
            summary = result
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = "산책 동선을 불러오지 못했어요."
        }
    }
    val diagnostics = com.daengs.app.map.provider.naver.LocalWalkMapDiagnostics.current
    SideEffect {
        diagnostics?.details?.set("source", when {
            input == null -> "동선 선택 없음"
            error != null -> "동선 읽기 실패"
            summary == null -> "원본 동선 읽는 중"
            source == null -> "요약 동선 · 미리보기 입력"
            else -> "원본 동선 · loadRoute"
        })
    }
    DisposableEffect(diagnostics) { onDispose { diagnostics?.details?.remove("source") } }
    return RecordsRoutePresentation(summary, error, { retry++ })
}
